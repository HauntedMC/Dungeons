package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.FunctionTargetType;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (this.item != null) {
         ItemStack item = this.item.clone();

          if (ItemUtils.verifyDungeonItem(this.item)) {
              ItemMeta preMeta = item.getItemMeta();
              PersistentDataContainer data = preMeta.getPersistentDataContainer();
              data.set(new NamespacedKey(Dungeons.inst(), "DungeonItem"), PersistentDataType.INTEGER, 1);
              item.setItemMeta(preMeta);
          }

          item.addUnsafeEnchantments(this.item.getEnchantments());

         if (!targets.isEmpty() && !this.drop) {
            for (DungeonPlayer mPlayer : targets) {
               String itemName = HelperUtils.itemDisplayName(item);

               Player player = mPlayer.getPlayer();
               if (this.notify) {
                  LangUtils.sendMessage(player, "instance.functions.item-dispenser", String.valueOf(item.getAmount()), itemName);
               }

               ItemUtils.giveOrDropSilently(player, item.clone());
            }
         } else {
            this.instance.getInstanceWorld().dropItem(this.location, item.clone());
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
            Dungeons.inst().getGuiApi().openGUI(player, "selectitem_function");
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
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet behaviour to '&6item is dropped at this location&a'"));
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet behaviour to '&6item is given to the target player(s)&a'"));
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
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aPlayers &bwill &abe notified when they receive the item."));
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aPlayers &cwill NOT &abe notified when they receive the item."));
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
