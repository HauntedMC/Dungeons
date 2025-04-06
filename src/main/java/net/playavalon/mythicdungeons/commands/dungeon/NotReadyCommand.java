package net.playavalon.mythicdungeons.commands.dungeon;

import java.util.Collections;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.queue.QueueData;
import net.playavalon.mythicdungeons.commands.Command;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NotReadyCommand extends Command<MythicDungeons> {
   public NotReadyCommand(MythicDungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else {
         MythicPlayer aPlayer = this.getPlugin().getMythicPlayer(player);
         QueueData queue = this.getPlugin().getQueueManager().getQueue(aPlayer);
         if (queue == null) {
            LangUtils.sendMessage(player, "commands.notready.not-queued");
            return true;
         } else {
            queue.notReady(aPlayer);
            return true;
         }
      }
   }

   @Override
   protected List<String> onTabComplete(CommandSender sender, String[] args) {
      return Collections.emptyList();
   }

   @Override
   protected String getPermissionNode() {
      return "mythicdungeons.notready";
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
