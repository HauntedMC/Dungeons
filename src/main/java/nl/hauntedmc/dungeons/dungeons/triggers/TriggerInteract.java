package nl.hauntedmc.dungeons.dungeons.triggers;

import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredTrigger;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
         if (!HelperUtils.isInteractionDenied(event)) {
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

                           this.trigger(Dungeons.inst().getDungeonPlayer(player));
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
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerInteract.this.allowRetrigger = !TriggerInteract.this.allowRetrigger;
         }
      });
   }
}
