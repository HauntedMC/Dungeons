package nl.hauntedmc.dungeons.bootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.registry.ConditionRegistry;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.registry.TriggerRegistry;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.util.config.ConfigSyncUtils;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Initializes the concrete runtime state that is required before the plugin can register Bukkit
 * integrations or load dungeon content.
 */
final class CoreBootstrap {
    private final DungeonsRuntime runtime;

    /**
     * Creates the bootstrap stage that owns core runtime initialization.
     */
    CoreBootstrap(DungeonsRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Loads config-backed state and ensures the plugin data layout exists on disk.
     */
    void initializeCoreState() {
        this.initializePluginState();
        this.ensureDataDirectories();
        this.initializeLanguageResources();
        this.reloadRuntimeConfiguration();
    }

    /**
     * Creates the long-lived runtime services that other bootstrap stages depend on.
     */
    void initializeManagers() {
        DungeonsPlugin plugin = this.runtime.environment().plugin();
        this.runtime.setGuiService(new GuiService(plugin));
        this.runtime.setPlayerSessions(new PlayerSessionRegistry(plugin));

        ActiveInstanceRegistry activeInstanceManager = new ActiveInstanceRegistry(plugin);
        this.runtime.setActiveInstances(activeInstanceManager);

        DungeonQueueRegistry queueManager =
                new DungeonQueueRegistry(this.runtime.playerSessions(), activeInstanceManager);
        this.runtime.setQueueRegistry(queueManager);

        DungeonTeamService teamManager =
                new DungeonTeamService(plugin, this.runtime.playerSessions(), queueManager);
        this.runtime.setTeamService(teamManager);

        // Queue startup coordination depends on both queue storage and team state, so the
        // coordinator is wired only after those services exist and can cross-reference each other.
        DungeonQueueCoordinator queueCoordinator =
                new DungeonQueueCoordinator(
                        plugin,
                        this.runtime.playerSessions(),
                        activeInstanceManager,
                        queueManager,
                        teamManager);
        this.runtime.setDungeonQueueCoordinator(queueCoordinator);
        queueManager.setQueueCoordinator(queueCoordinator);

        this.runtime.setFunctionRegistry(
                new FunctionRegistry(plugin, activeInstanceManager, this.runtime.elementEventHandler()));
        this.runtime.setTriggerRegistry(
                new TriggerRegistry(plugin, activeInstanceManager, this.runtime.elementEventHandler()));
        this.runtime.setConditionRegistry(new ConditionRegistry(plugin));
        this.runtime.setLootTableRepository(new LootTableRepository(plugin));
    }

    /**
     * Creates the dungeon repository after all registry-backed types have been registered.
     */
    void initializeDungeonCatalog() {
        DungeonRepository dungeonRepository = new DungeonRepository(this.runtime);
        this.runtime.setDungeonCatalog(dungeonRepository);
        this.runtime.dungeonQueueCoordinator().setDungeonCatalog(dungeonRepository);
    }

    /**
     * Rebuilds lightweight player state for players who were already online during a plugin reload.
     */
    void restoreOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.runtime.playerSessions().put(player);
        }
    }

    /**
     * Emits a concise startup summary once all bootstrap stages have completed.
     */
    void logStartupSummary() {
        this.runtime
                .environment()
                .logger()
                .info(
                        "Loaded {} functions.",
                        this.runtime.functionRegistry().getRegisteredFunctions().size());
        this.runtime
                .environment()
                .logger()
                .info("Loaded {} triggers.", this.runtime.triggerRegistry().getRegisteredTriggers().size());
        this.runtime
                .environment()
                .logger()
                .info(
                        "Loaded {} conditions.",
                        this.runtime.conditionRegistry().getRegisteredConditions().size());
        this.runtime
                .environment()
                .logger()
                .info("Dungeons {} initialized successfully.", this.runtime.environment().version());
    }

    /**
     * Reloads config-backed runtime values without rebuilding the whole runtime graph.
     */
    void reloadRuntimeConfiguration() {
        DungeonsPlugin plugin = this.runtime.environment().plugin();
        this.syncPluginConfigWithDefaults(plugin);
        this.runtime.setConfig(plugin.getConfig());
        this.loadConfiguredMaterials();
    }

    /**
     * Initializes the initial plugin configuration state before any managers are created.
     */
    private void initializePluginState() {
        DungeonsPlugin plugin = this.runtime.environment().plugin();
        plugin.saveDefaultConfig();
        this.syncPluginConfigWithDefaults(plugin);
        this.runtime.setConfig(plugin.getConfig());
        this.runtime.setDefaultDungeonConfig(new YamlConfiguration());
        this.runtime.setLogPrefix("[Dungeons] ");
    }

    /**
     * Ensures the plugin data folders used by dungeon and player persistence exist.
     */
    private void ensureDataDirectories() {
        File dungeonFiles = new File(this.runtime.environment().plugin().getDataFolder(), "dungeons");
        this.runtime.setDungeonFiles(dungeonFiles);
        this.createDirectory(dungeonFiles);
        this.createDirectory(
                new File(this.runtime.environment().plugin().getDataFolder(), "players"));
    }

    /**
     * Creates a directory when it does not already exist and logs failures.
     */
    private void createDirectory(File directory) {
        if (directory.exists()) {
            return;
        }

        if (!directory.mkdirs()) {
            this.runtime
                    .environment()
                    .logger()
                    .error("Failed to create plugin data directory '{}'.", directory.getAbsolutePath());
        }
    }

    /**
     * Initializes the language subsystem and writes missing bundled keys.
     */
    private void initializeLanguageResources() {
        LangUtils.initialize();
        LangUtils.saveMissingValues();
    }

    /**
     * Synchronizes the live plugin config with the bundled default template.
     */
    private void syncPluginConfigWithDefaults(DungeonsPlugin plugin) {
        YamlConfiguration template;
        try (InputStream resource = plugin.getResource("config.yml")) {
            if (resource == null) {
                this.runtime
                        .environment()
                        .logger()
                        .warn("Bundled config.yml resource was not found in the plugin jar.");
                return;
            }

            try (InputStreamReader reader = new InputStreamReader(resource)) {
                template = YamlConfiguration.loadConfiguration(reader);
            }
        } catch (IOException exception) {
            this.runtime
                    .environment()
                    .logger()
                    .error("Failed to read bundled config.yml resource.", exception);
            return;
        }

        if (template.getKeys(true).isEmpty()) {
            this.runtime
                    .environment()
                    .logger()
                    .warn("Skipping plugin config sync because the bundled config template is empty.");
            return;
        }

        FileConfiguration liveConfig = plugin.getConfig();
        if (!ConfigSyncUtils.syncMissingAndObsolete(template, liveConfig)) {
            return;
        }

        plugin.saveConfig();
    }

    /**
     * Loads config-backed tool materials into the runtime cache.
     */
    private void loadConfiguredMaterials() {
        FileConfiguration config = this.runtime.config();
        this.runtime.setFunctionBuilderMaterial(
                this.readConfiguredMaterial(
                        PluginConfigView.getFunctionToolMaterialName(config),
                        "editor.tools.function_item",
                        Material.FEATHER));
        this.runtime.setRoomEditorMaterial(
                this.readConfiguredMaterial(
                        PluginConfigView.getRoomToolMaterialName(config),
                        "editor.tools.room_item",
                        Material.GOLDEN_AXE));
    }

    /**
     * Resolves a configured material name and falls back to the supplied default when invalid.
     */
    private Material readConfiguredMaterial(String configured, String path, Material fallback) {
        String normalized = configured == null ? "" : configured.trim();
        Material material = Material.matchMaterial(normalized);
        if (material != null && material != Material.AIR) {
            return material;
        }

        this.runtime
                .environment()
                .logger()
                .warn(
                        "Invalid material '{}' configured for '{}'. Using '{}' instead.",
                        normalized,
                        path,
                        fallback.name());
        return fallback;
    }
}
