package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Base event for runtime events that belong to a dungeon or dungeon instance.
 */
public abstract class DungeonEvent extends Event {
    protected DungeonInstance instance;
    protected final DungeonDefinition dungeon;
    private static final HandlerList HANDLERS_LIST = new HandlerList();

    /**
     * Creates an event that is scoped to a live instance.
     *
     * @param instance instance that originated the event
     */
    public DungeonEvent(DungeonInstance instance) {
        this.instance = instance;
        this.dungeon = instance.getDungeon();
    }

    /**
     * Creates an event that is scoped only to a dungeon definition.
     *
     * @param dungeon dungeon that originated the event
     */
    public DungeonEvent(DungeonDefinition dungeon) {
        this.dungeon = dungeon;
    }

    /**
     * Returns the shared Bukkit handler list for this event hierarchy.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS_LIST;
    }

    /**
     * Returns the shared Bukkit handler list for this event instance.
     *
     * @return handler list
     */
    @NotNull public HandlerList getHandlers() {
        return HANDLERS_LIST;
    }

    /**
     * Returns the live instance associated with this event, if any.
     *
     * @return originating instance
     */
    public DungeonInstance getInstance() {
        return this.instance;
    }

    /**
     * Returns the dungeon definition associated with this event.
     *
     * @return originating dungeon
     */
    public DungeonDefinition getDungeon() {
        return this.dungeon;
    }
}
