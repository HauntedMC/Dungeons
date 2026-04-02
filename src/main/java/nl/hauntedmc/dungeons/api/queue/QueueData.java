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
   private DungeonPlayer queueLeader;
   private IDungeonParty queuedParty;
   private final AbstractDungeon dungeon;
   private final String difficulty;
   private final List<UUID> queuedPlayers;
   private BukkitRunnable readyCheckCountdownTask;
   private int countdownStatus;
   private AbortableCountDownLatch readyCheckLatch;
   private boolean readyCheckWaiting = false;
   private final List<UUID> readyPlayers;

   public QueueData(DungeonPlayer queueLeader, AbstractDungeon dungeon, String difficulty) {
      this.queueLeader = queueLeader;
      if (Dungeons.inst().isPartiesEnabled()) {
         this.queuedParty = queueLeader.getiDungeonParty();
      }

      this.dungeon = dungeon;
      this.difficulty = difficulty;
      this.queuedPlayers = new ArrayList<>();
      this.readyPlayers = new ArrayList<>();
      if (this.queuedParty != null) {
         for (Player pPlayer : this.queuedParty.getPlayers()) {
            if (pPlayer != null) {
               this.queuedPlayers.add(pPlayer.getUniqueId());
            }
         }
      } else {
         this.queuedPlayers.add(queueLeader.getPlayer().getUniqueId());
      }
   }

   public void enterDungeon(boolean immediate) {
      if (immediate) {
         this.startDungeon();
         this.unqueueLeader();
         return;
      }

      this.beginReadyCheck();
   }

   private void beginReadyCheck() {
      this.readyCheckLatch = new AbortableCountDownLatch(this.queuedPlayers.size());
      this.readyCheckWaiting = true;
      this.syncPartyRoster();
      this.notifyReadyCheckStarted();
      int countdownTime = Dungeons.inst().getConfig().getInt("General.ReadyCheckTime", 45);
      this.startReadyCheckCountdown(countdownTime);
      this.awaitReadyCheckResult(countdownTime);
   }

   private void syncPartyRoster() {
      if (this.queuedParty != null) {
         Dungeons.inst().getProviderManager().updatePartyPlayers(this.queuedParty);
      }
   }

   private void notifyReadyCheckStarted() {
      for (UUID uuid : this.queuedPlayers) {
         DungeonPlayer queuedPlayer = Dungeons.inst().getDungeonPlayer(uuid);
         if (queuedPlayer.hasParty()) {
            Player player = queuedPlayer.getPlayer();
            player.playSound(player.getLocation(), "entity.player.levelup", 1.0F, 0.7F);
            player.playSound(player.getLocation(), "block.beacon.activate", 1.0F, 0.7F);
            LangUtils.sendMessage(player, "instance.queue.dungeon-ready", this.dungeon.getWorldName());
            StringUtils.sendReadyCheckMessage(player);
         }
      }
   }

   private void startReadyCheckCountdown(int countdownTime) {
      this.countdownStatus = countdownTime + 1;
      this.readyCheckCountdownTask = new BukkitRunnable() {
         @Override
         public void run() {
            QueueData.this.countdownStatus--;
            if (QueueData.this.countdownStatus <= 0) {
               this.cancel();
               return;
            }

            for (UUID uuid : QueueData.this.queuedPlayers) {
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
      this.readyCheckCountdownTask.runTaskTimer(Dungeons.inst(), 0L, 20L);
   }

   private void awaitReadyCheckResult(int countdownTime) {
      Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> {
         boolean allPlayersReady;
         boolean interrupted = false;
         try {
            allPlayersReady = this.readyCheckLatch.await(countdownTime, TimeUnit.SECONDS);
         } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            allPlayersReady = false;
            interrupted = true;
         }

         boolean finalAllPlayersReady = allPlayersReady;
         boolean finalInterrupted = interrupted;
         Bukkit.getScheduler().runTask(Dungeons.inst(), () -> this.finishReadyCheck(finalAllPlayersReady, finalInterrupted));
      });
   }

   public void ready(DungeonPlayer readyPlayer) {
      if (this.readyCheckLatch == null) {
         return;
      }

      if (!this.readyPlayers.contains(readyPlayer.getPlayer().getUniqueId())) {
         this.readyPlayers.add(readyPlayer.getPlayer().getUniqueId());

         for (UUID uuid : this.queuedPlayers) {
            DungeonPlayer queuePlayer = Dungeons.inst().getDungeonPlayer(uuid);
            Player player = queuePlayer.getPlayer();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
            LangUtils.sendMessage(
               player,
               "commands.ready.success",
               HelperUtils.playerDisplayName(readyPlayer.getPlayer()),
               String.valueOf(this.readyPlayers.size()),
               String.valueOf(this.queuedPlayers.size())
            );
         }

         this.readyCheckLatch.countDown();
      }
   }

   public void notReady(DungeonPlayer cancellingPlayer) {
      if (this.readyCheckLatch == null) {
         return;
      }

      for (UUID uuid : this.queuedPlayers) {
         DungeonPlayer queuePlayer = Dungeons.inst().getDungeonPlayer(uuid);
         Player player = queuePlayer.getPlayer();
         player.playSound(player.getLocation(), "block.beacon.deactivate", 1.0F, 0.7F);
         LangUtils.sendMessage(player, "commands.notready.cancel", HelperUtils.playerDisplayName(cancellingPlayer.getPlayer()));
      }

      this.readyCheckLatch.abort();
   }

   public boolean isPlayerReady(DungeonPlayer queuedPlayer) {
      return this.readyPlayers.contains(queuedPlayer.getPlayer().getUniqueId());
   }

   public IDungeonParty getParty() {
      return this.queuedParty;
   }

   public AbstractDungeon getDungeon() {
      return this.dungeon;
   }

   public String getDifficulty() {
      return this.difficulty;
   }

   public List<UUID> getPlayers() {
      return this.queuedPlayers;
   }

   public boolean isReadyCheckWaiting() {
      return this.readyCheckWaiting;
   }

   private void finishReadyCheck(boolean ready, boolean interrupted) {
      this.readyCheckWaiting = false;
      this.stopReadyCheckCountdown();
      this.clearReadyCheckTitles();
      this.readyCheckLatch = null;

      if (interrupted) {
         this.failInterruptedReadyCheck();
         return;
      }

      if (!ready) {
         this.handleTimedOutReadyCheck();
         return;
      }

      for (UUID uuid : this.queuedPlayers) {
         Player player = Dungeons.inst().getDungeonPlayer(uuid).getPlayer();
         LangUtils.sendMessage(player, "instance.queue.all-ready");
      }

      this.startDungeon();
      this.unqueueLeader();
   }

   private void stopReadyCheckCountdown() {
      if (this.readyCheckCountdownTask != null) {
         this.readyCheckCountdownTask.cancel();
         this.readyCheckCountdownTask = null;
      }
   }

   private void clearReadyCheckTitles() {
      for (UUID uuid : this.queuedPlayers) {
         Player player = Bukkit.getPlayer(uuid);
         if (player != null) {
            HelperUtils.resetTitle(player);
         }
      }
   }

   private void failInterruptedReadyCheck() {
      this.unqueueLeader();
      this.setAwaitingDungeon(false);
   }

   private void handleTimedOutReadyCheck() {
      if (this.canStartWithoutUnreadyPlayers()) {
         this.removeUnreadyPartyMembers();
         this.resolveLeaderAfterPartyPrune();
         this.startDungeon();
      } else {
         for (UUID uuid : this.queuedPlayers) {
            DungeonPlayer queuedPlayer = Dungeons.inst().getDungeonPlayer(uuid);
            Player player = queuedPlayer.getPlayer();
            player.playSound(player.getLocation(), "block.beacon.deactivate", 1.0F, 0.7F);
            LangUtils.sendMessage(player, "instance.queue.not-all-ready");
         }
      }

      this.setAwaitingDungeon(false);
      this.unqueueLeader();
   }

   private boolean canStartWithoutUnreadyPlayers() {
      return Dungeons.inst().getConfig().getBoolean("General.StartWithoutUnreadyPlayers", false)
         && (!Dungeons.inst().getConfig().getBoolean("General.ReadyCheckRequireLeader", true)
            || this.readyPlayers.contains(this.queueLeader.getPlayer().getUniqueId()));
   }

   private void removeUnreadyPartyMembers() {
      if (this.queuedParty == null) {
         return;
      }

      for (UUID uuid : this.queuedPlayers) {
         if (!this.readyPlayers.contains(uuid)) {
            Player player = Dungeons.inst().getDungeonPlayer(uuid).getPlayer();
            this.queuedParty.removePlayer(player);
         }
      }
   }

   private void resolveLeaderAfterPartyPrune() {
      if (this.queuedParty != null && !this.queuedParty.hasPlayer(this.queueLeader.getPlayer())) {
         this.queueLeader = Dungeons.inst().getDungeonPlayer(this.queuedParty.getLeader().getUniqueId());
      }
   }

   private void startDungeon() {
      Dungeons.inst().getDungeonManager().createInstance(this.dungeon.getWorldName(), this.queueLeader.getPlayer(), this.difficulty);
   }

   private void setAwaitingDungeon(boolean awaitingDungeon) {
      for (UUID uuid : this.queuedPlayers) {
         Dungeons.inst().getDungeonPlayer(uuid).setAwaitingDungeon(awaitingDungeon);
      }
   }

   private void unqueueLeader() {
      Dungeons.inst().getQueueManager().unqueue(this.queueLeader);
   }
}
