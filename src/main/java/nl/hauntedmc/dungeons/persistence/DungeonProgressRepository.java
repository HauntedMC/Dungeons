package nl.hauntedmc.dungeons.persistence;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Canonical-player-bound progression writes with optimistic revisions and durable retry receipts. */
public final class DungeonProgressRepository {
    public enum CooldownType { ACCESS, REWARD }
    public record CooldownKey(CooldownType type, String sourceId) {
        public CooldownKey {
            Objects.requireNonNull(type, "type");
            if (sourceId == null || !sourceId.matches("[a-z0-9._:-]{0,96}"))
                throw new IllegalArgumentException("Cooldown source must be a stable lowercase identifier");
            if (type == CooldownType.ACCESS && !sourceId.isEmpty())
                throw new IllegalArgumentException("Access cooldowns use the dungeon-wide source");
        }
    }
    /** Null expiry explicitly clears this cooldown; values are stored with millisecond precision. */
    public record CooldownChange(CooldownKey key, Instant expiresAt) {
        public CooldownChange {
            Objects.requireNonNull(key, "key");
            if (expiresAt != null) {
                if (expiresAt.isBefore(Instant.EPOCH) || expiresAt.isAfter(Instant.parse("9999-12-31T23:59:59.999Z")))
                    throw new IllegalArgumentException("Cooldown expiry is outside the supported database range");
                expiresAt = Instant.ofEpochMilli(expiresAt.toEpochMilli());
            }
        }
    }
    public record Checkpoint(String data, String fingerprint) {
        public Checkpoint {
            if (data == null || data.getBytes(StandardCharsets.UTF_8).length > 60_000)
                throw new IllegalArgumentException("Checkpoint payload is missing or too large");
            if (fingerprint == null || !fingerprint.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Checkpoint requires a content fingerprint");
        }
    }
    /** A completion correction changes progression only, never historical run credits. */
    public record Changes(Boolean completed, boolean checkpointChanged, Checkpoint checkpoint, List<CooldownChange> cooldowns) {
        public Changes {
            if (!checkpointChanged && checkpoint != null) throw new IllegalArgumentException("Checkpoint change must be explicit");
            var sorted = new ArrayList<>(Objects.requireNonNull(cooldowns, "cooldowns"));
            sorted.sort(Comparator.comparing((CooldownChange change) -> change.key().type()).thenComparing(change -> change.key().sourceId()));
            var seen = new HashSet<CooldownKey>();
            for (var change : sorted) if (!seen.add(change.key())) throw new IllegalArgumentException("Duplicate cooldown change");
            cooldowns = List.copyOf(sorted);
            if (completed == null && !checkpointChanged && cooldowns.isEmpty()) throw new IllegalArgumentException("No progression changes requested");
        }
    }
    public record Snapshot(long revision, boolean completed, Instant firstCompletedAt, Instant lastCompletedAt,
            Checkpoint checkpoint, Map<CooldownKey, Instant> cooldowns) {
        public Snapshot { cooldowns = Map.copyOf(cooldowns); }
    }
    public record Receipt(UUID operationId, long revision, boolean replayed) {}
    public static final class RevisionConflict extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        public RevisionConflict() { super("Progression changed; refresh before applying a correction"); }
    }

    private final DungeonDatabase database;
    public DungeonProgressRepository(DungeonDatabase database) { this.database = Objects.requireNonNull(database); }

    public CompletableFuture<Snapshot> load(PlayerIdentity identity, UUID dungeonId) {
        Objects.requireNonNull(dungeonId, "dungeonId");
        return verified(identity, () -> database.transaction(connection -> {
            var rows = DungeonSql.rows(connection, "SELECT p.revision,p.completed,p.first_completed_at,p.last_completed_at,"
                    + "p.checkpoint,p.checkpoint_fingerprint,c.cooldown_type,c.source_id,c.expires_at"
                    + " FROM dungeon_definition d LEFT JOIN dungeon_player_progress p ON p.dungeon_id=d.dungeon_id AND p.player_id=?"
                    + " LEFT JOIN dungeon_cooldown c ON c.dungeon_id=d.dungeon_id AND c.player_id=? AND c.expires_at>UTC_TIMESTAMP(3)"
                    + " WHERE d.dungeon_id=?", identity.playerId(), identity.playerId(), dungeonId);
            if (rows.isEmpty()) throw new IllegalArgumentException("Unknown dungeon identity");
            var first = rows.getFirst();
            var cooldowns = new HashMap<CooldownKey, Instant>();
            for (var row : rows) if (row.get("cooldown_type") != null)
                cooldowns.put(new CooldownKey(CooldownType.valueOf((String) row.get("cooldown_type")), (String) row.get("source_id")),
                        (Instant) row.get("expires_at"));
            Checkpoint checkpoint = first.get("checkpoint") == null ? null
                    : new Checkpoint((String) first.get("checkpoint"), (String) first.get("checkpoint_fingerprint"));
            Object completed = first.get("completed");
            return new Snapshot(first.get("revision") == null ? 0 : ((Number) first.get("revision")).longValue(),
                    Boolean.TRUE.equals(completed) || completed instanceof Number number && number.intValue() != 0,
                    (Instant) first.get("first_completed_at"), (Instant) first.get("last_completed_at"), checkpoint, cooldowns);
        }));
    }

