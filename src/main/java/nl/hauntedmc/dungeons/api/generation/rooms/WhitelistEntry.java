package nl.hauntedmc.dungeons.api.generation.rooms;

import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.config.SerializableFile;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonProcedural;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

public class WhitelistEntry implements SerializableFile {
   private DungeonProcedural dungeon;
   @SavedField
   private String roomName;
   @SavedField
   private String materialName;
   @SavedField
   private double weight;

   public WhitelistEntry(DungeonRoomContainer room) {
      this.dungeon = room.getDungeon();
      this.roomName = room.getNamespace();
      this.weight = room.getWeight();
   }

   public void setRoom(DungeonRoomContainer room) {
      this.roomName = room.getNamespace();
   }

   public void setMaterial(Material mat) {
      this.materialName = mat.name();
   }

   public void setWeight(double weight) {
      this.weight = Math.max(0.0, weight);
   }

   @Nullable
   public Material getMaterial() {
      try {
         return Material.valueOf(this.materialName != null ? this.materialName : "STRUCTURE_BLOCK");
      } catch (IllegalArgumentException var2) {
         return null;
      }
   }

   @Nullable
   public DungeonRoomContainer getRoom() {
      return this.dungeon == null ? null : this.dungeon.getRoom(this.roomName);
   }

   public DungeonRoomContainer getRoom(DungeonProcedural dungeon) {
      return dungeon.getRoom(this.roomName);
   }

   public DungeonProcedural getDungeon() {
      return this.dungeon;
   }

   public void setDungeon(DungeonProcedural dungeon) {
      this.dungeon = dungeon;
   }

   public String getRoomName() {
      return this.roomName;
   }

   public double getWeight() {
      return this.weight;
   }
}
