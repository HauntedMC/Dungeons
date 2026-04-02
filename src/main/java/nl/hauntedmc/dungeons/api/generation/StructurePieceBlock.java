package nl.hauntedmc.dungeons.api.generation;

import java.util.HashMap;
import java.util.Map;

import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.version.NBTEditor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

public class StructurePieceBlock implements Cloneable {
   private final int x;
   private final int y;
   private final int z;
   private BlockData blockData;
   @Nullable
   private NBTEditor.NBTCompound blockNbt;
   private static final Map<String, BlockData> dataCache = new HashMap<>();

   public StructurePieceBlock(Location loc, BlockData data, NBTEditor.NBTCompound nbt) {
      this(new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), data, nbt);
   }

   public StructurePieceBlock(Vector3i pos, BlockData data, @Nullable NBTEditor.NBTCompound nbt) {
      this.x = pos.x;
      this.y = pos.y;
      this.z = pos.z;
      this.blockData = checkCache(data);
      this.blockNbt = nbt;
   }

   public void setBlockData(BlockData data) {
      this.blockData = checkCache(data);
   }

   public Vector3i getPos() {
      return new Vector3i(this.x, this.y, this.z);
   }

   public void placeAt(Location loc) {
      World world = loc.getWorld();
      world.setBlockData(loc, this.blockData);
      NBTEditor.set(world.getBlockAt(loc), this.blockNbt);
   }

   public static StructurePieceBlock from(Block block) {
      NBTEditor.NBTCompound compound = NBTEditor.getNBTCompound(block);
      return new StructurePieceBlock(block.getLocation(), block.getBlockData().clone(), compound);
   }

   @Override
   public String toString() {
      return this.blockData.getAsString(true);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj == null) {
         return false;
      } else if (this.getClass() != obj.getClass()) {
         return false;
      } else {
         StructurePieceBlock compare = (StructurePieceBlock)obj;
         if (this.x != compare.x) {
            return false;
         } else if (this.y != compare.y) {
            return false;
         } else if (this.z != compare.z) {
            return false;
         } else {
            return this.blockData.equals(compare.blockData) && (this.blockNbt == null || this.blockNbt.equals(compare.blockNbt)) && (compare.blockNbt == null || compare.blockNbt.equals(this.blockNbt));
         }
      }
   }

   public StructurePieceBlock clone() {
      try {
         StructurePieceBlock clone = (StructurePieceBlock)super.clone();
         clone.blockData = this.blockData;
         clone.blockNbt = this.blockNbt;
         return clone;
      } catch (CloneNotSupportedException var2) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var2.getMessage());
         return this;
      }
   }

   private static BlockData checkCache(BlockData data) {
      String str = data.getAsString(true);
      return dataCache.computeIfAbsent(str, k -> data);
   }

   public static StructurePieceBlock air(Vector3i pos) {
      return new StructurePieceBlock(pos, Bukkit.createBlockData(Material.AIR), null);
   }

   public int getX() {
      return this.x;
   }

   public int getY() {
      return this.y;
   }

   public int getZ() {
      return this.z;
   }

   public BlockData getBlockData() {
      return this.blockData;
   }

   @Nullable
   public NBTEditor.NBTCompound getBlockNbt() {
      return this.blockNbt;
   }
}
