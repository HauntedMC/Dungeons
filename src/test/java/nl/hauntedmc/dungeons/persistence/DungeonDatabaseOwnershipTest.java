package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDataAccess;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class DungeonDatabaseOwnershipTest {
    static final class Fixture {
        final DungeonsPlugin plugin = mock(DungeonsPlugin.class);
        final DataProviderAPI api = mock(DataProviderAPI.class);
        final DataSource source = mock(DataSource.class);
        final RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        final RelationalDataAccess access = mock(RelationalDataAccess.class);
        final ORMContext orm = mock(ORMContext.class);
        final YamlConfiguration config = new YamlConfiguration();

        @SuppressWarnings("unchecked") Fixture() throws Exception {
            var server = mock(Server.class);
            var services = mock(ServicesManager.class);
            RegisteredServiceProvider<DataProviderAPI> registration = mock(RegisteredServiceProvider.class);
            when(registration.getProvider()).thenReturn(api);
            when(services.getRegistration(DataProviderAPI.class)).thenReturn(registration);
            var plugins = mock(PluginManager.class);
            var registry = mock(Plugin.class, withSettings().extraInterfaces(DataRegistryApiProvider.class));
            var registryApi = mock(DataRegistryApi.class);
            when(((DataRegistryApiProvider) registry).getDataRegistry()).thenReturn(registryApi);
            when(plugins.getPlugin("DataRegistry")).thenReturn(registry);
            when(server.getServicesManager()).thenReturn(services);
            when(server.getPluginManager()).thenReturn(plugins);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getConfig()).thenReturn(config);
            config.set("runtime.server-id", "test-backend");
            config.set("database.connection-id", "custom_rw");
            when(api.forPlugin(plugin)).thenReturn(api);
            when(api.registerDatabaseOrThrow(DatabaseType.MYSQL, "custom_rw", RelationalDatabaseProvider.class)).thenReturn(provider);
            when(provider.getDataSource()).thenReturn(source);
            when(provider.getDataAccess()).thenReturn(access);
            when(access.executeTransactionally(any())).thenReturn(CompletableFuture.completedFuture(null));
            var connection = mock(Connection.class);
            var statement = mock(Statement.class);
            var result = mock(ResultSet.class);
            var metadata = mock(ResultSetMetaData.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(result);
            when(result.getMetaData()).thenReturn(metadata);
            when(metadata.getColumnType(1)).thenReturn(Types.BIGINT);
            when(metadata.getColumnType(2)).thenReturn(Types.VARCHAR);
            when(metadata.getPrecision(2)).thenReturn(36);
            when(metadata.isNullable(2)).thenReturn(ResultSetMetaData.columnNoNulls);
            when(metadata.isSigned(1)).thenReturn(true);
            when(metadata.isNullable(1)).thenReturn(ResultSetMetaData.columnNoNulls);
            when(api.createConfiguredOrmContext(eq(source), any(), any(Class[].class))).thenReturn(orm);
        }
    }

    @Test void failedStartupReleasesRegistrationWithoutWaitingForPluginDisable() throws Exception {
        var f = new Fixture();
        when(f.source.getConnection()).thenThrow(new SQLException("database offline"));
        var database = new DungeonDatabase(f.plugin);
        assertThrows(ExecutionException.class, () -> database.start().get(2, TimeUnit.SECONDS));
        assertEquals(DungeonDatabase.State.UNAVAILABLE, database.state());
        assertEquals("database offline", database.problem());
        database.closeAsync().get(2, TimeUnit.SECONDS);
        verify(f.api).unregisterDatabase(DatabaseType.MYSQL, "custom_rw");
        verify(f.api, never()).createConfiguredOrmContext(any(), any(), any(Class[].class));
    }

    @Test void configuredFactoryAndOrmBeforeProviderReleaseMatchThePublicOwnershipContract() throws Exception {
        var f = new Fixture();
        var database = new DungeonDatabase(f.plugin);
        database.start().get(2, TimeUnit.SECONDS);
        assertTrue(database.ready());
        database.closeAsync().get(2, TimeUnit.SECONDS);
        var order = inOrder(f.orm, f.api);
        order.verify(f.orm).close();
        order.verify(f.api).unregisterDatabase(DatabaseType.MYSQL, "custom_rw");
        verify(f.api, never()).createOrmContext(any(), any(), anyString(), any(Class[].class));
    }

    @Test void closeDuringRegistrationPreventsLateSchemaInitializationAndStillReleasesTheHandle() throws Exception {
        var f = new Fixture();
        var registered = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        when(f.api.registerDatabaseOrThrow(DatabaseType.MYSQL, "custom_rw", RelationalDatabaseProvider.class))
                .thenAnswer(call -> { registered.countDown(); assertTrue(proceed.await(2, TimeUnit.SECONDS)); return f.provider; });
        var database = new DungeonDatabase(f.plugin);
        database.start();
        assertTrue(registered.await(2, TimeUnit.SECONDS));
        var closing = database.closeAsync();
        assertFalse(closing.isDone());
        proceed.countDown();
        closing.get(2, TimeUnit.SECONDS);
        verify(f.api, never()).createConfiguredOrmContext(any(), any(), any(Class[].class));
        verify(f.api).unregisterDatabase(DatabaseType.MYSQL, "custom_rw");
        assertFalse(database.ready());
    }

    @Test void everyCleanupStageRunsAndRetainsFailuresEvenWhenLeaseAndOrmReleaseFail() throws Exception {
        var f = new Fixture();
        var database = new DungeonDatabase(f.plugin);
        database.start().get(2, TimeUnit.SECONDS);
        when(f.access.executeTransactionally(any())).thenReturn(CompletableFuture.failedFuture(new SQLException("lease release")));
        doThrow(new IllegalStateException("orm close")).when(f.orm).close();
        doThrow(new IllegalStateException("provider close")).when(f.api).unregisterDatabase(DatabaseType.MYSQL, "custom_rw");
        var failure = assertThrows(ExecutionException.class, () -> database.closeAsync().get(2, TimeUnit.SECONDS));
        assertEquals(2, failure.getCause().getSuppressed().length);
        verify(f.orm).close();
        verify(f.api).unregisterDatabase(DatabaseType.MYSQL, "custom_rw");
        assertEquals(DungeonDatabase.State.CLOSED, database.state());
    }

    @Test void incompatibleDependencyCannotLeaveStartupPermanentlyPending() throws Exception {
        var f = new Fixture();
        when(f.api.createConfiguredOrmContext(eq(f.source), any(), any(Class[].class)))
                .thenThrow(new NoClassDefFoundError("incompatible ORM dependency"));
        var database = new DungeonDatabase(f.plugin);
        assertThrows(ExecutionException.class, () -> database.start().get(2, TimeUnit.SECONDS));
        assertEquals(DungeonDatabase.State.UNAVAILABLE, database.state());
        database.closeAsync().get(2, TimeUnit.SECONDS);
        verify(f.api).unregisterDatabase(DatabaseType.MYSQL, "custom_rw");
    }
}
