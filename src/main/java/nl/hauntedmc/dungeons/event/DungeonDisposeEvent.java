package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;

/** Event fired once an instance has unloaded successfully and released its registration. */
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
