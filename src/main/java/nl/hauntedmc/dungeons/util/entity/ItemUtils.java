package nl.hauntedmc.dungeons.util.entity;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemUtils {
   public static void giveOrDrop(Player player, ItemStack item) {
      if (item != null && item.getType() != Material.AIR) {
         if (player.getInventory().firstEmpty() == -1) {
            LangUtils.sendMessage(player, "misc.item-dropped");
            player.getWorld().dropItem(player.getLocation(), item);
         } else {
            player.getInventory().addItem(item);
         }
      }
   }

   public static void giveOrDrop(Player player, ItemStack... items) {
      boolean invFull = false;

      for (ItemStack item : items) {
         if (player.getInventory().firstEmpty() == -1) {
            invFull = true;
         }

         if (item != null && item.getType() != Material.AIR) {
            if (invFull) {
               player.getWorld().dropItem(player.getLocation(), item);
            } else {
               player.getInventory().addItem(item);
            }
         }
      }

      if (invFull) {
         LangUtils.sendMessage(player, "misc.item-dropped");
      }
   }

   public static void giveOrDropSilently(Player player, ItemStack item) {
      if (item != null && item.getType() != Material.AIR) {
         if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), item);
         } else {
            player.getInventory().addItem(item);
         }
      }
   }

   public static ItemStack getFunctionTool() {
      ItemStack feather = new ItemStack(Dungeons.inst().getFunctionBuilderMaterial());
      ItemMeta meta = feather.getItemMeta();
      List<String> lore = new ArrayList<>(LangUtils.getMessageList("general.function-editor-lore"));
      meta.lore(HelperUtils.components(lore));
      meta.itemName(HelperUtils.component(LangUtils.getMessage("general.function-editor-name", false)));
      meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, new AttributeModifier(new NamespacedKey(Dungeons.inst(), "reach"), 5.5, Operation.ADD_NUMBER));

      feather.setItemMeta(meta);
      return feather;
   }

   public static boolean isFunctionTool(ItemStack item) {
      if (item == null || item.getType() == Material.AIR) {
         return false;
      }

      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return false;
      } else {
          return HelperUtils.serialize(meta.itemName()).equals(LangUtils.getMessage("general.function-editor-name", false));
      }
   }

   public static ItemStack getRoomTool() {
      ItemStack tool = new ItemStack(Dungeons.inst().getRoomEditorMaterial());
      ItemMeta meta = tool.getItemMeta();
      List<String> lore = new ArrayList<>(LangUtils.getMessageList("general.room-editor-lore"));
      meta.lore(HelperUtils.components(lore));
      meta.itemName(HelperUtils.component(LangUtils.getMessage("general.room-editor-name", false)));
      meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, new AttributeModifier(new NamespacedKey(Dungeons.inst(), "reach"), 5.5, Operation.ADD_NUMBER));

      tool.setItemMeta(meta);
      return tool;
   }

   public static boolean isRoomTool(ItemStack item) {
      if (item == null || item.getType() == Material.AIR) {
         return false;
      }

      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return false;
      } else {
         return HelperUtils.serialize(meta.itemName()).equals(LangUtils.getMessage("general.room-editor-name", false));
      }
   }

   public static ItemStack getDefaultKeyItem() {
      ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
      ItemMeta meta = key.getItemMeta();
      PersistentDataContainer data = meta.getPersistentDataContainer();
      NamespacedKey keyData = new NamespacedKey(Dungeons.inst(), "dungeonkey");
      data.set(keyData, PersistentDataType.INTEGER, 1);
      meta.displayName(HelperUtils.component("&6Dungeon Key"));
      key.setItemMeta(meta);
      return key;
   }

   public static boolean verifyKeyItem(ItemStack item) {
      if (item == null) {
         return false;
      } else {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            return false;
         } else {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            NamespacedKey verification = new NamespacedKey(Dungeons.inst(), "dungeonkey");
            return data.has(verification, PersistentDataType.INTEGER);
         }
      }
   }

   public static boolean verifyDungeonItem(ItemStack item) {
      if (item == null) {
         return false;
      } else {
         ItemMeta meta = item.getItemMeta();
         if (meta == null) {
            return false;
         } else {
            PersistentDataContainer data = meta.getPersistentDataContainer();
            NamespacedKey verification = new NamespacedKey(Dungeons.inst(), "DungeonItem");
            return data.has(verification, PersistentDataType.INTEGER);
         }
      }
   }

   public static String getItemDisplayName(ItemStack item) {
      if (item == null || item.getType() == Material.AIR) {
         return "";
      }

      return HelperUtils.itemDisplayName(item);
   }

   public static boolean isItemBanned(AbstractDungeon dungeon, ItemStack itemStack) {
      List<String> bannedItems = dungeon.getBannedItems();
      if (bannedItems.contains(itemStack.getType().toString())) {
         return true;
      } else {
         for (ItemStack item : dungeon.getCustomBannedItems()) {
            if (item.isSimilar(itemStack)) {
               return true;
            }
         }

         return false;
      }
   }

   public static ItemStack getBlockedMenuItem() {
      return getBlockedMenuItem(Material.BLACK_STAINED_GLASS_PANE);
   }

   public static ItemStack getBlockedMenuItem(Material mat) {
      ItemStack item = new ItemStack(mat);
      ItemMeta meta = item.getItemMeta();

      assert meta != null;

      meta.displayName(HelperUtils.component(" "));
      item.setItemMeta(meta);
      return item;
   }

   public static ItemStack skullFromName(ItemStack item, String name) {
      if (item.getItemMeta() instanceof SkullMeta meta) {
         meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
         item.setItemMeta(meta);
      }

      return item;
   }

   public static ItemStack getPlayerHead(Player player) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
       if (head.getItemMeta() instanceof SkullMeta skull) {
           skull.setOwningPlayer(player);
           head.setItemMeta(skull);
       }
       return head;
   }
}
