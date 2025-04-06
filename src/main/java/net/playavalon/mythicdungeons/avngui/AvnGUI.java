package net.playavalon.mythicdungeons.avngui;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class AvnGUI extends JavaPlugin {
   public static AvnGUI plugin;
   public static boolean debug = true;
   private AvnAPI api;

   public void onEnable() {
      plugin = this;
   }

   public void onDisable() {
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      return true;
   }
}
