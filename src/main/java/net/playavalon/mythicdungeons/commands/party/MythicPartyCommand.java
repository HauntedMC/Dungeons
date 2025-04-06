package net.playavalon.mythicdungeons.commands.party;

import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.commands.Command;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MythicPartyCommand extends Command<MythicDungeons> {
   public MythicPartyCommand(MythicDungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (args.length == 0) {
         if (!(sender instanceof Player player)) {
            return false;
         } else {
            MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
            MythicParty party = mythicPlayer.getMythicParty();
            if (party == null) {
               sender.sendMessage(LangUtils.getMessage("commands.party.not-in-party"));
               return false;
            } else {
               party.sendPartyInfo(player);
               return false;
            }
         }
      } else {
         String subCommand = args[0];
         switch (subCommand) {
            case "invite":
               if (args.length != 2) {
                  return false;
               }

               if (!(sender instanceof Player player)) {
                  return false;
               }

               MythicPlayer senderPlayer = MythicDungeons.inst().getMythicPlayer(player);
               Player targetPlayer = Bukkit.getPlayer(args[1]);
               if (targetPlayer == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.invite.player-not-found", args[1]));
                  return false;
               }

               if (player.getName().equalsIgnoreCase(args[1])) {
                  player.sendMessage(LangUtils.getMessage("commands.party.invite.player-is-self"));
                  return false;
               }

               MythicPlayer targetMythicPlayer = MythicDungeons.inst().getMythicPlayer(targetPlayer);
               if (targetMythicPlayer.getMythicParty() != null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.invite.player-is-in-a-party", args[1]));
                  return false;
               }

               MythicParty currentParty = senderPlayer.getMythicParty();
               if (currentParty != null) {
                  if (currentParty.getMythicLeader() != senderPlayer) {
                     player.sendMessage(LangUtils.getMessage("commands.party.invite.sender-not-leader"));
                     return false;
                  }

                  if (targetMythicPlayer.getMythicParty() == currentParty) {
                     player.sendMessage(LangUtils.getMessage("commands.party.invite.player-is-in-sender-party"));
                     return false;
                  }
               }

               targetMythicPlayer.setInviteFrom(player);
               player.sendMessage(LangUtils.getMessage("commands.party.invite.success", targetPlayer.getName()));
               break;
            case "join":
               if (args.length != 1) {
                  return false;
               }

               if (!(sender instanceof Player player)) {
                  return false;
               }

               MythicPlayer joiningPlayer = MythicDungeons.inst().getMythicPlayer(player);
               Player inviteFrom = joiningPlayer.getInviteFrom();
               if (inviteFrom == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.join.no-pending-invites"));
                  return false;
               }

               MythicParty oldParty = MythicDungeons.inst().getPartyManager().getParty(player);
               if (oldParty != null) {
                  oldParty.removePlayer(player, true);
               }

               MythicParty partyToJoin = MythicDungeons.inst().getPartyManager().getParty(inviteFrom);
               if (partyToJoin == null) {
                  partyToJoin = new MythicParty(inviteFrom);
               }

               partyToJoin.addPlayer(player);
               joiningPlayer.setInviteFrom(null);
               break;
            case "leave":
               if (args.length != 1) {
                  return false;
               }

               if (!(sender instanceof Player player)) {
                  return false;
               }

               MythicPlayer leavingPlayer = MythicDungeons.inst().getMythicPlayer(player);
               MythicParty currentPartyLeave = leavingPlayer.getMythicParty();
               if (currentPartyLeave == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.leave.not-in-party"));
                  return false;
               }

               player.sendMessage(LangUtils.getMessage("commands.party.leave.success", currentPartyLeave.getMythicLeader().getPlayer().getName()));
               currentPartyLeave.removePlayer(player, true);
               break;
            case "givelead":
               if (args.length != 2) {
                  return false;
               }

               if (!(sender instanceof Player player)) {
                  return false;
               }

               MythicPlayer currentPlayer = MythicDungeons.inst().getMythicPlayer(player);
               MythicParty partyForGiveLead = currentPlayer.getMythicParty();
               if (partyForGiveLead == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.givelead.not-in-party"));
                  return false;
               }

               if (currentPlayer != partyForGiveLead.getMythicLeader()) {
                  player.sendMessage(LangUtils.getMessage("commands.party.givelead.not-party-lead"));
                  return false;
               }

               MythicPlayer newLeaderCandidate = partyForGiveLead.getPlayer(args[1]);
               if (newLeaderCandidate == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.givelead.player-not-found", args[1]));
                  return false;
               }

               Player newLeader = newLeaderCandidate.getPlayer();
               if (newLeader == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.givelead.player-not-found", args[1]));
                  return false;
               }

               partyForGiveLead.setMythicLeader(newLeader);
               break;
            case "kick":
               if (args.length != 2) {
                  return false;
               }

               if (!(sender instanceof Player player)) {
                  return false;
               }

               MythicPlayer leaderPlayer = MythicDungeons.inst().getMythicPlayer(player);
               MythicParty currentPartyKick = leaderPlayer.getMythicParty();
               if (currentPartyKick == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.kick.not-in-party"));
                  return false;
               }

               if (leaderPlayer != currentPartyKick.getMythicLeader()) {
                  player.sendMessage(LangUtils.getMessage("commands.party.kick.not-party-lead"));
                  return false;
               }

               if (currentPartyKick.getPlayer(args[1]) == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.kick.player-not-found", args[1]));
                  return false;
               }

               currentPartyKick.kickPlayer(args[1]);
               if (player.getName().equalsIgnoreCase(args[1])) {
                  player.sendMessage(LangUtils.getMessage("commands.party.kick.player-is-self"));
               }
               break;
            case "disband":
               if (args.length != 1) {
                  return false;
               }

               if (!(sender instanceof Player player)) {
                  return false;
               }

               MythicPlayer disbandingPlayer = MythicDungeons.inst().getMythicPlayer(player);
               MythicParty currentPartyDisband = disbandingPlayer.getMythicParty();
               if (currentPartyDisband == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.disband.not-in-party"));
                  return false;
               }

               if (disbandingPlayer != currentPartyDisband.getMythicLeader()) {
                  player.sendMessage(LangUtils.getMessage("commands.party.disband.not-party-lead"));
                  return false;
               }

               currentPartyDisband.disband();
               break;
            case "chat":
               if (!(sender instanceof Player player)) {
                  return false;
               }

               MythicPlayer chatPlayer = MythicDungeons.inst().getMythicPlayer(player);
               MythicParty chatParty = chatPlayer.getMythicParty();
               if (chatParty == null) {
                  player.sendMessage(LangUtils.getMessage("commands.party.chat.not-in-party"));
                  return false;
               }

               if (args.length == 1) {
                  chatPlayer.togglePartyChat();
                  return false;
               }

               StringBuilder chatMessage = new StringBuilder();
               for (int i = 1; i < args.length; i++) {
                  chatMessage.append(args[i]).append(" ");
               }
               chatParty.sendChatMessage(player, chatMessage.toString());
               break;
            case "spy":
               if (!(sender instanceof Player)) {
                  return false;
               }

               if (!Util.hasPermission(sender, "dungeonparties.spy") && !Util.hasPermission(sender, "mythicparties.spy")) {
                  return false;
               }

               Player spyPlayer = (Player) sender;
               MythicPlayer mythicSpyPlayer = MythicDungeons.inst().getMythicPlayer(spyPlayer);
               if (!MythicDungeons.inst().getPartyManager().getSpies().contains(mythicSpyPlayer)) {
                  MythicDungeons.inst().getPartyManager().addSpy(mythicSpyPlayer);
                  spyPlayer.sendMessage(LangUtils.getMessage("commands.party.spy.enabled"));
               } else {
                  MythicDungeons.inst().getPartyManager().removeSpy(mythicSpyPlayer);
                  spyPlayer.sendMessage(LangUtils.getMessage("commands.party.spy.disabled"));
               }
               break;
            default:
               return false;
         }
         return true;
      }
   }

   @Override
   protected List<String> onTabComplete(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return null;
      } else {
         MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
         MythicParty party = mythicPlayer.getMythicParty();
         List<String> completions = new ArrayList<>();
         if (args.length == 1) {
            completions.add("invite");
            completions.add("join");
            if (party != null) {
               completions.add("leave");
               completions.add("chat");
               if (party.getMythicLeader() == mythicPlayer) {
                  completions.add("givelead");
                  completions.add("kick");
                  completions.add("disband");
               }
            }
            if (Util.hasPermissionSilent(player, "dungeonparties.spy")) {
               completions.add("spy");
            }
         }
         if (args.length == 2) {
            String subCommand = args[0];
            switch (subCommand) {
               case "invite":
                  for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                     if (onlinePlayer.getName().contains(args[1])) {
                        completions.add(onlinePlayer.getName());
                     }
                  }
                  break;
               case "kick":
               case "givelead":
                  if (party != null) {
                     for (MythicPlayer partyMember : party.getMythicPlayers()) {
                        completions.add(partyMember.getPlayer().getName());
                     }
                  }
                  break;
               default:
                  break;
            }
         }
         return completions;
      }
   }

   @Override
   protected String getPermissionNode() {
      return "mythicdungeons.party";
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
