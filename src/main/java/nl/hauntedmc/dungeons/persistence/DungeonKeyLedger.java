package nl.hauntedmc.dungeons.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Authoritative unique-key transitions. Metadata-only keys never consult this token ledger. */
public final class DungeonKeyLedger {
    private final DungeonDatabase database;
    private final UUID dungeonId;

    public DungeonKeyLedger(DungeonDatabase database, UUID dungeonId) {
        this.database = Objects.requireNonNull(database);
        this.dungeonId = Objects.requireNonNull(dungeonId);
    }

    public CompletableFuture<UUID> issue(String keyId, UUID token, UUID operation) {
        return database.transaction(connection -> {
            lockDefinition(connection, keyId);
            int inserted = DungeonSql.update(connection, "INSERT IGNORE INTO dungeon_key_instance"
                    + "(token_id,dungeon_id,key_id,state,operation_id,issued_at,updated_at)"
                    + " VALUES(?,?,?,'ISSUED',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", token, dungeonId, keyId, operation);
            Map<String, Object> row = token(connection, token);
            if (!keyId.equals(row.get("key_id"))) throw new SQLException("Key token conflicts with another definition");
            if (inserted == 1) {
                requireDefinition(connection, keyId);
                event(connection, "KEY_ISSUED", token, operation);
            }
            else if (!recorded(connection, "KEY_ISSUED", token, operation))
                throw new SQLException("Key token belongs to another issuance operation");
            return token;
        });
    }

    public CompletableFuture<Boolean> reserve(UUID token, UUID operation) {
        return database.transaction(connection -> {
            String keyId = identifyToken(connection, token);
            lockDefinition(connection, keyId);
            Map<String, Object> row = token(connection, token);
            requireDefinition(connection, keyId);
            if (recorded(connection, "KEY_RESERVED", token, operation)) {
                return row.get("state").equals("RESERVED") && operation.toString().equals(row.get("operation_id"))
                        && owned(connection, token, operation);
            }
            if (!row.get("state").equals("ISSUED")) return false;
            DungeonSql.update(connection, "UPDATE dungeon_key_instance SET state='RESERVED',operation_id=?,server_id=?,"
                    + "updated_at=UTC_TIMESTAMP(3) WHERE token_id=?", operation, database.serverId(), token);
            DungeonSql.update(connection, "INSERT INTO dungeon_key_reservation(token_id,server_id,boot_id,operation_id) VALUES(?,?,?,?)",
                    token, database.serverId(), database.bootId(), operation);
            event(connection, "KEY_RESERVED", token, operation);
            return true;
        });
    }

    public CompletableFuture<Boolean> release(UUID token, UUID operation) {
        return database.transaction(connection -> {
            Map<String, Object> row = token(connection, token);
            if (recorded(connection, "KEY_RELEASED", token, operation)) return true;
            if (!row.get("state").equals("RESERVED") || !operation.toString().equals(row.get("operation_id"))
                    || !owned(connection, token, operation)) return false;
            DungeonSql.update(connection, "UPDATE dungeon_key_instance SET state='ISSUED',operation_id=NULL,server_id=NULL,"
                    + "updated_at=UTC_TIMESTAMP(3) WHERE token_id=?", token);
            DungeonSql.update(connection, "DELETE FROM dungeon_key_reservation WHERE token_id=?", token);
            event(connection, "KEY_RELEASED", token, operation);
            return true;
        });
    }

    /** A null replacement consumes the key; otherwise the new token is issued atomically. */
    public CompletableFuture<Boolean> settle(UUID token, UUID operation, UUID replacement) {
        return database.transaction(connection -> {
            String keyId = identifyToken(connection, token);
            lockDefinition(connection, keyId);
            Map<String, Object> row = token(connection, token);
            String transition = replacement == null ? "KEY_CONSUMED" : "KEY_ROTATED";
            if (recorded(connection, transition, token, operation)) {
                if (!Objects.equals(replacement == null ? null : replacement.toString(), row.get("replacement_id")))
                    throw new SQLException("A retry cannot change a key replacement token");
                return true;
            }
            if (!row.get("state").equals("RESERVED") || !operation.toString().equals(row.get("operation_id"))
                    || !owned(connection, token, operation)) return false;
            requireDefinition(connection, (String) row.get("key_id"));
            if (replacement != null) {
                if (replacement.equals(token)) throw new IllegalArgumentException("Replacement must be a new token");
                DungeonSql.update(connection, "INSERT INTO dungeon_key_instance"
                        + "(token_id,dungeon_id,key_id,state,operation_id,issued_at,updated_at)"
                        + " VALUES(?,?,?,'ISSUED',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))", replacement, dungeonId, row.get("key_id"), operation);
            }
            DungeonSql.update(connection, "UPDATE dungeon_key_instance SET state=?,replacement_id=?,updated_at=UTC_TIMESTAMP(3)"
                    + " WHERE token_id=?", replacement == null ? "CONSUMED" : "ROTATED", replacement, token);
            DungeonSql.update(connection, "DELETE FROM dungeon_key_reservation WHERE token_id=?", token);
            event(connection, transition, token, operation);
            return true;
        });
    }

