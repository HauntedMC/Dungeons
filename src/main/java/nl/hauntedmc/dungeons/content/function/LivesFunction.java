package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Function that mutates the remaining-life counter for a run.
 */
@AutoRegister(id = "dungeons.function.lives")
@SerializableAs("dungeons.function.lives")
public class LivesFunction extends DungeonFunction {
    @PersistedField private int amount = 1;
    @PersistedField private int mode = 0;

    /**
     * Creates a new LivesFunction instance.
     */
    public LivesFunction(Map<String, Object> config) {
        super("Lives", config);
        this.setCategory(FunctionCategory.DUNGEON);
    }

    /**
     * Creates a new LivesFunction instance.
     */
    public LivesFunction() {
        super("Lives");
        this.setCategory(FunctionCategory.DUNGEON);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            if (!instance.isLivesEnabled()) {
                this.logger()
                        .info(
                                "{}{}",
                                RuntimeContext.logPrefix(),
                                ColorUtils.colorize(
                                        "&cUnable to edit player lives: Lives are disable in dungeon "
                                                + instance.getDungeon().getWorldName()
                                                + "!"));
                this.logger()
                        .info(
                                "{}{}",
                                RuntimeContext.logPrefix(),
                                ColorUtils.colorize(
                                        "&cMake sure '&ePlayerLives&c' are set to &61 or more &cin the dungeon's config!"));
            } else {
                for (DungeonPlayerSession playerSession : targets) {
                    Player player = playerSession.getPlayer();
                    UUID uuid = player.getUniqueId();
                    int currentLives = instance.getPlayerLives().get(uuid);

                    int newLives =
                            switch (this.mode) {
                                case 1 -> this.amount;
                                case 2 -> currentLives * this.amount;
                                default -> currentLives + this.amount;
                            };
                    if (newLives > 0) {
                        String plusMinus;
                        if (newLives < currentLives) {
                            plusMinus = "&c" + (newLives - currentLives);
                        } else {
                            plusMinus = "&a+" + (newLives - currentLives);
                        }

                        LangUtils.sendMessage(
                                player,
                                "instance.play.functions.lives-editor",
                                LangUtils.placeholder("lives", String.valueOf(newLives)),
                                LangUtils.placeholder("operation", plusMinus));
                        instance.getPlayerLives().put(uuid, newLives);
                    }
                }
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.PLAYER_HEAD);
        button.setDisplayName("&6Lives Editor");
        button.addLore("&eChanges the number of lives the");
        button.addLore("&eplayer(s) have.");
        return button;
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
                        this.button = new MenuButton(Material.REPEATER);
                        this.button.setDisplayName("&d&lSet Lives");
                        this.button.setAmount(LivesFunction.this.amount);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        switch (LivesFunction.this.mode) {
                            case 0:
                            case 1:
                                LangUtils.sendMessage(player, "editor.function.lives.ask-set");
                                break;
                            case 2:
                                LangUtils.sendMessage(player, "editor.function.lives.ask-multiply");
                            default:
                                LangUtils.sendMessage(player, "editor.function.lives.ask-gain");
                                break;
                        }
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        LivesFunction.this.amount = value.orElse(1);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.lives.amount-set",
                                LangUtils.placeholder("amount", String.valueOf(LivesFunction.this.amount)));
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.COMPARATOR);
                        this.button.setDisplayName("&d&lSet Lives Change Mode");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        LivesFunction.this.mode++;
                        if (LivesFunction.this.mode >= MathType.values().length) {
                            LivesFunction.this.mode = 0;
                        }

                        LangUtils.sendMessage(
                                player,
                                "editor.function.lives.mode-set",
                                LangUtils.placeholder("mode", MathType.intToMode(LivesFunction.this.mode)));
                    }
                });
    }

    /**
     * Sets the amount.
     */
    public void setAmount(int amount) {
        this.amount = amount;
    }

    /**
     * Sets the mode.
     */
    public void setMode(int mode) {
        this.mode = mode;
    }

    public enum MathType {
        ADD,
        SET,
        MULTIPLY;

        /**
         * Performs int to mode.
         */
        public static MathType intToMode(int index) {
                        return switch (index) {
                case 1 -> SET;
                case 2 -> MULTIPLY;
                default -> ADD;
            };
        }
    }
}
