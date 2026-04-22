package nl.hauntedmc.dungeons.content.condition;

import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Condition that passes randomly according to a configured probability.
 *
 * <p>This is typically used to make a trigger branch unpredictably without needing a dedicated
 * random trigger type.
 */
@AutoRegister(id = "dungeons.condition.chance")
@SerializableAs("dungeons.condition.chance")
public class ChanceCondition extends TriggerCondition {
    @PersistedField private double chance = 1.0;
    private transient boolean invalidChanceLogged;

    /**
     * Creates a new ChanceCondition instance.
     */
    public ChanceCondition(Map<String, Object> config) {
        super("Chance", config);
    }

    /**
     * Creates a new ChanceCondition instance.
     */
    public ChanceCondition() {
        super("Chance");
    }

    /**
     * Performs check.
     */
    @Override
    public boolean check(TriggerFireEvent event) {
        double normalizedChance = Math.max(0.0, Math.min(1.0, this.chance));
        if (normalizedChance != this.chance && !this.invalidChanceLogged) {
            this.invalidChanceLogged = true;
            this.logger()
                    .warn(
                            "ChanceCondition in dungeon '{}' at {} had invalid chance {}. Clamping into the 0.0-1.0 range.",
                            this.dungeonNameForLogs(),
                            this.locationForLogs(),
                            this.chance);
        }

        return MathUtils.getRandomBoolean(normalizedChance);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.ENDER_EYE);
        functionButton.setDisplayName("&dTrigger Chance");
        functionButton.addLore("&eRandomizes whether or not the");
        functionButton.addLore("&etrigger should run.");
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
                        this.button = new MenuButton(Material.ENDER_EYE);
                        this.button.setDisplayName("&d&lSet Chance");
                        this.button.addLore("&7Current chance: &6" + ChanceCondition.this.chance);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.chance.ask-chance");
                        LangUtils.sendMessage(
                                player,
                                "editor.condition.chance.current-chance",
                                LangUtils.placeholder("chance", String.valueOf(ChanceCondition.this.chance)));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Double> value = InputUtils.readDoubleInput(player, message);
                        ChanceCondition.this.chance = value.orElse(ChanceCondition.this.chance);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.condition.chance.chance-set",
                                    LangUtils.placeholder("chance", String.valueOf(ChanceCondition.this.chance)));
                        }
                    }
                });
    }
}
