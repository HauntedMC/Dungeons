package net.playavalon.mythicdungeons.utility;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.map.MapView.Scale;
import org.jetbrains.annotations.NotNull;

public class DungeonMapRenderer extends MapRenderer {
   private InstancePlayable instance;
   private World world;
   private int floorDepth;
   private Map<UUID, Location> players;

   public DungeonMapRenderer(InstancePlayable instance) {
      this.instance = instance;
      this.world = instance.getInstanceWorld();
      this.floorDepth = instance.getConfig().getInt("Map.FloorDepth", 3);
      this.players = new WeakHashMap<>();
   }

   public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
      Location loc = this.players.get(player.getUniqueId());
      Location currentLoc = player.getLocation().getBlock().getLocation();
      if (!currentLoc.equals(loc)) {
         loc = currentLoc.subtract(0.0, 1.0, 0.0);
         this.players.put(player.getUniqueId(), currentLoc);
         Scale scale = map.getScale();
         int size = this.getScaleSize(scale);
         int center = size / 2 - 1;
         int minX = loc.getBlockX() - center;
         int minZ = loc.getBlockZ() - center;
         int maxX = loc.getBlockX() + center;
         int maxZ = loc.getBlockZ() + center;

         for (int mx = 0; mx <= size; mx++) {
            for (int mz = 0; mz <= size; mz++) {
               int x = minX + mx;
               int z = minZ + mz;
               int y = loc.getBlockY();
               int minY = y - 1;
               int maxY = y + this.floorDepth;
               int highestY = minY;

               for (int var27 = minY; var27 <= maxY; var27++) {
                  if (!this.world.getBlockAt(x, var27, z).isEmpty()) {
                     highestY = var27;
                  }
               }

               Location target = new Location(this.world, x, highestY, z);
               Block block = this.world.getBlockAt(target);
               if (block.isEmpty()) {
                  canvas.setPixelColor(mx, mz, new Color(217, 184, 134));
               } else {
                  canvas.setPixelColor(mx, mz, new Color(block.getBlockData().getMapColor().asRGB()));
               }
            }
         }

         MapCursorCollection cursors = new MapCursorCollection();
         MapCursor cursor = new MapCursor((byte)0, (byte)0, this.getDirectionId(player.getFacing()), (byte)0, true);
         cursors.addCursor(cursor);
         canvas.setCursors(cursors);
         int floor = (loc.getBlockY() - 128) / this.floorDepth;
         canvas.drawText(1, 1, MinecraftFont.Font, "F" + (floor + 1));
      }
   }

   private int getScaleSize(Scale scale) {
      switch (scale) {
         case CLOSEST:
            return 128;
         case CLOSE:
            return 256;
         case NORMAL:
            return 512;
         case FAR:
            return 1024;
         case FARTHEST:
            return 2048;
         default:
            return 128;
      }
   }

   private byte getDirectionId(BlockFace face) {
      switch (face) {
         case SOUTH:
            return 0;
         case SOUTH_SOUTH_WEST:
            return 1;
         case SOUTH_WEST:
            return 2;
         case WEST_SOUTH_WEST:
            return 3;
         case WEST:
            return 4;
         case WEST_NORTH_WEST:
            return 5;
         case NORTH_WEST:
            return 6;
         case NORTH_NORTH_WEST:
            return 7;
         case NORTH:
            return 8;
         case NORTH_NORTH_EAST:
            return 9;
         case NORTH_EAST:
            return 10;
         case EAST_NORTH_EAST:
            return 11;
         case EAST:
            return 12;
         case EAST_SOUTH_EAST:
            return 13;
         case SOUTH_EAST:
            return 14;
         case SOUTH_SOUTH_EAST:
            return 15;
         default:
            return 0;
      }
   }
}
