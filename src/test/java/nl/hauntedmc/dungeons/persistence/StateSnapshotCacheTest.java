package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

class StateSnapshotCacheTest {
    private static final StateStore.Key KEY = new StateStore.Key("staff-state", "server", null);
    private static StateStore.Snapshot snapshot(long revision) { return new StateStore.Snapshot(revision, "revision: " + revision); }
    @Test void authoritativeRefreshAcceptsDeletedRowsButLateReadsCannotUndoACommit() {
        var cache = new StateSnapshotCache();
        cache.committed(cache.begin(KEY), 0, snapshot(1));
        var late = cache.begin(KEY);
        cache.committed(cache.begin(KEY), 1, snapshot(2));
        assertEquals(snapshot(2), cache.read(late, snapshot(1)));
        var empty = new StateStore.Snapshot(0, "");
        assertEquals(empty, cache.read(cache.begin(KEY), empty));
        assertEquals(empty, cache.get(KEY));
    }
    @Test void aNewLoginInvalidatesDeletedPlayerDocumentsAndOlderInFlightReads() {
        var cache = new StateSnapshotCache();
        UUID player = UUID.randomUUID();
        var key = new StateStore.Key("player-recovery", player.toString(), player);
        cache.committed(cache.begin(key), 0, snapshot(1));
        var late = cache.begin(key);
        var login = cache.beginPlayer(player);
        cache.player(login, Map.of());
        cache.read(late, snapshot(1));
        assertEquals(new StateStore.Snapshot(0, ""), cache.get(key));
    }
    @Test void aCommitRacingLoginPrefetchInvalidatesItsEarlierSnapshot() {
        var cache = new StateSnapshotCache();
        UUID player = UUID.randomUUID();
        var key = new StateStore.Key("player-profile", player.toString(), player);
        cache.committed(cache.begin(key), 0, snapshot(1));
        var write = cache.begin(key);
        var login = cache.beginPlayer(player);
        cache.player(login, Map.of(key, snapshot(1)));
        cache.committed(write, 1, snapshot(2));
        assertNull(cache.get(key));
        cache.read(cache.begin(key), snapshot(2));
        assertEquals(snapshot(2), cache.get(key));
    }
    @Test void completedWritesCannotResurrectAResetGenerationOrUndoLaterWrites() {
        var cache = new StateSnapshotCache();
        cache.committed(cache.begin(KEY), 0, snapshot(1));
        var delayed = cache.begin(KEY);
        cache.committed(cache.begin(KEY), 1, snapshot(2));
        cache.committed(delayed, 0, snapshot(1));
        assertEquals(snapshot(2), cache.get(KEY));
        delayed = cache.begin(KEY);
        cache.read(cache.begin(KEY), new StateStore.Snapshot(0, ""));
        cache.committed(delayed, 2, snapshot(3));
        assertNull(cache.get(KEY));
    }
    @Test void evictedCellsCannotBeReintroducedByOldReadCallbacks() {
        var cache = new StateSnapshotCache();
        var delayed = cache.begin(KEY);
        for (int i = 0; i < 2050; i++) cache.begin(new StateStore.Key("test", "key-" + i, null));
        cache.read(delayed, snapshot(1));
        assertNull(cache.get(KEY));
    }
}
