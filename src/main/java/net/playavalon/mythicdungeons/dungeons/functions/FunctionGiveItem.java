package net.playavalon.mythicdungeons.dungeons.functions;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.items.MythicItem;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.FunctionTargetType;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

@DeclaredFunction
public class FunctionGiveItem extends DungeonFunction {
   @SavedField
   protected ItemStack item;
   @SavedField
   protected boolean drop = false;
   @SavedField
   protected boolean notify = true;

   public FunctionGiveItem(String namespace, Map<String, Object> config) {
      super(namespace, config);
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.PLAYER);
   }

   public FunctionGiveItem(Map<String, Object> config) {
      super("Item Dispenser", config);
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.PLAYER);
   }

   public FunctionGiveItem(String namespace) {
      super(namespace);
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.PLAYER);
   }

   public FunctionGiveItem() {
      super("Item Dispenser");
      this.targetType = FunctionTargetType.PLAYER;
      this.setCategory(FunctionCategory.PLAYER);
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      if (this.item != null) {
         ItemStack item = this.item;
         if (MythicDungeons.inst().getMythicApi() != null) {
            String mythicItem = ItemUtils.getMythicItemType(item);
            if (mythicItem != null) {
               Optional<MythicItem> mItem = MythicDungeons.inst().getMythicApi().getItemManager().getItem(mythicItem);
               if (mItem.isPresent()) {
                  item = BukkitAdapter.adapt(mItem.get().generateItemStack(item.getAmount()));
               }
            }
         }

         if (item != null) {
            if (ItemUtils.verifyDungeonItem(this.item)) {
               ItemMeta preMeta = item.getItemMeta();
               PersistentDataContainer data = preMeta.getPersistentDataContainer();
               data.set(new NamespacedKey(MythicDungeons.inst(), "DungeonItem"), PersistentDataType.INTEGER, 1);
               item.setItemMeta(preMeta);
            }

            item.addUnsafeEnchantments(this.item.getEnchantments());
         }

         if (!targets.isEmpty() && !this.drop) {
            for (MythicPlayer mPlayer : targets) {
               ItemMeta meta = item.getItemMeta();

               assert meta != null;

               String itemName = meta.getDisplayName();
               if (itemName.equals("")) {
                  itemName = WordUtils.capitalizeFully(item.getType().name().toLowerCase(Locale.ROOT).replace("_", " "));
               }

               Player player = mPlayer.getPlayer();
               if (this.notify) {
                  LangUtils.sendMessage(player, "instance.functions.item-dispenser", String.valueOf(item.getAmount()), itemName);
               }

               ItemUtils.giveOrDropSilently(player, item);
            }
         } else {
            this.instance.getInstanceWorld().dropItem(this.location, item);
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.DROPPER);
      functionButton.setDisplayName("&aItem Dispenser");
      functionButton.addLore("&eGives or drops an item at this");
      functionButton.addLore("&elocation.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new MenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.ITEM_FRAME);
            this.button.setDisplayName("&d&lChoose Item");
         }

         @Override
         public void onSelect(PlayerEvent event) {
            Player player = event.getPlayer();
            MythicDungeons.inst().getAvnAPI().openGUI(player, "selectitem_function");
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DROPPER);
            this.button.setDisplayName("&d&lToggle Drop");
            this.button.setEnchanted(FunctionGiveItem.this.drop);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionGiveItem.this.drop) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet behaviour to '&6item is dropped at this location&a'"));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aSet behaviour to '&6item is given to the target player(s)&a'"));
            }

            FunctionGiveItem.this.drop = !FunctionGiveItem.this.drop;
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.NAME_TAG);
            this.button.setDisplayName("&d&lToggle Notification");
            this.button.setEnchanted(FunctionGiveItem.this.notify);
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionGiveItem.this.notify) {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aPlayers &bwill &abe notified when they receive the item."));
            } else {
               player.sendMessage(MythicDungeons.debugPrefix + Util.colorize("&aPlayers &cwill NOT &abe notified when they receive the item."));
            }

            FunctionGiveItem.this.notify = !FunctionGiveItem.this.notify;
         }
      });
   }

   public ItemStack getItem() {
      return this.item;
   }

   public void setItem(ItemStack item) {
      this.item = item;
   }

   public boolean isDrop() {
      return this.drop;
   }

   public void setDrop(boolean drop) {
      this.drop = drop;
   }

   public boolean isNotify() {
      return this.notify;
   }

   public void setNotify(boolean notify) {
      this.notify = notify;
   }
}
