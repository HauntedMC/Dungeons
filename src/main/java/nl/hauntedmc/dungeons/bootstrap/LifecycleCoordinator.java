package nl.hauntedmc.dungeons.bootstrap;

import java.util.ArrayList;
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
        this.runtime.environment().logger().info("Cleaning up active dungeons and player state.");
        this.discardQueuedStarts(null);
        this.refundOutstandingReservedKeys(null);

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                DungeonPlayerSession playerSession = this.runtime.playerSessions().get(player);
                if (playerSession == null) {
                    this.runtime
                            .environment()
                            .logger()
                            .warn(
                                    "No DungeonPlayerSession state was available during shutdown for '{}'.",
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
            this.runtime.teamService().shutdown();
        }

        if (this.runtime.dungeonCatalog() != null) {
            for (DungeonDefinition dungeon : this.runtime.dungeonCatalog().getLoadedDungeons()) {
                for (DungeonInstance instance : new ArrayList<>(dungeon.getInstances())) {
                    try {
                        instance.dispose();
                    } catch (Exception exception) {
                        String instanceName =
                                instance.getInstanceWorld() == null
                                        ? "<unloaded>"
                                        : instance.getInstanceWorld().getName();
                        this.runtime
                                .environment()
                                .logger()
                                .error(
                                        "Failed to dispose instance '{}' for dungeon '{}'.",
                                        instanceName,
                                        dungeon.getWorldName(),
                                        exception);
                    }
                }
            }
        }
    }

    /**
     * Reloads the dungeon catalogue while preserving the same injected manager objects.
     */
    public void reloadAllDungeons() {
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

        TextUtils.runTimed("Reloading Dungeons", () -> this.runtime.dungeonCatalog().reloadAll());
    }

    /**
     * Reloads a single dungeon definition and its in-memory instances.
     */
    public void reloadDungeon(DungeonDefinition dungeon) {
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

        this.runtime.dungeonCatalog().remove(dungeon);
        this.runtime.dungeonCatalog().loadDungeon(dungeon.getFolder());
    }

    /**
     * Reloads config-backed runtime values without rebuilding the runtime graph.
     */
    public void reloadConfigs() {
        this.runtime.environment().plugin().reloadConfig();
        this.coreBootstrap.reloadRuntimeConfiguration();
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

            this.runtime.dungeonQueueCoordinator().discardQueue(queue);
        }
    }

    /**
     * Refunds any reserved access keys for players who are waiting to start a dungeon.
     */
    private void refundOutstandingReservedKeys(@Nullable String dungeonName) {
        this.runtime.teamService().clearQueuedStarts(dungeonName);

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

            playerSession.refundReservedAccessKey();
            playerSession.setAwaitingDungeon(false);
        }
    }
}
