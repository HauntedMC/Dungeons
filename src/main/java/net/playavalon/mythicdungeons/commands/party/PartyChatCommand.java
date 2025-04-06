package net.playavalon.mythicdungeons.commands.party;

import java.util.Collections;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.commands.Command;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PartyChatCommand extends Command<MythicDungeons> {
   public PartyChatCommand(MythicDungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else {
         MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
         MythicParty party = mythicPlayer.getMythicParty();
         if (party == null) {
            player.sendMessage(LangUtils.getMessage("commands.party.chat.not-in-party"));
            return false;
         } else if (args.length == 0) {
            mythicPlayer.togglePartyChat();
            return false;
         } else {
            StringBuilder msg = new StringBuilder();

            for (String arg : args) {
               msg.append(arg + " ");
            }

            party.sendChatMessage(player, msg.toString());
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
      return "mythicdungeons.party.chat";
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
