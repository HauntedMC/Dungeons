package nl.hauntedmc.dungeons.content.condition;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Condition that checks the current playable instance difficulty namespace.
 *
 * <p>The condition treats a missing runtime difficulty as {@code DEFAULT} to match the rest of the
 * dungeon runtime.
 */
@AutoRegister(id = "dungeons.condition.difficulty")
@SerializableAs("dungeons.condition.difficulty")
public class DifficultyCondition extends TriggerCondition {
    @PersistedField private String difficulty;
    private transient boolean missingDifficultyLogged;

    /**
     * Creates a new DifficultyCondition instance.
     */
    public DifficultyCondition(Map<String, Object> config) {
        super("Difficulty", config);
    }

    /**
     * Creates a new DifficultyCondition instance.
     */
    public DifficultyCondition() {
        super("Difficulty");
    }

    /**
     * Performs check.
     */
    @Override
    public boolean check(TriggerFireEvent event) {
        if (this.difficulty == null || this.difficulty.isBlank()) {
            if (!this.missingDifficultyLogged) {
                this.missingDifficultyLogged = true;
                this.logger()
                        .warn(
                                "DifficultyCondition in dungeon '{}' at {} is missing a configured difficulty.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs());
            }
            return false;
        }

        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance == null) {
            return false;
        } else {
            String instanceDif;
            if (instance.getDifficulty() == null) {
                instanceDif = "DEFAULT";
            } else {
                instanceDif = instance.getDifficulty().getNamespace();
            }

            return this.difficulty.equals(instanceDif);
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.BLAZE_POWDER);
        functionButton.setDisplayName("&dDungeon Difficulty");
        functionButton.addLore("&eChecks if the dungeon is set to");
        functionButton.addLore("&ea specific difficulty level.");
        functionButton.addLore("");
        functionButton.addLore("&cNOTE: Unrelated to Minecraft's");
        functionButton.addLore("&c/difficulty command!");
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
                        this.button = new MenuButton(Material.BLAZE_POWDER);
                        this.button.setDisplayName("&d&lSet Difficulty");
                        this.button.addLore("&7Current difficulty: &6" + DifficultyCondition.this.difficulty);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.difficulty.ask-difficulty");
                        LangUtils.sendMessage(
                                player,
                                "editor.condition.difficulty.current-difficulty",
                                LangUtils.placeholder("difficulty", DifficultyCondition.this.difficulty));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        DifficultyCondition.this.difficulty = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.condition.difficulty.difficulty-set",
                                LangUtils.placeholder("difficulty", message));
                    }
                });
    }
}