    public CompletableFuture<Void> revoke(String keyId, UUID operation) {
        return database.transaction(connection -> {
            var definition = DungeonSql.rows(connection, "SELECT key_id FROM dungeon_key_definition WHERE dungeon_id=? AND key_id=? FOR UPDATE", dungeonId, keyId);
            if (definition.isEmpty()) throw new SQLException("Unknown key definition");
            DungeonSql.update(connection, "UPDATE dungeon_key_definition SET revoked=TRUE WHERE dungeon_id=? AND key_id=?", dungeonId, keyId);
            DungeonSql.update(connection, "UPDATE dungeon_key_instance SET state='REVOKED',updated_at=UTC_TIMESTAMP(3)"
                    + " WHERE dungeon_id=? AND key_id=? AND state IN ('ISSUED','RESERVED')", dungeonId, keyId);
            UUID subject = UUID.nameUUIDFromBytes((dungeonId + ":definition:" + keyId).getBytes(StandardCharsets.UTF_8));
            if (!recorded(connection, "KEY_REVOKED", subject, operation)) event(connection, "KEY_REVOKED", subject, operation);
            return null;
        });
    }

    private String identifyToken(Connection connection, UUID token) throws SQLException {
        var rows = DungeonSql.rows(connection, "SELECT key_id FROM dungeon_key_instance WHERE token_id=? AND dungeon_id=?", token, dungeonId);
        if (rows.isEmpty()) throw new SQLException("Unknown key token for this dungeon");
        return (String) rows.getFirst().get("key_id");
    }

    private Object lockDefinition(Connection connection, String keyId) throws SQLException {
        var definitions = DungeonSql.rows(connection, "SELECT revoked FROM dungeon_key_definition WHERE dungeon_id=? AND key_id=? FOR SHARE", dungeonId, keyId);
        if (definitions.isEmpty()) throw new SQLException("Unknown key definition");
        return definitions.getFirst().get("revoked");
    }

    private void requireDefinition(Connection connection, String keyId) throws SQLException {
        Object revoked = lockDefinition(connection, keyId);
        boolean active = Boolean.FALSE.equals(revoked) || revoked instanceof Number number && number.intValue() == 0;
        if (!active) throw new SQLException("Key definition is revoked");
    }

    private Map<String, Object> token(Connection connection, UUID token) throws SQLException {
        var tokens = DungeonSql.rows(connection, "SELECT key_id,state,operation_id,replacement_id FROM dungeon_key_instance"
                + " WHERE token_id=? AND dungeon_id=? FOR UPDATE", token, dungeonId);
        if (tokens.isEmpty()) throw new SQLException("Unknown key token for this dungeon");
        return tokens.getFirst();
    }

    private boolean owned(Connection connection, UUID token, UUID operation) throws SQLException {
        return !DungeonSql.rows(connection, "SELECT token_id FROM dungeon_key_reservation"
                + " WHERE token_id=? AND server_id=? AND boot_id=? AND operation_id=? FOR SHARE",
                token, database.serverId(), database.bootId(), operation).isEmpty();
    }

    private UUID eventId(String type, UUID subject, UUID operation) {
        return UUID.nameUUIDFromBytes((type + ":" + dungeonId + ":" + subject + ":" + operation).getBytes(StandardCharsets.UTF_8));
    }

    private boolean recorded(Connection connection, String type, UUID subject, UUID operation) throws SQLException {
        return !DungeonSql.rows(connection, "SELECT event_id FROM dungeon_event WHERE event_id=? FOR SHARE", eventId(type, subject, operation)).isEmpty();
    }

    private void event(Connection connection, String type, UUID subject, UUID operation) throws SQLException {
        DungeonSql.update(connection, "INSERT INTO dungeon_event(event_id,server_id,dungeon_id,operation_id,event_type,occurred_at,detail)"
                + " VALUES(?,?,?,?,?,UTC_TIMESTAMP(3),?)", eventId(type, subject, operation), database.serverId(), dungeonId,
                operation, type, subject.toString());
    }
}
