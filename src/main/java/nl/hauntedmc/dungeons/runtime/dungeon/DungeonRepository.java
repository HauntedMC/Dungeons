package nl.hauntedmc.dungeons.runtime.dungeon;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import nl.hauntedmc.dungeons.config.ConfigSyncUtils;
import nl.hauntedmc.dungeons.gui.menu.PlayMenus;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonLoadException;
import nl.hauntedmc.dungeons.registry.DungeonTypeRegistry;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Runtime catalogue of loaded dungeon definitions.
 *
 * <p>This service owns template synchronization, folder discovery, and runtime loading of dungeon
 * model instances.
 */
public final class DungeonRepository {
    private final DungeonsRuntime runtime;
    private final Map<String, String> loadFailures = new LinkedHashMap<>();

    public Map<String, String> loadFailures() {
        return Map.copyOf(loadFailures);
    }

    public CompletableFuture<Map<String, String>> reloadAllAsync() {
        var result = new CompletableFuture<Map<String, String>>();
        this.loadDefaultDungeonConfig();
        Bukkit.getScheduler()
                .runTask(
                        this.runtime.environment().plugin(),
                        () -> {
                            try {
                                File[] files = this.runtime.dungeonFiles().listFiles();
                                if (files == null)
                                    throw new IllegalStateException(
                                            "Cannot list dungeon directory");
                                var present = new HashSet<String>();
                                for (File file : files)
                                    if (file.isDirectory()) {
                                        present.add(file.getName());
                                        this.loadDungeon(file);
                                    }
                                this.dungeons.keySet().removeIf(name -> !present.contains(name));
                                this.loadFailures
                                        .keySet()
                                        .removeIf(name -> !present.contains(name));
                                result.complete(loadFailures());
                            } catch (Exception failure) {
                                result.completeExceptionally(failure);
                            }
                        });
        return result;
    }

    private final HashMap<String, DungeonDefinition> dungeons = new HashMap<>();

    /** Creates the repository and immediately loads all dungeon definitions. */
    public DungeonRepository(DungeonsRuntime runtime) {
        this.runtime = runtime;
        this.reloadAll();
    }

    /** Reloads the on-disk dungeon catalogue into this stable manager instance. */
    public void reloadAll() {
        this.loadDefaultDungeonConfig();
        this.scheduleDungeonDiscovery();
    }

