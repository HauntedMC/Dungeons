package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

class DungeonConcurrencyIT {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @BeforeAll static void start() throws Exception {
        MYSQL.start();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            DungeonSql.update(connection, "CREATE TABLE player_entity(id BIGINT PRIMARY KEY,uuid CHAR(36) NOT NULL UNIQUE)");
            DungeonSchema.initialize(connection, true);
        }
    }
    @AfterAll static void stop() { MYSQL.stop(); }

    @Test void aRetrySeesReceiptsCommittedAfterItsRepeatableReadSnapshot() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "retry")) {
            UUID dungeon = definition(host);
            var ledger = new DungeonKeyLedger(host.database, dungeon);
            UUID token = UUID.randomUUID();
            ledger.issue("key", token, UUID.randomUUID()).get(5, TimeUnit.SECONDS);
            UUID operation = UUID.randomUUID();
            CompletableFuture<Boolean> delayed;
            try (var gate = host.pauseAfter("SELECT key_id FROM dungeon_key_instance")) {
                delayed = ledger.reserve(token, operation);
                gate.await();
                assertTrue(ledger.reserve(token, operation).get(5, TimeUnit.SECONDS));
            }
            assertTrue(delayed.get(5, TimeUnit.SECONDS), "A repeated reservation must observe its committed receipt");
            try (var gate = host.pauseAfter("SELECT key_id FROM dungeon_key_instance")) {
                delayed = ledger.settle(token, operation, null);
                gate.await();
                assertTrue(ledger.settle(token, operation, null).get(5, TimeUnit.SECONDS));
            }
            assertTrue(delayed.get(5, TimeUnit.SECONDS), "A repeated settlement must observe its committed receipt");
        }
    }

    @Test void cancellingHeartbeatObservationDoesNotCancelFailureHandling() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "heartbeat")) {
            var failures = new AtomicInteger();
            host.database.onFailure(ignored -> failures.incrementAndGet());
            var renewal = new CompletableFuture<Object>();
            host.deferred.set(renewal);
            CompletableFuture<Void> observer = host.database.heartbeat();
            observer.cancel(true);
            renewal.completeExceptionally(new IllegalStateException("test outage"));
            assertEquals(DungeonDatabase.State.UNAVAILABLE, host.database.state());
            assertEquals(1, failures.get());
            host.database.heartbeat();
            assertEquals(1, failures.get(), "An unavailable database should report one state transition");
        }
    }

    @Test void anotherServerCannotSettleOrReleaseAReservationUsingTheSameOperationId() throws Exception {
        try (var origin = new DatabaseTestHost(MYSQL, "origin");
                var other = new DatabaseTestHost(MYSQL, "other")) {
            UUID dungeon = definition(origin);
            var owner = new DungeonKeyLedger(origin.database, dungeon);
            var foreign = new DungeonKeyLedger(other.database, dungeon);
            UUID token = UUID.randomUUID();
            UUID operation = UUID.randomUUID();
            owner.issue("key", token, UUID.randomUUID()).get(5, TimeUnit.SECONDS);
            assertTrue(owner.reserve(token, operation).get(5, TimeUnit.SECONDS));
            assertFalse(foreign.reserve(token, operation).get(5, TimeUnit.SECONDS));
            assertFalse(foreign.release(token, operation).get(5, TimeUnit.SECONDS));
            assertFalse(foreign.settle(token, operation, null).get(5, TimeUnit.SECONDS));
            assertTrue(owner.settle(token, operation, null).get(5, TimeUnit.SECONDS));
        }
    }

    @Test void oldProcessReservationsRequireExplicitRecoveryAfterRestart() throws Exception {
        UUID dungeon;
        UUID token = UUID.randomUUID();
        UUID operation = UUID.randomUUID();
        try (var original = new DatabaseTestHost(MYSQL, "restart-owner")) {
            dungeon = definition(original);
            var ledger = new DungeonKeyLedger(original.database, dungeon);
            ledger.issue("key", token, UUID.randomUUID()).get(5, TimeUnit.SECONDS);
            assertTrue(ledger.reserve(token, operation).get(5, TimeUnit.SECONDS));
        }
        try (var successor = new DatabaseTestHost(MYSQL, "restart-owner")) {
            var ledger = new DungeonKeyLedger(successor.database, dungeon);
            assertFalse(ledger.reserve(token, operation).get(5, TimeUnit.SECONDS));
            assertFalse(ledger.release(token, operation).get(5, TimeUnit.SECONDS));
            assertFalse(ledger.settle(token, operation, null).get(5, TimeUnit.SECONDS));
        }
    }

    @Test void overlappingHeartbeatObserversShareOneRenewalAndLeaseLossFailsImmediately() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "coalesced-heartbeat")) {
            var delayed = new CompletableFuture<Object>();
            host.deferred.set(delayed);
            int before = host.transactions.get();
            var first = host.database.heartbeat();
            var second = host.database.heartbeat();
            assertEquals(before + 1, host.transactions.get());
            first.cancel(true);
            delayed.complete(null);
            second.get(5, TimeUnit.SECONDS);
            var failures = new AtomicInteger();
            host.database.onFailure(failure -> { failures.incrementAndGet(); throw new IllegalStateException("observer failure"); });
            try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
                DungeonSql.update(connection, "UPDATE dungeon_server SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE server_id=?", host.database.serverId());
            }
            var ran = new AtomicInteger();
            assertThrows(java.util.concurrent.ExecutionException.class, () -> host.database.transaction(connection -> ran.incrementAndGet()).get(5, TimeUnit.SECONDS));
            assertEquals(0, ran.get());
            assertEquals(DungeonDatabase.State.UNAVAILABLE, host.database.state());
            assertEquals(1, failures.get());
        }
    }

    @Test void migrationTwoUpgradesVersionOneWithoutRewritingItsReceiptAndRejectsSchemaDrift() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            var original = DungeonSql.rows(connection, "SELECT checksum,applied_at FROM dungeon_schema_history WHERE version=1");
            DungeonSql.update(connection, "DROP TABLE dungeon_key_reservation");
            DungeonSql.update(connection, "DELETE FROM dungeon_schema_history WHERE version=2");
            assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, false));
            DungeonSchema.initialize(connection, true);
            assertEquals(original, DungeonSql.rows(connection, "SELECT checksum,applied_at FROM dungeon_schema_history WHERE version=1"));
            DungeonSql.update(connection, "ALTER TABLE dungeon_event MODIFY event_type VARCHAR(120) NOT NULL");
            try { assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, false)); }
            finally { DungeonSql.update(connection, "ALTER TABLE dungeon_event MODIFY event_type VARCHAR(48) NOT NULL"); }
            DungeonSql.update(connection, "ALTER TABLE dungeon_definition MODIFY created_at DATETIME(3) NULL DEFAULT CURRENT_TIMESTAMP(3)");
            try { assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, false)); }
            finally { DungeonSql.update(connection, "ALTER TABLE dungeon_definition MODIFY created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)"); }
            DungeonSql.update(connection, "ALTER TABLE dungeon_schema_history ENGINE=MyISAM");
            try { assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, false)); }
            finally { DungeonSql.update(connection, "ALTER TABLE dungeon_schema_history ENGINE=InnoDB"); }
            DungeonSql.update(connection, "ALTER TABLE dungeon_event DROP INDEX ix_event_operation");
            try { assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, false)); }
            finally { DungeonSql.update(connection, "ALTER TABLE dungeon_event ADD KEY ix_event_operation(operation_id)"); }
            DungeonSql.update(connection, "ALTER TABLE dungeon_staff_operation DROP PRIMARY KEY");
            try { assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, false)); }
            finally { DungeonSql.update(connection, "ALTER TABLE dungeon_staff_operation ADD PRIMARY KEY(operation_id)"); }
            DungeonSql.update(connection, "ALTER TABLE dungeon_restriction DROP FOREIGN KEY dungeon_restriction_ibfk_1");
            try {
                assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, false));
                DungeonSql.update(connection, "ALTER TABLE dungeon_restriction ADD CONSTRAINT fk_test_player FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE RESTRICT");
                assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, false));
            } finally {
                DungeonSql.update(connection, "ALTER TABLE dungeon_restriction DROP FOREIGN KEY fk_test_player");
                DungeonSql.update(connection, "ALTER TABLE dungeon_restriction ADD FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE");
            }
            connection.setAutoCommit(false);
            try { assertThrows(java.sql.SQLException.class, () -> DungeonSchema.initialize(connection, true)); }
            finally { connection.rollback(); connection.setAutoCommit(true); }
            DungeonSchema.initialize(connection, false);
        }
    }

    @Test void progressionAndCooldownWritesAreAtomicIdempotentIdentityBoundAndUtc() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "progression")) {
            UUID dungeon = definition(host);
            long playerId = 100;
            UUID uuid = UUID.randomUUID();
            host.database.transaction(connection -> {
                DungeonSql.update(connection, "INSERT INTO player_entity(id,uuid) VALUES(?,?)", playerId, uuid);
                return null;
            }).get(5, TimeUnit.SECONDS);
            var identity = new nl.hauntedmc.dataregistry.api.player.PlayerIdentity(playerId, uuid, "Explorer");
            org.mockito.Mockito.when(host.players.findIdentity(playerId)).thenReturn(CompletableFuture.completedFuture(java.util.Optional.of(identity)));
            var repository = new DungeonProgressRepository(host.database);
            var key = new DungeonProgressRepository.CooldownKey(DungeonProgressRepository.CooldownType.ACCESS, "");
            var expiry = java.time.Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            var checkpoint = new DungeonProgressRepository.Checkpoint("worldless checkpoint", "a".repeat(64));
            var change = new DungeonProgressRepository.Changes(true, true, checkpoint,
                    java.util.List.of(new DungeonProgressRepository.CooldownChange(key, expiry)));
            UUID operation = UUID.randomUUID();
            java.util.TimeZone previous = java.util.TimeZone.getDefault();
            try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"));
                assertEquals(1, repository.update(identity, dungeon, 0, change, operation).get(5, TimeUnit.SECONDS).revision());
                assertTrue(repository.update(identity, dungeon, 0, change, operation).get(5, TimeUnit.SECONDS).replayed());
                var loaded = repository.load(identity, dungeon).get(5, TimeUnit.SECONDS);
                assertEquals(1, loaded.revision());
                assertTrue(loaded.completed());
                assertEquals(expiry, loaded.cooldowns().get(key));
                assertEquals(checkpoint, loaded.checkpoint());
                assertNull(loaded.firstCompletedAt(), "Administrative progression must not invent finish history");
            } finally { java.util.TimeZone.setDefault(previous); }
            var clear = new DungeonProgressRepository.Changes(false, true, null,
                    java.util.List.of(new DungeonProgressRepository.CooldownChange(key, null)));
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> repository.update(identity, dungeon, 0, clear, operation).get(5, TimeUnit.SECONDS));
            var a = repository.update(identity, dungeon, 1, clear, UUID.randomUUID());
            var b = repository.update(identity, dungeon, 1, clear, UUID.randomUUID());
            a.handle((value, failure) -> null).get(5, TimeUnit.SECONDS);
            b.handle((value, failure) -> null).get(5, TimeUnit.SECONDS);
            assertNotEquals(a.isCompletedExceptionally(), b.isCompletedExceptionally());
            var loaded = repository.load(identity, dungeon).get(5, TimeUnit.SECONDS);
            assertEquals(2, loaded.revision());
            assertFalse(loaded.completed());
            assertNull(loaded.checkpoint());
            assertTrue(loaded.cooldowns().isEmpty());
            int queries = host.transactions.get();
            var replacement = new nl.hauntedmc.dataregistry.api.player.PlayerIdentity(playerId, UUID.randomUUID(), "AnotherIdentity");
            org.mockito.Mockito.when(host.players.findIdentity(playerId)).thenReturn(CompletableFuture.completedFuture(java.util.Optional.of(replacement)));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> repository.load(identity, dungeon).get(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.ExecutionException.class, () -> repository.update(identity, dungeon, 2, change, UUID.randomUUID()).get(5, TimeUnit.SECONDS));
            assertEquals(queries, host.transactions.get(), "Stale identity requests must not reach feature storage");
        }
    }

    private static UUID definition(DatabaseTestHost host) throws Exception {
        UUID id = UUID.randomUUID();
        host.database.transaction(connection -> {
            DungeonSql.update(connection, "INSERT INTO dungeon_definition(dungeon_id) VALUES(?)", id);
            DungeonSql.update(connection, "INSERT INTO dungeon_key_definition(dungeon_id,key_id) VALUES(?,'key')", id);
            return null;
        }).get(5, TimeUnit.SECONDS);
        return id;
    }
}
