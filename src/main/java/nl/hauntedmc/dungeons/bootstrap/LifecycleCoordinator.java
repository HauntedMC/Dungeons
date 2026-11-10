package nl.hauntedmc.dungeons.bootstrap;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinates runtime lifecycle operations that happen after initial bootstrap, such as shutdown
 * and administrative reload commands.
 *
 * <p>The coordinator deliberately works against stable manager instances. Reload paths refresh
 * manager contents in place instead of swapping manager objects, which keeps constructor-injected
 * collaborators valid after a reload.
 */
public final class LifecycleCoordinator {
    private final DungeonsRuntime runtime;
    private final CoreBootstrap coreBootstrap;
    private volatile boolean shuttingDown;
    private CompletableFuture<Boolean> reloadAttempt;

    /**
     * Creates the lifecycle coordinator for an already-bootstrapped runtime graph.
     */
    LifecycleCoordinator(DungeonsRuntime runtime, CoreBootstrap coreBootstrap) {
        this.runtime = runtime;
        this.coreBootstrap = coreBootstrap;
    }

    /**
     * Releases player and instance state during plugin shutdown.
     */
    void shutdown() {
        this.shuttingDown = true;
        if (this.reloadAttempt != null) this.reloadAttempt.complete(false);
        try {
            if (this.runtime.staffOperations() != null)
                this.runCleanup("staff operations", () -> this.runtime.staffOperations().close());
            this.runtime
                    .environment()
                    .logger()
                    .info("Cleaning up active dungeons and player state.");
            if (this.runtime.lootTableEditor() != null) {
                try {
                    this.runtime.lootTableEditor().close();
                } catch (RuntimeException failure) {
                    this.runtime
                            .environment()
                            .logger()
                            .error("Failed to close loot editors", failure);
                }
            }
            if (this.runtime.preparations() != null)
                this.runCleanup("pending admissions", () -> this.runtime.preparations().shutdown());
            if (this.runtime.hasQueueRegistry())
                this.runCleanup("queued starts", () -> this.discardQueuedStarts(null));
            if (this.runtime.hasPlayerSessions())
                this.runCleanup("reserved access keys", () -> this.refundOutstandingReservedKeys(null));

            for (Player player : this.runtime.hasPlayerSessions() ? Bukkit.getOnlinePlayers() : List.<Player>of()) {
                try {
                    DungeonPlayerSession playerSession = this.runtime.playerSessions().get(player);
                    if (playerSession == null) {
                        this.runtime
                                .environment()
                                .logger()
                                .warn(
                                        "No DungeonPlayerSession state was available during"
                                                + " shutdown for '{}'.",
                                        player.getName());
                        continue;
                    }

                    playerSession.restoreCapturedHotbar();
                    DungeonInstance instance = playerSession.getInstance();
                    if (instance != null) {
                        instance.removePlayer(playerSession);
                    }
                } catch (Exception exception) {
                    this.runtime
                            .environment()
                            .logger()
                            .error(
                                    "Failed to clean up player '{}' during plugin shutdown.",
                                    player.getName(),
                                    exception);
                }
            }

            if (this.runtime.hasTeamService()) {
                this.runCleanup("team service", () -> this.runtime.teamService().shutdown());
            }

            for (DungeonInstance instance : this.managedInstances()) {
                try {
                    for (DungeonPlayerSession participant :
                            new ArrayList<>(instance.getPlayers())) {
                        this.runCleanup("instance participant", () -> instance.removePlayer(participant, true));
                    }
                    instance.dispose();
                } catch (Exception exception) {
                    this.runtime
                            .environment()
                            .logger()
                            .error(
                                    "Failed to dispose managed instance '{}'.",
                                    instance.getInstanceWorldName(),
                                    exception);
                }
            }
        } finally {
            try {
                if (this.runtime.hasPlayerSessions()) this.runtime.playerSessions().shutdown();
            } catch (RuntimeException failure) {
                this.runtime
                        .environment()
                        .logger()
                        .error(
                                "Failed to checkpoint remaining player sessions during shutdown.",
                                failure);
            } finally {
                // This also executes owner-thread captures cancelled by Paper during disable.
                ConfigurationFile.flushPendingSaves();
            }
        }
    }

