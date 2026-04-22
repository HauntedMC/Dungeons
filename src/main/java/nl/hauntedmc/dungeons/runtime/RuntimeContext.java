package nl.hauntedmc.dungeons.runtime;

import java.util.Objects;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.registry.ConditionRegistry;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.registry.TriggerRegistry;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.slf4j.Logger;

/**
 * Static bridge for utility and GUI code that still cannot receive dependencies directly.
 *
 * <p>This is intentionally treated as a boundary adapter, not as the primary runtime access
 * pattern. New code should prefer constructor injection or ownership-driven access instead.
 */
public final class RuntimeContext {
    private static DungeonsRuntime runtime;

    /** Utility class. */
    private RuntimeContext() {}

    /** Makes the runtime available to static access sites during plugin startup. */
    public static void initialize(DungeonsRuntime runtime) {
        RuntimeContext.runtime = Objects.requireNonNull(runtime, "runtime");
    }

    /** Removes the runtime bridge during plugin shutdown. */
    public static void clear() {
        RuntimeContext.runtime = null;
    }

    /** Returns the current runtime or throws when accessed outside the plugin lifecycle. */
    public static DungeonsRuntime runtime() {
        if (runtime == null) {
            throw new IllegalStateException("Dungeons runtime has not been initialized.");
        }
        return runtime;
    }

    /** Returns the runtime plugin environment adapter. */
    public static PluginEnvironment environment() {
        return runtime().environment();
    }

    /** Returns the plugin instance. */
    public static DungeonsPlugin plugin() {
        return environment().plugin();
    }

    /** Returns the plugin logger. */
    public static Logger logger() {
        return environment().logger();
    }

    /** Returns the plugin version string. */
    public static String version() {
        return environment().version();
    }

    /** Returns the configured runtime log prefix. */
    public static String logPrefix() {
        return runtime().logPrefix();
    }

    /** Returns the live plugin configuration. */
    public static FileConfiguration config() {
        return runtime().config();
    }

    /** Returns the shared GUI service. */
    public static GuiService guiService() {
        return runtime().guiService();
    }

    /** Returns configured material for function builder items. */
    public static Material functionBuilderMaterial() {
        return runtime().functionBuilderMaterial();
    }

    /** Returns configured material for room editor items. */
    public static Material roomEditorMaterial() {
        return runtime().roomEditorMaterial();
    }

    /** Returns the player session registry. */
    public static PlayerSessionRegistry playerSessions() {
        return runtime().playerSessions();
    }

    /** Returns the active instance registry. */
    public static ActiveInstanceRegistry activeInstances() {
        return runtime().activeInstances();
    }

    /** Returns the dungeon repository. */
    public static DungeonRepository dungeonCatalog() {
        return runtime().dungeonCatalog();
    }

    /** Returns the queue coordinator service. */
    public static DungeonQueueCoordinator dungeonQueueCoordinator() {
        return runtime().dungeonQueueCoordinator();
    }

    /** Returns the function registry. */
    public static FunctionRegistry functionRegistry() {
        return runtime().functionRegistry();
    }

    /** Returns the trigger registry. */
    public static TriggerRegistry triggerRegistry() {
        return runtime().triggerRegistry();
    }

    /** Returns the condition registry. */
    public static ConditionRegistry conditionRegistry() {
        return runtime().conditionRegistry();
    }

    /** Returns the queue entry registry. */
    public static DungeonQueueRegistry queueRegistry() {
        return runtime().queueRegistry();
    }

    /** Returns the team service. */
    public static DungeonTeamService teamService() {
        return runtime().teamService();
    }

    /** Returns the loot table repository. */
    public static LootTableRepository lootTableRepository() {
        return runtime().lootTableRepository();
    }
}
