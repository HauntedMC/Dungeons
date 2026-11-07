package nl.hauntedmc.dungeons.gui.loot;

import nl.hauntedmc.dungeons.content.reward.*;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.*;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.*;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Rendering and inventory actions for one table; persistence is delegated to its editor owner. */
final class LootTableMenu {
    final LootTable table;
    private final LootTableEditor owner;
    private ItemStack openSlotItem;
    private GuiWindow tableMenu;

    LootTableMenu(LootTableEditor owner, LootTable table) {
        this.owner = owner;
        this.table = table;
    }

    void close() {
        if (tableMenu != null) tableMenu.unregister();
    }

    boolean visible(Player player) {
        return tableMenu != null && tableMenu.getInventoryFor(player) != null;
    }

    /** Initializes open slot item. */
    private void initializeOpenSlotItem() {
        this.openSlotItem = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = this.openSlotItem.getItemMeta();

        assert meta != null;

        meta.displayName(
                ComponentUtils.component(LangUtils.getMessage("menus.common.empty-slot", false)));
        this.openSlotItem.setItemMeta(meta);
    }

    /** Initializes menu. */
    void initialize() {
        this.initializeOpenSlotItem();
        this.tableMenu =
                new GuiWindow(LootTableEditor.windowName(this.table, false), 54, "&8Edit Loot");
        this.tableMenu.setCancelClick(false);
        this.tableMenu.addCloseAction(
                "save",
                event -> {
                    this.owner.closed(this.table, (Player) event.getPlayer());
                });
        this.tableMenu.addOpenAction(
                "load",
                event -> {
                    Player player = (Player) event.getPlayer();

                    for (int slotIndex = 9; slotIndex < 54; slotIndex++) {
                        LootTableItem lootItem = this.table.getItems().get(slotIndex);
                        Button button = this.getLiveButton(player, slotIndex);
                        if (button == null) {
                            continue;
                        }

                        button.setItem(
                                lootItem == null
                                        ? this.openSlotItem.clone()
                                        : lootItem.getItem().clone());
                        button.clearLore();
                        if (lootItem != null) {
                            button.clearLore();
                            button.addLore(
                                    ColorUtils.colorize(
                                            "&6"
                                                    + lootItem.getMinItems()
                                                    + "-"
                                                    + lootItem.getMaxItems()
                                                    + " &eitems"));
                            button.addLore(
                                    ColorUtils.colorize("&eWeight of &6" + lootItem.getWeight()));
                            button.addLore(ColorUtils.colorize(""));
                            button.addLore(ColorUtils.colorize("&8Click to edit."));
                            button.addLore(ColorUtils.colorize("&8Shift-Click to remove."));
                        }
                    }

                    this.tableMenu.updateButtons(player);
                });
        ItemStack blockedSlot = ItemUtils.getBlockedMenuItem();
        this.tableMenu.addButton(0, new Button("blocked_0", blockedSlot));
        this.tableMenu.addButton(1, new Button("blocked_1", blockedSlot));
        this.tableMenu.addButton(2, new Button("blocked_2", blockedSlot));
        this.tableMenu.addButton(6, new Button("blocked_6", blockedSlot));
        this.tableMenu.addButton(7, new Button("blocked_7", blockedSlot));
        this.tableMenu.addButton(8, new Button("blocked_8", blockedSlot));
        Button minButton = new Button("min", Material.COAL, "&aMinimum Items");
        this.updateMinButton(minButton);
        minButton.addAction(
                "left_click",
                event -> {
                    event.setCancelled(true);
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.LEFT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                        this.table.setMinimumItems(
                                Math.min(this.table.getMaxItems(), this.table.getMinItems() + 1));
                        this.updateMinButton(this.getLiveButton(player, 3));
                        this.tableMenu.updateButtons(player);
                    }
                });
        minButton.addAction(
                "right_click",
                event -> {
                    event.setCancelled(true);
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.RIGHT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                        this.table.setMinimumItems(Math.max(0, this.table.getMinItems() - 1));
                        this.updateMinButton(this.getLiveButton(player, 3));
                        this.tableMenu.updateButtons(player);
                    }
                });
        this.tableMenu.addButton(3, minButton);
        Button maxButton = new Button("max", Material.GOLD_INGOT, "&aMaximum Items");
        this.updateMaxButton(maxButton);
        maxButton.addAction(
                "left_click",
                event -> {
                    event.setCancelled(true);
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.LEFT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                        this.table.setMaximumItems(
                                Math.min(Integer.MAX_VALUE - 1, this.table.getMaxItems() + 1));
                        this.updateMaxButton(this.getLiveButton(player, 4));
                        this.tableMenu.updateButtons(player);
                    }
                });
        maxButton.addAction(
                "right_click",
                event -> {
                    event.setCancelled(true);
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.RIGHT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                        this.table.setMaximumItems(
                                Math.max(this.table.getMinItems(), this.table.getMaxItems() - 1));
                        this.updateMaxButton(this.getLiveButton(player, 4));
                        this.tableMenu.updateButtons(player);
                    }
                });
        this.tableMenu.addButton(4, maxButton);
        Button allowDupesButton = new Button("dupes", Material.DIAMOND, "&aDuplicate Items");
        this.updateAllowDuplicatesButton(allowDupesButton);
        allowDupesButton.addAction(
                "click",
                event -> {
                    event.setCancelled(true);
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    Player player = (Player) event.getWhoClicked();
                    this.table.setAllowDuplicates(!this.table.isAllowDuplicates());
                    if (this.table.isAllowDuplicates()) {
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                    } else {
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                    }

                    this.updateAllowDuplicatesButton(this.getLiveButton(player, 5));
                    this.tableMenu.updateButtons(player);
                });
        this.tableMenu.addButton(5, allowDupesButton);

        for (int slot = 9; slot < 54; slot++) {
            ItemStack item = this.openSlotItem.clone();
            LootTableItem tableItem = this.table.getItems().get(slot);
            if (tableItem != null) {
                item = tableItem.getItem().clone();
            }

            Button button = new Button("item_" + slot, item);
            int finalSlot = slot;
            button.addAction(
                    "click",
                    event -> {
                        event.setCancelled(true);
                        if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                        ClickType clickType = event.getClick();
                        if (clickType == ClickType.LEFT
                                || clickType == ClickType.RIGHT
                                || clickType == ClickType.SHIFT_LEFT
                                || clickType == ClickType.SHIFT_RIGHT) {
                            LootTableItem lootData = this.table.getItems().get(finalSlot);
                            Player player = (Player) event.getWhoClicked();
                            Button liveButton = this.getLiveButton(player, finalSlot);
                            ItemStack cursorItem = event.getCursor();
                            if (lootData != null) {
                                if (clickType == ClickType.SHIFT_LEFT
                                        || clickType == ClickType.SHIFT_RIGHT) {
                                    if (liveButton != null) {
                                        liveButton.setItem(this.openSlotItem);
                                        liveButton.clearLore();
                                    }
                                    ItemUtils.giveOrDrop(player, lootData.getItem());
                                    this.table.removeItem(finalSlot);
                                    this.tableMenu.updateButtons(player);
                                    return;
                                }

                                this.editLootItem(lootData, player);
                                return;
                            }

                            if (cursorItem.getType() != Material.AIR) {
                                LootTableItem lootItem = new LootTableItem(cursorItem.clone());
                                this.table.putItem(finalSlot, lootItem);
                                ItemStack buttonItem = cursorItem.clone();
                                if (liveButton != null) {
                                    liveButton.setItem(buttonItem);
                                    liveButton.clearLore();
                                }
                                cursorItem.setAmount(0);
                                if (liveButton != null) {
                                    liveButton.addLore(
                                            ColorUtils.colorize(
                                                    "&6"
                                                            + lootItem.getMinItems()
                                                            + "-"
                                                            + lootItem.getMaxItems()
                                                            + " &eitems"));
                                    liveButton.addLore(
                                            ColorUtils.colorize(
                                                    "&eWeight of &6" + lootItem.getWeight()));
                                    liveButton.addLore(ColorUtils.colorize(""));
                                    liveButton.addLore(ColorUtils.colorize("&8Click to edit."));
                                    liveButton.addLore(
                                            ColorUtils.colorize("&8Shift-Click to remove."));
                                }
                                this.tableMenu.updateButtons(player);
                            }
                        }
                    });
            this.tableMenu.addButton(slot, button);
        }
    }

    /** Performs edit loot item. */
    private void editLootItem(LootTableItem lootItem, Player player) {
        this.owner.openItem(this.table, lootItem, player);
    }

    /** Updates min button. */
    private void updateMinButton(Button minButton) {
        if (minButton == null) {
            return;
        }

        minButton.clearLore();
        minButton.setAmount(Math.max(1, this.table.getMinItems()));
        minButton.addLore(
                ColorUtils.colorize("&eAt least &6" + this.table.getMinItems() + " &eitems"));
        minButton.addLore(ColorUtils.colorize(""));
        minButton.addLore(ColorUtils.colorize("&7Determines the minimum amount of"));
        minButton.addLore(ColorUtils.colorize("&7the below items this loot table can"));
        minButton.addLore(ColorUtils.colorize("&7generate."));
        minButton.addLore(ColorUtils.colorize(""));
        minButton.addLore(ColorUtils.colorize("&8Left click increases."));
        minButton.addLore(ColorUtils.colorize("&8Right click decreases."));
    }

    /** Updates max button. */
    private void updateMaxButton(Button maxButton) {
        if (maxButton == null) {
            return;
        }

        maxButton.clearLore();
        maxButton.setAmount(Math.max(1, this.table.getMaxItems()));
        maxButton.addLore(
                ColorUtils.colorize("&eAt most &6" + this.table.getMaxItems() + " &eitems"));
        maxButton.addLore(ColorUtils.colorize(""));
        maxButton.addLore(ColorUtils.colorize("&7Determines the maximum amount of"));
        maxButton.addLore(ColorUtils.colorize("&7the below items this loot table can"));
        maxButton.addLore(ColorUtils.colorize("&7generate."));
        maxButton.addLore(ColorUtils.colorize(""));
        maxButton.addLore(ColorUtils.colorize("&8Left click increases."));
        maxButton.addLore(ColorUtils.colorize("&8Right click decreases."));
    }

    /** Updates allow duplicates button. */
    private void updateAllowDuplicatesButton(Button allowDuplicatesButton) {
        if (allowDuplicatesButton == null) {
            return;
        }

        String dupeInfo =
                this.table.isAllowDuplicates()
                        ? "&aAllow duplicate items"
                        : "&cDo not allow duplicate items";
        String dupeLabel =
                this.table.isAllowDuplicates() ? "&aDuplicate Items" : "&cDuplicate Items";
        allowDuplicatesButton.setDisplayName(dupeLabel);
        allowDuplicatesButton.clearLore();
        allowDuplicatesButton.addLore(ColorUtils.colorize(dupeInfo));
        allowDuplicatesButton.addLore("");
        allowDuplicatesButton.addLore(ColorUtils.colorize("&7Toggles whether or not multiple"));
        allowDuplicatesButton.addLore(ColorUtils.colorize("&7of the same item can generate in"));
        allowDuplicatesButton.addLore(ColorUtils.colorize("&7a loot table."));
    }

    private Button getLiveButton(Player player, int slot) {
        if (player == null) {
            return null;
        }

        GuiInventory gui = this.tableMenu.getInventoryFor(player);
        return gui == null ? this.tableMenu.getButtons().get(slot) : gui.buttons().get(slot);
    }
}
