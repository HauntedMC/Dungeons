package nl.hauntedmc.dungeons.commands.dungeon;

import java.util.Collections;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.commands.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RewardsCommand extends Command<Dungeons> {
   public RewardsCommand(Dungeons plugin, String command) {
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
      return "dungeons.rewards";
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
