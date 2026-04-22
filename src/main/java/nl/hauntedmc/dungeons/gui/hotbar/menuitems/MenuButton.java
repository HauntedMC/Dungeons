package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Renderable item backing one hotbar menu slot.
 */
public class MenuButton {
    private final ItemStack item;
    private String displayName;
    private int amount;

    /** Creates a menu button from one material. */
    public MenuButton(Material mat) {
        this.item = new ItemStack(mat);
    }

    /** Creates a menu button from an item stack template. */
    public MenuButton(ItemStack item) {
        this.item = item;
    }

    /** Sets the button display name. */
    public void setDisplayName(String displayName) {
        this.displayName = ColorUtils.fullColor(displayName);
        ItemMeta meta = this.item.getItemMeta();
        meta.displayName(ComponentUtils.component(this.displayName));
        this.item.setItemMeta(meta);
    }

    /** Appends one lore line to the button item. */
    public void addLore(String line) {
        ItemMeta meta = this.item.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore();
        if (lore == null) {
            lore = new ArrayList<>();
        }

        lore.add(ComponentUtils.component(line));
        meta.lore(lore);
        this.item.setItemMeta(meta);
    }

    /** Sets visible amount on the button item. */
    public void setAmount(int amount) {
        this.item.setAmount(Math.max(1, Math.min(amount, 99)));
        this.amount = amount;
    }

    /** Toggles enchantment glint on the button item. */
    public void setEnchanted(boolean enchanted) {
        if (enchanted) {
            this.item.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        } else {
            this.item.removeEnchantment(Enchantment.AQUA_AFFINITY);
        }
    }

    /** Returns the rendered item for this button. */
    public ItemStack getItem() {
        return this.item;
    }

    /** Returns the display name configured for this button. */
    public String getDisplayName() {
        return this.displayName;
    }

    /** Returns the configured item amount for this button. */
    public int getAmount() {
        return this.amount;
    }
}
