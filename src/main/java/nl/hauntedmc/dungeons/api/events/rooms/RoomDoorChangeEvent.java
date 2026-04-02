package nl.hauntedmc.dungeons.api.events.rooms;

import nl.hauntedmc.dungeons.api.events.DungeonEvent;
import nl.hauntedmc.dungeons.api.generation.rooms.ConnectorDoor;
import nl.hauntedmc.dungeons.api.generation.rooms.DoorAction;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;

public class RoomDoorChangeEvent extends DungeonEvent {
   private final InstanceRoom room;
   private final ConnectorDoor door;
   private final DoorAction action;

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
