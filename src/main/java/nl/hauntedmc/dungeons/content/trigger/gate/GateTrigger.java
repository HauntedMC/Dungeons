package nl.hauntedmc.dungeons.content.trigger.gate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Base trigger that delegates to a set of child triggers and combines their results.
 */
public abstract class GateTrigger extends DungeonTrigger {
    @PersistedField protected List<DungeonTrigger> triggers = new ArrayList<>();
    protected Map<DungeonTrigger, Boolean> triggerTracker = new HashMap<>();

    /**
     * Creates a new GateTrigger instance.
     */
    public GateTrigger(String displayName, Map<String, Object> config) {
        super(displayName, config);
        this.setCategory(TriggerCategory.META);
    }

    /**
     * Creates a new GateTrigger instance.
     */
    public GateTrigger(String id) {
        super(id);
        this.setCategory(TriggerCategory.META);
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();

        for (DungeonTrigger trigger : this.triggers) {
            trigger.initialize();
        }
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        this.triggerTracker.clear();
        for (DungeonTrigger trigger : this.triggers) {
            // Child triggers inherit the gate's location and instance so the gate can be dropped
            // into content as a single logical trigger node.
            trigger.setInstance(this.instance);
            trigger.setLocation(this.location);
            trigger.enable(null, this.instance);
            this.triggerTracker.put(trigger, false);
        }
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        for (DungeonTrigger trigger : this.triggers) {
            trigger.disable();
        }

        this.triggerTracker.clear();
    }

    /**
     * Adds trigger.
     */
    public void addTrigger(DungeonTrigger trigger) {
        this.triggers.add(trigger);
    }

    /**
     * Removes trigger.
     */
    public void removeTrigger(DungeonTrigger trigger) {
        this.triggers.remove(trigger);
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.COMMAND_BLOCK);
                        this.button.setDisplayName("&a&lAdd Trigger");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        if (GateTrigger.this.triggers.size() >= 54) {
                            LangUtils.sendMessage(player, "editor.trigger.gate.max-triggers-reached");
                        } else {
                            RuntimeContext.guiService().openGui(player, "triggermenu");
                        }
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
                        this.button.setDisplayName("&e&lEdit Trigger");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "editgatetrigger");
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.BARRIER);
                        this.button.setDisplayName("&c&lRemove Trigger");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "removegatetrigger");
                    }
                });
    }

    /**
     * Performs clone.
     */
    public GateTrigger clone() {
        GateTrigger clone = (GateTrigger) super.clone();
        List<DungeonTrigger> newTriggers = new ArrayList<>();

        for (DungeonTrigger oldTrigger : this.triggers) {
            DungeonTrigger clonedTrigger = oldTrigger.clone();
            if (clonedTrigger != null) {
                clonedTrigger.setLocation(clone.location);
                newTriggers.add(clonedTrigger);
            }
        }

        clone.triggers = newTriggers;
        clone.triggerTracker = new HashMap<>();
        return clone;
    }

    /**
     * Returns the triggers.
     */
    public List<DungeonTrigger> getTriggers() {
        return this.triggers;
    }

    /**
     * Returns the trigger tracker.
     */
    public Map<DungeonTrigger, Boolean> getTriggerTracker() {
        return this.triggerTracker;
    }
}
