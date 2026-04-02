package nl.hauntedmc.dungeons.dungeons.triggers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredTrigger;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@DeclaredTrigger
public class TriggerKeyItem extends DungeonTrigger {
   @SavedField
   private ItemStack item;
   @SavedField
   private boolean consumeItem = true;
   @SavedField
   private boolean useAnywhere = false;
   @SavedField
   private String clickType = "RIGHT";
   private TriggerKeyItem.ClickType click;

   public TriggerKeyItem(Map<String, Object> config) {
      super("Key Item", config);
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
      this.item = ItemUtils.getDefaultKeyItem();
   }

   public TriggerKeyItem() {
      super("Key Item");
      this.setCategory(TriggerCategory.PLAYER);
      this.setHasTarget(true);
      this.item = ItemUtils.getDefaultKeyItem();
   }

   @Override
   public void init() {
      super.init();
      this.click = TriggerKeyItem.ClickType.valueOf(this.clickType);
   }

   @EventHandler
   public void onUseItem(PlayerInteractEvent event) {
      Player player = event.getPlayer();
      if (player.getWorld() == this.instance.getInstanceWorld()) {
         if (event.getHand() != EquipmentSlot.OFF_HAND) {
            if (this.click.hasAction(event.getAction())) {
               if (!this.useAnywhere) {
                  if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
                     return;
                  }

                  Location blockLoc = event.getClickedBlock().getLocation();
                  Location aboveLoc = blockLoc.clone();
                  aboveLoc.setY(blockLoc.getY() + 1.0);
                  Location belowLoc = blockLoc.clone();
                  belowLoc.setY(blockLoc.getY() - 1.0);
                  if (!blockLoc.equals(this.location) && !aboveLoc.equals(this.location) && !belowLoc.equals(this.location)) {
                     return;
                  }
               }

               ItemStack usedItem = event.getItem();
               ItemStack item = this.item.clone();

               if (usedItem != null && usedItem.getType() != Material.AIR) {
                  if (item.isSimilar(usedItem)) {
                     if (this.consumeItem) {
                        usedItem.setAmount(usedItem.getAmount() - 1);
                     }

                     DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
                     this.trigger(aPlayer);
                     event.setCancelled(true);
                  }
               }
            }
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton button = new MenuButton(Material.ITEM_FRAME);
      button.setDisplayName("&aKey Item Detector");
      button.addLore("&eTriggers when a player uses");
      button.addLore("&ean item with right-click.");
      return button;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerKeyItem.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerKeyItem.this.allowRetrigger) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerKeyItem.this.allowRetrigger = !TriggerKeyItem.this.allowRetrigger;
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.ITEM_FRAME);
            this.button.setDisplayName("&d&lChoose Item");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            Dungeons.inst().getGuiApi().openGUI(player, "selectitem_trigger");
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.FURNACE);
            this.button.setDisplayName("&d&lToggle Consume Item");
            this.button.setEnchanted(TriggerKeyItem.this.consumeItem);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerKeyItem.this.consumeItem) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet to '&6item &bwill &6be consumed when used&a'!"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet to '&6item &cwill NOT &6be consumed when used&a'!"));
            }

            TriggerKeyItem.this.consumeItem = !TriggerKeyItem.this.consumeItem;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.TARGET);
            this.button.setDisplayName("&d&lToggle Use Anywhere");
            this.button.setEnchanted(TriggerKeyItem.this.useAnywhere);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerKeyItem.this.useAnywhere) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet to '&6item &bcan &6be used anywhere&a'!"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet to '&6item &cmust &6be used at this function&a'!"));
            }

            TriggerKeyItem.this.useAnywhere = !TriggerKeyItem.this.useAnywhere;
         }
      });
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PLAYER_HEAD);
            this.button.setDisplayName("&d&lClick Type: " + TriggerKeyItem.this.clickType);
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
            String var4 = TriggerKeyItem.this.clickType;
            switch (var4) {
               case "RIGHT":
                  TriggerKeyItem.this.clickType = "LEFT";
                  break;
               case "LEFT":
                  TriggerKeyItem.this.clickType = "BOTH";
                  break;
               case "BOTH":
                  TriggerKeyItem.this.clickType = "RIGHT";
            }

            TriggerKeyItem.this.click = TriggerKeyItem.ClickType.valueOf(TriggerKeyItem.this.clickType);
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet required click type to '&6" + TriggerKeyItem.this.clickType + "&a'"));
            mPlayer.setHotbar(this.menu, false);
         }
      });
   }

   public ItemStack getItem() {
      return this.item;
   }

   public void setItem(ItemStack item) {
      this.item = item;
   }

   public void setConsumeItem(boolean consumeItem) {
      this.consumeItem = consumeItem;
   }

   public void setUseAnywhere(boolean useAnywhere) {
      this.useAnywhere = useAnywhere;
   }

   private enum ClickType {
      RIGHT(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK),
      LEFT(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK),
      BOTH(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK, Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK);

      private final List<Action> actions = new ArrayList<>();

      ClickType(Action... actions) {
         Collections.addAll(this.actions, actions);
      }

      public boolean hasAction(Action action) {
         return this.actions.contains(action);
      }
   }
}
