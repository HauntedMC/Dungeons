package nl.hauntedmc.dungeons.runtime.dungeon;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import nl.hauntedmc.dungeons.gui.menu.PlayMenus;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonLoadException;
import nl.hauntedmc.dungeons.registry.DungeonTypeRegistry;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.util.config.ConfigSyncUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime catalogue of loaded dungeon definitions.
 *
 * <p>This service owns template synchronization, folder discovery, and runtime loading of dungeon
 * model instances.</p>
 */
public final class DungeonRepository {
    private final DungeonsRuntime runtime;
    private final HashMap<String, DungeonDefinition> dungeons = new HashMap<>();

    /** Creates the repository and immediately loads all dungeon definitions. */
    public DungeonRepository(DungeonsRuntime runtime) {
        this.runtime = runtime;
        this.reloadAll();
    }

    /** Reloads the on-disk dungeon catalogue into this stable manager instance. */
    public void reloadAll() {
        this.dungeons.clear();
        this.ensureDefaultResources();
        this.loadDefaultDungeonConfig();
        this.syncDefaultGeneratorTemplate();
        this.scheduleDungeonDiscovery();
    }

    /** Ensures default bundled resources exist in the dungeon data directory. */
    private void ensureDefaultResources() {
        File defaultConfig = new File(this.runtime.dungeonFiles(), "config_default.yml");
        if (!defaultConfig.exists()) {
            this.runtime.environment().plugin().saveResource("dungeons/config_default.yml", false);
        }

        File defaultGenerator = new File(this.runtime.dungeonFiles(), "generator_settings_default.yml");
        if (!defaultGenerator.exists()) {
            this.runtime
                    .environment()
                    .plugin()
                    .saveResource("dungeons/generator_settings_default.yml", false);
        }
    }

    /** Loads and synchronizes the default dungeon configuration template. */
    private void loadDefaultDungeonConfig() {
        File defaultConfig = new File(this.runtime.dungeonFiles(), "config_default.yml");
        try {
            YamlConfiguration bundledDefaults = this.loadBundledDefaultDungeonConfig();
            if (bundledDefaults != null && !bundledDefaults.getKeys(true).isEmpty()) {
                YamlConfiguration diskDefaults = new YamlConfiguration();
                diskDefaults.load(defaultConfig);

                if (ConfigSyncUtils.syncMissingAndObsolete(bundledDefaults, diskDefaults)) {
                    diskDefaults.save(defaultConfig);
                }
            }

            this.runtime.defaultDungeonConfig().load(defaultConfig);
        } catch (InvalidConfigurationException | IOException exception) {
            this.runtime
                    .environment()
                    .logger()
                    .error(
                            "Failed to load default dungeon config '{}'.",
                            defaultConfig.getAbsolutePath(),
                            exception);
        }
    }

    /** Loads the bundled default dungeon config from the plugin jar. */
    private YamlConfiguration loadBundledDefaultDungeonConfig() {
        try (InputStream resource =
                this.runtime.environment().plugin().getResource("dungeons/config_default.yml")) {
            if (resource == null) {
                this.runtime
                        .environment()
                        .logger()
                        .warn("Bundled default dungeon config resource was not found.");
                return null;
            }

            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            this.runtime
                    .environment()
                    .logger()
                    .error("Failed to read bundled default dungeon config resource.", exception);
            return null;
        }
    }

    /** Loads and synchronizes the default generator settings template. */
    private void syncDefaultGeneratorTemplate() {
        File defaultGenerator = new File(this.runtime.dungeonFiles(), "generator_settings_default.yml");
        try {
            YamlConfiguration bundledDefaults = this.loadBundledDefaultGeneratorTemplate();
            if (bundledDefaults != null && !bundledDefaults.getKeys(true).isEmpty()) {
                YamlConfiguration diskDefaults = new YamlConfiguration();
                diskDefaults.load(defaultGenerator);

                if (ConfigSyncUtils.syncMissingAndObsolete(bundledDefaults, diskDefaults)) {
                    diskDefaults.save(defaultGenerator);
                }
            }
        } catch (InvalidConfigurationException | IOException exception) {
            this.runtime
                    .environment()
                    .logger()
                    .error(
                            "Failed to load default generator settings '{}'.",
                            defaultGenerator.getAbsolutePath(),
                            exception);
        }
    }

    /** Loads the bundled default generator settings from the plugin jar. */
    private YamlConfiguration loadBundledDefaultGeneratorTemplate() {
        try (InputStream resource =
                this.runtime
                        .environment()
                        .plugin()
                        .getResource("dungeons/generator_settings_default.yml")) {
            if (resource == null) {
                this.runtime
                        .environment()
                        .logger()
                        .warn("Bundled default generator settings resource was not found.");
                return null;
            }

            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            this.runtime
                    .environment()
                    .logger()
                    .error("Failed to read bundled default generator settings resource.", exception);
            return null;
        }
    }

    /** Schedules one-tick-later directory discovery and dungeon loading. */
    private void scheduleDungeonDiscovery() {
        Bukkit.getScheduler()
                .runTaskLater(
                        this.runtime.environment().plugin(),
                        () -> {
                            File[] files = this.runtime.dungeonFiles().listFiles();
                            if (files != null) {
                                for (File file : files) {
                                    if (file.isDirectory()) {
                                        this.loadDungeon(file);
                                    }
                                }
                            }
                        },
                        1L);
    }

