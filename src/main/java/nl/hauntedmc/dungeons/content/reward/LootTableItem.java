package nl.hauntedmc.dungeons.content.reward;

import java.util.HashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * One weighted entry inside a {@link LootTable}.
 *
 * <p>Each entry stores the base item plus its weight and randomized stack-size bounds, along with
 * a small editor window for tuning those values.
 */
@TypeKey(id = "dungeons.reward.loot_table_item")
@SerializableAs("dungeons.reward.loot_table_item")
public class LootTableItem implements ConfigurationSerializable {
    private final ItemStack item;
    private int weight = 1;
    private int minItems = 1;
    private int maxItems = 1;
    private GuiWindow itemMenu;

    /**
     * Creates a new LootTableItem instance.
     */
    public LootTableItem(Map<String, Object> config) {
        this.item =
                (ItemStack) config.getOrDefault("item", new ItemStack(Material.RED_STAINED_GLASS_PANE));
        this.weight = (Integer) config.getOrDefault("weight", 1);
        this.minItems = (Integer) config.getOrDefault("minItems", 1);
        this.maxItems = (Integer) config.getOrDefault("maxItems", 1);
    }

    /**
     * Creates a new LootTableItem instance.
     */
    public LootTableItem(ItemStack item) {
        this.item = item;
    }

    /**
     * Returns the randomized item.
     */
    public ItemStack getRandomizedItem() {
        ItemStack item = this.item.clone();
        item.setAmount(
                item.getAmount() * MathUtils.getRandomNumberInRange(this.minItems, this.maxItems));
        return item;
    }

