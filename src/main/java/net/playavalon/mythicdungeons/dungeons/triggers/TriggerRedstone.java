package net.playavalon.mythicdungeons.dungeons.triggers;

import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.scheduler.BukkitRunnable;

@DeclaredTrigger
public class TriggerRedstone extends DungeonTrigger {
   private boolean powered;
   private BukkitRunnable rateLimiter;
   private boolean rateLimited = false;

   public TriggerRedstone(Map<String, Object> config) {
      super("Redstone Receiver", config);
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.GENERAL);
   }

   public TriggerRedstone() {
      super("Redstone Receiver");
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.GENERAL);
   }

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

   @Override
   public void onDisable() {
      if (this.rateLimiter != null && !this.rateLimiter.isCancelled()) {
         this.rateLimiter.cancel();
      }
   }

   @EventHandler
   public void onRedstoneSignal(BlockRedstoneEvent event) {
      World world = event.getBlock().getWorld();
      if (world == this.instance.getInstanceWorld()) {
         Block block = this.location.getBlock();
         if (!block.isBlockPowered()) {
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

   private void rateLimit() {
      this.rateLimited = true;
      this.rateLimiter = new BukkitRunnable() {
         public void run() {
            TriggerRedstone.this.rateLimited = false;
         }
      };
      this.rateLimiter.runTaskLater(MythicDungeons.inst(), 2L);
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerRedstone.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerRedstone.this.allowRetrigger) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerRedstone.this.allowRetrigger = !TriggerRedstone.this.allowRetrigger;
         }
      });
   }
}
