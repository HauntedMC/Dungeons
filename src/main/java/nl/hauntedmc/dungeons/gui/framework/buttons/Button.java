package nl.hauntedmc.dungeons.gui.framework.buttons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.gui.framework.actions.Action;
import nl.hauntedmc.dungeons.gui.framework.text.GuiTextUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Clickable inventory button model used by GUI windows.
 */
public class Button {
    private final String id;
    private ItemStack item;
    private final ArrayList<String> commands;
    private final HashMap<String, Action<InventoryClickEvent>> actions;

    /** Creates a button from material and display name. */
    public Button(@NotNull String id, @NotNull Material mat, @NotNull String display) {
        this.id = id;
        this.item = new ItemStack(mat);
        ItemMeta meta = this.item.getItemMeta();
        meta.displayName(GuiTextUtils.component(display));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        this.item.setItemMeta(meta);
        this.commands = new ArrayList<>();
        this.actions = new HashMap<>();
    }

    /** Creates a button from an item stack template. */
    public Button(@NotNull String id, @NotNull ItemStack item) {
        this.id = id;
        this.item = item.clone();
        ItemMeta meta = this.item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        this.item.setItemMeta(meta);
        this.commands = new ArrayList<>();
        this.actions = new HashMap<>();
    }

    /** Creates a shallow copy of another button definition. */
    public Button(Button button) {
        this.id = button.getId();
        this.item = button.item.clone();
        ItemMeta meta = this.item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        this.item.setItemMeta(meta);
        this.commands = button.getCommands();
        this.actions = button.getActions();
    }

    /** Returns the stable button identifier. */
    public final String getId() {
        return this.id;
    }

    /** Returns the rendered item backing this button. */
    public final ItemStack getItem() {
        return this.item;
    }

    /** Replaces the rendered item backing this button. */
    public final void setItem(ItemStack item) {
        this.item = item;
    }

    /** Sets displayed stack amount for this button item. */
    public final void setAmount(int amount) {
        this.item.setAmount(amount);
    }

    /** Returns displayed stack amount for this button item. */
    public final int getAmount() {
        return this.item.getAmount();
    }

    /** Toggles a hidden enchant glint highlight on this button item. */
    public final void setEnchanted(boolean enchanted) {
        if (enchanted) {
            this.item.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        } else {
            this.item.removeEnchantment(Enchantment.AQUA_AFFINITY);
        }
    }

    /** Sets display name for this button item. */
    public final void setDisplayName(String display) {
        ItemMeta meta = this.item.getItemMeta();
        meta.displayName(GuiTextUtils.component(display));
        this.item.setItemMeta(meta);
    }

    /** Returns display name for this button item. */
    public final String getDisplayName() {
        ItemMeta meta = this.item.getItemMeta();
        return GuiTextUtils.serialize(meta.displayName());
    }

    /** Replaces lore for this button item. */
    public final void setLore(List<String> lore) {
        ItemMeta meta = this.item.getItemMeta();
        meta.lore(GuiTextUtils.components(lore));
        this.item.setItemMeta(meta);
    }

    /** Appends multiple lore lines to this button item. */
    public final void addLore(List<String> lines) {
        ItemMeta meta = this.item.getItemMeta();
        ArrayList<net.kyori.adventure.text.Component> lore;
        if (meta.lore() == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(meta.lore());
        }

        lore.addAll(GuiTextUtils.components(lines));
        meta.lore(lore);
        this.item.setItemMeta(meta);
    }

    /** Appends one lore line to this button item. */
    public final void addLore(String line) {
        ItemMeta meta = this.item.getItemMeta();
        ArrayList<net.kyori.adventure.text.Component> lore;
        if (meta.lore() == null) {
            lore = new ArrayList<>();
        } else {
            lore = new ArrayList<>(meta.lore());
        }

        lore.add(GuiTextUtils.component(line));
        meta.lore(lore);
        this.item.setItemMeta(meta);
    }

    /** Clears lore from this button item. */
    public final void clearLore() {
        ItemMeta meta = this.item.getItemMeta();
        meta.lore(List.of());
        this.item.setItemMeta(meta);
    }

    /** Adds console commands executed when this button is clicked. */
    public final void addCommands(List<String> commands) {
        this.commands.addAll(commands);
    }

    /** Returns configured console commands for this button. */
    public final ArrayList<String> getCommands() {
        return this.commands;
    }

    /** Runs configured console commands for this button. */
    public final void runCommands() {
        for (String command : this.commands) {
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
        }
    }

    /** Adds a click action callback keyed by id. */
    public final void addAction(String id, Action<InventoryClickEvent> action) {
        this.actions.put(id, action);
    }

    /** Returns click action callbacks keyed by id. */
    public final HashMap<String, Action<InventoryClickEvent>> getActions() {
        return this.actions;
    }

    /** Runs click action callbacks for this button. */
    public final void runActions(InventoryClickEvent event) {
        for (Entry<String, Action<InventoryClickEvent>> pair : this.actions.entrySet()) {
            pair.getValue().run(event);
        }
    }

    /** Handles one inventory click on this button. */
    public void click(InventoryClickEvent event) {
        this.runCommands();
        this.runActions(event);
    }

    /** Creates a copy usable in player-specific GUI inventories. */
    public Button clone() {
        Button button = new Button(this.id, this.item);
        button.addCommands(button.getCommands());
        return button;
    }
}
