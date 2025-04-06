package net.playavalon.mythicdungeons.commands.dungeon;

import java.util.Collections;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.commands.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RewardsCommand extends Command<MythicDungeons> {
   public RewardsCommand(MythicDungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else {
         this.getPlugin().getAvnAPI().openGUI(player, "rewards");
         return true;
      }
   }

   @Override
   protected List<String> onTabComplete(CommandSender sender, String[] args) {
      return Collections.emptyList();
   }

   @Override
   protected String getPermissionNode() {
      return "mythicdungeons.rewards";
   }

   @Override
   protected boolean isConsoleFriendly() {
      return false;
   }

   @Override
   protected String getName() {
      return null;
   }
}
