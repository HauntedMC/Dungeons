package nl.hauntedmc.dungeons.listener;

import nl.hauntedmc.dungeons.event.HotbarSetEvent;
import nl.hauntedmc.dungeons.model.element.DungeonElement;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Keeps the active editor selection in sync with the currently shown hotbar menu.
 */
public class ElementHotbarListener implements Listener {
    private final DungeonElement element;

    /**
     * Creates the listener for one concrete element instance.
     */
    public ElementHotbarListener(DungeonElement element) {
        this.element = element;
    }

    /**
     * Marks the wrapped element as active when its hotbar menu becomes visible.
     */
    @EventHandler
    public void onHotbarSet(HotbarSetEvent event) {
        if (event.getNewHotbar() == null || this.element.getMenu() == null) {
            return;
        }

        if (event.getNewHotbar() == this.element.getMenu()) {
            switch (this.element) {
                case DungeonFunction dungeonFunction ->
                        event.getPlayerSession().setActiveFunction(dungeonFunction);
                case DungeonTrigger dungeonTrigger ->
                        event.getPlayerSession().setActiveTrigger(dungeonTrigger);
                default -> {}
            }
        }
    }
}
