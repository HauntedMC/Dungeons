package net.playavalon.mythicdungeons.avngui.GUI;

import java.util.HashMap;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.Button;
import org.bukkit.inventory.Inventory;

public class GUIInventory {
   private Inventory inv;
   private HashMap<Integer, Button> buttons;

   public GUIInventory(Inventory inv) {
      this.inv = inv;
      this.buttons = new HashMap<>();
   }

   public GUIInventory(Window window, Inventory inv) {
      this.inv = inv;
      this.buttons = new HashMap<>(window.getButtons());
   }

   public Inventory getInv() {
      return this.inv;
   }

   public HashMap<Integer, Button> getButtons() {
      return this.buttons;
   }

   public void setButton(int slot, Button button) {
      this.buttons.put(slot, button);
      this.inv.setItem(slot, button.getItem());
   }

   public void removeButton(int slot) {
      this.buttons.remove(slot);
      this.inv.setItem(slot, null);
   }

   public void sort(int slotStart, int slotEnd) {
      HashMap<Integer, Button> newButtons = new HashMap<>();
      int slot = slotStart;

      for (Button button : this.buttons.values()) {
         if (slot > slotEnd) {
            break;
         }

         newButtons.put(slot, button);
      }

      this.buttons = newButtons;
   }
}
