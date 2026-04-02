package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MenuButton {
   private final ItemStack item;
   private String displayName;
   private int amount;

    public MenuButton(Material mat) {
      this.item = new ItemStack(mat);
   }

   public MenuButton(ItemStack item) {
      this.item = item;
   }

   public void setDisplayName(String displayName) {
      this.displayName = HelperUtils.fullColor(displayName);
      ItemMeta meta = this.item.getItemMeta();
      meta.displayName(HelperUtils.component(this.displayName));
      this.item.setItemMeta(meta);
   }

   public void addLore(String line) {
      ItemMeta meta = this.item.getItemMeta();
      List<net.kyori.adventure.text.Component> lore = meta.lore();
      if (lore == null) {
         lore = new ArrayList<>();
      }

      lore.add(HelperUtils.component(line));
      meta.lore(lore);
      this.item.setItemMeta(meta);
   }

   public void setAmount(int amount) {
      this.item.setAmount(Math.max(1, Math.min(amount, 99)));
      this.amount = amount;
   }

   public void setEnchanted(boolean enchanted) {
      if (enchanted) {
         this.item.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
      } else {
         this.item.removeEnchantment(Enchantment.AQUA_AFFINITY);
      }

   }

   public ItemStack getItem() {
      return this.item;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public int getAmount() {
      return this.amount;
   }

}
