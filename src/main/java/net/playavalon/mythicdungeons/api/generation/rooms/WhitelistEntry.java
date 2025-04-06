package net.playavalon.mythicdungeons.api.generation.rooms;

import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.config.AvalonSerializable;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

public class WhitelistEntry implements AvalonSerializable {
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
