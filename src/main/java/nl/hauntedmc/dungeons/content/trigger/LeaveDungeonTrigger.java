package nl.hauntedmc.dungeons.content.trigger;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/**
 * Trigger that fires when a player leaves the current dungeon instance.
 */
@AutoRegister(id = "dungeons.trigger.leave_dungeon")
@SerializableAs("dungeons.trigger.leave_dungeon")
public class LeaveDungeonTrigger extends DungeonTrigger {
    @PersistedField private boolean preventLeaving = false;

    /**
     * Creates a new LeaveDungeonTrigger instance.
     */
    public LeaveDungeonTrigger(Map<String, Object> config) {
        super("Player Leave", config);
        this.waitForConditions = true;
        this.setCategory(TriggerCategory.DUNGEON);
        this.setHasTarget(true);
    }

    /**
     * Creates a new LeaveDungeonTrigger instance.
     */
    public LeaveDungeonTrigger() {
        super("Player Leave");
        this.waitForConditions = true;
        this.setCategory(TriggerCategory.DUNGEON);
        this.setHasTarget(true);
    }

    /**
     * Performs on player leave.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerLeave(PlayerLeaveDungeonEvent event) {
        DungeonPlayerSession playerSession = event.getPlayerSession();
        if (this.preventLeaving) {
            event.setCancelled(true);
        }

        this.trigger(playerSession, !this.allowRetrigger);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.DARK_OAK_DOOR);
        functionButton.setDisplayName("&6Leave Dungeon Listener");
        functionButton.addLore("&eTriggered when a player leaves");
        functionButton.addLore("&ethe dungeon.");
        functionButton.addLore("");
        functionButton.addLore("&7Optionally, can also stop players");
        functionButton.addLore("&7from leaving with /dungeon leave or leave");
        functionButton.addLore("&7functions.");
        return functionButton;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.BARRIER);
                        this.button.setDisplayName("&d&lPrevent Leaving");
                        this.button.setEnchanted(LeaveDungeonTrigger.this.preventLeaving);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!LeaveDungeonTrigger.this.preventLeaving) {
                            LangUtils.sendMessage(player, "editor.trigger.leave-dungeon.block-leave");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.leave-dungeon.allow-leave");
                        }

                        LeaveDungeonTrigger.this.preventLeaving = !LeaveDungeonTrigger.this.preventLeaving;
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
                        this.button.setEnchanted(LeaveDungeonTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!LeaveDungeonTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.leave-dungeon.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.leave-dungeon.prevent-repeat");
                        }

                        LeaveDungeonTrigger.this.allowRetrigger = !LeaveDungeonTrigger.this.allowRetrigger;
                    }
                });
    }
}
