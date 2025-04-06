package net.playavalon.mythicdungeons.commands.dungeon;

import java.util.Collections;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.commands.Command;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StuckCommand extends Command<MythicDungeons> {
   public StuckCommand(MythicDungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player)) {
         return false;
      } else if (!Util.hasPermission(sender, "dungeons.stuck")) {
         return false;
      } else {
         Player player = (Player)sender;
         MythicPlayer aPlayer = this.getPlugin().getMythicPlayer(player);
         if (aPlayer.getInstance() == null) {
            LangUtils.sendMessage(player, "commands.stuck.not-in-dungeon");
            return true;
         } else {
            if (this.getPlugin().isStuckKillsPlayer()) {
               player.damage(9.99999999E8);
            } else {
               LangUtils.sendMessage(player, "commands.stuck.success");
               aPlayer.sendToCheckpoint();
            }

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
      return "mythicdungeons.quickstuck";
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
