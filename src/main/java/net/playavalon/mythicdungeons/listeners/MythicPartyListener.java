package net.playavalon.mythicdungeons.listeners;

import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.events.party.MythicPartyKickEvent;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class MythicPartyListener implements Listener {
   @EventHandler
   public void onPlayerKicked(MythicPartyKickEvent event) {
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(event.getKickedPlayer().getPlayer());
      if (mythicPlayer.getInstance() != null) {
         mythicPlayer.getInstance().removePlayer(mythicPlayer);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      if (aPlayer != null) {
         MythicParty party = aPlayer.getMythicParty();
         if (party != null) {
            party.setPlayerOnline(player, true);
            party.updateScoreboard();
         }
      }
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      final Player player = event.getPlayer();
      MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
      final MythicParty party = mythicPlayer.getMythicParty();
      if (party != null) {
         (new BukkitRunnable() {
            public void run() {
               if (!player.isOnline()) {
                  party.setPlayerOnline(player, false);
                  party.updateScoreboard();
               }
            }
         }).runTaskLater(MythicDungeons.inst(), 1L);
         (new BukkitRunnable() {
            public void run() {
               if (!player.isOnline()) {
                  party.partyMessage(LangUtils.getMessage("party.kick.offline-too-long", player.getName()));
                  party.removePlayer(player, false);
               }
            }
         }).runTaskLater(MythicDungeons.inst(), 6000L);
      }
   }

   @EventHandler(
      priority = EventPriority.LOW
   )
   public void onPartyChat(AsyncPlayerChatEvent event) {
      if (MythicDungeons.inst().getConfig().getBoolean("General.PartyChat")) {
         Player player = event.getPlayer();
         MythicPlayer mythicPlayer = MythicDungeons.inst().getMythicPlayer(player);
         if (mythicPlayer.isPartyChat()) {
            MythicParty party = mythicPlayer.getMythicParty();
            String message = event.getMessage();
            party.sendChatMessage(player, message);
            event.setCancelled(true);
         }
      }
   }
}
