package nl.hauntedmc.dungeons.integration;

import nl.hauntedmc.dungeons.api.DungeonsApi;
import nl.hauntedmc.dungeons.persistence.DatabaseLifecycle;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Publishes snapshots for peer plugins without sharing mutable dungeon or database objects. */
public final class DungeonService implements DungeonsApi, AutoCloseable {
    private final DungeonsRuntime runtime;
    private final String serverId;
    private final DatabaseLifecycle database;
    private final DungeonPrivacy privacy;
    private volatile List<Dungeon> catalog = List.of();
    private volatile boolean closed;

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

    public void start() {
        var plugin = runtime.environment().plugin();
        refresh();
        runtime.resources().ownIngress(this, DungeonService::close);
        plugin.getServer()
                .getServicesManager()
                .register(DungeonsApi.class, this, plugin, ServicePriority.Normal);
        var task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, 20L, 20L);
        runtime.resources().ownIngress(task, BukkitTask::cancel);
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
        closed = true;
        catalog = List.of();
        privacy.close();
        runtime.environment()
                .plugin()
                .getServer()
                .getServicesManager()
                .unregister(DungeonsApi.class, this);
    }
}
