package nl.hauntedmc.dungeons.bootstrap;

import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Central composition root for the plugin.
 *
 * <p>This class owns the startup and shutdown sequence and keeps the ordering explicit in one
 * place. The individual bootstrap collaborators remain concrete classes because the codebase has a
 * single startup path and does not benefit from an extra bootstrap framework.</p>
 */
public final class DungeonsApplication {
    private final DungeonsRuntime runtime;
    private final WorldGeneratorResolver worldGeneratorResolver;
    private final CoreBootstrap coreBootstrap;
    private final RegistryBootstrap registryBootstrap;
    private final ListenerBootstrap listenerBootstrap;
    private final IntegrationBootstrap integrationBootstrap;
    private final MenuBootstrap menuBootstrap;
    private final LifecycleCoordinator lifecycleCoordinator;
    private final CommandBootstrap commandBootstrap;
    private boolean started;

    /**
     * Creates the application composition root around the Bukkit plugin entrypoint.
     */
    public DungeonsApplication(DungeonsPlugin plugin) {
        this.runtime = new DungeonsRuntime(plugin);
        this.worldGeneratorResolver = new WorldGeneratorResolver(this.runtime.environment());
        this.coreBootstrap = new CoreBootstrap(this.runtime);
        this.registryBootstrap = new RegistryBootstrap(this.runtime);
        this.listenerBootstrap = new ListenerBootstrap(this.runtime);
        this.integrationBootstrap = new IntegrationBootstrap(this.runtime);
        this.menuBootstrap = new MenuBootstrap(this.runtime);
        this.lifecycleCoordinator = new LifecycleCoordinator(this.runtime, this.coreBootstrap);
        this.commandBootstrap = new CommandBootstrap(this.runtime, this.lifecycleCoordinator);
    }

    /**
     * Starts the application in a strict order so every later stage receives fully initialized
     * collaborators.
     */
    public void start() {
        this.initializeRuntimeAccess();

        try {
            this.initializeCoreState();
            this.initializeRuntimeServices();
            this.registerRuntimeTypes();
            this.initializeContentManagers();
            this.bindBukkitIntegrations();
            this.initializeUserInterfaces();
            this.restoreRuntimeState();
            this.logStartupSummary();
            this.started = true;
        } catch (RuntimeException | Error exception) {
            RuntimeContext.clear();
            throw exception;
        }
    }

    /**
     * Stops the application and always clears the static runtime bridge afterwards.
     */
    public void stop() {
        try {
            if (this.started) {
                this.lifecycleCoordinator.shutdown();
            }
        } finally {
            this.started = false;
            RuntimeContext.clear();
        }
    }

    /**
     * Resolves the plugin-owned world generator hook required by Bukkit.
     */
    @Nullable
    public ChunkGenerator resolveWorldGenerator(@NotNull String worldName, @Nullable String id) {
        return this.worldGeneratorResolver.resolve(worldName, id);
    }

    /**
     * Publishes the freshly created runtime graph through the legacy static bridge.
     */
    private void initializeRuntimeAccess() {
        RuntimeContext.initialize(this.runtime);
    }

    /**
     * Loads config-backed runtime state before any registry or service initialization occurs.
     */
    private void initializeCoreState() {
        this.coreBootstrap.initializeCoreState();
        this.registryBootstrap.registerConfigurationSerializers();
    }

    /**
     * Creates the long-lived services and managers that later startup stages consume.
     */
    private void initializeRuntimeServices() {
        this.coreBootstrap.initializeManagers();
    }

    /**
     * Registers the type metadata and serializers required before dungeon content is loaded.
     */
    private void registerRuntimeTypes() {
        this.registryBootstrap.registerElementSerialization();
        this.registryBootstrap.registerRuntimeTypes();
    }

    /**
     * Loads the dungeon catalogue after all registries know how to deserialize its contents.
     */
    private void initializeContentManagers() {
        // Commands, listeners and optional integrations all depend on the dungeon catalogue being
        // available at registration time. Keeping this earlier prevents stale null dependencies.
        this.coreBootstrap.initializeDungeonCatalog();
    }

    /**
     * Registers Bukkit-facing commands, listeners, and optional integrations.
     */
    private void bindBukkitIntegrations() {
        this.commandBootstrap.registerCommands();
        this.listenerBootstrap.registerCoreListeners();
        this.integrationBootstrap.registerOptionalIntegrations();
    }

    /**
     * Initializes static GUI state that the current menu layer expects to exist globally.
     */
    private void initializeUserInterfaces() {
        this.menuBootstrap.initializeMenus();
    }

    /**
     * Rebuilds lightweight runtime state for players that were already online during a reload.
     */
    private void restoreRuntimeState() {
        this.coreBootstrap.restoreOnlinePlayers();
    }

    /**
     * Emits the startup summary after every stage has completed successfully.
     */
    private void logStartupSummary() {
        this.coreBootstrap.logStartupSummary();
    }
}
