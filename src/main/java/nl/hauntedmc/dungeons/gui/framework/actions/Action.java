package nl.hauntedmc.dungeons.gui.framework.actions;

import org.bukkit.event.Event;

/**
 * Functional callback used by inventory GUI windows.
 *
 * @param <T> inventory event type handled by the callback
 */
public interface Action<T extends Event> {
    /**
     * Runs the callback for the given event.
     *
     * @param event triggering event
     */
    void run(T event);
}
