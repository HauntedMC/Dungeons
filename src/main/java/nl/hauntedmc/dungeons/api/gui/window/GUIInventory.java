package nl.hauntedmc.dungeons.api.gui.window;

import java.util.HashMap;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import org.bukkit.inventory.Inventory;

public class GUIInventory {
   private final Inventory inv;
   private final HashMap<Integer, Button> buttons;


   public GUIInventory(GUIWindow GUIWindow, Inventory inv) {
      this.inv = inv;
      this.buttons = new HashMap<>(GUIWindow.getButtons());
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

}
