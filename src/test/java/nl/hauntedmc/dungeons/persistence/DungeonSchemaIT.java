package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

class DungeonSchemaIT {
    @Test
    void migratesResumesValidatesAndPreservesSharedHistoryWhenAPlayerIsDeleted() throws Exception {
        try (var mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                DungeonSql.update(connection, "CREATE TABLE player_entity(id BIGINT PRIMARY KEY,uuid CHAR(36) NOT NULL UNIQUE)");
                DungeonSchema.initialize(connection, true);
                DungeonSchema.initialize(connection, true);
                DungeonSchema.initialize(connection, false);
                assertEquals(2, DungeonSql.rows(connection, "SELECT * FROM dungeon_schema_history").size());
                DungeonSql.update(connection, "UPDATE dungeon_schema_history SET completed=FALSE");
                assertThrows(SQLException.class, () -> DungeonSchema.initialize(connection, false));
                DungeonSchema.initialize(connection, true);
                var dungeon = UUID.randomUUID();
                var run = UUID.randomUUID();
                var boot = UUID.randomUUID();
                DungeonSql.update(connection, "INSERT INTO player_entity VALUES(1,?),(2,?)", UUID.randomUUID(), UUID.randomUUID());
                DungeonSql.update(connection, "INSERT INTO dungeon_server(server_id,boot_id,epoch,lease_until) VALUES('test',?,1,UTC_TIMESTAMP(3))", boot);
                DungeonSql.update(connection, "INSERT INTO dungeon_definition(dungeon_id) VALUES(?)", dungeon);
                DungeonSql.update(connection, "INSERT INTO dungeon_run(run_id,server_id,boot_id,dungeon_id,world_name,dungeon_type,"
                        + "difficulty,fingerprint,config_snapshot,status,created_at) VALUES(?,'test',?,?,'world','static','','hash','','ACTIVE',UTC_TIMESTAMP(3))", run, boot, dungeon);
                for (long player : new long[] {1, 2}) {
                    DungeonSql.update(connection, "INSERT INTO dungeon_participation(participation_id,run_id,player_id,team_size,entered_at,outcome)"
                            + " VALUES(?,?,?,2,UTC_TIMESTAMP(3),'PLAYING')", UUID.randomUUID(), run, player);
                }
                DungeonSql.update(connection, "DELETE FROM player_entity WHERE id=1");
                assertEquals(1, DungeonSql.rows(connection, "SELECT * FROM dungeon_run").size());
                assertEquals(1, DungeonSql.rows(connection, "SELECT * FROM dungeon_participation").size());
                var first = new ServerLease("lease-test", UUID.randomUUID());
                var second = new ServerLease("lease-test", UUID.randomUUID());
                first.acquire(connection);
                first.check(connection);
                assertThrows(SQLException.class, () -> second.acquire(connection));
                DungeonSql.update(connection, "UPDATE dungeon_server SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(3)) WHERE server_id='lease-test'");
                assertThrows(SQLException.class, () -> first.renew(connection));
                second.acquire(connection);
                assertThrows(SQLException.class, () -> first.check(connection));
                first.release(connection);
                second.check(connection);
                second.release(connection);
                verifyProviderLifecycle(mysql);
                DungeonSql.update(connection, "ALTER TABLE dungeon_run DROP COLUMN exclusion_reason");
                assertThrows(SQLException.class, () -> DungeonSchema.initialize(connection, false));
                DungeonSql.update(connection, "ALTER TABLE dungeon_run ADD exclusion_reason VARCHAR(256)");
                DungeonSchema.initialize(connection, false);
                DungeonSql.update(connection, "UPDATE dungeon_schema_history SET checksum=REPEAT('0',64)");
                assertThrows(SQLException.class, () -> DungeonSchema.initialize(connection, true));
            }
        }
    }
    private void verifyProviderLifecycle(MySQLContainer mysql) throws Exception {
        var source = org.mockito.Mockito.mock(javax.sql.DataSource.class);
        org.mockito.Mockito.when(source.getConnection()).thenAnswer(ignored ->
                DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
        var access = org.mockito.Mockito.mock(nl.hauntedmc.dataprovider.database.relational.RelationalDataAccess.class);
        org.mockito.Mockito.when(access.executeTransactionally(org.mockito.Mockito.any())).thenAnswer(invocation -> {
            nl.hauntedmc.dataprovider.database.relational.TransactionCallback<?> callback = invocation.getArgument(0);
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try (var connection = source.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        Object result = callback.doInTransaction(connection);
                        connection.commit();
                        return result;
                    } catch (Exception failure) {
                        connection.rollback();
                        throw failure;
                    }
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            });
        });
        var relational = org.mockito.Mockito.mock(nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider.class);
        org.mockito.Mockito.when(relational.getDataSource()).thenReturn(source);
        org.mockito.Mockito.when(relational.getDataAccess()).thenReturn(access);
        var api = org.mockito.Mockito.mock(nl.hauntedmc.dataprovider.api.DataProviderAPI.class);
        var plugin = org.mockito.Mockito.mock(nl.hauntedmc.dungeons.plugin.DungeonsPlugin.class);
        org.mockito.Mockito.when(api.forPlugin(plugin)).thenReturn(api);
        org.mockito.Mockito.when(api.registerDatabaseOrThrow(nl.hauntedmc.dataprovider.database.DatabaseType.MYSQL,
                "player_data_rw", nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider.class)).thenReturn(relational);
        var server = org.mockito.Mockito.mock(org.bukkit.Server.class);
        var services = org.mockito.Mockito.mock(org.bukkit.plugin.ServicesManager.class);
        var registration = org.mockito.Mockito.mock(org.bukkit.plugin.RegisteredServiceProvider.class);
        org.mockito.Mockito.when(registration.getProvider()).thenReturn(api);
        org.mockito.Mockito.when(services.getRegistration(nl.hauntedmc.dataprovider.api.DataProviderAPI.class)).thenReturn(registration);
        var manager = org.mockito.Mockito.mock(org.bukkit.plugin.PluginManager.class);
        var registry = org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class,
                org.mockito.Mockito.withSettings().extraInterfaces(nl.hauntedmc.dataregistry.api.DataRegistryApiProvider.class));
        var registryApi = org.mockito.Mockito.mock(nl.hauntedmc.dataregistry.api.DataRegistryApi.class);
        org.mockito.Mockito.when(((nl.hauntedmc.dataregistry.api.DataRegistryApiProvider) registry).getDataRegistry()).thenReturn(registryApi);
        org.mockito.Mockito.when(manager.getPlugin("DataRegistry")).thenReturn(registry);
        org.mockito.Mockito.when(server.getPluginManager()).thenReturn(manager);
        org.mockito.Mockito.when(server.getServicesManager()).thenReturn(services);
        org.mockito.Mockito.when(plugin.getServer()).thenReturn(server);
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("runtime.server-id", "lifecycle");
        org.mockito.Mockito.when(plugin.getConfig()).thenReturn(config);
        var database = new DungeonDatabase(plugin);
        database.start().get(15, java.util.concurrent.TimeUnit.SECONDS);
        database.start().get(15, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(database.ready());
        database.heartbeat().get(10, java.util.concurrent.TimeUnit.SECONDS);
        verifyKeyTransactions(database);
        var pending = new java.util.concurrent.CompletableFuture<java.util.List<java.util.Map<String, Object>>>();
        org.mockito.Mockito.when(access.queryForList("pending")).thenReturn(pending);
        database.read("pending").cancel(true);
        var closing = database.closeAsync();
        assertFalse(closing.isDone());
        pending.complete(java.util.List.of());
        closing.get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(DungeonDatabase.State.CLOSED, database.state());
        assertFalse(database.ready());
        org.mockito.Mockito.verify(api).unregisterDatabase(nl.hauntedmc.dataprovider.database.DatabaseType.MYSQL, "player_data_rw");
        org.mockito.Mockito.verify(api).registerDatabaseOrThrow(nl.hauntedmc.dataprovider.database.DatabaseType.MYSQL,
                "player_data_rw", nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider.class);
    }

    private void verifyKeyTransactions(DungeonDatabase database) throws Exception {
        UUID dungeon = UUID.randomUUID();
        database.transaction(connection -> {
            DungeonSql.update(connection, "INSERT INTO dungeon_definition(dungeon_id) VALUES(?)", dungeon);
            DungeonSql.update(connection, "INSERT INTO dungeon_key_definition(dungeon_id,key_id) VALUES(?,'key')", dungeon);
            return null;
        }).get(10, java.util.concurrent.TimeUnit.SECONDS);
        var ledger = new DungeonKeyLedger(database, dungeon);
        UUID token = UUID.randomUUID();
        UUID issue = UUID.randomUUID();
        assertEquals(token, ledger.issue("key", token, issue).get());
        assertEquals(token, ledger.issue("key", token, issue).get());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        var a = ledger.reserve(token, first);
        var b = ledger.reserve(token, second);
        boolean firstWon = a.get();
        boolean secondWon = b.get();
        assertNotEquals(firstWon, secondWon);
        UUID winner = firstWon ? first : second;
        UUID loser = firstWon ? second : first;
        assertFalse(ledger.release(token, loser).get());
        assertTrue(ledger.release(token, winner).get());
        UUID next = UUID.randomUUID();
        assertTrue(ledger.reserve(token, next).get());
        assertTrue(ledger.release(token, winner).get());
        UUID replacement = UUID.randomUUID();
        assertTrue(ledger.settle(token, next, replacement).get());
        assertTrue(ledger.settle(token, next, replacement).get());
        assertFalse(ledger.reserve(token, UUID.randomUUID()).get());
        UUID use = UUID.randomUUID();
        assertTrue(ledger.reserve(replacement, use).get());
        assertTrue(ledger.settle(replacement, use, null).get());
        assertTrue(ledger.settle(replacement, use, null).get());
        ledger.revoke("key", UUID.randomUUID()).get();
        assertEquals(token, ledger.issue("key", token, issue).get());
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> ledger.issue("key", UUID.randomUUID(), UUID.randomUUID()).get());
    }

}
