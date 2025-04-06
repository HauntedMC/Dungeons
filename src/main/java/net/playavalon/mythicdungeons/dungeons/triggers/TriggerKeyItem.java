package net.playavalon.mythicdungeons.dungeons.triggers;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.items.MythicItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredTrigger;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
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
               if (MythicDungeons.inst().getMythicApi() != null) {
                  String mythicItem = ItemUtils.getMythicItemType(item);
                  if (mythicItem != null) {
                     Optional<MythicItem> mItem = MythicDungeons.inst().getMythicApi().getItemManager().getItem(mythicItem);
                     if (mItem.isPresent()) {
                        item = BukkitAdapter.adapt(mItem.get().generateItemStack(item.getAmount()));
                     }
                  }
               }

               if (usedItem != null && usedItem.getType() != Material.AIR) {
                  if (item.isSimilar(usedItem)) {
                     if (this.consumeItem) {
                        usedItem.setAmount(usedItem.getAmount() - 1);
                     }

                     MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
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
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
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
            MythicDungeons.inst().getAvnAPI().openGUI(player, "selectitem_trigger");
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
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet to '&6item &bwill &6be consumed when used&a'!"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet to '&6item &cwill NOT &6be consumed when used&a'!"));
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
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet to '&6item &bcan &6be used anywhere&a'!"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet to '&6item &cmust &6be used at this function&a'!"));
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
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
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
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet required click type to '&6" + TriggerKeyItem.this.clickType + "&a'"));
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

   private static enum ClickType {
      RIGHT(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK),
      LEFT(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK),
      BOTH(Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK, Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK);

      private final List<Action> actions = new ArrayList<>();

      private ClickType(Action... actions) {
         Collections.addAll(this.actions, actions);
      }

      public boolean hasAction(Action action) {
         return this.actions.contains(action);
      }
   }
}
