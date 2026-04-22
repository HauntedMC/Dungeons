package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.event.RemoteTriggerEvent;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that emits a named remote trigger signal within the current instance.
 */
@AutoRegister(id = "dungeons.function.remote_trigger")
@SerializableAs("dungeons.function.remote_trigger")
public class RemoteTriggerFunction extends DungeonFunction {
    @PersistedField private String triggerName = "trigger";
    @PersistedField private double range = 0.0;

    /**
     * Creates a new RemoteTriggerFunction instance.
     */
    public RemoteTriggerFunction(Map<String, Object> config) {
        super("Signal Sender", config);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.DUNGEON);
    }

    /**
     * Creates a new RemoteTriggerFunction instance.
     */
    public RemoteTriggerFunction() {
        super("Signal Sender");
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.DUNGEON);
    }

    /**
     * Performs initialize.
     */
    @Override
    public void initialize() {
        super.initialize();
        this.setDisplayName("Signal: " + this.triggerName);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            RemoteTrigger remoteTrig = new RemoteTrigger();
            remoteTrig.setTriggerName(this.triggerName);
            RemoteTriggerEvent event =
                                        new RemoteTriggerEvent(
                            remoteTrig.getTriggerName(),
                            remoteTrig,
                            instance,
                            this.range,
                            this.location,
                            triggerEvent.getPlayerSession());
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.REDSTONE_TORCH);
        functionButton.setDisplayName("&6Signal Sender");
        functionButton.addLore("&eSends a signal to any Signal");
        functionButton.addLore("&eReceiver triggers.");
        return functionButton;
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
                        LangUtils.sendMessage(player, "editor.function.remote-trigger.ask-signal");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.remote-trigger.current-signal",
                                LangUtils.placeholder("signal", RemoteTriggerFunction.this.triggerName));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        RemoteTriggerFunction.this.triggerName = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.remote-trigger.signal-set",
                                LangUtils.placeholder("signal", message));
                        RemoteTriggerFunction.this.setDisplayName(
                                "Signal: " + RemoteTriggerFunction.this.triggerName);
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REPEATER);
                        this.button.setDisplayName("&d&lSignal Range");
                        this.button.setAmount((int) MathUtils.round(RemoteTriggerFunction.this.range, 0));
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.remote-trigger.ask-range");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.remote-trigger.current-range",
                                LangUtils.placeholder("range", String.valueOf(RemoteTriggerFunction.this.range)));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Double> value = InputUtils.readDoubleInput(player, message);
                        RemoteTriggerFunction.this.range = value.orElse(RemoteTriggerFunction.this.range);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.remote-trigger.range-set",
                                    LangUtils.placeholder("range", String.valueOf(RemoteTriggerFunction.this.range)));
                        }
                    }
                });
    }

    /**
     * Sets the trigger name.
     */
    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    /**
     * Returns the range.
     */
    public double getRange() {
        return this.range;
    }

    /**
     * Sets the range.
     */
    public void setRange(double range) {
        this.range = range;
    }
}
