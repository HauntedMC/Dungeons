package nl.hauntedmc.dungeons.listeners;

import java.util.Date;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.player.party.DungeonPartyWrapper;
import nl.hauntedmc.dungeons.player.party.partyfinder.RecruitmentListing;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PAPIPlaceholders extends PlaceholderExpansion {

    public PAPIPlaceholders() {
    }

   @NotNull
   public String getAuthor() {
      return "HauntedMC";
   }

   @NotNull
   public String getIdentifier() {
      return "dungeons";
   }

   @NotNull
   public String getVersion() {
      return Dungeons.inst().getVersion();
   }

   public boolean persist() {
      return true;
   }

   public String onRequest(OfflinePlayer oPlayer, @NotNull String params) {
      DungeonPlayer mPlayer = null;
      if (oPlayer != null) {
         mPlayer = Dungeons.inst().getDungeonPlayer(oPlayer.getUniqueId());
      }

      String[] elements = params.split("_");
      if (params.contains("dungeon")) {
         if (elements.length < 1) {
            return null;
         }

         String targetDungeonName = elements[1];
         AbstractDungeon dungeon = Dungeons.inst().getDungeons().get(targetDungeonName);
         if (dungeon != null) {
            if (params.equalsIgnoreCase("dungeon_" + targetDungeonName + "_lives")) {
               int lives = dungeon.getConfig().getInt("General.PlayerLives", 0);
               String result;
               if (lives <= 0) {
                  result = "Infinite";
               } else {
                  result = String.valueOf(lives);
               }

               return result;
            }

            if (params.equalsIgnoreCase("dungeon_" + targetDungeonName + "_min_players")) {
               return String.valueOf(dungeon.getConfig().getInt("Requirements.MinPartySize", 1));
            }

            if (params.equalsIgnoreCase("dungeon_" + targetDungeonName + "_max_players")) {
               return String.valueOf(dungeon.getConfig().getInt("Requirements.MaxPartySize", 4));
            }

            if (params.equalsIgnoreCase("dungeon_" + targetDungeonName + "_min_class_level")) {
               return String.valueOf(dungeon.getConfig().getInt("Requirements.ClassLevel", 1));
            }

            if (params.equalsIgnoreCase("dungeon_" + targetDungeonName + "_unlock_time")) {
               if (mPlayer == null) {
                  return null;
               }

               Date unlockTime = dungeon.getAccessCooldown(mPlayer.getPlayer());
               if (unlockTime == null) {
                  return "Unlocked";
               }

               return StringUtils.formatDate(unlockTime);
            }

            if (params.equalsIgnoreCase("dungeon_" + targetDungeonName + "_unlock_time_seconds")) {
               if (mPlayer == null) {
                  return null;
               }

               Date unlockTime = dungeon.getAccessCooldown(mPlayer.getPlayer());
               if (unlockTime == null) {
                  return "0";
               }

               long current = System.currentTimeMillis();
               long time = unlockTime.getTime();
               long msToUnlock = time - current;
               if (msToUnlock < 0L) {
                  return "0";
               }

               return String.valueOf(msToUnlock / 1000L);
            }
         }

         if (mPlayer == null) {
            return null;
         }

         AbstractInstance instance = mPlayer.getInstance();
         if (instance == null) {
            return " ";
         }

         if (params.equalsIgnoreCase("dungeon_name")) {
            return instance.getDungeon().getWorldName();
         }

         if (params.equalsIgnoreCase("dungeon_display_name")) {
            return instance.getDungeon().getDisplayName();
         }

         if (params.equalsIgnoreCase("dungeon_unlock_time")) {
            Date unlockTimex = instance.getDungeon().getAccessCooldown(mPlayer.getPlayer());
            if (unlockTimex == null) {
               return "Unlocked";
            }

            return StringUtils.formatDate(unlockTimex);
         }

         if (params.equalsIgnoreCase("dungeon_id")) {
            return instance.getInstanceWorld().getName();
         }

         if (params.equalsIgnoreCase("dungeon_players")) {
            return String.valueOf(instance.getPlayers().size());
         }

         InstancePlayable play = instance.asPlayInstance();
         if (play != null) {
            if (params.equalsIgnoreCase("dungeon_players_living")) {
               return String.valueOf(play.getLivingPlayers().size());
            }

            if (params.equalsIgnoreCase("dungeon_lives_left")) {
               String result;
               if (!play.isLivesEnabled()) {
                  result = "Infinite";
               } else {
                  result = String.valueOf(play.getPlayerLives().get(mPlayer.getPlayer().getUniqueId()));
               }

               return result;
            }

            if (params.equals("dungeon_time_left")) {
               return StringUtils.formatDuration(play.getTimeLeft() * 1000L);
            }

            if (params.equals("dungeon_time_elapsed")) {
               return StringUtils.formatDuration(play.getTimeElapsed() * 1000L);
            }
         }
      }

      if (params.contains("recruitment")) {
         if (elements.length < 1) {
            return null;
         }

         String targetPlayerName = elements[1];
         Player player = Bukkit.getPlayer(targetPlayerName);
         if (player != null) {
            RecruitmentListing listing = Dungeons.inst().getListingManager().getListing(player);
            if (listing == null) {
               return " ";
            }

            if (params.equalsIgnoreCase("recruitment_" + player.getName() + "_host")) {
               return player.getName();
            }

            if (params.equalsIgnoreCase("recruitment_" + player.getName() + "_label")) {
               return listing.getLabel();
            }

            if (params.equalsIgnoreCase("recruitment_" + player.getName() + "_description")) {
               return listing.getDescription();
            }

            if (params.equalsIgnoreCase("recruitment_" + player.getName() + "_players_present")) {
               return String.valueOf(listing.getParty().getPlayers().size());
            }

            if (params.equalsIgnoreCase("recruitment_" + player.getName() + "_players_needed")) {
               return String.valueOf(listing.getPartySize() - listing.getParty().getPlayers().size());
            }

            if (params.equalsIgnoreCase("recruitment_" + player.getName() + "_total_players")) {
               return String.valueOf(listing.getPartySize());
            }

            if (params.equalsIgnoreCase("recruitment_" + player.getName() + "_needs_password")) {
               return listing.getPassword() != null ? "Yes" : "No";
            }

            return null;
         }
      }

      if (params.contains("party")) {
         if (mPlayer == null) {
            return null;
         }

         IDungeonParty party = mPlayer.getiDungeonParty();
         if (Dungeons.inst().isThirdPartyProvider()) {
            if (party == null) {
               party = new DungeonPartyWrapper(mPlayer);
            }

            if (party.getPlayers().size() == 1) {
               party = null;
            }
         }

         if (params.equalsIgnoreCase("party_leader")) {
            if (party == null) {
               return " ";
            }

            return party.getLeader().getName();
         }

         if (params.equalsIgnoreCase("party_size")) {
            if (party == null) {
               return "0";
            }

            return String.valueOf(party.getPlayers().size());
         }

         if (params.contains("party_member_")) {
            if (elements.length < 3) {
               return " ";
            }

            String numString = elements[2];
            int index = 0;

            try {
               index = Integer.parseInt(numString) - 1;
            } catch (NumberFormatException ignored) {
            }

            if (party == null) {
               return " ";
            }

            if (index >= party.getPlayers().size()) {
               return " ";
            }

            return party.getPlayers().get(index).getDisplayName();
         }
      }

      return null;
   }
}
