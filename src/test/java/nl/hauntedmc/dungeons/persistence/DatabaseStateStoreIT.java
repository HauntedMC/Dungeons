package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.DriverManager;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

class DatabaseStateStoreIT {
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @BeforeAll static void start() throws Exception {
        MYSQL.start();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            DungeonSql.update(connection, "CREATE TABLE player_entity(id BIGINT PRIMARY KEY,uuid VARCHAR(36) NOT NULL UNIQUE)");
        }
        OrmTestSupport.initialize(MYSQL, "update");
    }
    @AfterAll static void stop() { MYSQL.stop(); }

    @Test void snapshotsAreDurableIdempotentVersionedAndBoundToCanonicalPlayers() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "state-versioned")) {
            UUID player = UUID.randomUUID();
            host.database.transaction(connection -> {
                DungeonSql.update(connection, "INSERT INTO player_entity(id,uuid) VALUES(100,?)", player);
                return null;
            }).get(5, TimeUnit.SECONDS);
            when(host.players.findIdentity(player)).thenReturn(CompletableFuture.completedFuture(Optional.of(new PlayerIdentity(100L, player, "Player"))));
            var store = new DatabaseStateStore(host.database, 3000);
            var key = new StateStore.Key("player-profile", player.toString(), player);
            UUID operation = UUID.randomUUID();
            var saved = store.save(key, 0, "Completed: true\n", operation).get(5, TimeUnit.SECONDS);
            assertEquals(1, saved.revision());
            assertEquals(saved, store.save(key, 0, "Completed: true\n", operation).get(5, TimeUnit.SECONDS));
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> store.save(key, 1, "changed", operation).get(5, TimeUnit.SECONDS));
            var fresh = new DatabaseStateStore(host.database, 3000);
            assertEquals(saved, fresh.load(key).get(5, TimeUnit.SECONDS));
            fresh.prefetch(player).get(5, TimeUnit.SECONDS);
            assertEquals(saved, fresh.load(key).get(5, TimeUnit.SECONDS));
            var document = fresh.document(key);
            document.save("Completed: false\n");
            assertEquals(2, document.revision());
            assertEquals("Completed: false\n", new DatabaseStateStore(host.database, 3000).load(key).get(5, TimeUnit.SECONDS).payload());
            assertThrows(RuntimeException.class, () -> store.await(store.save(key, 1, "stale", UUID.randomUUID())));
            assertFalse(host.database.ready());
        }
    }
    @Test void corruptedOrMovedSnapshotsCloseAdmissionAndNeverFallBackToFiles() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "state-corruption")) {
            var store = new DatabaseStateStore(host.database, 3000);
            var key = new StateStore.Key("staff-state", "server", null);
            store.save(key, 0, "maintenance: true\n", UUID.randomUUID()).get(5, TimeUnit.SECONDS);
            host.database.transaction(connection -> {
                DungeonSql.update(connection, "UPDATE dungeon_state_document SET payload='maintenance: false' WHERE server_id=?", host.database.serverId());
                return null;
            }).get(5, TimeUnit.SECONDS);
            assertThrows(RuntimeException.class, () -> store.await(store.refresh(key)));
            assertFalse(host.database.ready());
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> store.save(key, 1, "overwrite", UUID.randomUUID()).get(5, TimeUnit.SECONDS));
        }
    }
    @Test void nativeRecoveryPayloadSurvivesARealDatabaseOwnerRestart() throws Exception {
        var key = new StateStore.Key("player-recovery-test", "receipt", null);
        try (var host = new DatabaseTestHost(MYSQL, "state-restart")) {
            new DatabaseStateStore(host.database, 3000).document(key).save("version: 2\nrecovery: pending\n");
        }
        try (var host = new DatabaseTestHost(MYSQL, "state-restart")) {
            var restored = new DatabaseStateStore(host.database, 3000).load(key).get(5, TimeUnit.SECONDS);
            assertEquals(1, restored.revision());
            assertTrue(restored.payload().contains("recovery: pending"));
        }
    }
    @Test void multiDocumentFailureRollsBackEveryRowAndNeverPublishesUncommittedCache() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "state-atomic")) {
            var store = new DatabaseStateStore(host.database, 3000);
            var ledger = new StateStore.Key("key-ledger", "atomic", null);
            var journal = new StateStore.Key("player-recovery-test", "atomic", null);
            var initial = store.saveAll(java.util.List.of(
                    new StateStore.Write(ledger, 0, "original", UUID.randomUUID()),
                    new StateStore.Write(journal, 0, "reserved", UUID.randomUUID()))).get(5, TimeUnit.SECONDS);
            assertEquals(2, initial.size());
            host.failBeforeCommit.set(true);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> store.saveAll(java.util.List.of(
                    new StateStore.Write(ledger, 1, "rotated", UUID.randomUUID()),
                    new StateStore.Write(journal, 1, "replacement", UUID.randomUUID()))).get(5, TimeUnit.SECONDS));
            assertEquals("original", store.load(ledger).get(5, TimeUnit.SECONDS).payload());
            var fresh = new DatabaseStateStore(host.database, 3000);
            assertEquals("original", fresh.load(ledger).get(5, TimeUnit.SECONDS).payload());
            assertEquals("reserved", fresh.load(journal).get(5, TimeUnit.SECONDS).payload());
            // A stale second document also rolls back a preceding valid write.
            assertThrows(java.util.concurrent.ExecutionException.class, () -> store.saveAll(java.util.List.of(
                    new StateStore.Write(ledger, 1, "rotated", UUID.randomUUID()),
                    new StateStore.Write(journal, 0, "stale", UUID.randomUUID()))).get(5, TimeUnit.SECONDS));
            assertEquals("original", fresh.refresh(ledger).get(5, TimeUnit.SECONDS).payload());
        }
    }
    @Test void lostCommitResponseCanBeRetriedWithoutRepeatingEitherDocument() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "state-ambiguous")) {
            var store = new DatabaseStateStore(host.database, 3000);
            var ledger = new StateStore.Key("key-ledger", "ambiguous", null);
            var journal = new StateStore.Key("player-recovery-test", "ambiguous", null);
            var writes = java.util.List.of(new StateStore.Write(ledger, 0, "rotated", UUID.randomUUID()),
                    new StateStore.Write(journal, 0, "replacement", UUID.randomUUID()));
            host.failAfterCommit.set(true);
            assertThrows(java.util.concurrent.ExecutionException.class, () -> store.saveAll(writes).get(5, TimeUnit.SECONDS));
            var retried = store.saveAll(writes).get(5, TimeUnit.SECONDS);
            assertEquals(java.util.List.of(new StateStore.Snapshot(1, "rotated"), new StateStore.Snapshot(1, "replacement")), retried);
            var fresh = new DatabaseStateStore(host.database, 3000);
            assertEquals(1, fresh.load(ledger).get(5, TimeUnit.SECONDS).revision());
            assertEquals("replacement", fresh.load(journal).get(5, TimeUnit.SECONDS).payload());
        }
    }
    @Test void unknownCanonicalPlayersAndDuplicateBatchKeysAreRejectedWithoutDisablingDatabase() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "state-validation")) {
            var store = new DatabaseStateStore(host.database, 3000);
            UUID player = UUID.randomUUID();
            when(host.players.findIdentity(player)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            var key = new StateStore.Key("player-profile", player.toString(), player);
            when(host.players.findActiveIdentityCached(player)).thenReturn(Optional.empty());
            assertDoesNotThrow(() -> store.prefetch(player).get(5, TimeUnit.SECONDS));
            assertThrows(IllegalArgumentException.class, () -> store.requirePlayerReady(player));
            when(host.players.findActiveIdentityCached(player)).thenReturn(Optional.of(new PlayerIdentity(999L, player, "Ready")));
            assertDoesNotThrow(() -> store.requirePlayerReady(player));
            assertThrows(IllegalArgumentException.class, () -> store.await(store.save(key, 0, "invalid", UUID.randomUUID())));
            var write = new StateStore.Write(new StateStore.Key("staff-state", "server", null), 0, "value", UUID.randomUUID());
            assertThrows(IllegalArgumentException.class, () -> store.await(store.saveAll(java.util.List.of(write, write))));
            assertTrue(host.database.ready());
            assertThrows(IllegalArgumentException.class, () -> new StateStore.Key("player-profile", UUID.randomUUID().toString(), player));
        }
    }

    @Test void deletedSnapshotsAreNotResurrectedFromTheSharedCache() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "state-deleted")) {
            var store = new DatabaseStateStore(host.database, 3000);
            var staff = new StateStore.Key("staff-state", "server", null);
            store.save(staff, 0, "old", UUID.randomUUID()).get(5, TimeUnit.SECONDS);
            host.database.transaction(connection -> {
                DungeonSql.update(connection, "DELETE FROM dungeon_state_document WHERE server_id=?", host.database.serverId());
                return null;
            }).get(5, TimeUnit.SECONDS);
            assertEquals(new StateStore.Snapshot(0, ""), store.refresh(staff).get(5, TimeUnit.SECONDS));
            assertEquals(new StateStore.Snapshot(0, ""), store.load(staff).get(5, TimeUnit.SECONDS));

            UUID player = UUID.randomUUID();
            host.database.transaction(connection -> {
                DungeonSql.update(connection, "INSERT INTO player_entity(id,uuid) VALUES(300,?)", player);
                return null;
            }).get(5, TimeUnit.SECONDS);
            when(host.players.findIdentity(player)).thenReturn(CompletableFuture.completedFuture(Optional.of(new PlayerIdentity(300L, player, "Deleted"))));
            var profile = new StateStore.Key("player-profile", player.toString(), player);
            store.save(profile, 0, "previous account", UUID.randomUUID()).get(5, TimeUnit.SECONDS);
            host.database.transaction(connection -> {
                DungeonSql.update(connection, "DELETE FROM player_entity WHERE id=300");
                return null;
            }).get(5, TimeUnit.SECONDS);
            when(host.players.findIdentity(player)).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
            store.prefetch(player).get(5, TimeUnit.SECONDS);
            assertEquals(new StateStore.Snapshot(0, ""), store.load(profile).get(5, TimeUnit.SECONDS));
        }
    }
    @Test void mismatchedNumericIdentitiesOnTheSelectedDatabaseCloseAdmissionBeforeAnyBatchWrite() throws Exception {
        UUID claimed = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            DungeonSql.update(connection, "INSERT INTO player_entity(id,uuid) VALUES(301,?)", UUID.randomUUID());
        }
        for (boolean prefetch : new boolean[] {false, true}) {
            try (var host = new DatabaseTestHost(MYSQL, "state-wrong-owner-" + prefetch)) {
                when(host.players.findIdentity(claimed)).thenReturn(CompletableFuture.completedFuture(Optional.of(new PlayerIdentity(301L, claimed, "WrongDatabase"))));
                var store = new DatabaseStateStore(host.database, 3000);
                if (prefetch) assertThrows(java.util.concurrent.ExecutionException.class,
                        () -> store.prefetch(claimed).get(5, TimeUnit.SECONDS));
                else assertThrows(java.util.concurrent.ExecutionException.class, () -> store.saveAll(java.util.List.of(
                        new StateStore.Write(new StateStore.Key("key-ledger", "wrong-owner", null), 0, "rotated", UUID.randomUUID()),
                        new StateStore.Write(new StateStore.Key("player-profile", claimed.toString(), claimed), 0, "wrong owner", UUID.randomUUID())))
                        .get(5, TimeUnit.SECONDS));
                assertFalse(host.database.ready());
                try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
                    assertTrue(DungeonSql.rows(connection, "SELECT * FROM dungeon_state_document WHERE server_id=?", host.database.serverId()).isEmpty());
                }
            }
        }
    }
    @Test void playerDeletionBetweenIdentityLookupAndWriteIsRejectedWithoutInventingAnAccount() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "state-player-deleted")) {
            UUID player = UUID.randomUUID();
            when(host.players.findIdentity(player)).thenReturn(CompletableFuture.completedFuture(Optional.of(new PlayerIdentity(302L, player, "Deleted"))));
            var store = new DatabaseStateStore(host.database, 3000);
            var key = new StateStore.Key("player-recovery", player.toString(), player);
            assertThrows(IllegalArgumentException.class, () -> store.await(store.save(key, 0, "escrow", UUID.randomUUID())));
            assertTrue(host.database.ready());
            assertEquals(new StateStore.Snapshot(0, ""), store.load(key).get(5, TimeUnit.SECONDS));
        }
    }
    @Test void aDelayedReadCannotReplaceACommittedNewerSnapshot() throws Exception {
        try (var host = new DatabaseTestHost(MYSQL, "state-delayed-read")) {
            var store = new DatabaseStateStore(host.database, 3000);
            var key = new StateStore.Key("staff-state", "server", null);
            store.save(key, 0, "first", UUID.randomUUID()).get(5, TimeUnit.SECONDS);
            CompletableFuture<StateStore.Snapshot> delayed;
            try (var gate = host.pauseAfter("SELECT player_id,revision,payload,checksum FROM dungeon_state_document")) {
                delayed = store.refresh(key);
                gate.await();
                store.save(key, 1, "second", UUID.randomUUID()).get(5, TimeUnit.SECONDS);
            }
            assertEquals(new StateStore.Snapshot(2, "second"), delayed.get(5, TimeUnit.SECONDS));
            assertEquals(new StateStore.Snapshot(2, "second"), store.load(key).get(5, TimeUnit.SECONDS));
        }
    }
    @Test void incompleteCanonicalUuidColumnsPreventBootBeforeOrmCanMutateAnything() throws Exception {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            DungeonSql.update(connection, "ALTER TABLE player_entity MODIFY uuid VARCHAR(36) NULL");
            try {
                var before = DungeonSql.rows(connection, "SHOW CREATE TABLE player_entity");
                assertThrows(java.sql.SQLException.class, () -> OrmTestSupport.initialize(MYSQL, "update"));
                assertEquals(before, DungeonSql.rows(connection, "SHOW CREATE TABLE player_entity"));
            } finally {
                DungeonSql.update(connection, "ALTER TABLE player_entity MODIFY uuid VARCHAR(36) NOT NULL");
            }
        }
    }

}
