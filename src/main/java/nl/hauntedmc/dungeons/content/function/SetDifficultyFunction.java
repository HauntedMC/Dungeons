package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that changes the active runtime difficulty of a playable instance.
 */
@AutoRegister(id = "dungeons.function.set_difficulty")
@SerializableAs("dungeons.function.set_difficulty")
public class SetDifficultyFunction extends DungeonFunction {
    @PersistedField private String difficulty;
    private transient boolean invalidDifficultyLogged;

    /**
     * Creates a new SetDifficultyFunction instance.
     */
    public SetDifficultyFunction(Map<String, Object> config) {
        super("Set Difficulty", config);
        this.setCategory(FunctionCategory.DUNGEON);
        this.targetType = FunctionTargetType.NONE;
        this.setAllowChangingTargetType(false);
    }

    /**
     * Creates a new SetDifficultyFunction instance.
     */
    public SetDifficultyFunction() {
        super("Set Difficulty");
        this.setCategory(FunctionCategory.DUNGEON);
        this.targetType = FunctionTargetType.NONE;
        this.setAllowChangingTargetType(false);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (this.difficulty == null || this.difficulty.isBlank()) {
            if (!this.invalidDifficultyLogged) {
                this.invalidDifficultyLogged = true;
                this.logger()
                        .warn(
                                "SetDifficultyFunction in dungeon '{}' at {} has no configured difficulty.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs());
            }
            return;
        }

        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            DungeonDifficulty difficulty =
                    instance.getDungeon().getDifficultyLevels().get(this.difficulty);
            if (difficulty != null) {
                instance.setDifficulty(difficulty);
            } else if (!this.invalidDifficultyLogged) {
                this.invalidDifficultyLogged = true;
                this.logger()
                        .warn(
                                "SetDifficultyFunction in dungeon '{}' at {} references unknown difficulty '{}'.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                this.difficulty);
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.BLAZE_POWDER);
        functionButton.setDisplayName("&dDungeon Difficulty");
        functionButton.addLore("&eSets the difficulty level of the");
        functionButton.addLore("&edungeon, which can be used for");
        functionButton.addLore("&econdition checks and triggers.");
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
                        this.button.addLore("&7Current difficulty: &6" + SetDifficultyFunction.this.difficulty);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.set-difficulty.ask-difficulty");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.set-difficulty.current-difficulty",
                                LangUtils.placeholder("difficulty", SetDifficultyFunction.this.difficulty));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        SetDifficultyFunction.this.difficulty = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.set-difficulty.difficulty-set",
                                LangUtils.placeholder("difficulty", message));
                    }
                });
    }
}
