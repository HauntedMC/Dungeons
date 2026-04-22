package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.instance.InstanceUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.MessageUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that sends a title and subtitle to its targets.
 */
@AutoRegister(id = "dungeons.function.title")
@SerializableAs("dungeons.function.title")
public class TitleFunction extends DungeonFunction {
    @PersistedField private String title = " ";
    @PersistedField private String subtitle = " ";
    @PersistedField private int fadeIn = 10;
    @PersistedField private int stay = 80;
    @PersistedField private int fadeOut = 10;

    /**
     * Creates a new TitleFunction instance.
     */
    public TitleFunction(Map<String, Object> config) {
        super("Title", config);
        this.setCategory(FunctionCategory.PLAYER);
    }

    /**
     * Creates a new TitleFunction instance.
     */
    public TitleFunction() {
        super("Title");
        this.setCategory(FunctionCategory.PLAYER);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            for (DungeonPlayerSession playerSession : targets) {
                Player player = playerSession.getPlayer();
                String title = this.title;
                String subtitle = this.subtitle;

                title = InstanceUtils.parseVars(instance, title);
                subtitle = InstanceUtils.parseVars(instance, subtitle);
                MessageUtils.showTitle(player, title, subtitle, this.fadeIn, this.stay, this.fadeOut);
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.FILLED_MAP);
        button.setDisplayName("&aTitle Sender");
        button.addLore("&eSends a title and subtitle");
        button.addLore("&eto the target player(s).");
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
                        this.button = new MenuButton(Material.KNOWLEDGE_BOOK);
                        this.button.setDisplayName("&d&lEdit Title");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.title.ask-title");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.title.current-title",
                                LangUtils.placeholder("title", TitleFunction.this.title));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        TitleFunction.this.title = message;
                        LangUtils.sendMessage(
                                player, "editor.function.title.title-set", LangUtils.placeholder("title", message));
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.BOOK);
                        this.button.setDisplayName("&d&lEdit Subtitle");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.title.ask-subtitle");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.title.current-subtitle",
                                LangUtils.placeholder("subtitle", TitleFunction.this.subtitle));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        TitleFunction.this.subtitle = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.title.subtitle-set",
                                LangUtils.placeholder("subtitle", message));
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.LIGHT_BLUE_BANNER);
                        this.button.setDisplayName("&d&lDuration");
                        this.button.setAmount(TitleFunction.this.stay);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.title.ask-stay");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        TitleFunction.this.stay = value.orElse(TitleFunction.this.stay);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.title.stay-set",
                                    LangUtils.placeholder("stay", String.valueOf(TitleFunction.this.stay)));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.LIME_BANNER);
                        this.button.setDisplayName("&d&lFade-In Ticks");
                        this.button.setAmount(TitleFunction.this.fadeIn);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.title.ask-fade-in");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        TitleFunction.this.fadeIn = value.orElse(TitleFunction.this.fadeIn);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.title.fade-in-set",
                                    LangUtils.placeholder("fade_in", String.valueOf(TitleFunction.this.fadeIn)));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.RED_BANNER);
                        this.button.setDisplayName("&d&lFade-Out Ticks");
                        this.button.setAmount(TitleFunction.this.fadeOut);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.title.ask-fade-out");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        TitleFunction.this.fadeOut = value.orElse(TitleFunction.this.fadeOut);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.title.fade-out-set",
                                    LangUtils.placeholder("fade_out", String.valueOf(TitleFunction.this.fadeOut)));
                        }
                    }
                });
    }

    /**
     * Sets the title.
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Sets the subtitle.
     */
    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }
}
