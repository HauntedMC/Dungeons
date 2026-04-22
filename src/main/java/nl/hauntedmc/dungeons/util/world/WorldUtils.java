package nl.hauntedmc.dungeons.util.world;

import org.bukkit.Location;
import org.bukkit.World;

/** World-level helpers for dungeon runtime world tuning. */
public final class WorldUtils {

    /** Releases forced loading on the world spawn chunk. */
    public static void releaseSpawnChunk(World world) {
        if (world == null) return;
        Location spawn = world.getSpawnLocation();
        world.setChunkForceLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4, false);
    }
}
