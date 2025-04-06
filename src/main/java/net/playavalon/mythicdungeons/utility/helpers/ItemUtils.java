package net.playavalon.mythicdungeons.utility.helpers;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.utility.ServerVersion;
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
            player.getInventory().addItem(new ItemStack[]{item});
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
               player.getInventory().addItem(new ItemStack[]{item});
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
            player.getInventory().addItem(new ItemStack[]{item});
         }
      }
   }

   public static ItemStack getFunctionTool() {
      ItemStack feather = new ItemStack(MythicDungeons.inst().getFunctionBuilderMaterial());
      ItemMeta meta = feather.getItemMeta();
      List<String> lore = new ArrayList<>(LangUtils.getMessageList("general.function-editor-lore"));
      meta.setLore(lore);
      if (ServerVersion.get().isAfterOrEqual(ServerVersion.v1_20_5)) {
         meta.setItemName(LangUtils.getMessage("general.function-editor-name", false));
         meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, new AttributeModifier("reach", 5.5, Operation.ADD_NUMBER));
      } else {
         meta.setDisplayName(LangUtils.getMessage("general.function-editor-name", false));
      }

      feather.setItemMeta(meta);
      return feather;
   }

   public static boolean isFunctionTool(ItemStack item) {
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return false;
      } else {
         if (ServerVersion.get().isAfterOrEqual(ServerVersion.v1_20_5)) {
            if (!meta.getItemName().equals(LangUtils.getMessage("general.function-editor-name", false))) {
               return false;
            }
         } else if (!meta.getDisplayName().equals(LangUtils.getMessage("general.function-editor-name", false))) {
            return false;
         }

         return true;
      }
   }

   public static ItemStack getRoomTool() {
      ItemStack tool = new ItemStack(MythicDungeons.inst().getRoomEditorMaterial());
      ItemMeta meta = tool.getItemMeta();
      List<String> lore = new ArrayList<>(LangUtils.getMessageList("general.room-editor-lore"));
      meta.setLore(lore);
      if (ServerVersion.get().isAfterOrEqual(ServerVersion.v1_20_5)) {
         meta.setItemName(LangUtils.getMessage("general.room-editor-name", false));
         meta.addAttributeModifier(Attribute.BLOCK_INTERACTION_RANGE, new AttributeModifier("reach", 5.5, Operation.ADD_NUMBER));
      } else {
         meta.setDisplayName(LangUtils.getMessage("general.room-editor-name", false));
      }

      tool.setItemMeta(meta);
      return tool;
   }

   public static boolean isRoomTool(ItemStack item) {
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return false;
      } else {
         return ServerVersion.get().isAfterOrEqual(ServerVersion.v1_20_5)
            ? meta.getItemName().equals(LangUtils.getMessage("general.room-editor-name", false))
            : meta.getDisplayName().equals(LangUtils.getMessage("general.room-editor-name", false));
      }
   }

   public static ItemStack getDefaultKeyItem() {
      ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
      ItemMeta meta = key.getItemMeta();
      PersistentDataContainer data = meta.getPersistentDataContainer();
      NamespacedKey keyData = new NamespacedKey(MythicDungeons.inst(), "dungeonkey");
      data.set(keyData, PersistentDataType.INTEGER, 1);
      meta.setDisplayName(Util.colorize("&6Dungeon Key"));
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
            NamespacedKey verification = new NamespacedKey(MythicDungeons.inst(), "dungeonkey");
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
            NamespacedKey verification = new NamespacedKey(MythicDungeons.inst(), "DungeonItem");
            return data.has(verification, PersistentDataType.INTEGER);
         }
      }
   }

   public static String getItemDisplayName(ItemStack item) {
      ItemMeta meta = item.getItemMeta();
      String displayName = meta.getDisplayName();
      if (displayName.equals("")) {
         displayName = item.getType().toString();
      }

      return displayName;
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

      meta.setDisplayName(Util.colorize(" "));
      item.setItemMeta(meta);
      return item;
   }

   @Nullable
   public static String getMythicItemType(ItemStack item) {
      return MythicDungeons.inst().getMythicApi() == null ? null : MythicDungeons.inst().getMythicApi().getItemManager().getMythicTypeFromItem(item);
   }

   public static ItemStack skullFromName(ItemStack item, String name) {
      SkullMeta meta = (SkullMeta)item.getItemMeta();
      meta.setOwner(name);
      item.setItemMeta(meta);
      return item;
   }

   public static ItemStack getPlayerHead(Player player) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      if (!(head.getItemMeta() instanceof SkullMeta skull)) {
         return head;
      } else {
         skull.setOwningPlayer(player);
         head.setItemMeta(skull);
         return head;
      }
   }
}
