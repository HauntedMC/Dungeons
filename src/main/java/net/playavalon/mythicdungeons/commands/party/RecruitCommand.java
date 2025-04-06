package net.playavalon.mythicdungeons.commands.party;

import java.util.Collections;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.commands.Command;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partyfinder.RecruitmentBuilder;
import net.playavalon.mythicdungeons.player.party.partyfinder.RecruitmentListing;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RecruitCommand extends Command<MythicDungeons> {
   public RecruitCommand(MythicDungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player)) {
         return false;
      } else if (!Util.hasPermission(sender, "dungeons.party.recruit")) {
         return false;
      } else {
         Player player = (Player)sender;
         MythicPlayer aPlayer = this.getPlugin().getMythicPlayer(player);
         if (!this.getPlugin().isPartiesEnabled()) {
            LangUtils.sendMessage(player, "commands.recruit.no-party-system");
            return true;
         } else if (args.length == 0) {
            if (this.getPlugin().getListingManager().getBuilder(player) == null && this.getPlugin().getListingManager().getListing(player) == null) {
               IDungeonParty party = aPlayer.getDungeonParty();
               if (party != null && party.getLeader() != player) {
                  LangUtils.sendMessage(player, "commands.recruit.leader-only");
                  return true;
               } else {
                  this.getPlugin().getListingManager().putBuilder(player, RecruitmentBuilder.newListing(aPlayer));
                  LangUtils.sendMessage(player, "commands.recruit.recruit-for");
                  return true;
               }
            } else {
               LangUtils.sendMessage(player, "commands.recruit.already-recruiting");
               return true;
            }
         } else if (args[0].equalsIgnoreCase("cancel")) {
            RecruitmentListing listing = this.getPlugin().getListingManager().getListing(player);
            if (listing == null) {
               LangUtils.sendMessage(player, "commands.recruit.cancel.not-recruiting");
               return true;
            } else {
               listing.removeListing();
               LangUtils.sendMessage(player, "commands.recruit.cancel.success");
               this.getPlugin().getListingManager().removeListing(player);
               this.getPlugin().getListingManager().removeBuilder(player);
               return true;
            }
         } else if (args[0].equalsIgnoreCase("join")) {
            if (args.length != 2) {
               LangUtils.sendMessage(player, "commands.recruit.join.unspecified");
               return true;
            } else {
               String hostName = args[1];
               if (hostName.equals(player.getName())) {
                  LangUtils.sendMessage(player, "commands.recruit.join.join-self");
                  return true;
               } else {
                  Player hostPlayer = Bukkit.getPlayer(hostName);
                  if (hostPlayer == null) {
                     LangUtils.sendMessage(player, "commands.recruit.join.player-not-found");
                     return true;
                  } else {
                     RecruitmentListing listing = this.getPlugin().getListingManager().getListing(hostPlayer);
                     if (listing == null) {
                        LangUtils.sendMessage(player, "commands.recruit.join.not-recruiting");
                        return true;
                     } else {
                        listing.join(aPlayer);
                        return true;
                     }
                  }
               }
            }
         } else {
            if (args[0].equalsIgnoreCase("browse")) {
               MythicDungeons.inst().getAvnAPI().openGUI(player, "partybrowser");
            }

            return false;
         }
      }
   }

   @Override
   protected List<String> onTabComplete(CommandSender sender, String[] args) {
      return Collections.emptyList();
   }

   @Override
   protected String getPermissionNode() {
      return "mythicdungeons.recruit";
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
