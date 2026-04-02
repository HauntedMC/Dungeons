package nl.hauntedmc.dungeons.api.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import nl.hauntedmc.dungeons.util.tasks.AbortableCountDownLatch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class QueueData {
   private DungeonPlayer aPlayer;
   private IDungeonParty party;
   private final AbstractDungeon dungeon;
   private final String difficulty;
   private final List<UUID> players;
   private BukkitRunnable countdownTimer;
   private int countdownStatus;
   private AbortableCountDownLatch latch;
   private boolean readyCheckWaiting = false;
   private final List<UUID> readyPlayers;

   public QueueData(DungeonPlayer aPlayer, AbstractDungeon dungeon, String difficulty) {
      this.aPlayer = aPlayer;
      if (Dungeons.inst().isPartiesEnabled()) {
         this.party = aPlayer.getiDungeonParty();
      }

      this.dungeon = dungeon;
      this.difficulty = difficulty;
      this.players = new ArrayList<>();
      this.readyPlayers = new ArrayList<>();
      if (this.party != null) {
         for (Player pPlayer : this.party.getPlayers()) {
            if (pPlayer != null) {
               this.players.add(pPlayer.getUniqueId());
            }
         }
      } else {
         this.players.add(aPlayer.getPlayer().getUniqueId());
      }
   }

   public void enterDungeon(boolean immediate) {
      if (immediate) {
         Dungeons.inst().getDungeonManager().createInstance(this.dungeon.getWorldName(), this.aPlayer.getPlayer(), this.difficulty);
         Dungeons.inst().getQueueManager().unqueue(this.aPlayer);
      } else {
         this.latch = new AbortableCountDownLatch(this.players.size());
         this.readyCheckWaiting = true;
         if (this.party != null) {
            Dungeons.inst().getProviderManager().updatePartyPlayers(this.party);
         }

         for (UUID uuid : this.players) {
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(uuid);
            if (aPlayer.hasParty()) {
               Player player = aPlayer.getPlayer();
               player.playSound(player.getLocation(), "entity.player.levelup", 1.0F, 0.7F);
               player.playSound(player.getLocation(), "block.beacon.activate", 1.0F, 0.7F);
               LangUtils.sendMessage(player, "instance.queue.dungeon-ready", this.dungeon.getWorldName());
               StringUtils.sendReadyCheckMessage(player);
            }
         }

         int countdownTime = Dungeons.inst().getConfig().getInt("General.ReadyCheckTime", 45);
         this.countdownStatus = countdownTime + 1;
         this.countdownTimer = new BukkitRunnable() {
            public void run() {
               QueueData.this.countdownStatus--;
               if (QueueData.this.countdownStatus <= 0) {
                  this.cancel();
                  return;
               }

               for (UUID uuid : QueueData.this.players) {
                  Player player = Bukkit.getPlayer(uuid);
                  if (player != null) {
                     HelperUtils.showTitle(
                        player,
                        LangUtils.getMessage("instance.queue.dungeon-ready-title", false),
                        "&e" + QueueData.this.countdownStatus,
                        0,
                        25,
                        0
                     );
                  }
               }
            }
         };
         this.countdownTimer.runTaskTimer(Dungeons.inst(), 0L, 20L);

         Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> {
            boolean ready;
            boolean interrupted = false;
            try {
               ready = this.latch.await(countdownTime, TimeUnit.SECONDS);
            } catch (InterruptedException var4) {
               Thread.currentThread().interrupt();
               ready = false;
               interrupted = true;
            }

            boolean finalReady = ready;
            boolean finalInterrupted = interrupted;
            Bukkit.getScheduler().runTask(Dungeons.inst(), () -> this.finishReadyCheck(finalReady, finalInterrupted));
         });
      }
   }

   public void ready(DungeonPlayer aPlayer) {
      if (!this.readyPlayers.contains(aPlayer.getPlayer().getUniqueId())) {
         this.readyPlayers.add(aPlayer.getPlayer().getUniqueId());

         for (UUID uuid : this.players) {
            DungeonPlayer queuePlayer = Dungeons.inst().getDungeonPlayer(uuid);
            Player player = queuePlayer.getPlayer();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
            LangUtils.sendMessage(
               player,
               "commands.ready.success",
               HelperUtils.playerDisplayName(aPlayer.getPlayer()),
               String.valueOf(this.readyPlayers.size()),
               String.valueOf(this.players.size())
            );
         }

         this.latch.countDown();
      }
   }

   public void notReady(DungeonPlayer aPlayer) {
      for (UUID uuid : this.players) {
         DungeonPlayer queuePlayer = Dungeons.inst().getDungeonPlayer(uuid);
         Player player = queuePlayer.getPlayer();
         player.playSound(player.getLocation(), "block.beacon.deactivate", 1.0F, 0.7F);
         LangUtils.sendMessage(player, "commands.notready.cancel", HelperUtils.playerDisplayName(aPlayer.getPlayer()));
      }

      this.latch.abort();
   }

   public boolean isPlayerReady(DungeonPlayer aPlayer) {
      return this.readyPlayers.contains(aPlayer.getPlayer().getUniqueId());
   }

   public IDungeonParty getParty() {
      return this.party;
   }

   public AbstractDungeon getDungeon() {
      return this.dungeon;
   }

   public String getDifficulty() {
      return this.difficulty;
   }

   public List<UUID> getPlayers() {
      return this.players;
   }

   public boolean isReadyCheckWaiting() {
      return this.readyCheckWaiting;
   }

   private void finishReadyCheck(boolean ready, boolean interrupted) {
      this.readyCheckWaiting = false;
      if (this.countdownTimer != null) {
         this.countdownTimer.cancel();
      }

      if (interrupted) {
         Dungeons.inst().getQueueManager().unqueue(this.aPlayer);

         for (UUID uuid : this.players) {
            DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(uuid);
            Player player = aPlayer.getPlayer();
            HelperUtils.resetTitle(player);
            aPlayer.setAwaitingDungeon(false);
         }

         return;
      }

      if (!ready) {
         boolean canStartWithoutUnreadyPlayers = Dungeons.inst().getConfig().getBoolean("General.StartWithoutUnreadyPlayers", false)
            && (!Dungeons.inst().getConfig().getBoolean("General.ReadyCheckRequireLeader", true)
               || this.readyPlayers.contains(this.aPlayer.getPlayer().getUniqueId()));
         if (canStartWithoutUnreadyPlayers) {
            for (UUID uuid : this.players) {
               DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(uuid);
               Player player = aPlayer.getPlayer();
               aPlayer.setAwaitingDungeon(false);
               if (this.party != null && !this.readyPlayers.contains(uuid)) {
                  this.party.removePlayer(player);
               }
            }

            if (this.party != null && !this.party.hasPlayer(this.aPlayer.getPlayer())) {
               this.aPlayer = Dungeons.inst().getDungeonPlayer(this.party.getLeader().getUniqueId());
            }

            Dungeons.inst().getDungeonManager().createInstance(this.dungeon.getWorldName(), this.aPlayer.getPlayer(), this.difficulty);
         } else {
            for (UUID uuid : this.players) {
               DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(uuid);
               Player player = aPlayer.getPlayer();
               player.playSound(player.getLocation(), "block.beacon.deactivate", 1.0F, 0.7F);
               LangUtils.sendMessage(player, "instance.queue.not-all-ready");
               aPlayer.setAwaitingDungeon(false);
            }
         }

         Dungeons.inst().getQueueManager().unqueue(this.aPlayer);
         return;
      }

      for (UUID uuid : this.players) {
         DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(uuid);
         Player player = aPlayer.getPlayer();
         LangUtils.sendMessage(player, "instance.queue.all-ready");
         HelperUtils.resetTitle(player);
      }

      Dungeons.inst().getDungeonManager().createInstance(this.dungeon.getWorldName(), this.aPlayer.getPlayer(), this.difficulty);
      Dungeons.inst().getQueueManager().unqueue(this.aPlayer);
   }
}
