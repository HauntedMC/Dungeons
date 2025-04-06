package net.playavalon.mythicdungeons.api.generation.rooms;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.config.AvalonSerializable;
import net.playavalon.mythicdungeons.api.events.dungeon.rooms.RoomDoorChangeEvent;
import net.playavalon.mythicdungeons.api.generation.StructurePieceBlock;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.utility.SimpleLocation;
import net.playavalon.mythicdungeons.utility.helpers.ParticleUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class ConnectorDoor implements AvalonSerializable {
   @SavedField
   private String namespace;
   @SavedField
   private List<SimpleLocation> locations = new ArrayList<>();
   @SavedField
   private boolean startOpen = true;
   @SavedField
   private boolean disableSound = false;
   private InstanceRoom room;
   private WeakReference<World> world;
   private boolean open;
   private Map<SimpleLocation, StructurePieceBlock> blocks = new HashMap<>();
   private boolean hasAdjacentRoom;

   public ConnectorDoor(Connector connector) {
      int x = (int)connector.getLocation().getX();
      int y = (int)connector.getLocation().getY();
      int z = (int)connector.getLocation().getZ();
      this.namespace = "Door_" + x + "," + y + "," + z;
   }

   public ConnectorDoor(String namespace) {
      this.namespace = namespace;
   }

   public void toggleLocation(Location loc) {
      this.toggleLocation(SimpleLocation.from(loc));
   }

   public void toggleLocation(SimpleLocation loc) {
      if (this.locations.contains(loc)) {
         this.removeLocation(loc);
      } else {
         this.addLocation(loc);
      }
   }

   public void addLocation(Location loc) {
      this.addLocation(SimpleLocation.from(loc));
   }

   public void addLocation(SimpleLocation loc) {
      this.locations.add(loc);
   }

   public void removeLocation(Location loc) {
      this.removeLocation(SimpleLocation.from(loc));
   }

   public void removeLocation(SimpleLocation loc) {
      this.locations.remove(loc);
   }

   public void init(InstanceRoom room, World world) {
      this.room = room;
      this.world = new WeakReference<>(world);
   }

   public void toggleDoor() {
      if (this.world != null) {
         if (this.open) {
            this.closeDoor();
         } else {
            this.openDoor();
         }
      }
   }

   public void openDoor() {
      World world = this.world.get();
      if (world != null) {
         this.open = true;

         for (Entry<SimpleLocation, StructurePieceBlock> pair : this.blocks.entrySet()) {
            Location target = pair.getKey().asLocation(world);
            world.getBlockAt(target).setType(Material.AIR);
         }

         if (!this.locations.isEmpty() && !this.disableSound) {
            world.playSound(this.locations.getFirst().asLocation(world), "block.iron_door.open", 1.0F, 0.8F);
         }

         AbstractInstance inst = MythicDungeons.inst().getDungeonInstance(world.getName());
         Bukkit.getPluginManager().callEvent(new RoomDoorChangeEvent(inst, this.room, this, DoorAction.OPEN));
      }
   }

   public void closeDoor() {
      World world = this.world.get();
      if (world != null) {
         this.open = false;

         for (Entry<SimpleLocation, StructurePieceBlock> pair : this.blocks.entrySet()) {
            Location target = pair.getKey().asLocation(world);
            pair.getValue().placeAt(target);
         }

         if (!this.locations.isEmpty() && !this.disableSound) {
            world.playSound(this.locations.getFirst().asLocation(world), "block.iron_door.close", 1.0F, 0.8F);
         }

         AbstractInstance inst = MythicDungeons.inst().getDungeonInstance(world.getName());
         Bukkit.getPluginManager().callEvent(new RoomDoorChangeEvent(inst, this.room, this, DoorAction.CLOSE));
      }
   }

   public void displayParticles(Player player) {
      for (SimpleLocation rawLoc : this.locations) {
         Location loc = rawLoc.asLocation(player.getWorld());
         BoundingBox blockBox = new BoundingBox(loc.getX(), loc.getY(), loc.getZ(), loc.getX() + 1.0, loc.getY() + 1.0, loc.getZ() + 1.0);
         ParticleUtils.displayBoundingBox(player, ParticleUtils.getVersionParticle("DUST"), new DustOptions(Color.LIME, 0.25F), blockBox);
         loc.setX(loc.getX() + 0.5);
         loc.setY(loc.getY() + 0.7);
         loc.setZ(loc.getZ() + 0.5);
         player.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.01);
      }
   }

   public ConnectorDoor copy(Vector offset) {
      ConnectorDoor newDoor = this.clone();
      newDoor.locations.clear();

      for (SimpleLocation loc : this.locations) {
         SimpleLocation newLoc = loc.clone();
         newLoc.shift(offset);
         newDoor.addLocation(newLoc);
      }

      newDoor.blocks = new HashMap<>();
      return newDoor;
   }

   public ConnectorDoor copy(Vector destOffset, Vector origin, int rotation) {
      ConnectorDoor newDoor = new ConnectorDoor("");
      newDoor.setStartOpen(this.startOpen);
      newDoor.setDisableSound(this.disableSound);

      for (SimpleLocation loc : this.locations) {
         SimpleLocation adjusted = loc.clone();
         Vector blockOffset = loc.asVector().subtract(origin);
         Vector finalOffset = destOffset.clone().subtract(blockOffset);
         switch (rotation) {
            case -270:
            case 90:
               finalOffset.add(new Vector(-blockOffset.getBlockZ(), blockOffset.getBlockY(), blockOffset.getBlockX()));
               break;
            case -180:
            case 180:
               finalOffset.add(new Vector(-blockOffset.getBlockX(), blockOffset.getBlockY(), -blockOffset.getBlockZ()));
               break;
            case -90:
            case 270:
               finalOffset.add(new Vector(-blockOffset.getBlockZ(), blockOffset.getBlockY(), -blockOffset.getBlockX()));
         }

         adjusted.shift(finalOffset);
         newDoor.addLocation(adjusted);
      }

      return newDoor;
   }

   public ConnectorDoor copy(Vector offset, int rotation, SimpleLocation size) {
      ConnectorDoor newDoor = new ConnectorDoor("");
      newDoor.setStartOpen(this.startOpen);
      newDoor.setDisableSound(this.disableSound);

      for (SimpleLocation loc : this.locations) {
         SimpleLocation adjusted = loc.clone();
         SimpleLocation newLoc = RotatedRoom.rotateLocation(offset, adjusted, rotation, size);
         newLoc.shift(offset.getX(), 0.0, offset.getZ());
         newDoor.addLocation(newLoc);
      }

      return newDoor;
   }

   public ConnectorDoor clone() {
      ConnectorDoor clone = new ConnectorDoor(this.namespace);
      clone.startOpen = this.startOpen;
      clone.locations = new ArrayList<>(this.locations);
      clone.blocks = new HashMap<>();
      return clone;
   }

   public String getNamespace() {
      return this.namespace;
   }

   public void setNamespace(String namespace) {
      this.namespace = namespace;
   }

   public List<SimpleLocation> getLocations() {
      return this.locations;
   }

   public boolean isStartOpen() {
      return this.startOpen;
   }

   public void setStartOpen(boolean startOpen) {
      this.startOpen = startOpen;
   }

   public boolean isDisableSound() {
      return this.disableSound;
   }

   public void setDisableSound(boolean disableSound) {
      this.disableSound = disableSound;
   }

   public boolean isOpen() {
      return this.open;
   }

   public void setOpen(boolean open) {
      this.open = open;
   }

   public Map<SimpleLocation, StructurePieceBlock> getBlocks() {
      return this.blocks;
   }

   public boolean isHasAdjacentRoom() {
      return this.hasAdjacentRoom;
   }

   public void setHasAdjacentRoom(boolean hasAdjacentRoom) {
      this.hasAdjacentRoom = hasAdjacentRoom;
   }
}
