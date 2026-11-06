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
        return Bukkit.getWorldContainer()
                .toPath()
                .resolve(Bukkit.getUnsafe().getMainLevelName())
                .resolve("dimensions")
                .resolve("minecraft")
                .resolve(worldName.toLowerCase(Locale.ROOT))
                .toFile();
    }

    /** Releases forced loading on the world spawn chunk. */
    public static void releaseSpawnChunk(World world) {
        if (world == null) return;
        Location spawn = world.getSpawnLocation();
        world.setChunkForceLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4, false);
    }
}
