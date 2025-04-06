package net.playavalon.mythicdungeons.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.events.dungeon.DungeonDisposeEvent;
import net.playavalon.mythicdungeons.api.queue.QueueData;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonClassic;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

public final class QueueManager implements Listener {
   private final List<QueueData> queueEntries = Collections.synchronizedList(new ArrayList<>());
   private final Map<UUID, QueueData> queueEntriesByPlayer = new HashMap<>();

   public QueueManager() {
      Bukkit.getPluginManager().registerEvents(this, MythicDungeons.inst());
   }

   public void enqueue(MythicPlayer aPlayer, DungeonClassic dungeon) {
      QueueData queue = new QueueData(aPlayer, dungeon);
      this.queueEntries.add(queue);
      this.queueEntriesByPlayer.put(aPlayer.getPlayer().getUniqueId(), queue);
   }

   public void enqueue(QueueData queue) {
      this.queueEntries.add(queue);

      for (UUID uuid : queue.getPlayers()) {
         this.queueEntriesByPlayer.put(uuid, queue);
      }
   }

   public void unqueue(MythicPlayer aPlayer) {
      QueueData queue = this.queueEntriesByPlayer.get(aPlayer.getPlayer().getUniqueId());
      if (queue != null) {
         this.queueEntries.remove(queue);

         for (UUID uuid : queue.getPlayers()) {
            this.queueEntriesByPlayer.remove(uuid);
         }
      }
   }

   public QueueData getQueue(MythicPlayer aPlayer) {
      return this.queueEntriesByPlayer.get(aPlayer.getPlayer().getUniqueId());
   }

   @Nullable
   public QueueData getNextInLine() {
      return this.getNextInLine(0);
   }

   @Nullable
   public QueueData getNextInLine(int index) {
      if (!this.queueEntries.isEmpty() && this.queueEntries.size() > index) {
         QueueData queue = this.queueEntries.get(index);
         return !queue.getDungeon().hasAvailableInstances() ? this.getNextInLine(index + 1) : queue;
      } else {
         return null;
      }
   }

   @EventHandler
   public void onDungeonEnd(DungeonDisposeEvent event) {
      QueueData nextInLine = this.getNextInLine();
      if (nextInLine != null) {
         nextInLine.enterDungeon(!MythicDungeons.inst().getConfig().getBoolean("General.ReadyCheckInQueue", true));
      }
   }
}
