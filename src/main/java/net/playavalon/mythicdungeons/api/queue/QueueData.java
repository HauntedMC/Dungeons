package net.playavalon.mythicdungeons.api.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import net.playavalon.mythicdungeons.utility.tasks.AbortableCountDownLatch;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class QueueData {
   private MythicPlayer aPlayer;
   private IDungeonParty party;
   private final AbstractDungeon dungeon;
   private final String difficulty;
   private final List<UUID> players;
   private BukkitRunnable countdownTimer;
   private int countdownStatus;
   private AbortableCountDownLatch latch;
   private boolean readyCheckWaiting = false;
   private final List<UUID> readyPlayers;

   public QueueData(MythicPlayer aPlayer, AbstractDungeon dungeon) {
      this(aPlayer, dungeon, "DEFAULT");
   }

   public QueueData(MythicPlayer aPlayer, AbstractDungeon dungeon, String difficulty) {
      this.aPlayer = aPlayer;
      if (MythicDungeons.inst().isPartiesEnabled()) {
         this.party = aPlayer.getDungeonParty();
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
         MythicDungeons.inst().getDungeonManager().createInstance(this.dungeon.getWorldName(), this.aPlayer.getPlayer(), this.difficulty);
         MythicDungeons.inst().getQueueManager().unqueue(this.aPlayer);
      } else {
         Bukkit.getScheduler()
            .runTaskAsynchronously(
               MythicDungeons.inst(),
               () -> {
                  this.latch = new AbortableCountDownLatch(this.players.size());
                  this.readyCheckWaiting = true;
                  MythicDungeons.inst().getProviderManager().updatePartyPlayers(this.party);

                  for (UUID uuid : this.players) {
                     MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(uuid);
                     if (aPlayer.hasParty()) {
                        Player player = aPlayer.getPlayer();
                        player.playSound(player.getLocation(), "entity.player.levelup", 1.0F, 0.7F);
                        player.playSound(player.getLocation(), "block.beacon.activate", 1.0F, 0.7F);
                        LangUtils.sendMessage(player, "instance.queue.dungeon-ready", this.dungeon.getWorldName());
                        StringUtils.sendReadyCheckMessage(player);
                     }
                  }

                  int countdownTime = MythicDungeons.inst().getConfig().getInt("General.ReadyCheckTime", 45);
                  this.countdownStatus = countdownTime + 1;
                  this.countdownTimer = new BukkitRunnable() {
                     public void run() {
                        QueueData.this.countdownStatus--;
                        if (QueueData.this.countdownStatus <= 0) {
                           this.cancel();
                        } else {
                           for (UUID uuidx : QueueData.this.players) {
                              Player player = Bukkit.getPlayer(uuidx);
                              if (player != null) {
                                 player.sendTitle(
                                    LangUtils.getMessage("instance.queue.dungeon-ready-title", false),
                                    Util.colorize("&e" + QueueData.this.countdownStatus),
                                    0,
                                    25,
                                    0
                                 );
                              }
                           }
                        }
                     }
                  };
                  this.countdownTimer.runTaskTimerAsynchronously(MythicDungeons.inst(), 0L, 20L);

                  try {
                     boolean ready = this.latch.await(countdownTime, TimeUnit.SECONDS);
                     if (!ready) {
                        this.readyCheckWaiting = false;
                        if (MythicDungeons.inst().getConfig().getBoolean("General.StartWithoutUnreadyPlayers", false)
                           && (
                              !MythicDungeons.inst().getConfig().getBoolean("General.ReadyCheckRequireLeader", true)
                                 || this.readyPlayers.contains(this.aPlayer.getPlayer().getUniqueId())
                           )) {
                           for (UUID uuidx : this.players) {
                              MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(uuidx);
                              Player player = aPlayer.getPlayer();
                              aPlayer.setAwaitingDungeon(false);
                              if (!this.readyPlayers.contains(uuidx)) {
                                 this.party.removePlayer(player);
                              }
                           }

                           if (!this.party.hasPlayer(this.aPlayer.getPlayer())) {
                              this.aPlayer = MythicDungeons.inst().getMythicPlayer(this.party.getLeader().getUniqueId());
                           }

                           MythicDungeons.inst().getDungeonManager().createInstance(this.dungeon.getWorldName(), this.aPlayer.getPlayer(), this.difficulty);
                        } else {
                           for (UUID uuidxx : this.players) {
                              MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(uuidxx);
                              Player player = aPlayer.getPlayer();
                              player.playSound(player.getLocation(), "block.beacon.deactivate", 1.0F, 0.7F);
                              LangUtils.sendMessage(player, "instance.queue.not-all-ready");
                              aPlayer.setAwaitingDungeon(false);
                           }
                        }

                        MythicDungeons.inst().getQueueManager().unqueue(this.aPlayer);
                        return;
                     }

                     this.countdownTimer.cancel();

                     for (UUID uuidxx : this.players) {
                        MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(uuidxx);
                        Player player = aPlayer.getPlayer();
                        LangUtils.sendMessage(player, "instance.queue.all-ready");
                        player.sendTitle(" ", " ", 0, 0, 0);
                     }

                     MythicDungeons.inst().getDungeonManager().createInstance(this.dungeon.getWorldName(), this.aPlayer.getPlayer(), this.difficulty);
                     MythicDungeons.inst().getQueueManager().unqueue(this.aPlayer);
                  } catch (InterruptedException var7) {
                     this.countdownTimer.cancel();
                     MythicDungeons.inst().getQueueManager().unqueue(this.aPlayer);

                     for (UUID uuidxx : this.players) {
                        MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(uuidxx);
                        Player player = aPlayer.getPlayer();
                        player.sendTitle(" ", " ", 0, 0, 0);
                        aPlayer.setAwaitingDungeon(false);
                     }
                  }
               }
            );
      }
   }

   public void ready(MythicPlayer aPlayer) {
      if (!this.readyPlayers.contains(aPlayer.getPlayer().getUniqueId())) {
         this.readyPlayers.add(aPlayer.getPlayer().getUniqueId());

         for (UUID uuid : this.players) {
            MythicPlayer queuePlayer = MythicDungeons.inst().getMythicPlayer(uuid);
            Player player = queuePlayer.getPlayer();
            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.0F);
            LangUtils.sendMessage(
               player,
               "commands.ready.success",
               aPlayer.getPlayer().getDisplayName(),
               String.valueOf(this.readyPlayers.size()),
               String.valueOf(this.players.size())
            );
         }

         this.latch.countDown();
      }
   }

   public void notReady(MythicPlayer aPlayer) {
      for (UUID uuid : this.players) {
         MythicPlayer queuePlayer = MythicDungeons.inst().getMythicPlayer(uuid);
         Player player = queuePlayer.getPlayer();
         player.playSound(player.getLocation(), "block.beacon.deactivate", 1.0F, 0.7F);
         LangUtils.sendMessage(player, "commands.notready.cancel", aPlayer.getPlayer().getDisplayName());
      }

      this.latch.abort();
   }

   public boolean isPlayerReady(MythicPlayer aPlayer) {
      return this.readyPlayers.contains(aPlayer);
   }

   public MythicPlayer getAPlayer() {
      return this.aPlayer;
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
}
