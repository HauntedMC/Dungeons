package net.playavalon.mythicdungeons.commands;

import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public abstract class Command<T extends Plugin> implements TabExecutor {
   protected final T plugin;

   public Command(T plugin, String command) {
      this.plugin = plugin;
      PluginCommand c = plugin.getServer().getPluginCommand(command);
      if (c != null) {
         c.setExecutor(this);
         c.setTabCompleter(this);
      }
   }

   public Command(Command<T> parent, String command) {
      this(parent.getPlugin(), command);
   }

   public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command cmd, @NotNull String label, @NotNull String[] args) {
      if (this.getPermissionNode() != null && !sender.hasPermission(this.getPermissionNode())) {
         sender.sendMessage(Util.colorize("&cYou don't have permission to do this!"));
         return true;
      } else if (!this.isConsoleFriendly() && !(sender instanceof Player)) {
         sender.sendMessage(Util.colorize("&cOnly players can do this!"));
         return true;
      } else {
         return this.onCommand(sender, args);
      }
   }

   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command cmd, @NotNull String label, @NotNull String[] args) {
      if (this.getPermissionNode() != null && !sender.hasPermission(this.getPermissionNode())) {
         return null;
      } else {
         List<String> tabCompletes = this.onTabComplete(sender, args);
         if (tabCompletes == null && args.length == 1) {
            tabCompletes = new ArrayList<>();
         }

         return tabCompletes;
      }
   }

   protected abstract boolean onCommand(CommandSender var1, String[] var2);

   protected abstract List<String> onTabComplete(CommandSender var1, String[] var2);

   protected abstract String getPermissionNode();

   protected abstract boolean isConsoleFriendly();

   protected abstract String getName();

   protected T getPlugin() {
      return this.plugin;
   }
}