    /** Loads and synchronizes the default dungeon configuration template. */
    private void loadDefaultDungeonConfig() {
        File defaultConfig = new File(this.runtime.dungeonFiles(), "config_default.yml");
        try {
            YamlConfiguration bundledDefaults = this.loadBundledDefaultDungeonConfig();
            if (bundledDefaults == null || bundledDefaults.getKeys(true).isEmpty()) {
                throw new IOException(
                        "Cannot load dungeon defaults because the bundled template is missing or"
                                + " empty.");
            }
            YamlConfiguration template = new YamlConfiguration();
            if (defaultConfig.exists()) template.load(defaultConfig);
            ConfigSyncUtils.syncMissingAndObsolete(bundledDefaults, template);
            template.save(defaultConfig);

            this.runtime.defaultDungeonConfig().load(defaultConfig);
        } catch (InvalidConfigurationException | IOException | IllegalArgumentException exception) {
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

    /** Schedules one-tick-later directory discovery and dungeon loading. */
    private void scheduleDungeonDiscovery() {
        Bukkit.getScheduler()
                .runTaskLater(
                        this.runtime.environment().plugin(),
                        () -> {
                            File[] files = this.runtime.dungeonFiles().listFiles();
                            if (files != null) {
                                var present = new HashSet<String>();
                                for (File file : files) {
                                    if (file.isDirectory()) {
                                        present.add(file.getName());
                                        this.loadDungeon(file);
                                    }
                                }
                                // Failed replacement files retain their last working definitions.
                                this.dungeons.keySet().removeIf(name -> !present.contains(name));
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
                var candidateId = DungeonIdentity.existing(config);
                if (candidateId != null
                        && this.dungeons.values().stream()
                                .anyMatch(
                                        existing ->
                                                !existing.getWorldName().equals(folder.getName())
                                                        && candidateId.equals(
                                                                existing.getDungeonId())))
                    throw new IllegalArgumentException("Duplicate dungeon UUID: " + candidateId);
                dungeonType = config.getString("dungeon.type", "static");
            }

            if (dungeonType.isEmpty()) {
                dungeonType = "static";
            }

            try {
                dungeon =
                        DungeonTypeRegistry.createDungeon(
                                this.runtime, dungeonType, folder, config);
            } catch (InvocationTargetException exception) {
                Throwable ex = exception.getTargetException();
                if (ex instanceof DungeonLoadException) {
                    ((DungeonLoadException) ex)
                            .printError(this.runtime.environment().logger(), folder);
                } else {
                    this.runtime
                            .environment()
                            .logger()
                            .error(
                                    "Failed to load dungeon '{}' due to an unexpected exception"
                                            + " during initialization.",
                                    folder.getName(),
                                    ex);
                }
            } catch (InstantiationException
                    | IllegalAccessException
                    | NoSuchMethodException exception) {
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
                this.loadFailures.put(folder.getName(), "Initialization failed; see server log");
                return null;
            }
            if (!dungeon.isRepositoryInitialized())
                this.runtime
                        .environment()
                        .logger()
                        .warn(
                                "Dungeon '{}' is disabled: repository metadata is missing or invalid. Use /dungeon repo init {}.",
                                folder.getName(),
                                folder.getName());
            dungeon.setGenerator(generator);
            if (dungeon.isUseDifficultyLevels()) {
                PlayMenus.initializeDifficultySelector(dungeon);
            }
            this.put(dungeon);

            this.loadFailures.remove(folder.getName());

            if (dungeon.isRepositoryInitialized())
                this.runtime
                        .environment()
                        .logger()
                        .info("Loaded dungeon '{}' successfully.", dungeon.getWorldName());

            return dungeon;

        } catch (InvalidConfigurationException | IOException | IllegalArgumentException exception) {
            this.loadFailures.put(
                    folder.getName(), "Invalid or unreadable configuration; see server log");
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
        if (dungeon.getDungeonId() != null
                && this.dungeons.values().stream()
                        .anyMatch(
                                existing ->
                                        !existing.getWorldName().equals(dungeon.getWorldName())
                                                && dungeon.getDungeonId()
                                                        .equals(existing.getDungeonId()))) {
            throw new IllegalArgumentException("Duplicate dungeon UUID: " + dungeon.getDungeonId());
        }
        this.dungeons.put(dungeon.getWorldName(), dungeon);
    }

    /** Removes a loaded dungeon from this catalogue. */
    public void remove(DungeonDefinition dungeon) {
        this.dungeons.remove(dungeon.getWorldName());
    }

    /** Returns one loaded dungeon by world name, or null when absent. */
    public DungeonDefinition get(String dungeonName) {
        var named = this.dungeons.get(dungeonName);
        if (named != null || dungeonName == null) return named;
        try {
            var id = UUID.fromString(dungeonName);
            if (!id.toString().equalsIgnoreCase(dungeonName)) return null;
            return this.dungeons.values().stream()
                    .filter(dungeon -> id.equals(dungeon.getDungeonId()))
                    .findFirst()
                    .orElse(null);
        } catch (IllegalArgumentException invalidId) {
            return null;
        }
    }

    /** Returns all loaded dungeons currently tracked in memory. */
    public Collection<DungeonDefinition> getLoadedDungeons() {
        return this.dungeons.values();
    }

    /** Cancels an isolated run before a departing player's state can be reused. */
    public void cancelPreparation(DungeonPlayerSession session) {
        this.runtime.preparations().cancel(session);
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
                        this.runtime.environment().plugin(),
                        () -> LangUtils.sendMessage(player, messageKey));
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
