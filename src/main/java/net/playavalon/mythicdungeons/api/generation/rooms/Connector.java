package net.playavalon.mythicdungeons.api.generation.rooms;

import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.config.AvalonSerializable;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.utility.SimpleLocation;
import org.bukkit.util.Vector;

public class Connector implements Cloneable, AvalonSerializable {
   DungeonProcedural dungeon;
   @SavedField
   private SimpleLocation location;
   @SavedField
   private double successChance = 0.5;
   @SavedField
   private List<WhitelistEntry> roomBlacklist = new ArrayList<>();
   @SavedField
   private List<WhitelistEntry> roomWhitelist = new ArrayList<>();
   @SavedField
   private ConnectorDoor door;

   public Connector(SimpleLocation location) {
      this.location = location;
      this.door = new ConnectorDoor(this);
   }

   public List<WhitelistEntry> getValidRooms(DungeonProcedural dungeon, DungeonRoomContainer parent) {
      List<WhitelistEntry> rooms = new ArrayList<>();
      if (this.roomWhitelist != null && !this.roomWhitelist.isEmpty()) {
         for (WhitelistEntry entry : this.roomWhitelist) {
            DungeonRoomContainer validRoom = entry.getRoom(dungeon);
            if (!validRoom.getConnectors().isEmpty() && validRoom.canGenerate(parent)) {
               rooms.add(entry);
            }
         }
      } else {
         for (WhitelistEntry entryx : parent.getValidRooms()) {
            DungeonRoomContainer validRoom = entryx.getRoom(dungeon);
            if (validRoom != parent) {
               rooms.add(entryx);
            }
         }
      }

      if (this.roomBlacklist != null && !this.roomBlacklist.isEmpty()) {
         for (WhitelistEntry entryxx : this.roomBlacklist) {
            rooms.remove(entryxx);
         }
      }

      return rooms;
   }

   public boolean canGenerate(DungeonRoomContainer room) {
      if (this.roomWhitelist != null && !this.roomWhitelist.isEmpty()) {
         for (WhitelistEntry entry : this.roomWhitelist) {
            if (entry.getRoomName().equals(room.getNamespace())) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   @Deprecated
   public boolean canGenerate(Connector connector) {
      new ArrayList();
      if (this.roomWhitelist != null) {
         for (WhitelistEntry var4 : this.roomWhitelist) {
            ;
         }
      }

      List<WhitelistEntry> otherWhite = connector.roomWhitelist;
      if (otherWhite != null) {
         for (WhitelistEntry var5 : otherWhite) {
            ;
         }
      }

      return false;
   }

   public void setDungeon(DungeonProcedural dungeon) {
      this.dungeon = dungeon;
      if (this.roomWhitelist != null) {
         this.roomWhitelist.forEach(v -> v.setDungeon(dungeon));
      }

      if (this.roomBlacklist != null) {
         this.roomBlacklist.forEach(v -> v.setDungeon(dungeon));
      }
   }

   public Connector copy(Vector offset) {
      SimpleLocation rotatedLoc = this.location.clone();
      rotatedLoc.shift(offset);
      Connector newCon = this.copy(rotatedLoc);
      newCon.door = this.door.copy(offset);
      return newCon;
   }

   public Connector copy(SimpleLocation rotatedLoc) {
      Connector newCon = new Connector(rotatedLoc);
      newCon.setRoomWhitelist(this.roomWhitelist);
      newCon.setRoomBlacklist(this.roomBlacklist);
      newCon.setSuccessChance(this.successChance);
      ConnectorDoor door = this.door == null ? new ConnectorDoor(this) : this.door;
      newCon.setDoor(door);
      return newCon;
   }

   public Connector clone() {
      try {
         Connector clone = (Connector)super.clone();
         clone.location = this.location.clone();
         clone.roomBlacklist = new ArrayList<>(this.roomBlacklist);
         clone.roomWhitelist = new ArrayList<>(this.roomWhitelist);
         return clone;
      } catch (CloneNotSupportedException var2) {
         var2.printStackTrace();
         return null;
      }
   }

   public Connector clone(SimpleLocation.Direction direction) {
      try {
         Connector clone = (Connector)super.clone();
         clone.location = new SimpleLocation(this.location.getX(), this.location.getY(), this.location.getZ(), direction);
         clone.roomBlacklist = new ArrayList<>(this.roomBlacklist);
         clone.roomWhitelist = new ArrayList<>(this.roomWhitelist);
         return clone;
      } catch (CloneNotSupportedException var3) {
         var3.printStackTrace();
         return null;
      }
   }

   public DungeonProcedural getDungeon() {
      return this.dungeon;
   }

   public SimpleLocation getLocation() {
      return this.location;
   }

   public void setLocation(SimpleLocation location) {
      this.location = location;
   }

   public double getSuccessChance() {
      return this.successChance;
   }

   public void setSuccessChance(double successChance) {
      this.successChance = successChance;
   }

   public List<WhitelistEntry> getRoomBlacklist() {
      return this.roomBlacklist;
   }

   public void setRoomBlacklist(List<WhitelistEntry> roomBlacklist) {
      this.roomBlacklist = roomBlacklist;
   }

   public List<WhitelistEntry> getRoomWhitelist() {
      return this.roomWhitelist;
   }

   public void setRoomWhitelist(List<WhitelistEntry> roomWhitelist) {
      this.roomWhitelist = roomWhitelist;
   }

   public ConnectorDoor getDoor() {
      return this.door;
   }

   public void setDoor(ConnectorDoor door) {
      this.door = door;
   }
}
