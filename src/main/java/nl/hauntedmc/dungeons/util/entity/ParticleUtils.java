package nl.hauntedmc.dungeons.util.entity;

import nl.hauntedmc.dungeons.Dungeons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Structure;
import org.bukkit.block.data.type.StructureBlock;
import org.bukkit.block.data.type.StructureBlock.Mode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BlockVector;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class ParticleUtils {
   public static void displayBoundingBox(Player to, int ticks, BoundingBox... boxes) {
      displayBoundingBox(to, ticks, Particle.WAX_OFF, boxes);
   }

   public static void displayBoundingBox(final Player to, int ticks, final Particle particle, final BoundingBox... boxes) {
      BukkitRunnable ticker = new BukkitRunnable() {
         public void run() {
            ParticleUtils.displayBoundingBox(to, particle, boxes);
         }
      };
      ticker.runTaskTimer(Dungeons.inst(), 0L, 10L);
      Bukkit.getScheduler().runTaskLater(Dungeons.inst(), ticker::cancel, ticks);
   }

   public static void displayBoundingBox(Player to, BoundingBox... boxes) {
      displayBoundingBox(to, Particle.WAX_OFF, boxes);
   }

   public static void displayStructureBox(Player to, BoundingBox... boxes) {
      for (BoundingBox box : boxes) {
         StructureBlock struct = (StructureBlock)Bukkit.createBlockData(Material.STRUCTURE_BLOCK);
         struct.setMode(Mode.SAVE);
         Location target = box.getMin().toLocation(to.getWorld()).subtract(0.0, 1.0, 0.0);
         to.sendBlockChange(target, struct);
         Structure structure = (Structure)struct.createBlockState();
         structure.setStructureSize(new BlockVector(box.getWidthX(), box.getHeight(), box.getWidthZ()));
         to.sendBlockUpdate(target, structure);
      }
   }

   public static void clearStructureBox(World world, BoundingBox... boxes) {
      for (Player player : world.getPlayers()) {
         clearStructureBox(player, boxes);
      }
   }

   public static void clearStructureBox(Player player, BoundingBox... boxes) {
      for (BoundingBox box : boxes) {
         Location target = box.getMin().toLocation(player.getWorld()).subtract(0.0, 1.0, 0.0);
         player.sendBlockChange(target, target.getBlock().getBlockData());
      }
   }

   public static void displayBoundingBox(Player to, Particle particle, BoundingBox... boxes) {
      displayBoundingBox(to, particle, null, boxes);
   }

   public static void displayBoundingBox(Player to, Particle particle, DustOptions opt, BoundingBox... boxes) {
      for (BoundingBox box : boxes) {
         drawOutline3D(to, box, particle, opt);
      }
   }

   public static void drawLine(Player to, Location pos1, Location pos2, Particle particle) {
      DustOptions dustOptions = null;
      if (particle == Particle.DUST) {
         dustOptions = new DustOptions(Color.RED, 0.5F);
      }

      drawLine(to, pos1, pos2, particle, dustOptions);
   }

   public static void drawLine(Player to, Location pos1, Location pos2, Particle particle, DustOptions opt) {
      if (pos1.getWorld().equals(pos2.getWorld())) {
         double distance = pos1.distance(pos2);
         int points = (int)Math.floor(distance) * 3;
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

   public static void drawOutline3D(Player to, BoundingBox region, Particle particle) {
      drawOutline3D(to, region, particle, null);
   }

   public static void drawOutline3D(Player to, BoundingBox region, Particle particle, DustOptions opt) {
      Location min = new Location(to.getWorld(), region.getMinX(), region.getMinY(), region.getMinZ());
      Location max = new Location(to.getWorld(), region.getMaxX(), region.getMaxY(), region.getMaxZ());
      Location b2 = new Location(to.getWorld(), max.getX(), min.getY(), min.getZ());
      Location b3 = new Location(to.getWorld(), min.getX(), min.getY(), max.getZ());
      Location b4 = new Location(to.getWorld(), max.getX(), min.getY(), max.getZ());
      Location t2 = new Location(to.getWorld(), max.getX(), max.getY(), min.getZ());
      Location t3 = new Location(to.getWorld(), min.getX(), max.getY(), max.getZ());
      Location t4 = new Location(to.getWorld(), min.getX(), max.getY(), min.getZ());
      drawLine(to, min, b2, particle, opt);
      drawLine(to, min, b3, particle, opt);
      drawLine(to, b4, b2, particle, opt);
      drawLine(to, b4, b3, particle, opt);
      drawLine(to, min, t4, particle, opt);
      drawLine(to, b2, t2, particle, opt);
      drawLine(to, b3, t3, particle, opt);
      drawLine(to, b4, max, particle, opt);
      drawLine(to, max, t2, particle, opt);
      drawLine(to, max, t3, particle, opt);
      drawLine(to, t4, t2, particle, opt);
      drawLine(to, t4, t3, particle, opt);
   }

}
