package nl.hauntedmc.dungeons.managers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.events.DungeonDisposeEvent;
import nl.hauntedmc.dungeons.api.queue.QueueData;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

public final class QueueManager implements Listener {
   private final List<QueueData> queueEntries = Collections.synchronizedList(new ArrayList<>());
   private final Map<UUID, QueueData> queueEntriesByPlayer = new HashMap<>();

   public QueueManager() {
      Bukkit.getPluginManager().registerEvents(this, Dungeons.inst());
   }

   public void enqueue(QueueData queue) {
      this.queueEntries.add(queue);

      for (UUID uuid : queue.getPlayers()) {
         this.queueEntriesByPlayer.put(uuid, queue);
      }
   }

   public void unqueue(DungeonPlayer aPlayer) {
      QueueData queue = this.queueEntriesByPlayer.get(aPlayer.getPlayer().getUniqueId());
      if (queue != null) {
         this.queueEntries.remove(queue);

         for (UUID uuid : queue.getPlayers()) {
            this.queueEntriesByPlayer.remove(uuid);
         }
      }
   }

   public QueueData getQueue(DungeonPlayer aPlayer) {
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
         nextInLine.enterDungeon(!Dungeons.inst().getConfig().getBoolean("General.ReadyCheckInQueue", true));
      }
   }
}
