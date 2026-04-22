package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
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
 * Function that updates the current status string of a playable instance.
 */
@AutoRegister(id = "dungeons.function.set_status")
@SerializableAs("dungeons.function.set_status")
public class SetStatusFunction extends DungeonFunction {
    @PersistedField private String status;
    private transient boolean invalidStatusLogged;

    /**
     * Creates a new SetStatusFunction instance.
     */
    public SetStatusFunction(Map<String, Object> config) {
        super("Set Status", config);
        this.setCategory(FunctionCategory.DUNGEON);
        this.targetType = FunctionTargetType.NONE;
        this.setAllowChangingTargetType(false);
    }

    /**
     * Creates a new SetStatusFunction instance.
     */
    public SetStatusFunction() {
        super("Set Status");
        this.setCategory(FunctionCategory.DUNGEON);
        this.targetType = FunctionTargetType.NONE;
        this.setAllowChangingTargetType(false);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (this.status == null || this.status.isBlank()) {
            if (!this.invalidStatusLogged) {
                this.invalidStatusLogged = true;
                this.logger()
                        .warn(
                                "SetStatusFunction in dungeon '{}' at {} has no configured status.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs());
            }
            return;
        }

        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            instance.setStatus(this.status);
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.NAME_TAG);
        functionButton.setDisplayName("&dDungeon Status");
        functionButton.addLore("&eSets the status of the dungeon,");
        functionButton.addLore("&ewhich can be used for condition");
        functionButton.addLore("&echecks and triggers.");
        functionButton.addLore("");
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
                        this.button.setDisplayName("&d&lSet Status");
                        this.button.addLore("&7Current status: &6" + SetStatusFunction.this.status);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.set-status.ask-status");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.set-status.current-status",
                                LangUtils.placeholder("status", SetStatusFunction.this.status));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        SetStatusFunction.this.status = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.set-status.status-set",
                                LangUtils.placeholder("status", message));
                    }
                });
    }
}
