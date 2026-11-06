package nl.hauntedmc.dungeons.util.world;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.File;
import java.util.Locale;

/** World-level helpers for dungeon runtime world tuning. */
public final class WorldUtils {
    private WorldUtils() {}

    /** Resolves the storage used by Paper 26.1+ for a legacy-named custom world. */
    public static File dimensionFolder(String worldName) {
        return dimensionRoot().toPath().resolve(worldName.toLowerCase(Locale.ROOT)).toFile();
    }

    /** Directory containing custom Minecraft dimensions. */
    public static File dimensionRoot() {
        return Bukkit.getWorldContainer()
                .toPath()
                .resolve(Bukkit.getUnsafe().getMainLevelName())
                .resolve("dimensions/minecraft")
                .toFile();
    }

    /** Commits a complete world snapshot without retaining deleted or legacy world data. */
    public static void saveTemplate(File worldFolder, File template) throws java.io.IOException {
        var target = template.toPath().toAbsolutePath();
        var staging =
                java.nio.file.Files.createTempDirectory(
                        target.getParent().getParent(), ".dungeons-save-");
        var snapshot = staging.resolve("snapshot");
        var previous = staging.resolve("previous");
        boolean committed = false;
        try {
            org.apache.commons.io.FileUtils.copyDirectory(template, snapshot.toFile());
            // Dungeon configuration, functions and room templates remain in the staged template.
            for (String entry :
                    java.util.List.of(
                            "region",
                            "entities",
                            "poi",
                            "data",
                            "DIM1",
                            "DIM-1",
                            "dimensions",
                            "players",
                            "playerdata",
                            "stats",
                            "advancements",
                            "level.dat",
                            "level.dat_old",
                            "uid.dat",
                            "session.lock")) {
                File old = snapshot.resolve(entry).toFile();
                if (old.isDirectory()) org.apache.commons.io.FileUtils.deleteDirectory(old);
                else java.nio.file.Files.deleteIfExists(old.toPath());
            }
            org.apache.commons.io.FileUtils.copyDirectory(
                    worldFolder,
                    snapshot.toFile(),
                    file ->
                            !file.getName().equals("rooms")
                                    && !file.getName().equals("session.lock"));
            java.nio.file.Files.deleteIfExists(snapshot.resolve("uid.dat"));
            java.nio.file.Files.move(target, previous);
            try {
                java.nio.file.Files.move(snapshot, target);
            } catch (java.io.IOException failure) {
                try {
                    java.nio.file.Files.move(previous, target);
                } catch (java.io.IOException rollback) {
                    failure.addSuppressed(rollback);
                    throw new java.io.IOException(
                            "Template backup retained at " + previous, failure);
                }
                throw failure;
            }
            committed = true;
        } finally {
            // Never remove the only surviving template if restoring the original failed.
            if (committed || !java.nio.file.Files.exists(previous)) {
                org.apache.commons.io.FileUtils.deleteDirectory(staging.toFile());
            }
        }
    }

    /** Updates removed registry names in copied per-world configuration before Paper loads it. */
    public static void migratePaperWorldConfig(File file) throws java.io.IOException {
        if (!file.isFile()) return;
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        try {
            yaml.load(file);
        } catch (org.bukkit.configuration.InvalidConfigurationException exception) {
            throw new java.io.IOException("Invalid Paper world configuration: " + file, exception);
        }
        var limits = yaml.getConfigurationSection("chunks.entity-per-chunk-save-limit");
        if (limits == null) return;
        boolean changed = false;
        for (String oldKey : java.util.List.of("potion", "minecraft:potion")) {
            if (!limits.contains(oldKey)) continue;
            Object limit = limits.get(oldKey);
            String prefix = oldKey.startsWith("minecraft:") ? "minecraft:" : "";
            for (String replacement : java.util.List.of("splash_potion", "lingering_potion")) {
                if (!limits.contains(prefix + replacement)) limits.set(prefix + replacement, limit);
            }
            limits.set(oldKey, null);
            changed = true;
        }
        if (changed) yaml.save(file);
    }

    /** Releases forced loading on the world spawn chunk. */
    public static void releaseSpawnChunk(World world) {
        if (world == null) return;
        Location spawn = world.getSpawnLocation();
        world.setChunkForceLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4, false);
    }
}
