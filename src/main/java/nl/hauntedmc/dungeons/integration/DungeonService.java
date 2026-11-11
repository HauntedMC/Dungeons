package nl.hauntedmc.dungeons.integration;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import nl.hauntedmc.dungeons.api.DungeonsApi;
import nl.hauntedmc.dungeons.persistence.DatabaseLifecycle;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.featureframework.lifecycle.CleanupSequence;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/** Publishes snapshots for peer plugins without sharing mutable dungeon or database objects. */
public final class DungeonService implements DungeonsApi, AutoCloseable {
    private final DungeonsRuntime runtime;
    private final String serverId;
    private final DatabaseLifecycle database;
    private final DungeonPrivacy privacy;
    private volatile List<Dungeon> catalog = List.of();
    private volatile boolean closed;
    private boolean started;
    private BukkitTask refreshTask;

    public DungeonService(DungeonsRuntime runtime) {
        this.runtime = runtime;
        this.database = runtime.database();
        this.serverId = database.database().serverId();
        this.privacy =
                new DungeonPrivacy(
                        database.database().players(),
                        this::ready,
                        (viewer, target) -> {
                            var server = runtime.environment().plugin().getServer();
                            if (!server.getPluginManager().isPluginEnabled("ServerFeatures"))
                                return CompletableFuture.completedFuture(Disclosure.UNAVAILABLE);
                            return ServerFeaturesPrivacy.friends(server, viewer, target);
                        });
    }

    /** Publishes this service once; its refresh task shares the service's lifetime. */
    public void start() {
        if (closed) throw new IllegalStateException("Dungeon service is closed");
        if (started) return;
        started = true;
        runtime.resources().ownIngress(this, DungeonService::close);
        var plugin = runtime.environment().plugin();
        try {
            refresh();
            plugin.getServer()
                    .getServicesManager()
                    .register(DungeonsApi.class, this, plugin, ServicePriority.Normal);
            refreshTask =
                    plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    public void refresh() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Dungeon snapshots require the server thread");
        if (closed) return;
        catalog =
                runtime.dungeonCatalog().getLoadedDungeons().stream()
                        .map(
                                dungeon ->
                                        new Dungeon(
                                                dungeon.getDungeonId(),
                                                dungeon.getWorldName(),
                                                dungeon.getDisplayName(),
                                                dungeon.getClass()
                                                        .getSimpleName()
                                                        .replace("Dungeon", "")
                                                        .toLowerCase(Locale.ROOT),
                                                dungeon.isEnabled(),
                                                dungeon.getInstances().size()))
                        .toList();
    }

    @Override
    public String serverId() {
        return serverId;
    }

    @Override
    public boolean ready() {
        return !closed && database.ready();
    }

    @Override
    public List<Dungeon> catalog() {
        return ready() ? catalog : List.of();
    }

    @Override
    public CompletionStage<Disclosure> canViewPlayerData(UUID viewer, UUID target) {
        return privacy.canView(viewer, target);
    }

    @Override
    public CompletionStage<Set<Long>> publicPlayerIds(Collection<Long> candidates) {
        return privacy.publicPlayerIds(candidates);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        catalog = List.of();
        CleanupSequence.run(
                privacy::close,
                () -> {
                    if (refreshTask != null) refreshTask.cancel();
                },
                () ->
                        runtime.environment()
                                .plugin()
                                .getServer()
                                .getServicesManager()
                                .unregister(DungeonsApi.class, this));
    }
}
