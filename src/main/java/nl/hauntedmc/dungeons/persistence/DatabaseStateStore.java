package nl.hauntedmc.dungeons.persistence;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Lease-fenced state snapshots with optimistic revisions, checksums and idempotent commit receipts. */
public final class DatabaseStateStore implements StateStore {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final StateSnapshotCache cache = new StateSnapshotCache();
    private final DungeonDatabase database;
    private final int timeoutMillis;

    public DatabaseStateStore(DungeonDatabase database, int timeoutMillis) {
        this.database = java.util.Objects.requireNonNull(database);
        this.timeoutMillis = timeoutMillis;
    }
    @Override public void requireReady() {
        if (!database.ready()) throw new IllegalStateException("Database is not ready: " + database.problem());
    }
    @Override public void requirePlayerReady(UUID player) {
        requireReady();
        if (database.players().findActiveIdentityCached(player).filter(identity -> player.equals(identity.uuid())).isEmpty())
            throw new IllegalArgumentException("Player data is still loading; try again shortly");
    }
    @Override public CompletableFuture<Void> prefetch(UUID player) {
        requireReady();
        var read = cache.beginPlayer(player);
        return database.players().findIdentity(player).thenCompose(identity -> {
            // Paper's DataRegistry can provision first-time identities after PlayerJoinEvent.
            // Do not prevent that join. Still probe our fenced connection before allowing it.
            if (identity.isEmpty()) return database.transaction(connection -> Map.<Key, Snapshot>of())
                    .thenAccept(snapshots -> cache.player(read, snapshots));
            if (!player.equals(identity.get().uuid()))
                return CompletableFuture.<Void>failedFuture(new IllegalStateException("DataRegistry identity mismatch"));
            return database.transaction(connection -> {
                requireCanonicalPlayer(connection, player, identity.get().playerId());
                var snapshots = new LinkedHashMap<Key, Snapshot>();
                for (var row : DungeonSql.rows(connection, "SELECT scope,record_id,player_id,revision,payload,checksum,operation_id"
                        + " FROM dungeon_state_document WHERE server_id=? AND player_id=?", database.serverId(), identity.get().playerId())) {
                    var key = new Key((String) row.get("scope"), (String) row.get("record_id"), player);
                    snapshots.put(key, decode(key, row));
                }
                snapshots.putIfAbsent(new Key("player-profile", player.toString(), player), new Snapshot(0, ""));
                snapshots.putIfAbsent(new Key("player-recovery", player.toString(), player), new Snapshot(0, ""));
                return snapshots;
            }).thenAccept(snapshots -> cache.player(read, snapshots));
        }).toCompletableFuture();
    }
    @Override public CompletableFuture<Snapshot> load(Key key) {
        requireReady();
        var hit = cache.get(key);
        return hit == null ? refresh(key) : CompletableFuture.completedFuture(hit);
    }
    @Override public CompletableFuture<Snapshot> refresh(Key key) {
        var read = cache.begin(key);
        return database.transaction(connection -> {
            var rows = DungeonSql.rows(connection, "SELECT player_id,revision,payload,checksum FROM dungeon_state_document"
                    + " WHERE server_id=? AND scope=? AND record_id=?", database.serverId(), key.scope(), key.record());
            return rows.isEmpty() ? new Snapshot(0, "") : decode(key, rows.getFirst());
        }).thenApply(snapshot -> cache.read(read, snapshot));
    }
    @Override public CompletableFuture<Map<String, Snapshot>> list(String scope) {
        new Key(scope, "validation", null);
        return database.transaction(connection -> {
            var result = new LinkedHashMap<String, Snapshot>();
            for (var row : DungeonSql.rows(connection, "SELECT record_id,player_id,revision,payload,checksum FROM dungeon_state_document"
                    + " WHERE server_id=? AND scope=? ORDER BY record_id", database.serverId(), scope))
                result.put((String) row.get("record_id"), decode(
                        new Key(scope, (String) row.get("record_id"), null), row));
            return Map.copyOf(result);
        });
    }
    @Override public CompletableFuture<java.util.List<Snapshot>> saveAll(java.util.List<Write> requested) {
        final java.util.List<Write> writes = java.util.List.copyOf(requested);
        var keys = new java.util.HashSet<String>();
        for (var write : writes) {
            if (write.key() == null || !keys.add(write.key().scope() + "\n" + write.key().record()) || write.revision() < 0 || write.operation() == null
                    || write.payload() == null || write.payload().getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
                return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid, duplicate or oversized gameplay snapshot"));
        }
        var reads = new LinkedHashMap<Key, StateSnapshotCache.Read>();
        var identities = new LinkedHashMap<Key, CompletableFuture<Long>>();
        for (var write : writes) {
            var key = write.key();
            reads.put(key, cache.begin(key));
            identities.put(key, key.player() == null ? CompletableFuture.completedFuture(null)
                    : database.players().findIdentity(key.player()).thenApply(identity -> {
                        var current = identity.orElseThrow(() -> new IllegalArgumentException("DataRegistry identity is unavailable for " + key.player()));
                        if (!key.player().equals(current.uuid())) throw new IllegalArgumentException("Canonical player identity mismatch");
                        return current.playerId();
                    }).toCompletableFuture());
        }
        return CompletableFuture.allOf(identities.values().toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> database.transaction(connection -> {
                    // Verify UUIDs on the selected connection too: a wrong database may contain
                    // the same numeric IDs for different players. Lock parents before child rows.
                    var players = new java.util.TreeMap<UUID, Long>();
                    writes.forEach(write -> {
                        if (write.key().player() != null) players.put(write.key().player(), identities.get(write.key()).join());
                    });
                    for (var player : players.entrySet()) requireCanonicalPlayer(connection, player.getKey(), player.getValue());
                    var results = new LinkedHashMap<Key, Snapshot>();
                    // Every multi-document writer acquires row locks in the same order.
                    for (var write : writes.stream().sorted(java.util.Comparator
                            .comparing((Write value) -> value.key().scope()).thenComparing(value -> value.key().record())).toList())
                        results.put(write.key(), write(connection, write, identities.get(write.key()).join()));
                    return writes.stream().map(value -> results.get(value.key())).toList();
                })).thenApply(results -> {
                    for (int index = 0; index < writes.size(); index++)
                        cache.committed(reads.get(writes.get(index).key()), writes.get(index).revision(), results.get(index));
                    return results;
                });
    }
    private void requireCanonicalPlayer(java.sql.Connection connection, UUID player, long playerId) throws java.sql.SQLException {
        var rows = DungeonSql.rows(connection, "SELECT uuid FROM player_entity WHERE id=? FOR SHARE", playerId);
        if (rows.isEmpty()) throw new IllegalArgumentException("Player identity is no longer available");
        if (!player.toString().equalsIgnoreCase((String) rows.getFirst().get("uuid"))) {
            var failure = new IllegalStateException("Selected database does not contain the canonical DataRegistry player identity");
            database.unavailable(failure);
            throw failure;
        }
    }
    private Snapshot write(java.sql.Connection connection, Write write, Long playerId) throws java.sql.SQLException {
        var key = write.key();
        String checksum = checksum(key, playerId, write.payload());
        // Inserting revision zero also serializes first writers. Rollback leaves no empty row.
        DungeonSql.update(connection, "INSERT INTO dungeon_state_document(server_id,scope,record_id,revision,payload,checksum,updated_at)"
                + " VALUES(?,?,?,0,'',?,UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE record_id=record_id",
                database.serverId(), key.scope(), key.record(), checksum(key, null, ""));
        var current = DungeonSql.rows(connection, "SELECT player_id,revision,payload,checksum,operation_id FROM dungeon_state_document"
                + " WHERE server_id=? AND scope=? AND record_id=? FOR UPDATE", database.serverId(), key.scope(), key.record()).getFirst();
        var verified = decode(key, current);
        if (write.operation().toString().equals(current.get("operation_id"))) {
            if (!checksum.equals(current.get("checksum"))) throw new IllegalArgumentException("A retry cannot change its snapshot");
            return verified;
        }
        if (verified.revision() != write.revision())
            throw new IllegalStateException("Gameplay state changed concurrently: " + key.scope() + "/" + key.record());
        long next = Math.addExact(write.revision(), 1);
        DungeonSql.update(connection, "UPDATE dungeon_state_document SET player_id=?,revision=?,payload=?,checksum=?,operation_id=?,"
                + "updated_at=UTC_TIMESTAMP(3) WHERE server_id=? AND scope=? AND record_id=?", playerId, next, write.payload(), checksum,
                write.operation(), database.serverId(), key.scope(), key.record());
        return new Snapshot(next, write.payload());
    }
    @Override public CompletableFuture<Void> audit(String message) {
        if (message == null || message.length() > 12000)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid audit message"));
        return database.transaction(connection -> {
            UUID operation = UUID.randomUUID();
            DungeonSql.update(connection, "INSERT INTO dungeon_event(event_id,server_id,operation_id,event_type,occurred_at,detail)"
                    + " VALUES(?,?,?,'STAFF_AUDIT',UTC_TIMESTAMP(3),?)", operation, database.serverId(), operation, message);
            return null;
        });
    }
    private String checksum(Key key, Object player, String payload) {
        String bound = database.serverId() + "\n" + key.scope() + "\n" + key.record() + "\n"
                + java.util.Objects.toString(player, "") + "\n" + payload;
        return DungeonSchema.checksum(bound.getBytes(StandardCharsets.UTF_8));
    }
    private Snapshot decode(Key key, Map<String, Object> row) {
        String payload = (String) row.get("payload");
        if (payload == null || payload.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES
                || !checksum(key, row.get("player_id"), payload).equals(row.get("checksum")))
            throw new IllegalStateException("Gameplay state checksum mismatch");
        if (!(row.get("revision") instanceof Number revision) || revision.longValue() < 0)
            throw new IllegalStateException("Invalid gameplay state revision");
        return new Snapshot(revision.longValue(), payload);
    }
    @Override public <T> T await(CompletableFuture<T> operation) {
        try {
            // No database work executes here. Existing inventory write-ahead APIs require a
            // bounded durability barrier before changing player state; reads are retained by owners.
            return operation.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            database.unavailable(failure);
            throw new IllegalStateException("Interrupted gameplay persistence", failure);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException | java.util.concurrent.CancellationException failure) {
            Throwable cause = failure;
            while ((cause instanceof java.util.concurrent.ExecutionException || cause instanceof java.util.concurrent.CompletionException)
                    && cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof IllegalArgumentException rejected) throw rejected;
            database.unavailable(failure);
            // Never cancel an ambiguous write: shutdown drains it and the next boot reloads DB state.
            throw new IllegalStateException("Gameplay persistence failed; database admission is closed", failure);
        }
    }
}