    /** Loads a dungeon from folder using type data from its config when available. */
    public DungeonDefinition loadDungeon(File folder) {
        return this.loadDungeon(folder, "", "");
    }

    /** Loads a dungeon from folder with optional type/generator overrides. */
    public DungeonDefinition loadDungeon(File folder, String dungeonType, String generator) {
        this.runtime.environment().logger().info("Loading dungeon '{}'.", folder.getName());

        try {
            DungeonDefinition dungeon = null;
            File configFile = new File(folder, "config.yml");
            YamlConfiguration config = null;
            if (configFile.exists()) {
                config = new YamlConfiguration();
                config.load(configFile);
                dungeonType = config.getString("dungeon.type", "static");
            }

            if (dungeonType.isEmpty()) {
                dungeonType = "static";
            }

            try {
                dungeon = DungeonTypeRegistry.createDungeon(this.runtime, dungeonType, folder, config);
            } catch (InvocationTargetException exception) {
                Throwable ex = exception.getTargetException();
                if (ex instanceof DungeonLoadException) {
                    ((DungeonLoadException) ex).printError(folder);
                } else {
                    this.runtime
                            .environment()
                            .logger()
                            .error(
                                    "Failed to load dungeon '{}' due to an unexpected exception during initialization.",
                                    folder.getName(),
                                    ex);
                }
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException exception) {
                this.runtime
                        .environment()
                        .logger()
                        .error(
                                "Failed to construct dungeon '{}' using type '{}'.",
                                folder.getName(),
                                dungeonType,
                                exception);
            }

            if (dungeon == null) {
                return null;
            } else {
                dungeon.setGenerator(generator);
                this.put(dungeon);
                if (dungeon.isUseDifficultyLevels()) {
                    PlayMenus.initializeDifficultySelector(dungeon);
                }

                this.runtime
                        .environment()
                        .logger()
                        .info("Loaded dungeon '{}' successfully.", dungeon.getWorldName());

                return dungeon;
            }
        } catch (InvalidConfigurationException | IOException exception) {
            this.runtime
                    .environment()
                    .logger()
                    .error(
                            "Failed to load dungeon '{}' because its config is invalid.",
                            folder.getName(),
                            exception);
            return null;
        }
    }

    /** Inserts or replaces a loaded dungeon in this catalogue. */
    public void put(DungeonDefinition dungeon) {
        this.dungeons.put(dungeon.getWorldName(), dungeon);
    }

    /** Removes a loaded dungeon from this catalogue. */
    public void remove(DungeonDefinition dungeon) {
        this.dungeons.remove(dungeon.getWorldName());
    }

    /** Returns one loaded dungeon by world name, or null when absent. */
    public DungeonDefinition get(String dungeonName) {
        return this.dungeons.get(dungeonName);
    }

    /** Returns all loaded dungeons currently tracked in memory. */
    public Collection<DungeonDefinition> getLoadedDungeons() {
        return this.dungeons.values();
    }

    /** Creates a dungeon instance for one player with default requested player count. */
    public boolean createInstance(String dungeonName, Player player, String difficulty) {
        return this.createInstance(dungeonName, player, difficulty, 1);
    }

    /** Creates a dungeon instance for one player with explicit requested player count. */
    public boolean createInstance(
            String dungeonName, Player player, String difficulty, int requestedPlayers) {
        return this.createInstance(dungeonName, player, difficulty, requestedPlayers, null);
    }

    /** Creates a dungeon instance with optional started-team leader context. */
    public boolean createInstance(
            String dungeonName,
            Player player,
            String difficulty,
            int requestedPlayers,
            @Nullable UUID startedTeamLeaderId) {
        DungeonDefinition dungeon = this.get(dungeonName);
        if (dungeon == null) {
            return false;
        } else if (dungeon.isSaving()) {
            this.sendPlayerMessage(player, "instance.lifecycle.is-saving");
            return false;
        } else {
            return dungeon.instantiate(player, difficulty, requestedPlayers, startedTeamLeaderId);
        }
    }

    /** Opens an editable dungeon instance for one player UUID. */
    public boolean editDungeon(String dungeonName, UUID playerId) {
        DungeonDefinition dungeon = this.get(dungeonName);
        if (dungeon == null) {
            return false;
        } else if (dungeon.isSaving()) {
            this.sendPlayerMessage(playerId, "instance.lifecycle.is-saving");
            return false;
        } else {
            dungeon.edit(playerId);
            return true;
        }
    }

    /** Sends a localized message to a player, hopping to the main thread if needed. */
    private void sendPlayerMessage(Player player, String messageKey) {
        if (Bukkit.isPrimaryThread()) {
            LangUtils.sendMessage(player, messageKey);
            return;
        }

        Bukkit.getScheduler()
                .runTask(
                        this.runtime.environment().plugin(), () -> LangUtils.sendMessage(player, messageKey));
    }

    /** Resolves a UUID to an online player before sending a localized message. */
    private void sendPlayerMessage(UUID playerId, String messageKey) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler()
                    .runTask(
                            this.runtime.environment().plugin(),
                            () -> this.sendPlayerMessage(playerId, messageKey));
            return;
        }

        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            this.sendPlayerMessage(player, messageKey);
        }
    }
}
