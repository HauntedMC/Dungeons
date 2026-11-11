package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

class DungeonConcurrencyIT {
    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @BeforeAll
    static void start() throws Exception {
        MYSQL.start();
        try (var connection =
                DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            DungeonSql.update(
                    connection,
                    "CREATE TABLE player_entity(id BIGINT PRIMARY KEY,uuid CHAR(36) NOT NULL UNIQUE)");
            OrmTestSupport.initialize(MYSQL, "update");
        }
    }

    @AfterAll
    static void stop() {
        MYSQL.stop();
    }

    @Test
    void aRetrySeesReceiptsCommittedAfterItsRepeatableReadSnapshot() throws Exception {
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
            assertTrue(
                    delayed.get(5, TimeUnit.SECONDS),
                    "A repeated reservation must observe its committed receipt");
            try (var gate = host.pauseAfter("SELECT key_id FROM dungeon_key_instance")) {
                delayed = ledger.settle(token, operation, null);
                gate.await();
                assertTrue(ledger.settle(token, operation, null).get(5, TimeUnit.SECONDS));
            }
            assertTrue(
                    delayed.get(5, TimeUnit.SECONDS),
                    "A repeated settlement must observe its committed receipt");
        }
    }

    @Test
    void cancellingHeartbeatObservationDoesNotCancelFailureHandling() throws Exception {
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
            assertEquals(
                    1,
                    failures.get(),
                    "An unavailable database should report one state transition");
        }
    }

    @Test
    void anotherServerCannotSettleOrReleaseAReservationUsingTheSameOperationId() throws Exception {
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

    @Test
    void oldProcessReservationsRequireExplicitRecoveryAfterRestart() throws Exception {
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

    @Test
    void overlappingHeartbeatObserversShareOneRenewalAndLeaseLossFailsImmediately()
            throws Exception {
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
            host.database.onFailure(
                    failure -> {
                        failures.incrementAndGet();
                        throw new IllegalStateException("observer failure");
                    });
            try (var connection =
                    DriverManager.getConnection(
                            MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
                DungeonSql.update(
                        connection,
                        "UPDATE dungeon_server SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE server_id=?",
                        host.database.serverId());
            }
            var ran = new AtomicInteger();
            assertThrows(
                    ExecutionException.class,
                    () ->
                            host.database
                                    .transaction(connection -> ran.incrementAndGet())
                                    .get(5, TimeUnit.SECONDS));
            assertEquals(0, ran.get());
            assertEquals(DungeonDatabase.State.UNAVAILABLE, host.database.state());
            assertEquals(1, failures.get());
        }
    }

    @Test
    void ormUpdateRestoresMissingTablesAndValidateRejectsMissingColumns() throws Exception {
        try (var connection =
                DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            DungeonSql.update(connection, "DROP TABLE dungeon_key_reservation");
            assertThrows(
                    RuntimeException.class, () -> OrmTestSupport.initialize(MYSQL, "validate"));
            OrmTestSupport.initialize(MYSQL, "update");
            OrmTestSupport.initialize(MYSQL, "validate");
            DungeonSql.update(connection, "ALTER TABLE dungeon_event DROP COLUMN detail");
            assertThrows(
                    RuntimeException.class, () -> OrmTestSupport.initialize(MYSQL, "validate"));
            OrmTestSupport.initialize(MYSQL, "update");
            OrmTestSupport.initialize(MYSQL, "validate");
        }
    }

    @Test
    void constraintDriftFailsClosedEvenWhenTheProviderDisablesDDL() throws Exception {
        try (var connection =
                DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            DungeonSql.update(
                    connection, "ALTER TABLE dungeon_event DROP INDEX ix_event_operation");
            try {
                assertThrows(
                        RuntimeException.class, () -> OrmTestSupport.initialize(MYSQL, "none"));
            } finally {
                DungeonSql.update(
                        connection,
                        "ALTER TABLE dungeon_event ADD INDEX ix_event_operation(operation_id)");
            }
            var constraints =
                    DungeonSql.rows(
                            connection,
                            "SELECT CONSTRAINT_NAME AS name FROM information_schema.KEY_COLUMN_USAGE"
                                    + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dungeon_restriction' AND REFERENCED_TABLE_NAME='player_entity'");
            String name = (String) constraints.getFirst().get("name");
            DungeonSql.update(
                    connection, "ALTER TABLE dungeon_restriction DROP FOREIGN KEY " + name);
            DungeonSql.update(
                    connection,
                    "ALTER TABLE dungeon_restriction ADD CONSTRAINT "
                            + name
                            + " FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE RESTRICT");
            try {
                assertThrows(
                        RuntimeException.class, () -> OrmTestSupport.initialize(MYSQL, "none"));
            } finally {
                DungeonSql.update(
                        connection, "ALTER TABLE dungeon_restriction DROP FOREIGN KEY " + name);
                DungeonSql.update(
                        connection,
                        "ALTER TABLE dungeon_restriction ADD CONSTRAINT "
                                + name
                                + " FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE");
            }
            OrmTestSupport.initialize(MYSQL, "validate");
        }
    }

    @Test
    void incompatibleColumnsAndForeignKeyUpdateRulesPreventReadiness() throws Exception {
        try (var connection =
                DriverManager.getConnection(
                        MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            DungeonSql.update(
                    connection, "ALTER TABLE dungeon_event MODIFY event_type VARCHAR(24) NOT NULL");
            try {
                assertThrows(
                        RuntimeException.class, () -> OrmTestSupport.initialize(MYSQL, "none"));
            } finally {
                DungeonSql.update(
                        connection,
                        "ALTER TABLE dungeon_event MODIFY event_type VARCHAR(48) NOT NULL");
            }
            DungeonSql.update(
                    connection,
                    "ALTER TABLE dungeon_server MODIFY storage_version INT NULL DEFAULT 0");
            try {
                assertThrows(
                        RuntimeException.class, () -> OrmTestSupport.initialize(MYSQL, "none"));
            } finally {
                DungeonSql.update(
                        connection,
                        "ALTER TABLE dungeon_server MODIFY storage_version INT NOT NULL DEFAULT 0");
            }
            var constraints =
                    DungeonSql.rows(
                            connection,
                            "SELECT CONSTRAINT_NAME AS name FROM information_schema.KEY_COLUMN_USAGE"
                                    + " WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='dungeon_restriction' AND REFERENCED_TABLE_NAME='player_entity'");
            String name = (String) constraints.getFirst().get("name");
            DungeonSql.update(
                    connection, "ALTER TABLE dungeon_restriction DROP FOREIGN KEY " + name);
            DungeonSql.update(
                    connection,
                    "ALTER TABLE dungeon_restriction ADD CONSTRAINT "
                            + name
                            + " FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE ON UPDATE CASCADE");
            try {
                assertThrows(
                        RuntimeException.class, () -> OrmTestSupport.initialize(MYSQL, "none"));
            } finally {
                DungeonSql.update(
                        connection, "ALTER TABLE dungeon_restriction DROP FOREIGN KEY " + name);
                DungeonSql.update(
                        connection,
                        "ALTER TABLE dungeon_restriction ADD CONSTRAINT "
                                + name
                                + " FOREIGN KEY(player_id) REFERENCES player_entity(id) ON DELETE CASCADE");
            }
            OrmTestSupport.initialize(MYSQL, "validate");
        }
    }

    private static UUID definition(DatabaseTestHost host) throws Exception {
        UUID id = UUID.randomUUID();
        host.database
                .transaction(
                        connection -> {
                            DungeonSql.update(
                                    connection,
                                    "INSERT INTO dungeon_definition(dungeon_id) VALUES(?)",
                                    id);
                            DungeonSql.update(
                                    connection,
                                    "INSERT INTO dungeon_key_definition(dungeon_id,key_id) VALUES(?,'key')",
                                    id);
                            return null;
                        })
                .get(5, TimeUnit.SECONDS);
        return id;
    }
}
