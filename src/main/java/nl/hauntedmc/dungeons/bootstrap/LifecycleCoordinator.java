package nl.hauntedmc.dungeons.bootstrap;

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

/**
 * Coordinates runtime lifecycle operations that happen after initial bootstrap, such as shutdown
 * and administrative reload commands.
 *
 * <p>The coordinator deliberately works against stable manager instances. Reload paths refresh
 * manager contents in place instead of swapping manager objects, which keeps constructor-injected
 * collaborators valid after a reload.</p>
 */
public final class LifecycleCoordinator {
    private final DungeonsRuntime runtime;
    private final CoreBootstrap coreBootstrap;

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
        try {
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
            this.runCleanup("pending admissions", () -> this.runtime.preparations().shutdown());
            this.runCleanup("queued starts", () -> this.discardQueuedStarts(null));
            this.runCleanup("reserved access keys", () -> this.refundOutstandingReservedKeys(null));

            for (Player player : Bukkit.getOnlinePlayers()) {
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

            if (this.runtime.teamService() != null) {
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
                this.runtime.playerSessions().shutdown();
            } catch (RuntimeException failure) {
                this.runtime
                        .environment()
                        .logger()
                        .error(
                                "Failed to checkpoint remaining player sessions during shutdown.",
                                failure);
            } finally {
                // This also executes owner-thread captures cancelled by Paper during disable.
                nl.hauntedmc.dungeons.config.ConfigurationFile.flushPendingSaves();
            }
        }
    }

    private void runCleanup(String stage, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            this.runtime.environment().logger()
                    .error("Failed to clean up {}; continuing other shutdown stages.", stage, failure);
        }
    }

    private java.util.Set<DungeonInstance> managedInstances() {
        var instances = new java.util.LinkedHashSet<DungeonInstance>();
        if (this.runtime.dungeonCatalog() != null) {
            for (DungeonDefinition dungeon : this.runtime.dungeonCatalog().getLoadedDungeons()) {
                instances.addAll(dungeon.getInstances());
            }
        }
        if (this.runtime.activeInstances() != null) {
            instances.addAll(this.runtime.activeInstances().getActiveInstances());
        }
        return instances;
    }

    private void disposeEmptyManagedInstances(@Nullable String dungeonName) {
        for (DungeonInstance instance : this.managedInstances()) {
            if (!instance.getPlayers().isEmpty()) continue;
            if (dungeonName != null
                    && (instance.getDungeon() == null
                            || !dungeonName.equals(instance.getDungeon().getWorldName()))) continue;
            try {
                instance.dispose();
            } catch (Exception failure) {
                this.runtime
                        .environment()
                        .logger()
                        .error(
                                "Cannot dispose retained instance '{}' during reload.",
                                instance.getInstanceWorldName(),
                                failure);
            }
        }
    }

    /**
     * Reloads the dungeon catalogue while preserving the same injected manager objects.
     */
    public void reloadAllDungeons() {
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

        this.disposeEmptyManagedInstances(null);
        TextUtils.runTimed("Reloading Dungeons", () -> this.runtime.dungeonCatalog().reloadAll());
    }

    /**
     * Reloads a single dungeon definition and its in-memory instances.
     */
    public void reloadDungeon(DungeonDefinition dungeon) {
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

        this.disposeEmptyManagedInstances(dungeon.getWorldName());
        this.runtime.dungeonCatalog().remove(dungeon);
        this.runtime.dungeonCatalog().loadDungeon(dungeon.getFolder());
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
        LangUtils.initialize();
        this.runtime.lootTableRepository().reload();
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
