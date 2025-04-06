package net.playavalon.mythicdungeons.api.events.dungeon.rooms;

import net.playavalon.mythicdungeons.api.events.dungeon.DungeonEvent;
import net.playavalon.mythicdungeons.api.generation.rooms.ConnectorDoor;
import net.playavalon.mythicdungeons.api.generation.rooms.DoorAction;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;

public class RoomDoorChangeEvent extends DungeonEvent {
   private InstanceRoom room;
   private ConnectorDoor door;
   private DoorAction action;

   public RoomDoorChangeEvent(AbstractInstance instance, InstanceRoom room, ConnectorDoor door, DoorAction action) {
      super(instance);
      this.room = room;
      this.door = door;
      this.action = action;
   }

   public InstanceRoom getRoom() {
      return this.room;
   }

   public ConnectorDoor getDoor() {
      return this.door;
   }

   public DoorAction getAction() {
      return this.action;
   }
}
