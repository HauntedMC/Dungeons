package net.playavalon.mythicdungeons.avngui.GUI.Buttons;

import net.playavalon.mythicdungeons.avngui.GUI.Window;
import net.playavalon.mythicdungeons.avngui.Utility.Util;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

public class ButtonPrevious extends Button {
   public ButtonPrevious(@NotNull String id) {
      super(id, Util.getPreviousButton());
   }

   public ButtonPrevious(@NotNull String id, @NotNull Material mat, @NotNull String display) {
      super(id, mat, display);
   }

   @Override
   public void click(InventoryClickEvent event, Window window) {
      window.previous((Player)event.getWhoClicked());
      super.click(event, window);
   }
}
