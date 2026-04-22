package nl.hauntedmc.dungeons.content.trigger;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.event.RemoteTriggerEvent;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;

/**
 * Trigger that listens for named remote signals emitted by other dungeon content.
 */
@AutoRegister(id = "dungeons.trigger.remote")
@SerializableAs("dungeons.trigger.remote")
public class RemoteTrigger extends DungeonTrigger {
    @PersistedField private String triggerName = "trigger";
    @PersistedField private boolean awaitConditions = true;

    /**
     * Creates a new RemoteTrigger instance.
     */
    public RemoteTrigger(Map<String, Object> config) {
        super("Signal Receiver", config);
        this.waitForConditions = true;
        this.setCategory(TriggerCategory.DUNGEON);
        this.setHasTarget(true);
    }

    /**
     * Creates a new RemoteTrigger instance.
     */
    public RemoteTrigger() {
        super("Signal Receiver");
        this.waitForConditions = true;
        this.setCategory(TriggerCategory.DUNGEON);
        this.setHasTarget(true);
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();
        this.setDisplayName("Signal: " + this.triggerName);
        this.waitForConditions = this.awaitConditions;
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.REDSTONE_TORCH);
        functionButton.setDisplayName("&6Signal Receiver");
        functionButton.addLore("&eTriggered when a signal sender");
        functionButton.addLore("&esends a signal with a matching");
        functionButton.addLore("&econfigured name.");
        return functionButton;
    }

    /**
     * Performs on trigger receive.
     */
    @EventHandler
    public void onTriggerReceive(RemoteTriggerEvent event) {
        if (this.instance == event.getInstance()) {
            if (event.getTriggerName().equals(this.triggerName)) {
                Location origin = event.getOrigin();
                boolean withinRange =
                        origin == null
                                || event.getRange() == 0.0
                                || origin.getWorld() == this.location.getWorld()
                                        && !(this.location.distance(origin) > event.getRange());
                if (withinRange) {
                    // Remote signals can optionally be spatially bounded, which lets one signal
                    // name be reused in different parts of the same dungeon safely.
                    if (this.matchesRoom(origin)) {
                        this.trigger(event.getPlayerSession());
                    }
                }
            }
        }
    }

    /**
     * Performs matches room.
     */
    private boolean matchesRoom(Location origin) {
        BranchingInstance instance = this.instance.as(BranchingInstance.class);
        if (instance == null) {
            return true;
        } else if (!this.limitToRoom) {
            return true;
        } else if (origin == null) {
            return false;
        } else {
            InstanceRoom originRoom = instance.getRoom(origin);
            InstanceRoom thisRoom = instance.getRoom(this.location);
            return thisRoom == originRoom;
        }
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PAPER);
                        this.button.setDisplayName("&d&lSignal Name");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.remote.ask-signal");
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.remote.current-signal",
                                LangUtils.placeholder("signal", RemoteTrigger.this.triggerName));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        RemoteTrigger.this.triggerName = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.remote.signal-set",
                                LangUtils.placeholder("signal", message));
                        RemoteTrigger.this.setDisplayName("Signal: " + RemoteTrigger.this.triggerName);
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lAllow Retrigger");
                        this.button.setEnchanted(RemoteTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!RemoteTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.remote.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.remote.prevent-repeat");
                        }

                        RemoteTrigger.this.allowRetrigger = !RemoteTrigger.this.allowRetrigger;
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REPEATER);
                        this.button.setDisplayName("&d&lWait For Conditions");
                        this.button.setEnchanted(RemoteTrigger.this.awaitConditions);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!RemoteTrigger.this.awaitConditions) {
                            LangUtils.sendMessage(player, "editor.trigger.remote.wait-after-trigger");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.remote.require-conditions-now");
                        }

                        RemoteTrigger.this.awaitConditions = !RemoteTrigger.this.awaitConditions;
                        RemoteTrigger.this.waitForConditions = !RemoteTrigger.this.waitForConditions;
                    }
                });
        this.addRoomLimitToggleButton();
    }

    /**
     * Returns the trigger name.
     */
    public String getTriggerName() {
        return this.triggerName;
    }

    /**
     * Sets the trigger name.
     */
    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    /**
     * Returns whether await conditions.
     */
    public boolean isAwaitConditions() {
        return this.awaitConditions;
    }

    /**
     * Sets the await conditions.
     */
    public void setAwaitConditions(boolean awaitConditions) {
        this.awaitConditions = awaitConditions;
    }
}
