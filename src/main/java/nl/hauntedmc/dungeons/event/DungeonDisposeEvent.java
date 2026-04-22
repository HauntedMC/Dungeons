package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.model.instance.DungeonInstance;

/**
 * Event fired when a dungeon instance is being disposed.
 */
public class DungeonDisposeEvent extends DungeonEvent {
    /**
     * Creates a dispose event for the given instance.
     *
     * @param instance instance being torn down
     */
    public DungeonDisposeEvent(DungeonInstance instance) {
        super(instance);
    }
}
