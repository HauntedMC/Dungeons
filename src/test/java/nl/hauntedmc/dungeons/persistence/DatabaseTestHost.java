package nl.hauntedmc.dungeons.persistence;

import static org.mockito.Mockito.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDataAccess;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.TransactionCallback;
import nl.hauntedmc.dataregistry.api.DataRegistryApi;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.testcontainers.mysql.MySQLContainer;

/** Public-provider adapter with real MySQL transactions and deterministic scheduling gates. */
final class DatabaseTestHost implements AutoCloseable {
    static final class QueryGate implements AutoCloseable {
        final String prefix;
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);
        QueryGate(String prefix) { this.prefix = prefix; }
        void await() throws InterruptedException {
            if (!reached.await(5, TimeUnit.SECONDS)) throw new AssertionError("Query gate was not reached: " + prefix);
        }
        @Override public void close() { proceed.countDown(); }
    }

    final DungeonDatabase database;
    final PlayerData players = mock(PlayerData.class);
    final AtomicInteger transactions = new AtomicInteger();
    final AtomicReference<CompletableFuture<Object>> deferred = new AtomicReference<>();
    private final AtomicReference<QueryGate> gate = new AtomicReference<>();
    private final java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(4);

    @SuppressWarnings("unchecked")
    DatabaseTestHost(MySQLContainer mysql, String name) throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenAnswer(ignored ->
                DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
        RelationalDataAccess access = mock(RelationalDataAccess.class);
        when(access.executeTransactionally(any())).thenAnswer(invocation -> {
            transactions.incrementAndGet();
            CompletableFuture<Object> delayed = deferred.getAndSet(null);
            if (delayed != null) return delayed;
            TransactionCallback<?> callback = invocation.getArgument(0);
            return CompletableFuture.supplyAsync(() -> {
                try (Connection connection = source.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        Object result = callback.doInTransaction(observed(connection));
                        connection.commit();
                        return result;
                    } catch (Exception failure) {
                        connection.rollback();
                        throw failure;
                    }
                } catch (Exception failure) {
                    throw new java.util.concurrent.CompletionException(failure);
                }
            }, workers);
        });
        var relational = mock(RelationalDatabaseProvider.class);
        when(relational.getDataSource()).thenReturn(source);
        when(relational.getDataAccess()).thenReturn(access);
        var api = mock(DataProviderAPI.class);
        OrmTestSupport.configure(api);
        var plugin = mock(DungeonsPlugin.class);
        when(api.forPlugin(plugin)).thenReturn(api);
        when(api.registerDatabaseOrThrow(DatabaseType.MYSQL, "player_data_rw", RelationalDatabaseProvider.class)).thenReturn(relational);
        var server = mock(Server.class);
        var services = mock(ServicesManager.class);
        RegisteredServiceProvider<DataProviderAPI> registration = mock(RegisteredServiceProvider.class);
        when(registration.getProvider()).thenReturn(api);
        when(services.getRegistration(DataProviderAPI.class)).thenReturn(registration);
        var manager = mock(PluginManager.class);
        var registry = mock(Plugin.class, withSettings().extraInterfaces(DataRegistryApiProvider.class));
        var registryApi = mock(DataRegistryApi.class);
        when(registryApi.players()).thenReturn(players);
        when(((DataRegistryApiProvider) registry).getDataRegistry()).thenReturn(registryApi);
        when(manager.getPlugin("DataRegistry")).thenReturn(registry);
        when(server.getPluginManager()).thenReturn(manager);
        when(server.getServicesManager()).thenReturn(services);
        when(plugin.getServer()).thenReturn(server);
        var config = new YamlConfiguration();
        config.set("runtime.server-id", name);
        when(plugin.getConfig()).thenReturn(config);
        database = new DungeonDatabase(plugin);
        try { database.start().get(15, TimeUnit.SECONDS); }
        catch (Exception failure) {
            try { close(); }
            catch (Exception cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    QueryGate pauseAfter(String prefix) {
        var next = new QueryGate(prefix);
        if (!gate.compareAndSet(null, next)) throw new IllegalStateException("A query gate is already installed");
        return next;
    }

    private Connection observed(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(connection, args);
                        if (!method.getName().equals("prepareStatement")) return result;
                        String sql = (String) args[0];
                        PreparedStatement statement = (PreparedStatement) result;
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[] {PreparedStatement.class},
                                (ignored, operation, parameters) -> {
                                    try {
                                        Object value = operation.invoke(statement, parameters);
                                        QueryGate current = gate.get();
                                        if (operation.getName().equals("executeQuery") && current != null
                                                && sql.startsWith(current.prefix) && gate.compareAndSet(current, null)) {
                                            current.reached.countDown();
                                            if (!current.proceed.await(10, TimeUnit.SECONDS)) throw new AssertionError("Query gate timed out");
                                        }
                                        return value;
                                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                                });
                    } catch (InvocationTargetException failure) { throw failure.getCause(); }
                });
    }

    @Override public void close() throws Exception {
        try {
            QueryGate current = gate.getAndSet(null);
            if (current != null) current.close();
            database.closeAsync().get(15, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
