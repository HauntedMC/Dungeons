package nl.hauntedmc.dungeons.persistence;

import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;

import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Mandatory database startup barrier and bounded shutdown. */
public final class DatabaseLifecycle implements AutoCloseable {
    private final DungeonsPlugin plugin;
    private final DungeonDatabase database;
    private final int shutdownTimeoutSeconds;
    private final AtomicBoolean closed = new AtomicBoolean();
    private BukkitTask heartbeat;
    private CompletableFuture<Void> startup;

    public DatabaseLifecycle(DungeonsPlugin plugin) {
        this(plugin, connect(plugin), DatabaseSettings.read(plugin.getConfig()).shutdownTimeoutSeconds());
    }

    DatabaseLifecycle(DungeonsPlugin plugin, DungeonDatabase database, int shutdownTimeoutSeconds) {
        this.plugin = plugin;
        this.database = database;
        this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
        database.onFailure(this::failed);
        try {
            heartbeat = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                if (!closed.get() && database.state() == DungeonDatabase.State.READY) database.heartbeat();
            }, 100L, 100L);
            startup = database.start();
            startup.thenRun(() -> {
                if (!closed.get() && database.ready()) plugin.getSLF4JLogger().info("Dungeons database ready for {}.", database.serverId());
            });
        } catch (RuntimeException | Error failure) {
            closed.set(true);
            try { if (heartbeat != null) heartbeat.cancel(); }
            catch (RuntimeException cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            try { database.closeAsync(); }
            catch (RuntimeException cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public static void requireDependencies(DungeonsPlugin plugin) {
        for (String dependency : List.of("DataProvider", "DataRegistry")) {
            if (!plugin.getServer().getPluginManager().isPluginEnabled(dependency))
                throw new IllegalStateException("Dungeons requires the enabled " + dependency + " plugin");
        }
    }

    private static DungeonDatabase connect(DungeonsPlugin plugin) {
        requireDependencies(plugin);
        return new DungeonDatabase(plugin);
    }

    private void failed(Throwable failure) {
        if (closed.get()) return;
        plugin.getSLF4JLogger().error("Dungeons database unavailable; disabling the plugin. Check DataProvider and database settings.", failure);
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!closed.get()) plugin.getServer().getPluginManager().disablePlugin(plugin);
            });
        } catch (RuntimeException rejected) {
            plugin.getSLF4JLogger().error("Could not schedule database failure shutdown; database admission remains closed.", rejected);
        }
    }

    public DungeonDatabase database() { return database; }

    public void awaitReady(int timeoutSeconds) {
        try {
            startup.get(timeoutSeconds, TimeUnit.SECONDS);
            if (!ready()) { close(); throw new IllegalStateException("Database startup did not become ready"); }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            close();
            throw new IllegalStateException("Interrupted while starting Dungeons database", failure);
        } catch (ExecutionException | TimeoutException failure) {
            close();
            throw new IllegalStateException("Dungeons cannot start without a valid, available database", failure);
        }
    }

    public DungeonDatabase.State state() { return database.state(); }
    public boolean ready() { return !closed.get() && database.ready(); }
    public String problem() { return database.problem(); }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try { if (heartbeat != null) heartbeat.cancel(); }
        catch (RuntimeException failure) {
            plugin.getSLF4JLogger().error("Could not cancel database heartbeat; continuing database cleanup.", failure);
        }
        try {
            database.closeAsync().get(shutdownTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            plugin.getSLF4JLogger().error("Interrupted while closing Dungeons database.", interrupted);
        } catch (TimeoutException timeout) {
            plugin.getSLF4JLogger().error("Database shutdown exceeded {} seconds; cleanup continues on its worker.", shutdownTimeoutSeconds);
        } catch (ExecutionException failure) {
            plugin.getSLF4JLogger().error("Database cleanup failed after attempting ORM and provider release.", failure.getCause());
        }
    }
}
