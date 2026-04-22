package nl.hauntedmc.dungeons.content.trigger;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Trigger that fires when the block at its location receives redstone power.
 */
@AutoRegister(id = "dungeons.trigger.redstone")
@SerializableAs("dungeons.trigger.redstone")
public class RedstoneTrigger extends DungeonTrigger {
    private boolean powered;
    private BukkitRunnable rateLimiter;
    private boolean rateLimited = false;

    /**
     * Creates a new RedstoneTrigger instance.
     */
    public RedstoneTrigger(Map<String, Object> config) {
        super("Redstone Receiver", config);
        this.waitForConditions = true;
        this.setCategory(TriggerCategory.GENERAL);
    }

    /**
     * Creates a new RedstoneTrigger instance.
     */
    public RedstoneTrigger() {
        super("Redstone Receiver");
        this.waitForConditions = true;
        this.setCategory(TriggerCategory.GENERAL);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.REDSTONE_BLOCK);
        functionButton.setDisplayName("&5Redstone Receiver");
        functionButton.addLore("&eTriggered when the block at this");
        functionButton.addLore("&elocation receives a redstone");
        functionButton.addLore("&esignal.");
        functionButton.addLore("");
        functionButton.addLore("&dNote: Functions triggered by this");
        functionButton.addLore("&dcan't target specific players or");
        functionButton.addLore("&dparties.");
        return functionButton;
    }

    /**
     * Performs on disable.
     */
    @Override
    public void onDisable() {
        if (this.rateLimiter != null && !this.rateLimiter.isCancelled()) {
            this.rateLimiter.cancel();
        }
    }

    /**
     * Performs on redstone signal.
     */
    @EventHandler
    public void onRedstoneSignal(BlockRedstoneEvent event) {
        World world = event.getBlock().getWorld();
        if (world == this.instance.getInstanceWorld()) {
            Block block = event.getBlock();
            if (!block.getLocation().equals(this.location)) {
                return;
            }

            if (event.getNewCurrent() <= 0) {
                this.powered = false;
            } else if (!this.powered) {
                this.powered = true;
                if (!this.rateLimited) {
                    this.rateLimit();
                    this.trigger();
                }
            }
        }
    }

    /**
     * Performs rate limit.
     */
    private void rateLimit() {
        this.rateLimited = true;
        this.rateLimiter =
                                new BukkitRunnable() {
                    /**
                     * Performs run.
                     */
                    public void run() {
                        RedstoneTrigger.this.rateLimited = false;
                    }
                };
        this.rateLimiter.runTaskLater(RuntimeContext.plugin(), 2L);
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
                        this.button.setEnchanted(RedstoneTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!RedstoneTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.redstone.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.redstone.prevent-repeat");
                        }

                        RedstoneTrigger.this.allowRetrigger = !RedstoneTrigger.this.allowRetrigger;
                    }
                });
    }
}
