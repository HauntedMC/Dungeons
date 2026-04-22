package nl.hauntedmc.dungeons.content.trigger;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.event.InteractionUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Trigger that fires when a player right-clicks the configured block location.
 */
@AutoRegister(id = "dungeons.trigger.interact")
@SerializableAs("dungeons.trigger.interact")
public class InteractTrigger extends DungeonTrigger {
    /**
     * Creates a new InteractTrigger instance.
     */
    public InteractTrigger(Map<String, Object> config) {
        super("Right-Click", config);
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Creates a new InteractTrigger instance.
     */
    public InteractTrigger() {
        super("Right-Click");
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.OAK_SIGN);
        functionButton.setDisplayName("&aRight-Click Block");
        functionButton.addLore("&eTriggered when a player");
        functionButton.addLore("&eright-clicks the block at this");
        functionButton.addLore("&elocation.");
        return functionButton;
    }

    /**
     * Performs on interact.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!InteractionUtils.isInteractionDenied(event)) {
                if (!event.getPlayer().getGameMode().equals(GameMode.SPECTATOR)) {
                    World world = event.getPlayer().getWorld();
                    if (world == this.instance.getInstanceWorld()) {
                        if (event.getClickedBlock() == null) {
                            return;
                        }

                        Location blockLoc = event.getClickedBlock().getLocation();
                        if (blockLoc.equals(this.location)) {
                            if (event.getHand() != EquipmentSlot.OFF_HAND) {
                                Player player = event.getPlayer();
                                DungeonPlayerSession dungeonPlayer =
                                        RuntimeContext.playerSessions().get(player);
                                if (this.allowRetrigger || !this.hasTrackedTarget(dungeonPlayer)) {
                                    event.setCancelled(true);
                                    if (this.function != null
                                            && this.function.getTargetType().isTargetedAtPlayers()) {
                                        if (!this.allowRetrigger) {
                                            this.rememberTriggeredTargets(dungeonPlayer);
                                        }
                                    } else if (!this.allowRetrigger) {
                                        this.disable();
                                    }

                                    this.trigger(dungeonPlayer);
                                }
                            }
                        }
                    }
                }
            }
        }
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
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lAllow Retrigger");
                        this.button.setEnchanted(InteractTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!InteractTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.interact.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.interact.prevent-repeat");
                        }

                        InteractTrigger.this.allowRetrigger = !InteractTrigger.this.allowRetrigger;
                    }
                });
    }
}
