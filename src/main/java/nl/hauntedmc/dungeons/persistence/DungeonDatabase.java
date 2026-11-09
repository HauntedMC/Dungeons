package nl.hauntedmc.dungeons.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.TransactionCallback;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;

/** Owns one plugin lifecycle's provider registration, schema and fenced server identity. */
public final class DungeonDatabase implements AutoCloseable {
    public enum State { STARTING, READY, UNAVAILABLE, CLOSED }

    private final DataProviderAPI api;
    private final PlayerData players;
    private final String connectionId;
    private final String serverId;
    private final UUID bootId = UUID.randomUUID();
    private final ServerLease lease;
    private final boolean migrate;
    private final ThreadPoolExecutor initializer;
    private volatile State state = State.STARTING;
    private volatile String problem = "Initializing database";
    private volatile RelationalDatabaseProvider provider;
    private volatile long leaseDeadline;
    private volatile Consumer<Throwable> failureHandler = ignored -> {};
    private final java.util.Set<CompletableFuture<?>> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private CompletableFuture<Void> startup;
    private CompletableFuture<Void> closing;
    private CompletableFuture<Void> renewal;
    private volatile boolean leaseAcquired;

    public DungeonDatabase(DungeonsPlugin plugin) {
        var registration = plugin.getServer().getServicesManager().getRegistration(DataProviderAPI.class);
        if (registration == null) throw new IllegalStateException("DataProvider API is unavailable");
        this.api = registration.getProvider().forPlugin(plugin);
        var dependency = plugin.getServer().getPluginManager().getPlugin("DataRegistry");
        if (!(dependency instanceof DataRegistryApiProvider registry)) {
            throw new IllegalStateException("DataRegistry public API is unavailable");
        }
        this.players = registry.getDataRegistry().players();
        String configured = plugin.getConfig().getString("runtime.server-id", "").trim();
        String published = registry.getRuntimeIdentity().map(identity -> identity.serviceName()).orElse("");
        if (!configured.isEmpty() && !published.isEmpty() && !configured.equals(published)) {
            throw new IllegalArgumentException("runtime.server-id differs from DataRegistry backend identity");
        }
        this.serverId = published.isEmpty() ? configured : published;
        if (!this.serverId.matches("[A-Za-z0-9._:-]{1,96}")) {
            throw new IllegalArgumentException("Set a stable runtime.server-id or DataRegistry backend service-name");
        }
        this.lease = new ServerLease(serverId, bootId);
        this.connectionId = plugin.getConfig().getString("database.connection-id", "player_data_rw");
        String schemaMode = plugin.getConfig().getString("database.schema-mode", "migrate");
        if (!List.of("migrate", "validate").contains(schemaMode)) {
            throw new IllegalArgumentException("database.schema-mode must be migrate or validate");
        }
        this.migrate = schemaMode.equals("migrate");
        this.initializer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(8), task -> {
                    var thread = new Thread(task, "Dungeons-database-lifecycle");
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized CompletableFuture<Void> start() {
        if (state == State.CLOSED) return CompletableFuture.failedFuture(new IllegalStateException("Database is closed"));
        if (startup != null) return startup.copy();
        startup = CompletableFuture.runAsync(() -> {
            try {
                this.provider = api.registerDatabaseOrThrow(DatabaseType.MYSQL, connectionId, RelationalDatabaseProvider.class);
                try (var connection = provider.getDataSource().getConnection()) {
                    connection.setAutoCommit(true);
                    DungeonSchema.initialize(connection, migrate);
                }
                // Lease acquisition is on the lifecycle worker, never the server thread.
                long requestedAt = System.nanoTime();
                provider.getDataAccess().executeTransactionally(connection -> {
                    lease.acquire(connection);
                    return null;
                }).join();
                leaseAcquired = true;
                synchronized (this) {
                    if (state == State.CLOSED) return;
                    leaseDeadline = requestedAt + TimeUnit.SECONDS.toNanos(15);
                    if (System.nanoTime() - leaseDeadline >= 0) throw new IllegalStateException("Lease acquisition exceeded its safety deadline");
                    state = State.READY;
                    problem = "";
                }
            } catch (RuntimeException | java.sql.SQLException | java.io.IOException failure) {
                fail(failure);
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, initializer);
        return startup.copy();
    }

    public void onFailure(Consumer<Throwable> handler) { this.failureHandler = handler; }
    public State state() { return state; }
    public String problem() { return problem; }
    public String serverId() { return serverId; }
    public UUID bootId() { return bootId; }
    public PlayerData players() { return players; }

    public boolean ready() {
        return state == State.READY && System.nanoTime() - leaseDeadline < 0;
    }

    public synchronized <T> CompletableFuture<T> transaction(TransactionCallback<T> action) {
        if (!ready()) return CompletableFuture.failedFuture(new IllegalStateException("Database is not ready: " + problem));
        try {
            return track(provider.getDataAccess().executeTransactionally(connection -> {
                lease.check(connection);
                return action.doInTransaction(connection);
            }));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    public synchronized CompletableFuture<List<Map<String, Object>>> read(String sql, Object... values) {
        if (!ready()) return CompletableFuture.failedFuture(new IllegalStateException("Database unavailable"));
        try {
            return track(provider.getDataAccess().queryForList(sql, values));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> operation) {
        pending.add(operation);
        operation.whenComplete((ignored, failure) -> {
            pending.remove(operation);
            var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
            for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
                if (cause instanceof ServerLease.Lost) { fail(failure); break; }
            }
        });
        return operation.copy();
    }

    public synchronized CompletableFuture<Void> heartbeat() {
        if (renewal != null && !renewal.isDone()) return renewal.copy();
        long requestedAt = System.nanoTime();
        renewal = transaction(connection -> {
            lease.renew(connection);
            return (Void) null;
        }).whenComplete((ignored, failure) -> {
            if (failure != null) fail(failure);
            else {
                synchronized (this) {
                    if (state != State.READY) return;
                    leaseDeadline = requestedAt + TimeUnit.SECONDS.toNanos(15);
                }
                if (System.nanoTime() - leaseDeadline >= 0)
                    fail(new IllegalStateException("Lease renewal exceeded its safety deadline"));
            }
        });
        // Cancellation of an observer cannot suppress lease renewal or failure processing.
        return renewal.copy();
    }

    private void fail(Throwable failure) {
        synchronized (this) {
            if (state == State.CLOSED || state == State.UNAVAILABLE) return;
            state = State.UNAVAILABLE;
            problem = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        }
        try { failureHandler.accept(failure); }
        catch (RuntimeException observerFailure) {
            if (observerFailure != failure) failure.addSuppressed(observerFailure);
        }
    }

    @Override
    public void close() {
        closeAsync();
    }

    public synchronized CompletableFuture<Void> closeAsync() {
        if (closing != null) return closing.copy();
        state = State.CLOSED;
        var admitted = CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
        closing = CompletableFuture.runAsync(() -> {
            // Drain admitted database work only. No worker waits for a Bukkit callback.
            admitted.handle((ignored, failure) -> null).join();
            try {
                if (leaseAcquired) provider.getDataAccess().executeTransactionally(connection -> {
                    lease.release(connection);
                    return null;
                }).join();
            } finally {
                if (provider != null) api.unregisterDatabase(DatabaseType.MYSQL, connectionId);
            }
        }, initializer);
        initializer.shutdown();
        return closing.copy();
    }
}
