package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import org.bukkit.event.Event;

/**
 * Functional callback used by hotbar menu items.
 *
 * @param <T> player event type handled by the callback
 */
public interface MenuAction<T extends Event> {
    /**
     * Runs the callback for the given event.
     *
     * @param event triggering event
     */
    void run(T event);
}
