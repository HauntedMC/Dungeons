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
 * Gate trigger that fires whenever any child trigger fires.
 */
@AutoRegister(id = "dungeons.trigger.gate_or")
@SerializableAs("dungeons.trigger.gate_or")
public class GateOrTrigger extends GateTrigger {
    /**
     * Creates a new GateOrTrigger instance.
     */
    public GateOrTrigger(Map<String, Object> config) {
        super("OR Gate", config);
        this.setHasTarget(true);
    }

    /**
     * Creates a new GateOrTrigger instance.
     */
    public GateOrTrigger() {
        super("OR Gate");
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
                this.trigger(event.getPlayerSession());
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.COMPARATOR);
        button.setDisplayName("&bOR Gate");
        button.addLore("&eTriggered when one of several");
        button.addLore("&especified triggers have been");
        button.addLore("&eactivated.");
        return button;
    }
}
