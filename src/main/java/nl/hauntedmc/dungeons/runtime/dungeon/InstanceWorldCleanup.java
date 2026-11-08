package nl.hauntedmc.dungeons.runtime.dungeon;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Removes only unloaded instance folders owned by one dungeon, preserving editor recovery. */
public final class InstanceWorldCleanup {
    private final String dungeonName;
    private final org.slf4j.Logger logger;

    public InstanceWorldCleanup(String dungeonName, org.slf4j.Logger logger) {
        this.dungeonName = dungeonName;
        this.logger = logger;
    }

    public void clean() {
        File[] worlds =
                java.util.stream.Stream.of(
                                Bukkit.getWorldContainer(),
                                nl.hauntedmc.dungeons.util.world.WorldUtils.dimensionRoot())
                        .flatMap(
                                root -> {
                                    File[] children = root.listFiles();
                                    return children == null
                                            ? java.util.stream.Stream.<File>empty()
                                            : java.util.Arrays.stream(children);
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
                        this.logger
                                .error(
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
        return java.util.stream.Stream.of(
                        new File(Bukkit.getWorldContainer(), worldName),
                        nl.hauntedmc.dungeons.util.world.WorldUtils.dimensionFolder(worldName))
                .anyMatch(
                        folder ->
                                new File(
                                                folder,
                                                nl.hauntedmc.dungeons.util.world.WorldUtils
                                                        .EDITOR_RECOVERY_MARKER)
                                        .exists());
    }

    private boolean isOwnedInstanceWorldFolder(File worldFolder) {
        String worldName = worldFolder.getName();
        String prefix = this.dungeonName + "_";
        if (!worldName
                .toLowerCase(java.util.Locale.ROOT)
                .startsWith(prefix.toLowerCase(java.util.Locale.ROOT))) {
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
