package nl.hauntedmc.dungeons.util.world;

import java.util.concurrent.ExecutionException;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.BoundingBox;

/**
 * Location and bounding-box helpers used by dungeon generation and runtime flows.
 */
public final class LocationUtils {

    /** Reads a location from config section coordinates with null world. */
    public static Location readLocation(ConfigurationSection config) {
        if (config == null) {
            return null;
        } else {
            return new Location(
                    null,
                    config.getDouble("x"),
                    config.getDouble("y"),
                    config.getDouble("z"),
                    (float) config.getDouble("yaw"),
                    (float) config.getDouble("pitch"));
        }
    }

    /** Reads a location or returns a fallback default when absent. */
    public static Location readLocation(ConfigurationSection config, Location def) {
        Location loc = readLocation(config);
        if (loc == null) {
            loc = def;
        }
        return loc;
    }

    /** Writes location coordinates to a nested config section path. */
    public static void writeLocation(String path, ConfigurationSection config, Location loc) {
        if (config == null || loc == null) return;
        ConfigurationSection section = config.createSection(path);
        // section.set("world", loc.getWorld().getName());  // Currently not part of the plugin
        section.set("x", loc.getX());
        section.set("y", loc.getY());
        section.set("z", loc.getZ());
        section.set("yaw", loc.getYaw());
        section.set("pitch", loc.getPitch());
    }

    /** Returns an axis-aligned bounding box from two corners. */
    public static BoundingBox captureBoundingBox(Location pos1, Location pos2) {
        return BoundingBox.of(pos1, pos2);
    }

    /** Returns a centered bounding box variant shifted by half a block in each axis. */
    public static BoundingBox captureOffsetBoundingBox(Location pos1, Location pos2) {
        BoundingBox box = BoundingBox.of(pos1, pos2);
        box.shift(0.5, 0.5, 0.5);
        return box;
    }

    /** Finds a safe standing location in a box, marshalling to main thread when needed. */
    public static Location findSafeLocationInBox(World world, BoundingBox box) {
        if (world == null || box == null) {
            return null;
        }

        if (!Bukkit.isPrimaryThread()) {
            if (!RuntimeContext.plugin().isEnabled()) {
                RuntimeContext.logger()
                        .warn(
                                "Refused to search for a safe spawn off-thread in world '{}' because the plugin is disabled.",
                                world.getName());
                return null;
            }

            try {
                return Bukkit.getScheduler()
                        .callSyncMethod(
                                RuntimeContext.plugin(), () -> findSafeLocationInBoxSync(world, box))
                        .get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                RuntimeContext.logger()
                        .warn(
                                "Safe spawn search was interrupted for world '{}' within box {}.",
                                world.getName(),
                                box,
                                exception);
                return null;
            } catch (ExecutionException exception) {
                Throwable root = exception.getCause() == null ? exception : exception.getCause();
                RuntimeContext.logger()
                        .error(
                                "Safe spawn search failed for world '{}' within box {}.",
                                world.getName(),
                                box,
                                root);
                return null;
            }
        }

        return findSafeLocationInBoxSync(world, box);
    }

    /** Main-thread implementation for safe spawn search in a bounding box. */
    private static Location findSafeLocationInBoxSync(World world, BoundingBox box) {
        int timeout = PluginConfigView.getSafeSpawnSearchTimeoutSeconds(RuntimeContext.config());
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime <= timeout * 1000L) {
            double x = selectCoordinate(box.getMinX(), box.getMaxX());
            double z = selectCoordinate(box.getMinZ(), box.getMaxZ());
            int chunkX = ((int) Math.floor(x)) >> 4;
            int chunkZ = ((int) Math.floor(z)) >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ, true);
            }

            Location target = findSafeStandingLocation(world, box, x, z);
            if (target != null) {
                RuntimeContext.logger()
                        .debug(
                                "Safe spawn search for world '{}' completed in {}ms.",
                                world.getName(),
                                System.currentTimeMillis() - startTime);
                return target;
            }
        }

        RuntimeContext.logger()
                .warn(
                        "Safe spawn search timed out after {}s for world '{}' within box {}.",
                        timeout,
                        world.getName(),
                        box);
        return null;
    }

    /** Selects one candidate x/z coordinate inside bounds with block-center offset. */
    private static double selectCoordinate(double min, double max) {
        int minBlock = (int) Math.floor(min) + 1;
        int maxBlock = (int) Math.ceil(max) - 1;
        if (minBlock > maxBlock) {
            return Math.floor((min + max) / 2.0D) + 0.5D;
        }

        return MathUtils.getRandomNumberInRange(minBlock, maxBlock) + 0.5D;
    }

    /** Scans downward for a non-colliding standing box with solid ground below. */
    private static Location findSafeStandingLocation(
            World world, BoundingBox box, double x, double z) {
        for (double y = box.getMaxY() - 1.0D; y >= box.getMinY(); y--) {
            BoundingBox standingBox =
                    new BoundingBox(x - 0.4D, y, z - 0.4D, x + 0.4D, y + 1.9D, z + 0.4D);
            if (world.hasCollisionsIn(standingBox)) {
                continue;
            }

            BoundingBox groundBox = new BoundingBox(x - 0.4D, y - 1.0D, z - 0.4D, x + 0.4D, y, z + 0.4D);
            if (!world.hasCollisionsIn(groundBox)) {
                continue;
            }

            return new Location(world, x, y, z);
        }

        return null;
    }
}
