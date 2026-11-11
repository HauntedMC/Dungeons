package nl.hauntedmc.dungeons.runtime.dungeon;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;
import nl.hauntedmc.dungeons.util.world.WorldUtils;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.slf4j.Logger;

/** Removes only unloaded instance folders owned by one dungeon, preserving editor recovery. */
public final class InstanceWorldCleanup {
    private final String dungeonName;
    private final Logger logger;

    public InstanceWorldCleanup(String dungeonName, Logger logger) {
        this.dungeonName = dungeonName;
        this.logger = logger;
    }

    public void clean() {
        File[] worlds =
                Stream.of(Bukkit.getWorldContainer(), WorldUtils.dimensionRoot())
                        .flatMap(
                                root -> {
                                    File[] children = root.listFiles();
                                    return children == null
                                            ? Stream.<File>empty()
                                            : Arrays.stream(children);
                                })
                        .toArray(File[]::new);
        if (worlds != null) {
            for (File world : worlds) {
                if (world.isDirectory()
                        && this.isOwnedInstanceWorldFolder(world)
                        && !this.hasEditorRecovery(world.getName())
                        && Bukkit.getWorlds().stream()
                                .noneMatch(
                                        loaded ->
                                                loaded.getName()
                                                        .equalsIgnoreCase(world.getName()))) {
                    try {
                        FileUtils.deleteDirectory(world);
                    } catch (IOException exception) {
                        this.logger.error(
                                "Failed to delete stale instance folder '{}'.",
                                world.getAbsolutePath(),
                                exception);
                    }
                }
            }
        }
    }

    private boolean hasEditorRecovery(String worldName) {
        // Retired folders were detached only after a successful final save and unload.
        if (worldName.contains(".disposed-")) return false;
        return Stream.of(
                        new File(Bukkit.getWorldContainer(), worldName),
                        WorldUtils.dimensionFolder(worldName))
                .anyMatch(folder -> new File(folder, WorldUtils.EDITOR_RECOVERY_MARKER).exists());
    }

    private boolean isOwnedInstanceWorldFolder(File worldFolder) {
        String worldName = worldFolder.getName();
        String prefix = this.dungeonName + "_";
        if (!worldName.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            return false;
        }

        String suffix = worldName.substring(prefix.length());
        int retired = suffix.indexOf(".disposed-");
        if (retired >= 0) {
            String token = suffix.substring(retired + ".disposed-".length());
            try {
                if (token.length() != 36) return false;
                UUID.fromString(token);
            } catch (IllegalArgumentException invalid) {
                return false;
            }
            suffix = suffix.substring(0, retired);
        }
        if (suffix.isEmpty()) {
            return false;
        }

        for (int i = 0; i < suffix.length(); i++) {
            if (!Character.isDigit(suffix.charAt(i))) {
                return false;
            }
        }

        return true;
    }
}