    /**
     * Initializes editor menu.
     */
    public void initializeEditorMenu(LootTable table) {
        this.itemMenu =
                                new GuiWindow("loottable_" + table.getNamespace() + "_item", 27, "&8Edit Loot Item");
        this.itemMenu.addCloseAction(
                "save",
                event -> {
                    RuntimeContext.lootTableRepository().saveTablesConfig();
                    table.setEditor(null);
                });
        Button backButton = new Button("back", Material.RED_STAINED_GLASS_PANE, "&cBack");
        backButton.addAction(
                "click",
                event -> {
                    Player player = (Player) event.getWhoClicked();
                    RuntimeContext.guiService().openGui(player, "loottable_" + table.getNamespace());
                });
        this.itemMenu.addButton(4, backButton);
        Button minButton = new Button("min", Material.COAL, "&aMinimum Items");
        this.updateMinButton(minButton);
        minButton.addAction(
                "left_click",
                event -> {
                    if (event.getClick() == ClickType.LEFT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                        this.minItems = Math.min(this.maxItems, this.minItems + 1);
                        this.updateMinButton(minButton);
                        this.itemMenu.updateButtons(player);
                    }
                });
        minButton.addAction(
                "right_click",
                event -> {
                    if (event.getClick() == ClickType.RIGHT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                        this.minItems = Math.max(0, this.minItems - 1);
                        this.updateMinButton(minButton);
                        this.itemMenu.updateButtons(player);
                    }
                });
        this.itemMenu.addButton(12, minButton);
        Button maxButton = new Button("max", Material.GOLD_INGOT, "&aMaximum Items");
        this.updateMaxButton(maxButton);
        maxButton.addAction(
                "left_click",
                event -> {
                    if (event.getClick() == ClickType.LEFT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                        this.maxItems++;
                        this.updateMaxButton(maxButton);
                        this.itemMenu.updateButtons(player);
                    }
                });
        maxButton.addAction(
                "right_click",
                event -> {
                    if (event.getClick() == ClickType.RIGHT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                        this.maxItems = Math.max(this.minItems, this.maxItems - 1);
                        this.updateMaxButton(maxButton);
                        this.itemMenu.updateButtons(player);
                    }
                });
        this.itemMenu.addButton(13, maxButton);
        Button weightButton = new Button("max", Material.EMERALD, "&aItem Weight");
        this.updateWeightButton(weightButton);
        weightButton.addAction(
                "left_click",
                event -> {
                    if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
                        int amount = 1;
                        if (event.getClick() == ClickType.SHIFT_LEFT) {
                            amount = 5;
                        }

                        this.weight += amount;
                        this.updateWeightButton(weightButton);
                        this.itemMenu.updateButtons(player);
                    }
                });
        weightButton.addAction(
                "right_click",
                event -> {
                    if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                        Player player = (Player) event.getWhoClicked();
                        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 0.8F);
                        int amount = 1;
                        if (event.getClick() == ClickType.SHIFT_RIGHT) {
                            amount = 5;
                        }

                        this.weight = Math.max(0, this.weight - amount);
                        this.updateWeightButton(weightButton);
                        this.itemMenu.updateButtons(player);
                    }
                });
        this.itemMenu.addButton(14, weightButton);
    }

    /**
     * Updates min button.
     */
    private void updateMinButton(Button minButton) {
        minButton.setAmount(Math.max(1, Math.min(this.minItems, 999)));
        minButton.clearLore();
        minButton.addLore(ColorUtils.colorize("&eAt least &6" + this.minItems + " &eitems"));
        minButton.addLore(ColorUtils.colorize(""));
        minButton.addLore(ColorUtils.colorize("&7Determines the min amount of this"));
        minButton.addLore(ColorUtils.colorize("&7item when it appears."));
        minButton.addLore(ColorUtils.colorize(""));
        minButton.addLore(ColorUtils.colorize("&8Left click increases."));
        minButton.addLore(ColorUtils.colorize("&8Right click decreases."));
    }

    /**
     * Updates max button.
     */
    private void updateMaxButton(Button maxButton) {
        maxButton.setAmount(Math.max(1, Math.min(this.maxItems, 999)));
        maxButton.clearLore();
        maxButton.addLore(ColorUtils.colorize("&eAt most &6" + this.maxItems + " &eitems"));
        maxButton.addLore(ColorUtils.colorize(""));
        maxButton.addLore(ColorUtils.colorize("&7Determines the max amount of this"));
        maxButton.addLore(ColorUtils.colorize("&7item when it appears."));
        maxButton.addLore(ColorUtils.colorize(""));
        maxButton.addLore(ColorUtils.colorize("&8Left click increases."));
        maxButton.addLore(ColorUtils.colorize("&8Right click decreases."));
    }

    /**
     * Updates weight button.
     */
    private void updateWeightButton(Button weightButton) {
        weightButton.clearLore();
        weightButton.addLore(ColorUtils.colorize("&eWeight of &6" + this.weight));
        weightButton.addLore(ColorUtils.colorize(""));
        weightButton.addLore(ColorUtils.colorize("&7Determines the chance of this item"));
        weightButton.addLore(ColorUtils.colorize("&7compared to others. An item with a"));
        weightButton.addLore(ColorUtils.colorize("&7weight of 4 is twice as common as an"));
        weightButton.addLore(ColorUtils.colorize("&7item with a weight of 2."));
        weightButton.addLore(ColorUtils.colorize(""));
        weightButton.addLore(ColorUtils.colorize("&8Left and Shift-Left click increases."));
        weightButton.addLore(ColorUtils.colorize("&8Right and Shift-Right click decreases."));
    }

    @NotNull public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("item", this.item);
        map.put("weight", this.weight);
        map.put("minItems", this.minItems);
        map.put("maxItems", this.maxItems);
        return map;
    }

    /**
     * Returns the item.
     */
    public ItemStack getItem() {
        return this.item;
    }

    /**
     * Returns the weight.
     */
    public int getWeight() {
        return this.weight;
    }

    /**
     * Returns the min items.
     */
    public int getMinItems() {
        return this.minItems;
    }

    /**
     * Returns the max items.
     */
    public int getMaxItems() {
        return this.maxItems;
    }
}
