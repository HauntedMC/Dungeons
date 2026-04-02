package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
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
      meta.setDisplayName(this.displayName);
      this.item.setItemMeta(meta);
   }

   public void addLore(String line) {
      ItemMeta meta = this.item.getItemMeta();
      List<String> lore = meta.getLore();
      if (lore == null) {
         lore = new ArrayList<>();
      }

      lore.add(HelperUtils.fullColor(line));
      meta.setLore(lore);
      this.item.setItemMeta(meta);
   }

   public void setAmount(int amount) {
      this.item.setAmount(Math.max(1, Math.min(amount, 99)));
      this.amount = amount;
   }

   public void setEnchanted(boolean enchanted) {
      if (enchanted) {
         this.item.addUnsafeEnchantment(HelperUtils.getVersionEnchantment("AQUA_AFFINITY"), 1);
      } else {
         this.item.removeEnchantment(HelperUtils.getVersionEnchantment("AQUA_AFFINITY"));
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
