package net.playavalon.mythicdungeons.dungeons.triggers;

import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

@DeclaredTrigger
public class TriggerInteract extends DungeonTrigger {
   public TriggerInteract(Map<String, Object> config) {
      super("Right-Click", config);
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
   }

   public TriggerInteract() {
      super("Right-Click");
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.OAK_SIGN);
      functionButton.setDisplayName("&aRight-Click Block");
      functionButton.addLore("&eTriggered when a player");
      functionButton.addLore("&eright-clicks the block at this");
      functionButton.addLore("&elocation.");
      return functionButton;
   }

   @EventHandler
   public void onInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         if (!event.isCancelled()) {
            if (!event.getPlayer().getGameMode().equals(GameMode.SPECTATOR)) {
               World world = event.getPlayer().getWorld();
               if (world == this.instance.getInstanceWorld()) {
                  Location blockLoc = event.getClickedBlock().getLocation();
                  if (blockLoc.equals(this.location)) {
                     if (event.getHand() != EquipmentSlot.OFF_HAND) {
                        Player player = event.getPlayer();
                        if (this.allowRetrigger || !this.playersFound.contains(player.getUniqueId())) {
                           event.setCancelled(true);
                           if (this.function != null && this.function.getTargetType() == FunctionTargetType.PLAYER) {
                              if (!this.allowRetrigger) {
                                 this.playersFound.add(player.getUniqueId());
                              }
                           } else if (!this.allowRetrigger) {
                              this.disable();
                           }

                           this.trigger(MythicDungeons.inst().getMythicPlayer(player));
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerInteract.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerInteract.this.allowRetrigger) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerInteract.this.allowRetrigger = !TriggerInteract.this.allowRetrigger;
         }
      });
   }
}
