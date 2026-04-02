package nl.hauntedmc.dungeons.commands.party;

import java.util.Collections;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.commands.Command;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.player.party.partyfinder.RecruitmentBuilder;
import nl.hauntedmc.dungeons.player.party.partyfinder.RecruitmentListing;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RecruitCommand extends Command<Dungeons> {
   public RecruitCommand(Dungeons plugin, String command) {
      super(plugin, command);
   }

   @Override
   protected boolean onCommand(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         return false;
      } else if (!HelperUtils.hasPermission(sender, "dungeons.party.recruit")) {
         return false;
      } else {
          DungeonPlayer aPlayer = this.getPlugin().getDungeonPlayer(player);
         if (!this.getPlugin().isPartiesEnabled()) {
            LangUtils.sendMessage(player, "commands.recruit.no-party-system");
            return true;
         } else if (args.length == 0) {
            if (this.getPlugin().getListingManager().getBuilder(player) == null && this.getPlugin().getListingManager().getListing(player) == null) {
               IDungeonParty party = aPlayer.getiDungeonParty();
               if (party != null && party.getLeader() != player) {
                  LangUtils.sendMessage(player, "commands.recruit.leader-only");
               } else {
                  this.getPlugin().getListingManager().putBuilder(player, RecruitmentBuilder.newListing(aPlayer));
                  LangUtils.sendMessage(player, "commands.recruit.recruit-for");
               }
            } else {
               LangUtils.sendMessage(player, "commands.recruit.already-recruiting");
            }
             return true;
         } else if (args[0].equalsIgnoreCase("cancel")) {
            RecruitmentListing listing = this.getPlugin().getListingManager().getListing(player);
            if (listing == null) {
               LangUtils.sendMessage(player, "commands.recruit.cancel.not-recruiting");
            } else {
               listing.removeListing();
               LangUtils.sendMessage(player, "commands.recruit.cancel.success");
               this.getPlugin().getListingManager().removeListing(player);
               this.getPlugin().getListingManager().removeBuilder(player);
            }
             return true;
         } else if (args[0].equalsIgnoreCase("join")) {
            if (args.length != 2) {
               LangUtils.sendMessage(player, "commands.recruit.join.unspecified");
            } else {
               String hostName = args[1];
               if (hostName.equals(player.getName())) {
                  LangUtils.sendMessage(player, "commands.recruit.join.join-self");
               } else {
                  Player hostPlayer = Bukkit.getPlayer(hostName);
                  if (hostPlayer == null) {
                     LangUtils.sendMessage(player, "commands.recruit.join.player-not-found");
                  } else {
                     RecruitmentListing listing = this.getPlugin().getListingManager().getListing(hostPlayer);
                     if (listing == null) {
                        LangUtils.sendMessage(player, "commands.recruit.join.not-recruiting");
                     } else {
                        listing.join(aPlayer);
                     }
                  }
               }
            }
             return true;
         } else {
            if (args[0].equalsIgnoreCase("browse")) {
               Dungeons.inst().getGuiApi().openGUI(player, "partybrowser");
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
      return "dungeons.recruit";
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
