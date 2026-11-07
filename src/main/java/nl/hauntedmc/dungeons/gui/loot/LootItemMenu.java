package nl.hauntedmc.dungeons.gui.loot;

import nl.hauntedmc.dungeons.content.reward.*;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.*;
import nl.hauntedmc.dungeons.util.text.*;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Rendering and validated edits for one item in its owning table. */
final class LootItemMenu {
    private final LootTableEditor owner;
    private final LootTable table;
    private final LootTableItem entry;
    private GuiWindow itemMenu;

    LootItemMenu(LootTableEditor owner, LootTable table, LootTableItem entry) {
        this.owner = owner;
        this.table = table;
        this.entry = entry;
    }

    void close() {
        if (itemMenu != null) itemMenu.unregister();
    }

    boolean visible(Player player) {
        return itemMenu != null && itemMenu.getInventoryFor(player) != null;
    }

    /** Initializes editor menu. */
    void initialize() {
        this.itemMenu =
                new GuiWindow(LootTableEditor.windowName(this.table, true), 27, "&8Edit Loot Item");
        this.itemMenu.addCloseAction(
                "save",
                event -> {
                    this.owner.closed(this.table, (Player) event.getPlayer());
                });
        Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&cBack");
        backButton.addAction(
                "click",
                event -> {
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    Player player = (Player) event.getWhoClicked();
                    this.owner.open(this.table, player);
                });
        this.itemMenu.addButton(4, backButton);
        Button minButton = new Button("min", Material.COAL, "&aMinimum Items");
        this.updateMinButton(minButton);
        minButton.addAction(
                "left_click",
                event -> {
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.LEFT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                        this.entry.setMinimumItems(
                                Math.min(this.entry.getMaxItems(), this.entry.getMinItems() + 1));
                        this.updateMinButton(this.getLiveButton(player, 12));
                        this.itemMenu.updateButtons(player);
                    }
                });
        minButton.addAction(
                "right_click",
                event -> {
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.RIGHT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                        this.entry.setMinimumItems(Math.max(0, this.entry.getMinItems() - 1));
                        this.updateMinButton(this.getLiveButton(player, 12));
                        this.itemMenu.updateButtons(player);
                    }
                });
        this.itemMenu.addButton(12, minButton);
        Button maxButton = new Button("max", Material.GOLD_INGOT, "&aMaximum Items");
        this.updateMaxButton(maxButton);
        maxButton.addAction(
                "left_click",
                event -> {
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.LEFT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                        this.entry.setMaximumItems(
                                Math.min(
                                        Math.min(
                                                Integer.MAX_VALUE - 1,
                                                Integer.MAX_VALUE
                                                        / this.entry.getItem().getAmount()),
                                        this.entry.getMaxItems() + 1));
                        this.updateMaxButton(this.getLiveButton(player, 13));
                        this.itemMenu.updateButtons(player);
                    }
                });
        maxButton.addAction(
                "right_click",
                event -> {
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.RIGHT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                        this.entry.setMaximumItems(
                                Math.max(this.entry.getMinItems(), this.entry.getMaxItems() - 1));
                        this.updateMaxButton(this.getLiveButton(player, 13));
                        this.itemMenu.updateButtons(player);
                    }
                });
        this.itemMenu.addButton(13, maxButton);
        Button weightButton = new Button("max", Material.EMERALD, "&aItem Weight");
        this.updateWeightButton(weightButton);
        weightButton.addAction(
                "left_click",
                event -> {
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.LEFT
                            || event.getClick() == ClickType.SHIFT_LEFT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                        int amount = 1;
                        if (event.getClick() == ClickType.SHIFT_LEFT) {
                            amount = 5;
                        }

                        this.entry.setWeight(
                                (int)
                                        Math.min(
                                                Integer.MAX_VALUE,
                                                (long) this.entry.getWeight() + amount));
                        this.updateWeightButton(this.getLiveButton(player, 14));
                        this.itemMenu.updateButtons(player);
                    }
                });
        weightButton.addAction(
                "right_click",
                event -> {
                    if (!this.owner.canEdit(this.table, (Player) event.getWhoClicked())) return;
                    if (event.getClick() == ClickType.RIGHT
                            || event.getClick() == ClickType.SHIFT_RIGHT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(
                                player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                        int amount = 1;
                        if (event.getClick() == ClickType.SHIFT_RIGHT) {
                            amount = 5;
                        }

                        this.entry.setWeight(Math.max(0, this.entry.getWeight() - amount));
                        this.updateWeightButton(this.getLiveButton(player, 14));
                        this.itemMenu.updateButtons(player);
                    }
                });
        this.itemMenu.addButton(14, weightButton);
    }

    /** Updates min button. */
    private void updateMinButton(Button minButton) {
        if (minButton == null) {
            return;
        }

        minButton.setAmount(Math.clamp(this.entry.getMinItems(), 1, 999));
        minButton.clearLore();
        minButton.addLore(
                ColorUtils.colorize("&eAt least &6" + this.entry.getMinItems() + " &eitems"));
        minButton.addLore(ColorUtils.colorize(""));
        minButton.addLore(ColorUtils.colorize("&7Determines the min amount of this"));
        minButton.addLore(ColorUtils.colorize("&7item when it appears."));
        minButton.addLore(ColorUtils.colorize(""));
        minButton.addLore(ColorUtils.colorize("&8Left click increases."));
        minButton.addLore(ColorUtils.colorize("&8Right click decreases."));
    }

    /** Updates max button. */
    private void updateMaxButton(Button maxButton) {
        if (maxButton == null) {
            return;
        }

        maxButton.setAmount(Math.clamp(this.entry.getMaxItems(), 1, 999));
        maxButton.clearLore();
        maxButton.addLore(
                ColorUtils.colorize("&eAt most &6" + this.entry.getMaxItems() + " &eitems"));
        maxButton.addLore(ColorUtils.colorize(""));
        maxButton.addLore(ColorUtils.colorize("&7Determines the max amount of this"));
        maxButton.addLore(ColorUtils.colorize("&7item when it appears."));
        maxButton.addLore(ColorUtils.colorize(""));
        maxButton.addLore(ColorUtils.colorize("&8Left click increases."));
        maxButton.addLore(ColorUtils.colorize("&8Right click decreases."));
    }

    /** Updates weight button. */
    private void updateWeightButton(Button weightButton) {
        if (weightButton == null) {
            return;
        }

        weightButton.clearLore();
        weightButton.addLore(ColorUtils.colorize("&eWeight of &6" + this.entry.getWeight()));
        weightButton.addLore(ColorUtils.colorize(""));
        weightButton.addLore(ColorUtils.colorize("&7Determines the chance of this item"));
        weightButton.addLore(ColorUtils.colorize("&7compared to others. An item with a"));
        weightButton.addLore(ColorUtils.colorize("&7weight of 4 is twice as common as an"));
        weightButton.addLore(ColorUtils.colorize("&7item with a weight of 2."));
        weightButton.addLore(ColorUtils.colorize(""));
        weightButton.addLore(ColorUtils.colorize("&8Left and Shift-Left click increases."));
        weightButton.addLore(ColorUtils.colorize("&8Right and Shift-Right click decreases."));
    }

    private Button getLiveButton(Player player, int slot) {
        if (player == null) {
            return null;
        }

        GuiInventory gui = this.itemMenu.getInventoryFor(player);
        return gui == null ? this.itemMenu.getButtons().get(slot) : gui.buttons().get(slot);
    }
}
