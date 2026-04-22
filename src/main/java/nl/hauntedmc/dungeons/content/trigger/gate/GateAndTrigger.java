package nl.hauntedmc.dungeons.content.trigger.gate;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.event.EventHandler;

/**
 * Gate trigger that fires only after every child trigger has fired once.
 */
@AutoRegister(id = "dungeons.trigger.gate_and")
@SerializableAs("dungeons.trigger.gate_and")
public class GateAndTrigger extends GateTrigger {
    /**
     * Creates a new GateAndTrigger instance.
     */
    public GateAndTrigger(Map<String, Object> config) {
        super("AND Gate", config);
        this.setHasTarget(true);
    }

    /**
     * Creates a new GateAndTrigger instance.
     */
    public GateAndTrigger() {
        super("AND Gate");
        this.setHasTarget(true);
    }

    /**
     * Performs on child triggered.
     */
    @EventHandler
    public void onChildTriggered(TriggerFireEvent event) {
        if (event.getInstance().getInstanceWorld() == this.instance.getInstanceWorld()) {
            DungeonTrigger trigger = event.getTrigger();
            if (this.triggerTracker.containsKey(trigger)) {
                this.triggerTracker.put(trigger, true);
                if (!this.triggerTracker.containsValue(false)) {
                    this.triggerTracker.replaceAll((t, v) -> false);
                    this.trigger(event.getPlayerSession());
                }
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.COMPARATOR);
        button.setDisplayName("&bAND Gate");
        button.addLore("&eTriggered when several other");
        button.addLore("&especified triggers have been");
        button.addLore("&eactivated.");
        return button;
    }
}
