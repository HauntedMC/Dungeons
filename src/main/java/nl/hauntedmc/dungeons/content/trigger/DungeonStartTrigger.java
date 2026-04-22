package nl.hauntedmc.dungeons.content.trigger;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.event.EventHandler;

/**
 * Trigger that fires when the dungeon run starts.
 */
@AutoRegister(id = "dungeons.trigger.dungeon_start")
@SerializableAs("dungeons.trigger.dungeon_start")
public class DungeonStartTrigger extends DungeonTrigger {
    /**
     * Creates a new DungeonStartTrigger instance.
     */
    public DungeonStartTrigger(Map<String, Object> config) {
        super("Dungeon Start", config);
        this.setCategory(TriggerCategory.DUNGEON);
        this.setHasTarget(true);
    }

    /**
     * Creates a new DungeonStartTrigger instance.
     */
    public DungeonStartTrigger() {
        super("Dungeon Start");
        this.setCategory(TriggerCategory.DUNGEON);
        this.setHasTarget(true);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.MOSSY_COBBLESTONE);
        functionButton.setDisplayName("&6Dungeon Start");
        functionButton.addLore("&eTriggered when the dungeon");
        functionButton.addLore("&ebegins.");
        return functionButton;
    }

    /**
     * Performs on dungeon start.
     */
    @EventHandler
    public void onDungeonStart(DungeonStartEvent event) {
        if (event.getInstance() == this.instance) {
            int delay = 10;
            if (this.instance.getDungeon().isLobbyEnabled()) {
                delay = 1;
            }

            Bukkit.getScheduler()
                    .runTaskLater(
                            RuntimeContext.plugin(),
                            () -> {
                                if (!event.getPlayerSessions().isEmpty()) {
                                    this.trigger(event.getPlayerSessions().getFirst());
                                }
                            },
                            delay);
        }
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {}
}
