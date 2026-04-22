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
 * Condition that checks the current runtime status string of a playable instance.
 */
@AutoRegister(id = "dungeons.condition.status")
@SerializableAs("dungeons.condition.status")
public class StatusCondition extends TriggerCondition {
    @PersistedField private String status;
    private transient boolean missingStatusLogged;

    /**
     * Creates a new StatusCondition instance.
     */
    public StatusCondition(Map<String, Object> config) {
        super("Status", config);
    }

    /**
     * Creates a new StatusCondition instance.
     */
    public StatusCondition() {
        super("Status");
    }

    /**
     * Performs check.
     */
    @Override
    public boolean check(TriggerFireEvent event) {
        if (this.status == null || this.status.isBlank()) {
            if (!this.missingStatusLogged) {
                this.missingStatusLogged = true;
                this.logger()
                        .warn(
                                "StatusCondition in dungeon '{}' at {} is missing its target status.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs());
            }
            return false;
        }

        PlayableInstance instance = this.instance.asPlayInstance();
        return instance != null && this.status.equals(instance.getStatus());
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.NAME_TAG);
        functionButton.setDisplayName("&dDungeon Status");
        functionButton.addLore("&eChecks if the dungeon is set to");
        functionButton.addLore("&ea specific status.");
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
                        this.button.addLore("&7Current status: &6" + StatusCondition.this.status);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.condition.status.ask-status");
                        LangUtils.sendMessage(
                                player,
                                "editor.condition.status.current-status",
                                LangUtils.placeholder("status", StatusCondition.this.status));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        StatusCondition.this.status = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.condition.status.status-set",
                                LangUtils.placeholder("status", message));
                    }
                });
    }
}
