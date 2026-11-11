package nl.hauntedmc.dungeons.persistence;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.DataProviderScope;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.database.relational.TransactionCallback;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.dataregistry.api.DataRegistryApiProvider;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;

/** Owns one plugin lifecycle's provider registration, schema and fenced server identity. */
public final class DungeonDatabase implements AutoCloseable {
    public enum State {
        STARTING,
        READY,
        UNAVAILABLE,
        CLOSED
    }

    private final DataProviderAPI api;
    private final PlayerData players;
    private final String connectionId;
    private final String serverId;
    private final UUID bootId = UUID.randomUUID();
    private final ServerLease lease;
    private final LoggerAdapter ormLogger;
    private volatile ORMContext orm;
    private final ThreadPoolExecutor initializer;
    private volatile State state = State.STARTING;
    private volatile String problem = "Initializing database";
    private volatile RelationalDatabaseProvider provider;
    private volatile DataProviderScope scope;
    private volatile long leaseDeadline;
    private volatile Consumer<Throwable> failureHandler = ignored -> {};
    private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
    private CompletableFuture<Void> startup;
    private CompletableFuture<Void> closing;
    private CompletableFuture<Void> renewal;
    private volatile boolean leaseAcquired;

    public DungeonDatabase(DungeonsPlugin plugin) {
        var settings = DatabaseSettings.read(plugin.getConfig());
        var registration =
                plugin.getServer().getServicesManager().getRegistration(DataProviderAPI.class);
        if (registration == null)
            throw new IllegalStateException("DataProvider API is unavailable");
        this.api = registration.getProvider().forPlugin(plugin);
        var dependency = plugin.getServer().getPluginManager().getPlugin("DataRegistry");
        if (!(dependency instanceof DataRegistryApiProvider registry)) {
            throw new IllegalStateException("DataRegistry public API is unavailable");
        }
        this.players = registry.getDataRegistry().players();
        String configured = plugin.getConfig().getString("runtime.server-id", "").trim();
        String published =
                registry.getRuntimeIdentity().map(identity -> identity.serviceName()).orElse("");
        if (!configured.isEmpty() && !published.isEmpty() && !configured.equals(published)) {
            throw new IllegalArgumentException(
                    "runtime.server-id differs from DataRegistry backend identity");
        }
        this.serverId = published.isEmpty() ? configured : published;
        if (!this.serverId.matches("[A-Za-z0-9._:-]{1,96}")) {
            throw new IllegalArgumentException(
                    "Set a stable runtime.server-id or DataRegistry backend service-name");
        }
        this.lease = new ServerLease(serverId, bootId);
        this.connectionId = settings.connectionId();
        this.ormLogger =
                (level, message, failure) ->
                        plugin.getLogger()
                                .log(
                                        switch (level) {
                                            case INFO -> Level.INFO;
                                            case WARN -> Level.WARNING;
                                            case ERROR -> Level.SEVERE;
                                        },
                                        message,
                                        failure);
        this.initializer =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(8),
                        task -> {
                            var thread = new Thread(task, "Dungeons-database-lifecycle");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
    }

    public synchronized CompletableFuture<Void> start() {
        if (state == State.CLOSED)
            return CompletableFuture.failedFuture(new IllegalStateException("Database is closed"));
        if (startup != null) return startup.copy();
        startup =
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                this.scope = api.scope("dungeons.gameplay");
                                this.provider =
                                        scope.registerDatabaseOrThrow(
                                                DatabaseType.MYSQL,
                                                connectionId,
                                                RelationalDatabaseProvider.class);
                                if (state == State.CLOSED) return;
                                this.orm =
                                        DungeonSchema.open(
                                                api, provider.getDataSource(), ormLogger);
                                if (state == State.CLOSED) return;
                                // Lease acquisition is on the lifecycle worker, never the server
                                // thread.
                                long requestedAt = System.nanoTime();
                                provider.getDataAccess()
                                        .executeTransactionally(
                                                connection -> {
                                                    lease.acquire(connection);
                                                    return null;
                                                })
                                        .join();
                                leaseAcquired = true;
                                synchronized (this) {
                                    if (state == State.CLOSED) return;
                                    leaseDeadline = requestedAt + TimeUnit.SECONDS.toNanos(15);
                                    if (System.nanoTime() - leaseDeadline >= 0)
                                        throw new IllegalStateException(
                                                "Lease acquisition exceeded its safety deadline");
                                    state = State.READY;
                                    problem = "";
                                }
                            } catch (Throwable failure) {
                                fail(failure);
                                throw new CompletionException(failure);
                            }
                        },
                        initializer);
        return startup.copy();
    }

    public void unavailable(Throwable failure) {
        fail(Objects.requireNonNull(failure));
    }

    public void onFailure(Consumer<Throwable> handler) {
        this.failureHandler = handler;
    }

    public State state() {
        return state;
    }

    public String problem() {
        return problem;
    }

    public String serverId() {
        return serverId;
    }

    public UUID bootId() {
        return bootId;
    }

    public PlayerData players() {
        return players;
    }

    public boolean ready() {
        return state == State.READY && System.nanoTime() - leaseDeadline < 0;
    }

    public synchronized <T> CompletableFuture<T> transaction(TransactionCallback<T> action) {
        if (!ready())
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Database is not ready: " + problem));
        try {
            return track(
                    provider.getDataAccess()
                            .executeTransactionally(
                                    connection -> {
                                        lease.check(connection);
                                        return action.doInTransaction(connection);
                                    }));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    public synchronized CompletableFuture<List<Map<String, Object>>> read(
            String sql, Object... values) {
        if (!ready())
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Database unavailable"));
        try {
            return track(provider.getDataAccess().queryForList(sql, values));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private <T> CompletableFuture<T> track(CompletableFuture<T> operation) {
        pending.add(operation);
        return operation
                .whenComplete(
                        (ignored, failure) -> {
                            pending.remove(operation);
                            var seen =
                                    Collections.newSetFromMap(
                                            new IdentityHashMap<Throwable, Boolean>());
                            for (Throwable cause = failure;
                                    cause != null && seen.add(cause);
                                    cause = cause.getCause()) {
                                if (cause instanceof ServerLease.Lost) {
                                    fail(failure);
                                    break;
                                }
                            }
                        })
                .copy();
    }

    public synchronized CompletableFuture<Void> heartbeat() {
        if (renewal != null && !renewal.isDone()) return renewal.copy();
        long requestedAt = System.nanoTime();
        renewal =
                transaction(
                                connection -> {
                                    lease.renew(connection);
                                    return (Void) null;
                                })
                        .whenComplete(
                                (ignored, failure) -> {
                                    if (failure != null) fail(failure);
                                    else {
                                        synchronized (this) {
                                            if (state != State.READY) return;
                                            leaseDeadline =
                                                    requestedAt + TimeUnit.SECONDS.toNanos(15);
                                        }
                                        if (System.nanoTime() - leaseDeadline >= 0)
                                            fail(
                                                    new IllegalStateException(
                                                            "Lease renewal exceeded its safety deadline"));
                                    }
                                });
        // Cancellation of an observer cannot suppress lease renewal or failure processing.
        return renewal.copy();
    }

    private void fail(Throwable failure) {
        synchronized (this) {
            if (state == State.CLOSED || state == State.UNAVAILABLE) return;
            state = State.UNAVAILABLE;
            Throwable detail = failure;
            while ((detail instanceof CompletionException || detail instanceof ExecutionException)
                    && detail.getCause() != null) detail = detail.getCause();
            problem =
                    detail.getMessage() == null
                            ? detail.getClass().getSimpleName()
                            : detail.getMessage();
            cleanupAsync();
        }
        try {
            failureHandler.accept(failure);
        } catch (RuntimeException observerFailure) {
            if (observerFailure != failure) failure.addSuppressed(observerFailure);
        }
    }

    @Override
    public void close() {
        closeAsync();
    }

    public synchronized CompletableFuture<Void> closeAsync() {
        state = State.CLOSED;
        return cleanupAsync().copy();
    }

    private synchronized CompletableFuture<Void> cleanupAsync() {
        if (closing != null) return closing;
        var admitted = CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
        closing =
                CompletableFuture.runAsync(
                        () -> {
                            // Drain on the lifecycle worker. No database worker waits for a Bukkit
                            // callback.
                            admitted.handle((ignored, failure) -> null).join();
                            Throwable failure = null;
                            try {
                                if (leaseAcquired)
                                    provider.getDataAccess()
                                            .executeTransactionally(
                                                    connection -> {
                                                        lease.release(connection);
                                                        return null;
                                                    })
                                            .join();
                            } catch (Throwable releaseFailure) {
                                failure = releaseFailure;
                            }
                            try {
                                if (orm != null) orm.close();
                            } catch (Throwable ormFailure) {
                                failure = append(failure, ormFailure);
                            }
                            try {
                                if (scope != null) scope.close();
                            } catch (Throwable unregisterFailure) {
                                failure = append(failure, unregisterFailure);
                            }
                            if (failure != null) throw new CompletionException(failure);
                        },
                        initializer);
        initializer.shutdown();
        return closing;
    }

    private static Throwable append(Throwable primary, Throwable next) {
        if (primary == null) return next;
        if (primary != next) primary.addSuppressed(next);
        return primary;
    }
}
