package nl.hauntedmc.dungeons.content.condition;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.util.instance.InstanceUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Condition that evaluates an instance-variable comparison expression.
 *
 * <p>The expression syntax is delegated to {@code InstanceUtils.compareVars}, which keeps editor
 * configuration and runtime evaluation aligned.
 */
@AutoRegister(id = "dungeons.condition.instance_variable")
@SerializableAs("dungeons.condition.instance_variable")
public class InstanceVariableCondition extends TriggerCondition {
    @PersistedField private String comparison;
    private transient boolean missingComparisonLogged;

    /**
     * Creates a new InstanceVariableCondition instance.
     */
    public InstanceVariableCondition(Map<String, Object> config) {
        super("Instance Variable", config);
    }

    /**
     * Creates a new InstanceVariableCondition instance.
     */
    public InstanceVariableCondition() {
        super("Instance Variable");
    }

    /**
     * Performs check.
     */
    @Override
    public boolean check(TriggerFireEvent event) {
        if (this.comparison == null || this.comparison.isBlank()) {
            if (!this.missingComparisonLogged) {
                this.missingComparisonLogged = true;
                this.logger()
                        .warn(
                                "InstanceVariableCondition in dungeon '{}' at {} is missing its comparison expression.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs());
            }
            return false;
        }

        PlayableInstance instance = this.instance.asPlayInstance();
        return instance != null && InstanceUtils.compareVars(instance, this.comparison);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.CHAIN_COMMAND_BLOCK);
        functionButton.setDisplayName("&dVariable Comparison");
        functionButton.addLore("&eChecks if a variable matches");
        functionButton.addLore("&ea given comparison value.");
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
                        this.button = new MenuButton(Material.NAME_TAG);
                        this.button.setDisplayName("&d&lSet Comparison");
                        this.button.addLore(
                                "&7Current comparison: &6" + InstanceVariableCondition.this.comparison);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.instance-variable.ask-expression");
                        LangUtils.sendMessage(player, "editor.condition.instance-variable.examples");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        InstanceVariableCondition.this.comparison = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.condition.instance-variable.expression-set",
                                LangUtils.placeholder("expression", message));
                    }
                });
    }
}
