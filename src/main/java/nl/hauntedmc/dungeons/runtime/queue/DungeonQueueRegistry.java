package nl.hauntedmc.dungeons.runtime.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import nl.hauntedmc.dungeons.event.DungeonDisposeEvent;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory queue registry for pending dungeon starts.
 *
 * <p>The registry owns queue membership and queue ordering, while the coordinator decides whether a
 * queue can actually be started or should be discarded.</p>
 */
public final class DungeonQueueRegistry implements Listener {
    private final PlayerSessionRegistry playerManager;
    private final ActiveInstanceRegistry activeInstanceManager;
    private final List<DungeonQueueEntry> queueEntries =
            Collections.synchronizedList(new ArrayList<>());
    private final Map<UUID, DungeonQueueEntry> queueEntriesByPlayer = new ConcurrentHashMap<>();
    private DungeonQueueCoordinator queueCoordinator;

    /**
     * Creates the queue registry against the shared player and instance services.
     */
    public DungeonQueueRegistry(
            PlayerSessionRegistry playerManager, ActiveInstanceRegistry activeInstanceManager) {
        this.playerManager = playerManager;
        this.activeInstanceManager = activeInstanceManager;
    }

    /**
     * Binds the higher-level coordinator used for queue invalidation and launch attempts.
     */
    public void setQueueCoordinator(DungeonQueueCoordinator queueCoordinator) {
        this.queueCoordinator = queueCoordinator;
    }

    /**
     * Inserts or updates a queue entry and evicts any older queues that still claim the same players.
     */
    public void enqueue(DungeonQueueEntry queue) {
        List<DungeonQueueEntry> overlappingQueues = new ArrayList<>();
        for (UUID playerId : queue.getPlayers()) {
            DungeonQueueEntry existing = this.queueEntriesByPlayer.get(playerId);
            if (existing != null && existing != queue && !overlappingQueues.contains(existing)) {
                overlappingQueues.add(existing);
            }
        }

        // Players may already be present in another queue because they re-queued or switched from a
        // solo queue to a team queue. Remove those stale entries before publishing the replacement.
        for (DungeonQueueEntry overlappingQueue : overlappingQueues) {
            if (this.queueCoordinator != null) {
                this.queueCoordinator.discardQueue(overlappingQueue);
            } else {
                this.unqueue(overlappingQueue);
            }
        }

        this.queueEntries.remove(queue);
        for (UUID playerId : queue.getPlayers()) {
            DungeonQueueEntry existing = this.queueEntriesByPlayer.put(playerId, queue);
            if (existing != null && existing != queue) {
                this.queueEntries.remove(existing);
            }
        }

        this.queueEntries.add(queue);
    }

    /**
     * Removes the queue entry associated with the provided player, if any.
     */
    public void unqueue(DungeonPlayerSession player) {
        if (player == null || player.getPlayer() == null) {
            return;
        }

        UUID playerId = player.getPlayer().getUniqueId();
        DungeonQueueEntry queue = this.queueEntriesByPlayer.get(playerId);
        if (queue != null) {
            this.unqueue(queue);
        }
    }

    /**
     * Removes a queue and resets waiting state for affected players.
     */
    public void unqueue(DungeonQueueEntry queue) {
        this.unqueue(queue, true);
    }

    /**
     * Removes a queue and optionally resets the per-player awaiting-dungeon state.
     */
    public void unqueue(DungeonQueueEntry queue, boolean resetAwaitingDungeon) {
        if (queue == null) {
            return;
        }

        this.queueEntries.remove(queue);
        for (UUID playerId : new ArrayList<>(queue.getPlayers())) {
            this.queueEntriesByPlayer.remove(playerId);
            DungeonPlayerSession dungeonPlayer = this.playerManager.get(playerId);
            if (resetAwaitingDungeon && dungeonPlayer != null && dungeonPlayer.getInstance() == null) {
                dungeonPlayer.refundReservedAccessKey(queue.getDungeon().getWorldName());
            }
            if (resetAwaitingDungeon && dungeonPlayer != null && dungeonPlayer.getInstance() == null) {
                dungeonPlayer.setAwaitingDungeon(false);
            }
        }
    }

