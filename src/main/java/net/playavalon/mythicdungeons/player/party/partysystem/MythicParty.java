package net.playavalon.mythicdungeons.player.party.partysystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.events.party.AsyncMythicPartyChatEvent;
import net.playavalon.mythicdungeons.api.events.party.MythicPartyCreateEvent;
import net.playavalon.mythicdungeons.api.events.party.MythicPartyJoinEvent;
import net.playavalon.mythicdungeons.api.events.party.MythicPartyKickEvent;
import net.playavalon.mythicdungeons.api.events.party.MythicPartyLeaveEvent;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partyfinder.RecruitmentListing;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;

public class MythicParty implements IDungeonParty {
   private MythicPlayer mythicLeader;
   private final List<MythicPlayer> mythicPlayers = new ArrayList<>();
   private final List<MythicPlayer> onlinePlayers = new ArrayList<>();
   private final HashMap<String, MythicPlayer> playersByName = new HashMap<>();
   private Scoreboard partyScoreboard;
   private Objective partyMemberDisplay;

   public MythicParty(Player leader) {
      this.setMythicLeader(leader);
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(leader);
      this.mythicPlayers.add(mythicPlayer);
      this.onlinePlayers.add(mythicPlayer);
      this.playersByName.put(leader.getName(), mythicPlayer);
      mythicPlayer.setMythicParty(this);
      MythicPartyCreateEvent event = new MythicPartyCreateEvent(this, mythicPlayer);
      Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> Bukkit.getPluginManager().callEvent(event));
      this.initDungeonParty(new String[]{"DungeonParties", "Default"});
   }

   public void updateScoreboard() {
      if (Bukkit.getPluginManager().getPlugin("AvNCombat") == null) {
         if (this.partyScoreboard != null) {
            for (MythicPlayer mythicPlayer : this.mythicPlayers) {
               Player player = mythicPlayer.getPlayer();
               player.setScoreboard(this.partyScoreboard);
               String scoreboardName;
               if (!player.isOnline()) {
                  this.partyScoreboard.resetScores(Util.colorize("&b" + player.getName()));
                  scoreboardName = Util.colorize("&7" + player.getName());
               } else {
                  this.partyScoreboard.resetScores(Util.colorize("&7" + player.getName()));
                  scoreboardName = Util.colorize("&b" + player.getName());
               }

               Score score = this.partyMemberDisplay.getScore(scoreboardName);
               score.setScore(0);
            }
         }
      }
   }

   public void sendChatMessage(Player player, String message) {
      AsyncMythicPartyChatEvent event = new AsyncMythicPartyChatEvent(this, message);
      Bukkit.getScheduler()
         .runTaskAsynchronously(
            MythicDungeons.inst(),
            () -> {
               Bukkit.getPluginManager().callEvent(event);
               if (!event.isCancelled()) {
                  String displayName = player.getDisplayName();

                  for (MythicPlayer mythicPlayer : this.onlinePlayers) {
                     mythicPlayer.getPlayer().sendMessage(LangUtils.getMessage("party.chat.format", false, displayName, event.getMessage()));
                  }

                  System.out.println(LangUtils.getMessage("party.chat.format", displayName, event.getMessage()));

                  for (MythicPlayer mythicPlayer : MythicDungeons.inst().getPartyManager().getSpies()) {
                     mythicPlayer.getPlayer()
                        .sendMessage(LangUtils.getMessage("party.chat.spy-format", this.mythicLeader.getPlayer().getName(), displayName, event.getMessage()));
                  }
               }
            }
         );
   }

   @Override
   public void partyMessage(String message) {
      for (MythicPlayer player : this.onlinePlayers) {
         if (player != null) {
            player.getPlayer().sendMessage(message);
         }
      }
   }

   public void leaderMessage(String message) {
      this.mythicLeader.getPlayer().sendMessage(message);
   }

   @Override
   public void addPlayer(Player player) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      MythicPartyJoinEvent event = new MythicPartyJoinEvent(this, mythicPlayer);
      Bukkit.getPluginManager().callEvent(event);
      if (!event.isCancelled()) {
         this.mythicPlayers.add(mythicPlayer);
         this.onlinePlayers.add(mythicPlayer);
         this.playersByName.put(player.getName(), mythicPlayer);
         mythicPlayer.setMythicParty(this);
         this.updateScoreboard();
         this.partyMessage(LangUtils.getMessage("party.join", player.getName()));

         for (Player target : this.getPlayers()) {
            target.playSound(target, "entity.experience_orb.pickup", 1.0F, 1.5F);
         }
      }
   }

   @Override
   public void removePlayer(Player player) {
      this.removePlayer(player, false);
   }

   public void removePlayer(Player player, boolean withAlert) {
      this.removePlayer(player, withAlert, true);
   }

   public void removePlayer(Player player, boolean withAlert, boolean kickFromDungeon) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      MythicPartyLeaveEvent event = new MythicPartyLeaveEvent(this, mythicPlayer);
      Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> Bukkit.getPluginManager().callEvent(event));
      this.mythicPlayers.remove(mythicPlayer);
      this.onlinePlayers.remove(mythicPlayer);
      this.playersByName.remove(player.getName());
      mythicPlayer.setDungeonParty(null);
      mythicPlayer.setMythicParty(null);
      mythicPlayer.setPartyChat(false);
      if (this.partyScoreboard != null) {
         player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
         this.partyScoreboard.resetScores(Util.colorize("&7" + player.getName()));
         this.partyScoreboard.resetScores(Util.colorize("&b" + player.getName()));
      }

      this.updateScoreboard();
      if (mythicPlayer.getInstance() != null && kickFromDungeon) {
         mythicPlayer.getInstance().removePlayer(mythicPlayer);
      }

      if (this.mythicPlayers.size() < 1 && this.mythicLeader != null) {
         this.disband();
      } else {
         if (withAlert) {
            this.partyMessage(LangUtils.getMessage("party.leave", player.getName()));
         }

         if (this.mythicLeader != null) {
            RecruitmentListing listing = MythicDungeons.inst().getListingManager().getListing(this.mythicLeader.getPlayer());
            if (listing != null) {
               listing.removePlayer(mythicPlayer);
            }

            if (mythicPlayer == this.mythicLeader) {
               if (listing != null) {
                  listing.removeListing();
                  this.partyMessage(LangUtils.getMessage("party.recruit.cancel-leader-left"));
               }

               this.defactoLeader();
            }
         }
      }
   }

   public void setPlayerOnline(Player player, boolean online) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (this.mythicPlayers.contains(mythicPlayer)) {
         if (online) {
            this.onlinePlayers.add(mythicPlayer);
            this.partyMessage(LangUtils.getMessage("party.login", player.getName()));
         } else {
            this.onlinePlayers.remove(mythicPlayer);
            this.partyMessage(LangUtils.getMessage("party.logout", player.getName()));
            if (this.onlinePlayers.isEmpty()) {
               this.disband();
               return;
            }

            if (mythicPlayer == this.mythicLeader) {
               this.defactoLeader();
            }
         }
      }
   }

   public boolean isPlayerOnline(Player player) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      return !this.mythicPlayers.contains(mythicPlayer) ? false : this.onlinePlayers.contains(mythicPlayer);
   }

   public void kickPlayer(Player player) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      MythicPartyKickEvent event = new MythicPartyKickEvent(this, mythicPlayer, this.mythicLeader);
      Bukkit.getPluginManager().callEvent(event);
      if (!event.isCancelled()) {
         LangUtils.sendMessage(mythicPlayer.getPlayer(), "party.kick.kicked-player-alert", this.mythicLeader.getPlayer().getName());
         this.removePlayer(player, false);
         this.partyMessage(LangUtils.getMessage("party.kick.party-kicked-player-alert", player.getName()));
      }
   }

   public void kickPlayer(String name) {
      MythicPlayer mythicPlayer = this.playersByName.get(name);
      Player player = mythicPlayer.getPlayer();
      MythicPartyKickEvent event = new MythicPartyKickEvent(this, mythicPlayer, this.mythicLeader);
      Bukkit.getPluginManager().callEvent(event);
      if (!event.isCancelled()) {
         if (player.isOnline()) {
            LangUtils.sendMessage(player, "party.kick.kicked-player-alert", this.mythicLeader.getPlayer().getName());
         }

         this.removePlayer(player, false);
         this.partyMessage(LangUtils.getMessage("party.kick.party-kicked-player-alert", player.getName()));
      }
   }

   public void disband() {
      if (this.mythicPlayers.size() > 1) {
         this.partyMessage(LangUtils.getMessage("party.disband"));
      }

      RecruitmentListing listing = MythicDungeons.inst().getListingManager().getListing(this.mythicLeader.getPlayer());
      if (listing != null) {
         listing.removeListing();
      }

      this.mythicLeader = null;

      for (MythicPlayer mythicPlayer : new ArrayList<>(this.mythicPlayers)) {
         this.removePlayer(mythicPlayer.getPlayer(), false, false);
      }
   }

   public void setMythicLeader(Player mythicLeader) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(mythicLeader);
      if (mythicPlayer != null) {
         this.mythicLeader = mythicPlayer;
         this.partyMessage(LangUtils.getMessage("party.new-leader.success", mythicLeader.getName()));
      }
   }

   public void changeLeader(Player leader) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(leader);
      if (!this.mythicPlayers.contains(mythicPlayer)) {
         this.leaderMessage(LangUtils.getMessage("party.new-leader.failure", leader.getName()));
      } else {
         this.setMythicLeader(leader);
      }
   }

   private void defactoLeader() {
      if (this.onlinePlayers.isEmpty()) {
         this.disband();
      } else {
         Player newLeader = this.onlinePlayers.get(0).getPlayer();
         this.setMythicLeader(newLeader);
      }
   }

   public MythicPlayer getPlayer(String name) {
      return this.playersByName.get(name);
   }

   public void sendPartyInfo(Player player) {
      LangUtils.sendMessage(player, "party.info", this.mythicLeader.getPlayer().getName());

      for (MythicPlayer mythicPlayer : this.mythicPlayers) {
         player.sendMessage(Util.colorize("&9" + mythicPlayer.getPlayer().getName()));
      }
   }

   @Override
   public List<Player> getPlayers() {
      List<Player> players = new ArrayList<>();

      for (MythicPlayer mPlayer : this.mythicPlayers) {
         if (mPlayer.getPlayer() != null) {
            players.add(mPlayer.getPlayer());
         }
      }

      return players;
   }

   @NotNull
   public Player getLeader() {
      return this.mythicLeader.getPlayer();
   }

   public MythicPlayer getMythicLeader() {
      return this.mythicLeader;
   }

   public List<MythicPlayer> getMythicPlayers() {
      return this.mythicPlayers;
   }
}
