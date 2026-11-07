package nl.hauntedmc.dungeons.runtime.instance;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/** Owns pending isolated-instance admissions for one runtime lifecycle. */
public final class PreparationRegistry {
    public interface Pending {
        DungeonDefinition dungeon();

        void cancel();
    }

    private final ConcurrentHashMap<DungeonPlayerSession, Pending> pending =
            new ConcurrentHashMap<>();
    private volatile boolean closed;

    /** Called on the server thread. A session may have only one pending admission. */
    public boolean register(DungeonPlayerSession session, Pending attempt) {
        return !closed && pending.putIfAbsent(session, attempt) == null;
    }

    public boolean contains(DungeonPlayerSession session) {
        return pending.containsKey(session);
    }

    public boolean owns(DungeonPlayerSession session, Pending attempt) {
        return pending.get(session) == attempt;
    }

    public boolean remove(DungeonPlayerSession session, Pending attempt) {
        return pending.remove(session, attempt);
    }

    /** Cancels on the server thread before a disconnect can replace player/session state. */
    public void cancel(DungeonPlayerSession session) {
        Pending attempt = pending.get(session);
        if (attempt != null) attempt.cancel();
    }

    /** Cancels starts before their dungeon definitions are replaced by a reload. */
    public void cancelDungeon(@Nullable String worldName) {
        for (Pending attempt : pending.values()) {
            if (worldName == null || attempt.dungeon().getWorldName().equals(worldName)) {
                try {
                    attempt.cancel();
                } catch (RuntimeException failure) {
                    // Keep failed ownership for retry while allowing unrelated players to settle.
                    attempt.dungeon().getEnvironment().logger()
                            .error("Failed to cancel a dungeon preparation; retaining it for retry.", failure);
                }
            }
        }
    }

    /** Rejects new starts and drains cleanup, including tasks Paper never ran. */
    public void shutdown() {
        closed = true;
        cancelDungeon(null);
    }
}
