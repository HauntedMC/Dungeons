package nl.hauntedmc.dungeons.util.entity;

import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.block.Structure;
import org.bukkit.block.data.type.StructureBlock;
import org.bukkit.block.data.type.StructureBlock.Mode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Particle and structure-box rendering helpers used by editor previews. */
public final class ParticleUtils {
    /** Displays one or more bounding boxes with default particle. */
    public static void displayBoundingBox(Player to, int ticks, BoundingBox... boxes) {
        displayBoundingBox(to, ticks, Particle.WAX_OFF, boxes);
    }

    /** Displays bounding boxes repeatedly for a limited number of ticks. */
    public static void displayBoundingBox(
            final Player to, int ticks, final Particle particle, final BoundingBox... boxes) {
        BukkitRunnable ticker =
                new BukkitRunnable() {
                    public void run() {
                        ParticleUtils.displayBoundingBox(to, particle, boxes);
                    }
                };
        ticker.runTaskTimer(RuntimeContext.plugin(), 0L, 10L);
        Bukkit.getScheduler().runTaskLater(RuntimeContext.plugin(), ticker::cancel, ticks);
    }

    /** Displays bounding boxes immediately with default particle. */
    public static void displayBoundingBox(Player to, BoundingBox... boxes) {
        displayBoundingBox(to, Particle.WAX_OFF, boxes);
    }

    /** Displays fake structure blocks to visualize one or more bounding boxes. */
    public static void displayStructureBox(Player to, BoundingBox... boxes) {
        for (BoundingBox box : boxes) {
            StructureBlock struct = (StructureBlock) Bukkit.createBlockData(Material.STRUCTURE_BLOCK);
            struct.setMode(Mode.SAVE);
            Location target = box.getMin().toLocation(to.getWorld()).subtract(0.0, 1.0, 0.0);
            to.sendBlockChange(target, struct);
            Structure structure = (Structure) struct.createBlockState();
            structure.setStructureSize(
                    new BlockVector(box.getWidthX(), box.getHeight(), box.getWidthZ()));
            to.sendBlockUpdate(target, structure);
        }
    }

    /** Clears structure-box preview blocks for all players in a world. */
    public static void clearStructureBox(World world, BoundingBox... boxes) {
        for (Player player : world.getPlayers()) {
            clearStructureBox(player, boxes);
        }
    }

    /** Clears structure-box preview blocks for one player. */
    public static void clearStructureBox(Player player, BoundingBox... boxes) {
        for (BoundingBox box : boxes) {
            Location target = box.getMin().toLocation(player.getWorld()).subtract(0.0, 1.0, 0.0);
            player.sendBlockChange(target, target.getBlock().getBlockData());
        }
    }

    /** Displays one or more bounding boxes with explicit particle type. */
    public static void displayBoundingBox(Player to, Particle particle, BoundingBox... boxes) {
        displayBoundingBox(to, particle, null, boxes);
    }

    /** Displays one or more bounding boxes with optional dust options. */
    public static void displayBoundingBox(
            Player to, Particle particle, DustOptions opt, BoundingBox... boxes) {
        for (BoundingBox box : boxes) {
            drawOutline3D(to, box, particle, opt);
        }
    }

    /** Draws a particle line between two positions. */
    public static void drawLine(
            Player to, Location pos1, Location pos2, Particle particle, DustOptions opt) {
        if (pos1.getWorld().equals(pos2.getWorld())) {
            double distance = pos1.distance(pos2);
            int points = (int) Math.floor(distance) * 3;
            double gap = distance / points;
            Vector add = pos1.toVector().subtract(pos2.toVector()).normalize().multiply(gap);
            Location start = pos1.clone();
            if (particle == Particle.DUST && opt == null) {
                opt = new DustOptions(Color.RED, 0.5F);
            }

            if (opt != null) {
                for (int i = 0; i <= points; i++) {
                    to.spawnParticle(particle, start, 1, opt);
                    start = start.subtract(add);
                }

                return;
            }

            for (int i = 0; i <= points; i++) {
                to.spawnParticle(particle, start, 1, 0.0, 0.0, 0.0, 0.0);
                start = start.subtract(add);
            }
        }
    }

    /** Draws a 3D rectangular outline with particles. */
    public static void drawOutline3D(
            Player to, BoundingBox region, Particle particle, DustOptions opt) {
        Location min =
                new Location(to.getWorld(), region.getMinX(), region.getMinY(), region.getMinZ());
        Location max =
                new Location(to.getWorld(), region.getMaxX(), region.getMaxY(), region.getMaxZ());
        Location bottomFrontRight = new Location(to.getWorld(), max.getX(), min.getY(), min.getZ());
        Location bottomBackLeft = new Location(to.getWorld(), min.getX(), min.getY(), max.getZ());
        Location bottomBackRight = new Location(to.getWorld(), max.getX(), min.getY(), max.getZ());
        Location topFrontRight = new Location(to.getWorld(), max.getX(), max.getY(), min.getZ());
        Location topBackLeft = new Location(to.getWorld(), min.getX(), max.getY(), max.getZ());
        Location topFrontLeft = new Location(to.getWorld(), min.getX(), max.getY(), min.getZ());
        drawLine(to, min, bottomFrontRight, particle, opt);
        drawLine(to, min, bottomBackLeft, particle, opt);
        drawLine(to, bottomBackRight, bottomFrontRight, particle, opt);
        drawLine(to, bottomBackRight, bottomBackLeft, particle, opt);
        drawLine(to, min, topFrontLeft, particle, opt);
        drawLine(to, bottomFrontRight, topFrontRight, particle, opt);
        drawLine(to, bottomBackLeft, topBackLeft, particle, opt);
        drawLine(to, bottomBackRight, max, particle, opt);
        drawLine(to, max, topFrontRight, particle, opt);
        drawLine(to, max, topBackLeft, particle, opt);
        drawLine(to, topFrontLeft, topFrontRight, particle, opt);
        drawLine(to, topFrontLeft, topBackLeft, particle, opt);
    }
}
