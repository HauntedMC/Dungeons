package nl.hauntedmc.dungeons.commands.dungeon;

import java.util.Collections;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.queue.QueueData;
import nl.hauntedmc.dungeons.commands.Command;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReadyCommand extends Command<Dungeons> {
   public ReadyCommand(Dungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else {
         DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
         QueueData queue = this.getPlugin().getQueueManager().getQueue(aPlayer);
         if (queue == null) {
            LangUtils.sendMessage(player, "commands.ready.not-queued");
         } else {
            queue.ready(aPlayer);
         }
          return true;
      }
   }

   @Override
   protected List<String> onTabComplete(CommandSender sender, String[] args) {
      return Collections.emptyList();
   }

   @Override
   protected String getPermissionNode() {
      return "dungeons.ready";
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