    /**
     * Removes a single player from a queue, collapsing the whole queue if the leader leaves.
     */
    public void removePlayer(UUID playerId) {
        DungeonQueueEntry queue = this.queueEntriesByPlayer.get(playerId);
        if (queue == null) {
            return;
        }

        if (queue.isLeader(playerId) || queue.getPlayers().size() <= 1) {
            this.unqueue(queue);
            return;
        }

        queue.removePlayer(playerId);
        this.queueEntriesByPlayer.remove(playerId);
        DungeonPlayerSession dungeonPlayer = this.playerManager.get(playerId);
        if (dungeonPlayer != null && dungeonPlayer.getInstance() == null) {
            dungeonPlayer.refundReservedAccessKey(queue.getDungeon().getWorldName());
            dungeonPlayer.setAwaitingDungeon(false);
        }
    }

    /**
     * Returns the queue currently owned by the supplied player session.
     */
    public DungeonQueueEntry getQueue(DungeonPlayerSession player) {
        if (player == null || player.getPlayer() == null) {
            return null;
        }

        return this.queueEntriesByPlayer.get(player.getPlayer().getUniqueId());
    }

    /**
     * Returns a stable snapshot of the current queue order.
     */
    public List<DungeonQueueEntry> snapshotQueues() {
        synchronized (this.queueEntries) {
            return new ArrayList<>(this.queueEntries);
        }
    }

    /**
     * Returns the first queue that is still valid and can start immediately.
     */
    @Nullable
    public DungeonQueueEntry getNextInLine() {
        return this.getNextInLine(0);
    }

    /**
     * Returns the next queue at or after the supplied index that can still be started.
     */
    @Nullable public DungeonQueueEntry getNextInLine(int index) {
        synchronized (this.queueEntries) {
            for (int i = index; i < this.queueEntries.size(); i++) {
                DungeonQueueEntry queue = this.queueEntries.get(i);
                DungeonPlayerSession queuedPlayer = queue.getQueuedPlayer();
                boolean invalidQueue = false;
                for (UUID playerId : new ArrayList<>(queue.getPlayers())) {
                    DungeonPlayerSession member = this.playerManager.get(playerId);
                    if (member == null
                            || member.getPlayer() == null
                            || !member.getPlayer().isOnline()
                            || member.getInstance() != null) {
                        invalidQueue = true;
                        break;
                    }
                }

                if (invalidQueue
                        || queuedPlayer.getPlayer() == null
                        || !queuedPlayer.getPlayer().isOnline()
                        || queuedPlayer.getInstance() != null) {
                    if (this.queueCoordinator != null) {
                        this.queueCoordinator.discardQueue(queue);
                    } else {
                        this.unqueue(queue);
                    }
                    i--;
                    continue;
                }

                if (this.activeInstanceManager.canStartDungeonNow(
                        queue.getDungeon(), queue.getPlayers().size(), queue.getDifficulty())) {
                    return queue;
                }
            }
        }

        return null;
    }

    /**
     * Attempts to start the next queued dungeon when a running dungeon disposes.
     */
    @EventHandler
    public void onDungeonEnd(DungeonDisposeEvent event) {
        DungeonQueueEntry nextInLine = this.getNextInLine();
        if (nextInLine != null && this.queueCoordinator != null) {
            this.queueCoordinator.tryStartQueuedDungeon(nextInLine);
        }
    }

    /**
     * Clears ad-hoc queues when a solo player disconnects.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        DungeonPlayerSession player = this.playerManager.get(event.getPlayer());
        if (player == null) {
            return;
        }

        DungeonQueueEntry queue = this.getQueue(player);
        if (queue != null && !queue.isTeamQueue()) {
            this.unqueue(queue);
        }
    }
}
