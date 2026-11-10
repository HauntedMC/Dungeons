package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderScope;
import nl.hauntedmc.dataprovider.core.DataProviderHandler;
import nl.hauntedmc.dataprovider.core.api.DefaultDataProviderApi;
import nl.hauntedmc.dataprovider.core.identity.PluginIdentityRegistry;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDataAccess;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.TransactionCallback;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;

import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

class DungeonSchemaIT {
    @Test
    void ormUpdatesValidatesAndPreservesSharedHistoryWhenAPlayerIsDeleted() throws Exception {
        try (var mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            try (Connection connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                DungeonSql.update(connection, "CREATE TABLE player_entity(id BIGINT AUTO_INCREMENT PRIMARY KEY,uuid VARCHAR(36) NOT NULL UNIQUE,username VARCHAR(32) NOT NULL)");
                var canonical = DungeonSql.rows(connection, "SHOW CREATE TABLE player_entity");
                OrmTestSupport.initialize(mysql, "update");
                assertEquals(canonical, DungeonSql.rows(connection, "SHOW CREATE TABLE player_entity"));
                OrmTestSupport.initialize(mysql, "update");
                OrmTestSupport.initialize(mysql, "validate");
                assertTrue(DungeonSql.rows(connection, "SHOW TABLES LIKE 'dungeon_schema_history'").isEmpty());
                var dungeon = UUID.randomUUID();
                var run = UUID.randomUUID();
                var boot = UUID.randomUUID();
                DungeonSql.update(connection, "INSERT INTO player_entity VALUES(1,?,'First'),(2,?,'Second')", UUID.randomUUID(), UUID.randomUUID());
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
                // A bounded one-connection pool must not deadlock ORM initialization.
                var poolConfig = new HikariConfig();
                poolConfig.setJdbcUrl(mysql.getJdbcUrl());
                poolConfig.setUsername(mysql.getUsername());
                poolConfig.setPassword(mysql.getPassword());
                poolConfig.setMaximumPoolSize(1);
                poolConfig.setConnectionTimeout(2000);
                try (var pool = new HikariDataSource(poolConfig)) {
                    // Exercise the real public facade, plugin binding and guarded DataSource,
                    // rather than an adapter which might accidentally honor a consumer mode.
                    var handler = Mockito.mock(DataProviderHandler.class);
                    var identity = new PluginIdentityRegistry()
                            .register("dungeons", DungeonEntities.class.getClassLoader());
                    Mockito.when(handler.issuePluginIdentity(ArgumentMatchers.any())).thenReturn(identity);
                    Mockito.when(handler.getPluginId(identity)).thenReturn("dungeons");
                    Mockito.when(handler.getConfiguredOrmSchemaMode(identity)).thenReturn("validate");
                    var backend = Mockito.mock(RelationalDatabaseProvider.class);
                    Mockito.when(backend.getDataSource()).thenReturn(pool);
                    Mockito.when(handler.registerDatabaseOrThrow(identity,
                            DatabaseType.MYSQL, "canonical_rw")).thenReturn(backend);
                    var api = new DefaultDataProviderApi(handler).forPlugin(new Object());
                    var registered = api.registerDatabaseOrThrow(DatabaseType.MYSQL,
                            "canonical_rw", RelationalDatabaseProvider.class);
                    try {
                        try (var context = DungeonSchema.open(api, registered.getDataSource(), (level, message, error) -> {})) { }
                        var beforeLegacyCall = DungeonSql.rows(connection, "SHOW CREATE TABLE player_entity");
                        try (var context = api.createOrmContext(registered.getDataSource(),
                                (level, message, error) -> {}, "create", DungeonEntities.types())) { }
                        assertEquals(beforeLegacyCall, DungeonSql.rows(connection, "SHOW CREATE TABLE player_entity"));
                        assertThrows(IllegalArgumentException.class,
                                () -> DungeonSchema.open(api, pool, (level, message, error) -> {}));
                    } finally {
                        api.unregisterDatabase(DatabaseType.MYSQL, "canonical_rw");
                    }
                    Mockito.verify(handler).unregisterDatabase(identity,
                            DatabaseType.MYSQL, "canonical_rw");
                }
                DungeonSql.update(connection, "ALTER TABLE dungeon_run DROP COLUMN exclusion_reason");
                assertThrows(RuntimeException.class, () -> OrmTestSupport.initialize(mysql, "validate"));
                DungeonSql.update(connection, "ALTER TABLE dungeon_run ADD exclusion_reason VARCHAR(256)");
                OrmTestSupport.initialize(mysql, "validate");

            }
        }
    }
    private void verifyProviderLifecycle(MySQLContainer mysql) throws Exception {
        var source = Mockito.mock(DataSource.class);
        Mockito.when(source.getConnection()).thenAnswer(ignored ->
                DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
        var access = Mockito.mock(RelationalDataAccess.class);
        Mockito.when(access.executeTransactionally(Mockito.any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return CompletableFuture.supplyAsync(() -> {
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
                    throw new CompletionException(failure);
                }
            });
        });
        var relational = Mockito.mock(RelationalDatabaseProvider.class);
        Mockito.when(relational.getDataSource()).thenReturn(source);
        Mockito.when(relational.getDataAccess()).thenReturn(access);
        var api = Mockito.mock(DataProviderAPI.class);
        OrmTestSupport.configure(api);
        var plugin = Mockito.mock(DungeonsPlugin.class);
        Mockito.when(api.forPlugin(plugin)).thenReturn(api);
        var scope = Mockito.mock(DataProviderScope.class);
        Mockito.when(api.scope("dungeons.gameplay")).thenReturn(scope);
        Mockito.when(scope.registerDatabaseOrThrow(DatabaseType.MYSQL,
                "player_data_rw", RelationalDatabaseProvider.class)).thenReturn(relational);
        var server = Mockito.mock(Server.class);
        var services = Mockito.mock(ServicesManager.class);
        var registration = Mockito.mock(RegisteredServiceProvider.class);
        Mockito.when(registration.getProvider()).thenReturn(api);
        Mockito.when(services.getRegistration(DataProviderAPI.class)).thenReturn(registration);
        var manager = Mockito.mock(PluginManager.class);
        var registry = Mockito.mock(Plugin.class,
                Mockito.withSettings().extraInterfaces(DataRegistryApiProvider.class));
        var registryApi = Mockito.mock(DataRegistryApi.class);
        Mockito.when(((DataRegistryApiProvider) registry).getDataRegistry()).thenReturn(registryApi);
        Mockito.when(manager.getPlugin("DataRegistry")).thenReturn(registry);
        Mockito.when(server.getPluginManager()).thenReturn(manager);
        Mockito.when(server.getServicesManager()).thenReturn(services);
        Mockito.when(plugin.getServer()).thenReturn(server);
        var config = new YamlConfiguration();
        config.set("runtime.server-id", "lifecycle");
        Mockito.when(plugin.getConfig()).thenReturn(config);
        var database = new DungeonDatabase(plugin);
        database.start().get(15, TimeUnit.SECONDS);
        database.start().get(15, TimeUnit.SECONDS);
        assertTrue(database.ready());
        database.heartbeat().get(10, TimeUnit.SECONDS);
        verifyKeyTransactions(database);
        var pending = new CompletableFuture<List<Map<String, Object>>>();
        Mockito.when(access.queryForList("pending")).thenReturn(pending);
        database.read("pending").cancel(true);
        var closing = database.closeAsync();
        assertFalse(closing.isDone());
        pending.complete(List.of());
        closing.get(10, TimeUnit.SECONDS);
        assertEquals(DungeonDatabase.State.CLOSED, database.state());
        assertFalse(database.ready());
        Mockito.verify(scope).close();
        Mockito.verify(scope).registerDatabaseOrThrow(DatabaseType.MYSQL,
                "player_data_rw", RelationalDatabaseProvider.class);
    }

    private void verifyKeyTransactions(DungeonDatabase database) throws Exception {
        UUID dungeon = UUID.randomUUID();
        database.transaction(connection -> {
            DungeonSql.update(connection, "INSERT INTO dungeon_definition(dungeon_id) VALUES(?)", dungeon);
            DungeonSql.update(connection, "INSERT INTO dungeon_key_definition(dungeon_id,key_id) VALUES(?,'key')", dungeon);
            return null;
        }).get(10, TimeUnit.SECONDS);
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
        assertThrows(ExecutionException.class,
                () -> ledger.issue("key", UUID.randomUUID(), UUID.randomUUID()).get());
    }

}
