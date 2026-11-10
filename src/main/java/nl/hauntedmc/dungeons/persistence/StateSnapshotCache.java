package nl.hauntedmc.dungeons.persistence;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** Bounded immutable snapshots. Tickets prevent late reads from undoing writes or invalidation. */
final class StateSnapshotCache {
    private static final int MAX_ENTRIES = 2048;
    private static final long MAX_BYTES = 32L * 1024 * 1024;
    private static final class Cell { StateStore.Snapshot snapshot; }
    record Read(StateStore.Key key, Cell cell, StateStore.Snapshot observed) {}
    record PlayerRead(long version, Map<StateStore.Key, Read> reads) {}
    private final LinkedHashMap<StateStore.Key, Cell> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long bytes;
    private long version;

    synchronized StateStore.Snapshot get(StateStore.Key key) {
        var cell = entries.get(key);
        return cell == null ? null : cell.snapshot;
    }
    synchronized Read begin(StateStore.Key key) {
        var cell = entries.computeIfAbsent(key, ignored -> new Cell());
        trim();
        return new Read(key, cell, cell.snapshot);
    }
    synchronized PlayerRead beginPlayer(UUID player) {
        var keys = new LinkedHashSet<StateStore.Key>();
        for (var key : entries.keySet()) if (player.equals(key.player())) keys.add(key);
        keys.add(new StateStore.Key("player-profile", player.toString(), player));
        keys.add(new StateStore.Key("player-recovery", player.toString(), player));
        var reads = new LinkedHashMap<StateStore.Key, Read>();
        for (var key : keys) {
            var previous = entries.put(key, new Cell());
            if (previous != null) bytes -= size(previous.snapshot);
            reads.put(key, begin(key));
        }
        version++;
        trim();
        return new PlayerRead(version, Map.copyOf(reads));
    }
    synchronized StateStore.Snapshot read(Read read, StateStore.Snapshot snapshot) {
        var cell = entries.get(read.key());
        if (cell != read.cell()) return snapshot; // Evicted or invalidated while SQL ran.
        if (cell.snapshot != read.observed()) return cell.snapshot == null ? snapshot : cell.snapshot;
        put(cell, snapshot);
        trim();
        return snapshot;
    }
    synchronized void player(PlayerRead read, Map<StateStore.Key, StateStore.Snapshot> snapshots) {
        boolean cacheUnknown = version == read.version();
        read.reads().forEach((key, ticket) -> read(ticket, snapshots.getOrDefault(key, new StateStore.Snapshot(0, ""))));
        if (cacheUnknown) snapshots.forEach((key, snapshot) -> {
            if (!read.reads().containsKey(key) && !entries.containsKey(key)) read(begin(key), snapshot);
        });
    }
    synchronized void committed(Read read, long expectedRevision, StateStore.Snapshot snapshot) {
        var cell = entries.get(read.key());
        if (cell == null) return;
        if (cell != read.cell() || cell.snapshot != null && cell.snapshot.revision() < expectedRevision) {
            // A new login or authoritative deletion/reset raced the commit. Reload instead of
            // resurrecting an old generation, or retaining a pre-commit login snapshot.
            put(cell, null);
        } else if (cell.snapshot == null || cell.snapshot.revision() <= snapshot.revision()) put(cell, snapshot);
        trim();
    }
    private void put(Cell cell, StateStore.Snapshot snapshot) {
        bytes += size(snapshot) - size(cell.snapshot);
        cell.snapshot = snapshot;
        version++;
    }
    private static long size(StateStore.Snapshot snapshot) { return snapshot == null ? 0 : snapshot.payload().length() * 2L; }
    private void trim() {
        while (entries.size() > MAX_ENTRIES || bytes > MAX_BYTES) {
            var iterator = entries.entrySet().iterator();
            var first = iterator.next();
            bytes -= size(first.getValue().snapshot);
            iterator.remove();
            version++;
        }
    }
}