    public CompletableFuture<Receipt> update(PlayerIdentity identity, UUID dungeonId, long expectedRevision, Changes changes, UUID operationId) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(dungeonId, "dungeonId");
        Objects.requireNonNull(changes, "changes");
        Objects.requireNonNull(operationId, "operationId");
        if (expectedRevision < 0) throw new IllegalArgumentException("Revision must not be negative");
        String digest = digest(identity, dungeonId, expectedRevision, changes);
        return verified(identity, () -> database.transaction(connection -> {
            DungeonSql.update(connection, "INSERT INTO dungeon_player_progress(player_id,dungeon_id) VALUES(?,?)"
                    + " ON DUPLICATE KEY UPDATE player_id=player_id", identity.playerId(), dungeonId);
            var row = DungeonSql.rows(connection, "SELECT revision FROM dungeon_player_progress WHERE player_id=? AND dungeon_id=? FOR UPDATE",
                    identity.playerId(), dungeonId).getFirst();
            var receipts = DungeonSql.rows(connection, "SELECT player_id,dungeon_id,event_type,detail FROM dungeon_event WHERE event_id=? FOR SHARE", operationId);
            if (!receipts.isEmpty()) {
                var receipt = receipts.getFirst();
                String detail = (String) receipt.get("detail");
                if (!Objects.equals(identity.playerId(), receipt.get("player_id")) || !dungeonId.toString().equals(receipt.get("dungeon_id"))
                        || !"PROGRESS_UPDATED".equals(receipt.get("event_type")) || !detail.startsWith(digest + ":"))
                    throw new IllegalArgumentException("Operation ID already belongs to another request");
                return new Receipt(operationId, Long.parseLong(detail.substring(digest.length() + 1)), true);
            }
            long current = ((Number) row.get("revision")).longValue();
            if (current != expectedRevision) throw new RevisionConflict();
            long revision = Math.addExact(current, 1);
            DungeonSql.update(connection, "UPDATE dungeon_player_progress SET completed=COALESCE(?,completed),revision=? WHERE player_id=? AND dungeon_id=?",
                    changes.completed(), revision, identity.playerId(), dungeonId);
            if (changes.checkpointChanged()) DungeonSql.update(connection, "UPDATE dungeon_player_progress SET checkpoint=?,checkpoint_fingerprint=?"
                    + " WHERE player_id=? AND dungeon_id=?", changes.checkpoint() == null ? null : changes.checkpoint().data(),
                    changes.checkpoint() == null ? null : changes.checkpoint().fingerprint(), identity.playerId(), dungeonId);
            for (var change : changes.cooldowns()) {
                if (change.expiresAt() == null) DungeonSql.update(connection, "DELETE FROM dungeon_cooldown"
                        + " WHERE player_id=? AND dungeon_id=? AND cooldown_type=? AND source_id=?",
                        identity.playerId(), dungeonId, change.key().type().name(), change.key().sourceId());
                else DungeonSql.update(connection, "INSERT INTO dungeon_cooldown(player_id,dungeon_id,cooldown_type,source_id,activated_at,expires_at)"
                        + " VALUES(?,?,?,?,UTC_TIMESTAMP(3),?) ON DUPLICATE KEY UPDATE activated_at=UTC_TIMESTAMP(3),expires_at=VALUES(expires_at)",
                        identity.playerId(), dungeonId, change.key().type().name(), change.key().sourceId(), change.expiresAt());
            }
            DungeonSql.update(connection, "INSERT INTO dungeon_event(event_id,server_id,dungeon_id,player_id,operation_id,event_type,occurred_at,detail)"
                    + " VALUES(?,?,?,?,?,'PROGRESS_UPDATED',UTC_TIMESTAMP(3),?)", operationId, database.serverId(), dungeonId,
                    identity.playerId(), operationId, digest + ":" + revision);
            return new Receipt(operationId, revision, false);
        }));
    }

    private <T> CompletableFuture<T> verified(PlayerIdentity identity, Supplier<CompletableFuture<T>> action) {
        Objects.requireNonNull(identity, "identity");
        return database.players().findIdentity(identity.playerId()).thenCompose(current -> {
            if (current.isEmpty() || !identity.uuid().equals(current.get().uuid()))
                return CompletableFuture.<T>failedFuture(new IllegalStateException("Canonical player identity is no longer valid"));
            return action.get();
        }).toCompletableFuture().copy();
    }

    private static String digest(PlayerIdentity identity, UUID dungeonId, long revision, Changes changes) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeInt(1);
                out.writeLong(identity.playerId());
                write(out, identity.uuid().toString());
                write(out, dungeonId.toString());
                out.writeLong(revision);
                out.writeByte(changes.completed() == null ? -1 : changes.completed() ? 1 : 0);
                out.writeBoolean(changes.checkpointChanged());
                write(out, changes.checkpoint() == null ? null : changes.checkpoint().data());
                write(out, changes.checkpoint() == null ? null : changes.checkpoint().fingerprint());
                out.writeInt(changes.cooldowns().size());
                for (var change : changes.cooldowns()) {
                    write(out, change.key().type().name());
                    write(out, change.key().sourceId());
                    write(out, change.expiresAt() == null ? null : change.expiresAt().toString());
                }
            }
            return DungeonSchema.checksum(bytes.toByteArray());
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void write(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes == null ? -1 : bytes.length);
        if (bytes != null) out.write(bytes);
    }
}
