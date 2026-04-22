package nl.hauntedmc.dungeons.runtime;

import java.io.File;
import java.util.Objects;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.listener.EventRelayListener;
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

/**
 * Mutable runtime state assembled by bootstrap and then shared across the application.
 *
 * <p>The bootstrap package is the only code allowed to mutate this object. Other parts of the
 * codebase may read from it or receive objects that were created from it, but ownership of the
 * runtime graph remains in the composition root.
 */
public final class DungeonsRuntime {
    private final PluginEnvironment environment;
    private final EventRelayListener elementEventHandler = new EventRelayListener();
    private final EventRelayListener instanceEventHandler = new EventRelayListener();

    private String logPrefix;
    private FileConfiguration config;
    private FileConfiguration defaultDungeonConfig;
    private Material functionBuilderMaterial;
    private Material roomEditorMaterial;
    private GuiService guiService;
    private File dungeonFiles;
    private PlayerSessionRegistry playerSessions;
    private ActiveInstanceRegistry activeInstances;
    private DungeonQueueCoordinator dungeonQueueCoordinator;
    private DungeonRepository dungeonRepository;
    private FunctionRegistry functionRegistry;
    private TriggerRegistry triggerRegistry;
    private ConditionRegistry conditionRegistry;
    private DungeonQueueRegistry queueRegistry;
    private DungeonTeamService teamService;
    private LootTableRepository lootTableRepository;

    /** Creates runtime state for one plugin lifecycle. */
    public DungeonsRuntime(DungeonsPlugin plugin) {
        this.environment = new PluginEnvironment(plugin);
    }

    /** Returns the stable wrapper around plugin-specific APIs. */
    public PluginEnvironment environment() {
        return this.environment;
    }

    /** Returns the configured log prefix used by shared message helpers. */
    public String logPrefix() {
        return this.requireInitialized(this.logPrefix, "logPrefix");
    }

    /** Bootstrap-only mutator for the configured log prefix. */
    public void setLogPrefix(String logPrefix) {
        this.logPrefix = logPrefix;
    }

    /** Returns the live plugin configuration. */
    public FileConfiguration config() {
        return this.requireInitialized(this.config, "config");
    }

    /** Bootstrap-only mutator for the live plugin configuration. */
    public void setConfig(FileConfiguration config) {
        this.config = config;
    }

    /** Returns the shared default dungeon configuration template. */
    public FileConfiguration defaultDungeonConfig() {
        return this.requireInitialized(this.defaultDungeonConfig, "defaultDungeonConfig");
    }

    /** Bootstrap-only mutator for the default dungeon template configuration. */
    public void setDefaultDungeonConfig(FileConfiguration defaultDungeonConfig) {
        this.defaultDungeonConfig = defaultDungeonConfig;
    }

    /** Returns the configured function-builder tool material. */
    public Material functionBuilderMaterial() {
        return this.requireInitialized(this.functionBuilderMaterial, "functionBuilderMaterial");
    }

    /** Bootstrap-only mutator for the configured function-builder material. */
    public void setFunctionBuilderMaterial(Material functionBuilderMaterial) {
        this.functionBuilderMaterial = functionBuilderMaterial;
    }

    /** Returns the configured room-editor tool material. */
    public Material roomEditorMaterial() {
        return this.requireInitialized(this.roomEditorMaterial, "roomEditorMaterial");
    }

    /** Bootstrap-only mutator for the configured room-editor material. */
    public void setRoomEditorMaterial(Material roomEditorMaterial) {
        this.roomEditorMaterial = roomEditorMaterial;
    }

    /** Returns the shared GUI service. */
    public GuiService guiService() {
        return this.requireInitialized(this.guiService, "guiService");
    }

    /** Bootstrap-only mutator for the shared GUI service. */
    public void setGuiService(GuiService guiService) {
        this.guiService = guiService;
    }

    /** Returns the root folder containing dungeon map data. */
    public File dungeonFiles() {
        return this.requireInitialized(this.dungeonFiles, "dungeonFiles");
    }

    /** Bootstrap-only mutator for the dungeon maps folder. */
    public void setDungeonFiles(File dungeonFiles) {
        this.dungeonFiles = dungeonFiles;
    }

