package nl.hauntedmc.dungeons.util.item;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;
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

/**
 * Item utility helpers for editor tools, key items, and reward transfers.
 */
public final class ItemUtils {

    /** Gives an item to a player inventory or drops it naturally if full. */
    public static void giveOrDrop(Player player, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            if (!player.getInventory().addItem(item).isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    /** Gives multiple items to a player inventory or drops overflow. */
    public static void giveOrDrop(Player player, ItemStack... items) {
        for (ItemStack item : items) {
            giveOrDrop(player, item);
        }
    }

    /** Gives an item or drops overflow without additional messaging. */
    public static void giveOrDropSilently(Player player, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            if (!player.getInventory().addItem(item).isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    /** Builds the configured function editor tool item. */
    public static ItemStack getFunctionTool() {
        ItemStack tool = new ItemStack(RuntimeContext.functionBuilderMaterial());
        ItemMeta meta = tool.getItemMeta();
        List<String> lore =
                new ArrayList<>(LangUtils.getMessageList("general.items.function-tool.lore"));
        meta.lore(ComponentUtils.components(lore));
        meta.itemName(
                ComponentUtils.component(LangUtils.getMessage("general.items.function-tool.name", false)));
        meta.addAttributeModifier(
                Attribute.BLOCK_INTERACTION_RANGE,
                new AttributeModifier(
                        new NamespacedKey(RuntimeContext.plugin(), "reach"),
                        5.5,
                        Operation.ADD_NUMBER));

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(getFunctionToolKey(), PersistentDataType.INTEGER, 1);

        tool.setItemMeta(meta);
        return tool;
    }

    /** Returns whether an item matches the function tool signature. */
    public static boolean isFunctionTool(ItemStack item) {
        return isTaggedTool(
                item,
                getFunctionToolKey(),
                RuntimeContext.functionBuilderMaterial(),
                LangUtils.getMessage("general.items.function-tool.name", false));
    }

    /** Builds the configured room editor tool item. */
    public static ItemStack getRoomTool() {
        ItemStack tool = new ItemStack(RuntimeContext.roomEditorMaterial());
        ItemMeta meta = tool.getItemMeta();
        List<String> lore = new ArrayList<>(LangUtils.getMessageList("general.items.room-tool.lore"));
        meta.lore(ComponentUtils.components(lore));
        meta.itemName(
                ComponentUtils.component(LangUtils.getMessage("general.items.room-tool.name", false)));
        meta.addAttributeModifier(
                Attribute.BLOCK_INTERACTION_RANGE,
                new AttributeModifier(
                        new NamespacedKey(RuntimeContext.plugin(), "reach"),
                        5.5,
                        Operation.ADD_NUMBER));

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(getRoomToolKey(), PersistentDataType.INTEGER, 1);

        tool.setItemMeta(meta);
        return tool;
    }

    /** Returns whether an item matches the room tool signature. */
    public static boolean isRoomTool(ItemStack item) {
        return isTaggedTool(
                item,
                getRoomToolKey(),
                RuntimeContext.roomEditorMaterial(),
                LangUtils.getMessage("general.items.room-tool.name", false));
    }

    /** Validates tool identity by persistent key, material, and fallback item name. */
    private static boolean isTaggedTool(
            ItemStack item, NamespacedKey key, Material expectedMaterial, String expectedName) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (data.has(key, PersistentDataType.INTEGER)) {
            return true;
        }

        if (item.getType() != expectedMaterial) {
            return false;
        }

        if (expectedName == null || expectedName.isBlank()) {
            return false;
        }

        String actualName = ComponentUtils.serialize(meta.itemName());
        return !actualName.isBlank() && actualName.equals(expectedName);
    }

    /** Returns the persistent data key used to tag function tools. */
    private static NamespacedKey getFunctionToolKey() {
        return new NamespacedKey(RuntimeContext.plugin(), "functiontool");
    }

    /** Returns the persistent data key used to tag room tools. */
    private static NamespacedKey getRoomToolKey() {
        return new NamespacedKey(RuntimeContext.plugin(), "roomtool");
    }

    /** Creates a default dungeon key item tagged in persistent data. */
    public static ItemStack getDefaultKeyItem() {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = key.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        NamespacedKey keyData = new NamespacedKey(RuntimeContext.plugin(), "dungeonkey");
        data.set(keyData, PersistentDataType.INTEGER, 1);
        meta.displayName(
                ComponentUtils.component(LangUtils.getMessage("general.items.dungeon-key.name", false)));
        key.setItemMeta(meta);
        return key;
    }

    /** Returns whether an item is tagged as a dungeon key. */
    public static boolean verifyKeyItem(ItemStack item) {
        if (item == null) {
            return false;
        } else {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            } else {
                PersistentDataContainer data = meta.getPersistentDataContainer();
                NamespacedKey verification =
                        new NamespacedKey(RuntimeContext.plugin(), "dungeonkey");
                return data.has(verification, PersistentDataType.INTEGER);
            }
        }
    }

    /** Returns whether an item is tagged as a dungeon-runtime item. */
    public static boolean verifyDungeonItem(ItemStack item) {
        if (item == null) {
            return false;
        } else {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return false;
            } else {
                PersistentDataContainer data = meta.getPersistentDataContainer();
                NamespacedKey verification =
                        new NamespacedKey(RuntimeContext.plugin(), "DungeonItem");
                return data.has(verification, PersistentDataType.INTEGER);
            }
        }
    }

    /** Returns the best available display name for an item stack. */
    public static String getItemDisplayName(ItemStack item) {
        if (item == null) {
            return "";
        }

        return getItemDisplayName(item.getItemMeta(), item.getType());
    }

    /** Returns the best available display name from metadata or material fallback. */
    public static String getItemDisplayName(ItemMeta meta, Material material) {
        if (meta != null) {
            String displayName = ComponentUtils.serialize(meta.displayName());
            if (!displayName.isEmpty()) {
                return displayName;
            }

            String itemName = ComponentUtils.serialize(meta.itemName());
            if (!itemName.isEmpty()) {
                return itemName;
            }
        }

        return TextUtils.humanize(material.name());
    }

    /** Returns whether an item is banned by material or custom banned-item list. */
    public static boolean isItemBanned(DungeonDefinition dungeon, ItemStack itemStack) {
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

    /** Returns the default blocked menu filler item. */
    public static ItemStack getBlockedMenuItem() {
        return getBlockedMenuItem(Material.BLACK_STAINED_GLASS_PANE);
    }

    /** Returns a blocked menu filler item using the supplied material. */
    public static ItemStack getBlockedMenuItem(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        assert meta != null;

        meta.displayName(ComponentUtils.component(" "));
        item.setItemMeta(meta);
        return item;
    }

    /** Applies a skull owner by player name for a skull item stack. */
    public static ItemStack skullFromName(ItemStack item, String name) {
        if (item.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
            item.setItemMeta(meta);
        }

        return item;
    }

    /** Creates a player head item for the supplied player. */
    public static ItemStack getPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
            head.setItemMeta(skull);
        }

        return head;
    }
}
