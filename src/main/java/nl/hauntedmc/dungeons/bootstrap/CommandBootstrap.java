package nl.hauntedmc.dungeons.bootstrap;

import nl.hauntedmc.dungeons.command.DungeonCommand;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

/**
 * Registers Bukkit commands using the already-initialized runtime graph.
 */
final class CommandBootstrap {
    private final DungeonsRuntime runtime;
    private final LifecycleCoordinator lifecycleCoordinator;

    /**
     * Creates the command bootstrap stage.
     */
    CommandBootstrap(DungeonsRuntime runtime, LifecycleCoordinator lifecycleCoordinator) {
        this.runtime = runtime;
        this.lifecycleCoordinator = lifecycleCoordinator;
    }

    /**
     * Creates and registers the main command handler.
     */
    void registerCommands() {
        new DungeonCommand(
                this.runtime.environment().plugin(),
                this.runtime.playerSessions(),
                this.runtime.dungeonCatalog(),
                this.runtime.activeInstances(),
                this.runtime.dungeonQueueCoordinator(),
                this.runtime.queueRegistry(),
                this.runtime.teamService(),
                this.runtime.lootTableRepository(),
                this.runtime.functionRegistry(),
                this.lifecycleCoordinator,
                this.runtime.guiService(),
                "dungeon");
    }
}