    /** Returns the registry of live player sessions. */
    public PlayerSessionRegistry playerSessions() {
        return this.requireInitialized(this.playerSessions, "playerSessions");
    }

    /** Bootstrap-only mutator for the player-session registry. */
    public void setPlayerSessions(PlayerSessionRegistry playerSessions) {
        this.playerSessions = playerSessions;
    }

    /** Returns the registry of active dungeon instances. */
    public ActiveInstanceRegistry activeInstances() {
        return this.requireInitialized(this.activeInstances, "activeInstances");
    }

    /** Bootstrap-only mutator for the active-instance registry. */
    public void setActiveInstances(ActiveInstanceRegistry activeInstances) {
        this.activeInstances = activeInstances;
    }

    /** Returns the coordinator responsible for queue-to-instance orchestration. */
    public DungeonQueueCoordinator dungeonQueueCoordinator() {
        return this.requireInitialized(this.dungeonQueueCoordinator, "dungeonQueueCoordinator");
    }

    /** Bootstrap-only mutator for the queue coordinator. */
    public void setDungeonQueueCoordinator(DungeonQueueCoordinator dungeonQueueCoordinator) {
        this.dungeonQueueCoordinator = dungeonQueueCoordinator;
    }

    /** Returns the loaded dungeon catalogue. */
    public DungeonRepository dungeonCatalog() {
        return this.requireInitialized(this.dungeonRepository, "dungeonRepository");
    }

    /** Bootstrap-only mutator for the dungeon catalogue. */
    public void setDungeonCatalog(DungeonRepository dungeonRepository) {
        this.dungeonRepository = dungeonRepository;
    }

    /** Returns the function registry. */
    public FunctionRegistry functionRegistry() {
        return this.requireInitialized(this.functionRegistry, "functionRegistry");
    }

    /** Bootstrap-only mutator for the function registry. */
    public void setFunctionRegistry(FunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
    }

    /** Returns the trigger registry. */
    public TriggerRegistry triggerRegistry() {
        return this.requireInitialized(this.triggerRegistry, "triggerRegistry");
    }

    /** Bootstrap-only mutator for the trigger registry. */
    public void setTriggerRegistry(TriggerRegistry triggerRegistry) {
        this.triggerRegistry = triggerRegistry;
    }

    /** Returns the condition registry. */
    public ConditionRegistry conditionRegistry() {
        return this.requireInitialized(this.conditionRegistry, "conditionRegistry");
    }

    /** Bootstrap-only mutator for the condition registry. */
    public void setConditionRegistry(ConditionRegistry conditionRegistry) {
        this.conditionRegistry = conditionRegistry;
    }

    /** Returns the registry of queued dungeon entries. */
    public DungeonQueueRegistry queueRegistry() {
        return this.requireInitialized(this.queueRegistry, "queueRegistry");
    }

    /** Bootstrap-only mutator for the queue registry. */
    public void setQueueRegistry(DungeonQueueRegistry queueRegistry) {
        this.queueRegistry = queueRegistry;
    }

    /** Returns the team domain service. */
    public DungeonTeamService teamService() {
        return this.requireInitialized(this.teamService, "teamService");
    }

    /** Bootstrap-only mutator for the team domain service. */
    public void setTeamService(DungeonTeamService teamService) {
        this.teamService = teamService;
    }

    /** Returns the loot-table repository. */
    public LootTableRepository lootTableRepository() {
        return this.requireInitialized(this.lootTableRepository, "lootTableRepository");
    }

    /** Bootstrap-only mutator for the loot-table repository. */
    public void setLootTableRepository(LootTableRepository lootTableRepository) {
        this.lootTableRepository = lootTableRepository;
    }

    /** Listener bridge used for element-scoped event dispatch. */
    public EventRelayListener elementEventHandler() {
        return this.elementEventHandler;
    }

    /** Listener bridge used for instance-scoped event dispatch. */
    public EventRelayListener instanceEventHandler() {
        return this.instanceEventHandler;
    }

    /** Validates that a runtime component was initialized before access. */
    private <T> T requireInitialized(T value, String componentName) {
        return Objects.requireNonNull(
                value,
                () -> "Dungeons runtime component '" + componentName + "' has not been initialized yet.");
    }
}
