package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.generation.room.DoorAction;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;

/**
 * Event fired when a branching room door opens, closes, or toggles.
 */
public class RoomDoorChangeEvent extends DungeonEvent {
    private final InstanceRoom room;
    private final ConnectorDoor door;
    private final DoorAction action;

    /**
     * Creates a room-door change event.
     *
     * @param instance instance containing the room
     * @param room room whose door changed
     * @param door door that changed
     * @param action action that occurred
     */
    public RoomDoorChangeEvent(
            DungeonInstance instance, InstanceRoom room, ConnectorDoor door, DoorAction action) {
        super(instance);
        this.room = room;
        this.door = door;
        this.action = action;
    }

    /**
     * Returns the affected room.
     *
     * @return room whose door changed
     */
    public InstanceRoom getRoom() {
        return this.room;
    }

    /**
     * Returns the affected connector door.
     *
     * @return changed door
     */
    public ConnectorDoor getDoor() {
        return this.door;
    }

    /**
     * Returns the door action that occurred.
     *
     * @return applied door action
     */
    public DoorAction getAction() {
        return this.action;
    }
}
