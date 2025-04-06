package net.playavalon.mythicdungeons.menu;

import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MenuButton {
   private ItemStack item;
   private String displayName;
   private int amount;
   private boolean enchanted;

   public MenuButton(Material mat) {
      this.item = new ItemStack(mat);
   }

   public MenuButton(ItemStack item) {
      this.item = item;
   }

   public void setDisplayName(String displayName) {
      this.displayName = Util.fullColor(displayName);
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

      lore.add(Util.fullColor(line));
      meta.setLore(lore);
      this.item.setItemMeta(meta);
   }

   public void setLoreLine(int index, String line) {
      ItemMeta meta = this.item.getItemMeta();
      List<String> lore = meta.getLore();
      if (lore != null) {
         try {
            lore.set(index, Util.fullColor(line));
            meta.setLore(lore);
            this.item.setItemMeta(meta);
         } catch (ArrayIndexOutOfBoundsException var6) {
            MythicDungeons.inst()
               .getLogger()
               .info(MythicDungeons.debugPrefix + Util.colorize("&cTried to set MenuButton lore at line " + index + ", but it was out of bounds!"));
            var6.printStackTrace();
         }
      }
   }

   public void removeLore(int index) {
      ItemMeta meta = this.item.getItemMeta();
      List<String> lore = meta.getLore();
      if (lore != null) {
         lore.remove(index);
         meta.setLore(lore);
         this.item.setItemMeta(meta);
      }
   }

   public void clearLore() {
      ItemMeta meta = this.item.getItemMeta();
      List<String> lore = meta.getLore();
      if (lore != null) {
         lore.clear();
         meta.setLore(lore);
         this.item.setItemMeta(meta);
      }
   }

   public void setAmount(int amount) {
      this.item.setAmount(Math.max(1, Math.min(amount, 99)));
      this.amount = amount;
   }

   public void setEnchanted(boolean enchanted) {
      if (enchanted) {
         this.item.addUnsafeEnchantment(Util.getVersionEnchantment("AQUA_AFFINITY"), 1);
      } else {
         this.item.removeEnchantment(Util.getVersionEnchantment("AQUA_AFFINITY"));
      }

      this.enchanted = enchanted;
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

   public boolean isEnchanted() {
      return this.enchanted;
   }
}
