package nl.hauntedmc.dungeons.player;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class DungeonPlayerHotbar {
   private final List<ItemStack> items;
    private int handSlot;

   public DungeonPlayerHotbar(Player player) {
       this.items = new ArrayList<>();
       PlayerInventory inv = player.getInventory();
      this.handSlot = inv.getHeldItemSlot();

      for (int i = 0; i < 9; i++) {
         ItemStack item = inv.getItem(i);
         if (item != null && item.getType() != Material.AIR) {
            this.items.add(inv.getItem(i));
         } else {
            this.items.add(new ItemStack(Material.AIR));
         }
      }
   }

   public DungeonPlayerHotbar() {
      this.items = new ArrayList<>();

      for (int i = 0; i < 9; i++) {
         this.items.add(new ItemStack(Material.AIR));
      }
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

   public ItemStack getItem(int index) {
      return this.items.get(index);
   }
}
