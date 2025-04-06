package net.playavalon.mythicdungeons.commands.dungeon;

import java.util.Collections;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.commands.Command;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LeaveCommand extends Command<MythicDungeons> {
   public LeaveCommand(MythicDungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else {
         MythicPlayer aPlayer = this.getPlugin().getMythicPlayer(player);
         if (aPlayer.getInstance() == null) {
            if (MythicDungeons.inst().getQueueManager().getQueue(aPlayer) != null) {
               MythicDungeons.inst().getQueueManager().unqueue(aPlayer);
               LangUtils.sendMessage(player, "commands.leave.left-queue");
               return true;
            } else {
               AbstractInstance inst = MythicDungeons.inst().getDungeonInstance(player.getWorld().getName());
               if (inst != null) {
                  if (aPlayer.getSavedPosition() != null) {
                     Util.forceTeleport(player, aPlayer.getSavedPosition());
                  } else {
                     Util.forceTeleport(player, player.getRespawnLocation());
                  }

                  return true;
               } else {
                  LangUtils.sendMessage(player, "commands.leave.not-in-dungeon");
                  return true;
               }
            }
         } else {
            aPlayer.getInstance().removePlayer(aPlayer, false);
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
      return "mythicdungeons.quickleave";
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
