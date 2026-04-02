package nl.hauntedmc.dungeons.api.chunkgenerators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.generation.StructurePieceBlock;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.util.world.BlockNbtUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector3i;

public class DungeonChunkGenerator extends FullBlockGenerator {
    private Map<Vector3i, StructurePieceBlock> blocks;
   private final Map<Vector2i, List<Vector3i>> outlines;
   private final Map<Vector2i, List<Vector3i>> chunkBlocks;
   private long nbtCount = 1L;

   public DungeonChunkGenerator(Material blockType, Collection<InstanceRoom> rooms, Map<Vector3i, StructurePieceBlock> blocks) {
      super(blockType);
      this.blocks = blocks;
      this.outlines = new HashMap<>();
      this.chunkBlocks = new HashMap<>();

      for (InstanceRoom room : rooms) {
         BoundingBox newBox = room.getBounds().clone();
         BoundingBox trimmed = newBox.clone().expand(1.0, 1.0, 1.0);

         for (double x = trimmed.getMinX(); x <= trimmed.getMaxX(); x++) {
            for (double y = trimmed.getMinY(); y <= trimmed.getMaxY(); y++) {
               for (double z = trimmed.getMinZ(); z <= trimmed.getMaxZ(); z++) {
                  boolean lessThanMax = x < trimmed.getMaxX() && y < trimmed.getMaxY() && z < trimmed.getMaxZ();
                  boolean greaterThanMin = x > trimmed.getMinX() && y > trimmed.getMinY() && z > trimmed.getMinZ();
                  List<Vector3i> chunkBlocks;
                  if (lessThanMax && greaterThanMin) {
                     chunkBlocks = this.chunkBlocks.computeIfAbsent(new Vector2i((int)x >> 4, (int)z >> 4), k -> new ArrayList<>());
                  } else {
                     chunkBlocks = this.outlines.computeIfAbsent(new Vector2i((int)x >> 4, (int)z >> 4), k -> new ArrayList<>());
                  }

                  chunkBlocks.add(new Vector3i((int)x, (int)y, (int)z));
               }
            }
         }
      }
   }

   @Override
   public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
      this.populateRoomOutlines(chunkX, chunkZ, chunkData);
      this.populateRooms(chunkX, chunkZ, worldInfo, chunkData);
   }

   private void populateRoomOutlines(int chunkX, int chunkZ, ChunkData data) {
      int xOffset = chunkX << 4;
      int zOffset = chunkZ << 4;
      Vector2i chunkVec = new Vector2i(chunkX, chunkZ);
      List<Vector3i> blockLocs = this.outlines.get(chunkVec);
      if (blockLocs != null) {
         for (Vector3i target : blockLocs) {
            data.setBlock(target.x - xOffset, target.y, target.z - zOffset, this.blockType);
         }

         this.outlines.remove(chunkVec);
      }
   }

   private void populateRooms(int chunkX, int chunkZ, WorldInfo worldInfo, ChunkData chunkData) {
      int xOffset = chunkX << 4;
      int zOffset = chunkZ << 4;
      Vector2i chunkVec = new Vector2i(chunkX, chunkZ);
      List<Vector3i> blockLocs = this.chunkBlocks.get(chunkVec);
      if (blockLocs != null) {
         for (Vector3i bTarget : blockLocs) {
            if (this.blocks == null) {
               break;
            }

            double x = bTarget.x - xOffset;
            double y = bTarget.y;
            double z = bTarget.z - zOffset;
            if (!this.blocks.containsKey(bTarget)) {
               chunkData.setBlock((int)x, (int)y, (int)z, Material.AIR);
            } else {
               StructurePieceBlock holder = this.blocks.remove(bTarget);
               if (holder != null) {
                  BlockData data = holder.getBlockData();
                  chunkData.setBlock((int)x, (int)y, (int)z, data);
                  if (this.blocks.isEmpty()) {
                     this.blocks = null;
                  }

                  World world = Bukkit.getWorld(worldInfo.getName());
                  if (world != null) {
                     String weTag = holder.getBlockNbt();
                     if (weTag != null) {
                        Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> {
                           if (Bukkit.getWorlds().contains(world)) {
                              BlockNbtUtils.applyTileSnbt(world.getBlockAt(bTarget.x, (int)y, bTarget.z), weTag);
                              this.nbtCount--;
                           }
                        }, this.nbtCount);
                        this.nbtCount++;
                     }
                  }
               }
            }
         }

         this.chunkBlocks.remove(chunkVec);
      }
   }

   public Map<Vector3i, StructurePieceBlock> getBlocks() {
      return this.blocks;
   }
}
