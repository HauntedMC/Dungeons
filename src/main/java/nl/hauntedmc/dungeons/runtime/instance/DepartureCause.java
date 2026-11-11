package nl.hauntedmc.dungeons.runtime.instance;

/** Staff recovery does not create new gameplay penalties or completion. */
public enum DepartureCause {
    NORMAL,
    STAFF_REMOVE,
    RESCUE,
    MAINTENANCE;

    public boolean recovery() {
        return this == RESCUE || this == MAINTENANCE;
    }
}
