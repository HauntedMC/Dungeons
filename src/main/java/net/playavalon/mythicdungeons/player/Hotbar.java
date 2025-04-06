package net.playavalon.mythicdungeons.player;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class Hotbar {
   private List<ItemStack> items;
   private Player player;
   private PlayerInventory inv;
   private int handSlot;

   public Hotbar(Player player) {
      this.player = player;
      this.items = new ArrayList<>();
      this.inv = player.getInventory();
      this.handSlot = this.inv.getHeldItemSlot();

      for (int i = 0; i < 9; i++) {
         ItemStack item = this.inv.getItem(i);
         if (item != null && item.getType() != Material.AIR) {
            this.items.add(this.inv.getItem(i));
         } else {
            this.items.add(new ItemStack(Material.AIR));
         }
      }
   }

   public Hotbar() {
      this.items = new ArrayList<>();

      for (int i = 0; i < 9; i++) {
         this.items.add(new ItemStack(Material.AIR));
      }
   }

   public void empty() {
      this.items.clear();
   }

   public void addItem(ItemStack item) {
      this.items.add(item);
   }

   public void setItem(int index, ItemStack item) {
      this.items.set(index, item);
   }

   public void setHotbar(PlayerInventory inv) {
      this.setHotbar(inv, false);
   }

   public void setHotbar(PlayerInventory inv, boolean setHand) {
      for (int i = 0; i < 9; i++) {
         inv.setItem(i, this.items.get(i));
      }

      if (setHand) {
         inv.setHeldItemSlot(this.handSlot);
      }
   }

   public void setItems(Inventory inv) {
      for (int i = 0; i < 9; i++) {
         inv.setItem(i, this.items.get(i));
      }
   }

   public void updateItems(Inventory inv) {
      for (int i = 0; i < 9; i++) {
         ItemStack item = inv.getItem(i);
         if (item != null && item.getType() != Material.AIR) {
            this.items.set(i, item);
         } else {
            this.items.set(i, item);
         }
      }
   }

   public void updateSlot() {
      this.handSlot = this.inv.getHeldItemSlot();
   }

   public List<ItemStack> getItems() {
      return this.items;
   }

   public ItemStack getItem(int index) {
      return this.items.get(index);
   }
}
