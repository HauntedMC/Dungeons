package nl.hauntedmc.dungeons.api.gui;

import nl.hauntedmc.dungeons.api.gui.window.GUIWindow;
import nl.hauntedmc.dungeons.api.gui.window.GUIWindowRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class GUIAPI {
   public static JavaPlugin plugin;

   public GUIAPI(JavaPlugin plugin) {
      GUIAPI.plugin = plugin;
   }

   public void openGUI(Player player, String gui) {
      GUIWindow GUIWindow = GUIWindowRegistry.getWindow(gui);
      if (GUIWindow != null) {
         GUIWindow.open(player);
      }
   }
}
