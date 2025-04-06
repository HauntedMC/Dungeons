package net.playavalon.mythicdungeons.avngui;

import net.playavalon.mythicdungeons.avngui.GUI.Window;
import net.playavalon.mythicdungeons.avngui.GUI.WindowGroup;
import net.playavalon.mythicdungeons.avngui.GUI.WindowGroupManager;
import net.playavalon.mythicdungeons.avngui.GUI.WindowManager;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.ButtonNext;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.ButtonPrevious;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class AvnAPI {
   public static JavaPlugin plugin;

   public AvnAPI(JavaPlugin plugin) {
      AvnAPI.plugin = plugin;
      new ButtonPrevious("previous");
      new ButtonNext("next");
   }

   public void openGUI(Player player, String gui) {
      Window window = WindowManager.getWindow(gui);
      if (window != null) {
         window.open(player);
      }
   }

   public void openGUIGroup(Player player, String guiGroup) {
      WindowGroup group = WindowGroupManager.getGroup(guiGroup);
      if (group != null) {
         group.open(player);
      }
   }
}