    private void runCleanup(String stage, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            this.runtime.environment().logger()
                    .error("Failed to clean up {}; continuing other cleanup stages.", stage, failure);
        }
    }

    private Set<DungeonInstance> managedInstances() {
        var instances = new LinkedHashSet<DungeonInstance>();
        if (this.runtime.hasDungeonCatalog()) {
            for (DungeonDefinition dungeon : this.runtime.dungeonCatalog().getLoadedDungeons()) {
                instances.addAll(dungeon.getInstances());
            }
        }
        if (this.runtime.hasActiveInstances()) {
            instances.addAll(this.runtime.activeInstances().getActiveInstances());
        }
        return instances;
    }

    private CompletableFuture<Boolean> disposeEmptyManagedInstances(@Nullable String dungeonName) {
        var editors = new ArrayList<CompletableFuture<Boolean>>();
        for (DungeonInstance instance : this.managedInstances()) {
            if (dungeonName != null
                    && (instance.getDungeon() == null
                            || !dungeonName.equals(instance.getDungeon().getWorldName()))) continue;
            if (!instance.getPlayers().isEmpty()) {
                if (instance.isEditInstance()) editors.add(CompletableFuture.completedFuture(false));
                continue;
            }
            try {
                if (instance.isEditInstance()) editors.add(instance.disposeAsync());
                else instance.dispose();
            } catch (Exception failure) {
                if (instance.isEditInstance()) editors.add(CompletableFuture.completedFuture(false));
                this.runtime
                        .environment()
                        .logger()
                        .error(
                                "Cannot dispose retained instance '{}' during reload.",
                                instance.getInstanceWorldName(),
                                failure);
            }
        }
        return CompletableFuture.allOf(editors.toArray(CompletableFuture[]::new))
                .handle((ignored, failure) -> failure == null
                        && editors.stream().allMatch(future -> Boolean.TRUE.equals(future.join())));
    }

    private CompletableFuture<Boolean> finishReload(CompletableFuture<Boolean> editors, Runnable replacement) {
        var result = this.reloadAttempt;
        editors.whenComplete((success, failure) -> {
            Runnable finish = () -> {
                if (this.shuttingDown || failure != null || !Boolean.TRUE.equals(success)) {
                    this.runtime.environment().logger().error(
                                                    "Dungeon reload aborted: editor persistence or"
                                                        + " unload did not succeed.");
                    result.complete(false);
                    return;
                }
                try {
                    replacement.run();
                    result.complete(true);
                } catch (RuntimeException reloadFailure) {
                    this.runtime.environment().logger().error("Dungeon reload failed.", reloadFailure);
                    result.complete(false);
                }
            };
            if (this.shuttingDown) result.complete(false);
            else if (Bukkit.isPrimaryThread()) finish.run();
            else {
                try {
                    Bukkit.getScheduler().runTask(this.runtime.environment().plugin(), finish);
                } catch (RuntimeException rejected) {
                    result.complete(false);
                }
            }
        });
        return result.copy();
    }

    private boolean reloadUnavailable() {
        return this.shuttingDown || this.reloadAttempt != null && !this.reloadAttempt.isDone();
    }

    /**
     * Reloads the dungeon catalogue while preserving the same injected manager objects.
     */
    public CompletableFuture<Boolean> reloadAllDungeons() {
        if (!Bukkit.isPrimaryThread())
            return ConfigurationFile.onOwnerThread(
                    this.runtime.environment().plugin(), this::reloadAllDungeons);
        if (this.reloadUnavailable()) return CompletableFuture.completedFuture(false);
        this.reloadAttempt = new CompletableFuture<>();
        try {
            this.runtime.preparations().cancelDungeon(null);
            this.discardQueuedStarts(null);
            this.refundOutstandingReservedKeys(null);

            for (Player player : Bukkit.getOnlinePlayers()) {
                DungeonPlayerSession playerSession = this.runtime.playerSessions().get(player);
                if (playerSession == null) {
                    continue;
                }

                playerSession.restoreCapturedHotbar();
                DungeonInstance instance = playerSession.getInstance();
                if (instance != null) {
                    instance.removePlayer(playerSession);
                    LangUtils.sendMessage(
                            playerSession.getPlayer(), "commands.dungeon.reload.reloaded-by-admin");
                }
            }

            return this.finishReload(this.disposeEmptyManagedInstances(null),
                    () -> TextUtils.runTimed("Reloading Dungeons", () -> this.runtime.dungeonCatalog().reloadAll()));
        } catch (RuntimeException failure) {
            this.runtime.environment().logger().error("Cannot prepare dungeon reload.", failure);
            this.reloadAttempt.complete(false);
            return this.reloadAttempt.copy();
        }
    }

    /**
     * Reloads a single dungeon definition and its in-memory instances.
     */
    public CompletableFuture<Boolean> reloadDungeon(DungeonDefinition dungeon) {
        if (!Bukkit.isPrimaryThread())
            return ConfigurationFile.onOwnerThread(
                    this.runtime.environment().plugin(), () -> this.reloadDungeon(dungeon));
        if (this.reloadUnavailable()) return CompletableFuture.completedFuture(false);
        this.reloadAttempt = new CompletableFuture<>();
        try {
            this.runtime.preparations().cancelDungeon(dungeon.getWorldName());
            this.discardQueuedStarts(dungeon.getWorldName());
            this.refundOutstandingReservedKeys(dungeon.getWorldName());

            for (DungeonInstance instance : new ArrayList<>(dungeon.getInstances())) {
                for (DungeonPlayerSession playerSession : new ArrayList<>(instance.getPlayers())) {
                    playerSession.restoreCapturedHotbar();
                    instance.removePlayer(playerSession);
                    LangUtils.sendMessage(
                            playerSession.getPlayer(), "commands.dungeon.reload.reloaded-by-admin");
                }
            }

            return this.finishReload(this.disposeEmptyManagedInstances(dungeon.getWorldName()), () -> {
                if (this.runtime.dungeonCatalog().loadDungeon(dungeon.getFolder()) == null)
                    throw new IllegalStateException("Replacement dungeon could not be loaded");
            });
        } catch (RuntimeException failure) {
            this.runtime.environment().logger().error("Cannot prepare dungeon reload.", failure);
            this.reloadAttempt.complete(false);
            return this.reloadAttempt.copy();
        }
    }

    /**
     * Reloads config-backed runtime values without rebuilding the runtime graph.
     */
    public void reloadConfigs() {
        this.runtime.environment().plugin().reloadConfig();
        this.coreBootstrap.reloadRuntimeConfiguration();
        for (DungeonDefinition dungeon : this.runtime.dungeonCatalog().getLoadedDungeons()) {
            dungeon.refreshRuleSettings();
        }
        if (!this.runtime.lootTableRepository().reload())
            throw new IllegalStateException("Plugin configuration refreshed, but loot reload failed; last working loot retained and editing blocked");
    }

    /**
     * Discards queued dungeon starts, optionally limiting the cleanup to a single dungeon.
     */
    private void discardQueuedStarts(@Nullable String dungeonName) {
        for (DungeonQueueEntry queue : this.runtime.queueRegistry().snapshotQueues()) {
            if (queue == null) {
                continue;
            }

            if (dungeonName != null && !dungeonName.equals(queue.getDungeon().getWorldName())) {
                continue;
            }

            this.runCleanup("queue", () -> this.runtime.dungeonQueueCoordinator().discardQueue(queue));
        }
    }

    /**
     * Refunds any reserved access keys for players who are waiting to start a dungeon.
     */
    private void refundOutstandingReservedKeys(@Nullable String dungeonName) {
        this.runCleanup("team queued starts", () -> this.runtime.teamService().clearQueuedStarts(dungeonName));

        for (Player player : Bukkit.getOnlinePlayers()) {
            DungeonPlayerSession playerSession = this.runtime.playerSessions().get(player);
            if (playerSession == null || playerSession.getInstance() != null) {
                continue;
            }

            boolean hasMatchingReservation =
                    dungeonName == null
                            ? playerSession.hasReservedAccessKey()
                            : playerSession.hasReservedAccessKey(dungeonName);
            if (!hasMatchingReservation) {
                continue;
            }

            String reservedDungeonName = playerSession.getReservedAccessKeyDungeon();
            DungeonDefinition reservedDungeon =
                    reservedDungeonName == null
                            ? null
                            : this.runtime.dungeonCatalog().get(reservedDungeonName);
            if (reservedDungeon != null) {
                reservedDungeon.refundReservedAccessKey(playerSession);
            } else {
                playerSession.refundReservedAccessKey();
            }
            playerSession.setAwaitingDungeon(false);
        }
    }
}
