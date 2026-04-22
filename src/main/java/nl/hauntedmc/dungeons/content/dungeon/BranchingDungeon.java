package nl.hauntedmc.dungeons.content.dungeon;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.generation.layout.Layout;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.dungeon.DungeonLoadException;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.registry.LayoutRegistry;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.config.ConfigSyncUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dungeon implementation that generates each run from a branching layout and room definitions.
 *
 * <p>Branching dungeons persist generation settings separately from normal dungeon config and keep
 * room-scoped function data in the {@code rooms/} folder.
 */
public class BranchingDungeon extends DungeonDefinition {
    private final YamlConfiguration genConfig;
    private final YamlConfiguration ruleConfig;
    private Layout layout;
    private final File roomsFolder;
    private final Map<String, BranchingRoomDefinition> uniqueRooms = new HashMap<>();
    private final Map<String, BranchingRoomDefinition> startRooms = new HashMap<>();

    /**
     * Creates a new BranchingDungeon instance.
     */
    public BranchingDungeon(
            @NotNull DungeonsRuntime runtime,
            @NotNull File folder,
            @Nullable YamlConfiguration loadedConfig)
            throws DungeonLoadException {
        super(runtime, folder, loadedConfig);
        this.genConfig = new YamlConfiguration();

        try {
            File configFile = new File(folder, "generation.yml");
            if (!configFile.exists()) {
                this.logger().info("Creating generator config for dungeon '{}'.", folder.getName());
                FileUtils.copyFile(
                                                new File(this.runtime().dungeonFiles(), "generator_settings_default.yml"), configFile);
            }

            this.genConfig.load(configFile);
            this.syncGenerationConfigWithDefaults(configFile);
            try {
                this.layout = this.createConfiguredLayout(true);
                if (this.layout == null) {
                                        throw new DungeonLoadException("Generator config references an unknown layout.", true);
                }
            } catch (InvocationTargetException
                    | InstantiationException
                    | IllegalAccessException
                    | NoSuchMethodException exception) {
                this.logger()
                        .error(
                                "Failed to initialize layout '{}' for dungeon '{}'.",
                                this.genConfig.getString("generator.layout", "branching"),
                                this.getWorldName(),
                                exception);
                                throw new DungeonLoadException("Generator layout failed to initialize.", true);
            }
        } catch (IOException exception) {
                        throw new DungeonLoadException(
                    "Access of generator.yml file failed!",
                    false,
                    "There may be another process accessing the file, or we may not have permission.");
        } catch (InvalidConfigurationException exception) {
                        throw new DungeonLoadException("Generator config has invalid YAML! See error below...", true);
        }

        this.ruleConfig = new YamlConfiguration();

        try {
            File configFile = new File(folder, "gamerules.yml");
            if (configFile.exists()) {
                this.ruleConfig.load(configFile);
            }
        } catch (IOException exception) {
                        throw new DungeonLoadException(
                    "Access of gamerules.yml file failed!",
                    false,
                    "There may be another process accessing the file, or we may not have permission.");
        } catch (InvalidConfigurationException exception) {
                        throw new DungeonLoadException("Gamerule config has invalid YAML! See error below...", true);
        }

        this.roomsFolder = new File(folder, "rooms");
        this.lobbyEnabled = false;
        this.loadRooms();
    }

    /**
     * Loads layout from generation config.
     */
    private void loadLayoutFromGenerationConfig() {
        try {
            Layout resolved = this.createConfiguredLayout(true);
            if (resolved != null) {
                this.layout = resolved;
            }
        } catch (InvocationTargetException
                | InstantiationException
                | IllegalAccessException
                | NoSuchMethodException exception) {
            this.logger()
                    .error(
                            "Failed to initialize layout '{}' for dungeon '{}'.",
                            this.genConfig.getString("generator.layout", "branching"),
                            this.getWorldName(),
                            exception);
        }
    }

    /**
     * Creates configured layout.
     */
    private @Nullable Layout createConfiguredLayout(boolean saveCorrection)
            throws InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException,
                    NoSuchMethodException {
        String configuredLayout = this.genConfig.getString("generator.layout", "branching");
        Layout resolved = LayoutRegistry.createLayoutInstance(configuredLayout, this, this.genConfig);
        if (resolved != null) {
            return resolved;
        }

        this.logger()
                .warn(
                        "Unknown generator layout '{}' in dungeon '{}'; falling back to 'branching'.",
                        configuredLayout,
                        this.getWorldName());
        this.genConfig.set("generator.layout", "branching");
        if (saveCorrection) {
            this.saveGenerationConfig();
        }

        return LayoutRegistry.createLayoutInstance("branching", this, this.genConfig);
    }

    /**
     * Synchronizes generation config with defaults.
     */
    private void syncGenerationConfigWithDefaults(File configFile)
            throws IOException, InvalidConfigurationException {
        File defaultConfigFile =
                                new File(this.runtime().dungeonFiles(), "generator_settings_default.yml");
        if (!defaultConfigFile.exists()) {
            return;
        }

        YamlConfiguration defaultConfig = new YamlConfiguration();
        defaultConfig.load(defaultConfigFile);
        if (defaultConfig.getKeys(true).isEmpty()) {
            return;
        }

        if (ConfigSyncUtils.syncMissingAndObsolete(defaultConfig, this.genConfig)) {
            this.genConfig.save(configFile);
        }
    }

    /**
     * Performs refresh runtime settings.
     */
    @Override
    public void refreshRuntimeSettings() {
        super.refreshRuntimeSettings();
        this.lobbyEnabled = false;
    }

    /**
     * Saves generation config.
     */
    public void saveGenerationConfig() {
        try {
            this.genConfig.save(new File(this.folder, "generation.yml"));
        } catch (IOException exception) {
            this.logger()
                    .error(
                            "Failed to save generator config for dungeon '{}'.", this.getWorldName(), exception);
        }
    }

    /**
     * Reloads generation settings.
     */
    public void reloadGenerationSettings() {
        this.loadLayoutFromGenerationConfig();
    }

    /**
     * Performs prepare instance.
     */
    @Override
    public boolean prepareInstance(
            Player player,
            String difficultyName,
            int requestedPlayers,
            @Nullable UUID startedTeamLeaderId) {
        DungeonPlayerSession playerSession = this.playerSessions().get(player);
        LangUtils.sendMessage(
                player,
                "instance.lifecycle.loading",
                LangUtils.placeholder("dungeon", this.getDisplayName()));
        DungeonDifficulty difficulty = this.difficultyLevels.get(difficultyName);

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        this.plugin(),
                        () ->
                                TextUtils.runTimed(
                                        "Loading Dungeon " + this.getWorldName(),
                                        () -> {
                                            CountDownLatch latch = new CountDownLatch(1);
                                            AtomicReference<BranchingInstance> instanceRef = new AtomicReference<>();

                                            Bukkit.getScheduler()
                                                    .runTask(
                                                            this.plugin(),
                                                            () -> {
                                                                // Layout cloning and world bootstrapping still
                                                                // touch Bukkit state, so the actual instance
                                                                // creation is deferred to the server thread.
                                                                PlayableInstance raw =
                                                                        this.createPlaySession(latch, startedTeamLeaderId);
                                                                BranchingInstance instance =
                                                                        raw == null ? null : raw.as(BranchingInstance.class);
                                                                instanceRef.set(instance);

                                                                if (instance == null) {
                                                                    latch.countDown();
                                                                }
                                                            });

                                            try {
                                                boolean loaded = latch.await(5L, TimeUnit.SECONDS);
                                                BranchingInstance instance = instanceRef.get();

                                                if (!loaded || instance == null) {
                                                    this.schedulePrepareFailure(
                                                            player, playerSession, instance, "instance.lifecycle.load-failed");
                                                    return;
                                                }

                                                if (instance.hasLoadFailed() || !instance.isReady()) {
                                                    this.schedulePrepareFailure(
                                                            player, playerSession, instance, "instance.lifecycle.load-failed");
                                                    return;
                                                }

                                                Bukkit.getScheduler()
                                                        .runTask(
                                                                this.plugin(),
                                                                () -> {
                                                                    // Start-point resolution depends on the
                                                                    // generated world being ready, so the player
                                                                    // is only admitted after the spawn has been
                                                                    // validated here.
                                                                    if (!player.isOnline()) {
                                                                        playerSession.refundReservedAccessKey(this.getWorldName());
                                                                        playerSession.setAwaitingDungeon(false);
                                                                        this.discardInstance(instance);
                                                                        this.logger()
                                                                                .warn(
                                                                                        "Aborted loading branching instance '{}' because player '{}' disconnected before join.",
                                                                                        this.getWorldName(),
                                                                                        player.getName());
                                                                        return;
                                                                    }

                                                                    instance.setDifficulty(difficulty);
                                                                    this.instances.add(instance);
                                                                    this.activeInstances().registerActiveInstance(instance);
                                                                    instance.prepareValidStartPoint();

                                                                    if (instance.getStartLoc() == null) {
                                                                        playerSession.refundReservedAccessKey(this.getWorldName());
                                                                        this.timeout(instance, playerSession);
                                                                        instance.dispose();
                                                                        return;
                                                                    }

                                                                    Bukkit.getScheduler()
                                                                            .runTaskLater(
                                                                                    this.plugin(),
                                                                                    () -> {
                                                                                        instance.addPlayer(playerSession);
                                                                                        if (playerSession.getInstance() == instance) {
                                                                                            playerSession.commitReservedAccessKey(
                                                                                                    this.getWorldName());
                                                                                            nl.hauntedmc.dungeons.util.lang.LangUtils.sendMessage(
                                                                                                    player,
                                                                                                    "instance.lifecycle.entered",
                                                                                                    LangUtils.placeholder(
                                                                                                            "dungeon", this.getDisplayName()));
                                                                                        } else {
                                                                                            playerSession.refundReservedAccessKey(
                                                                                                    this.getWorldName());
                                                                                            this.discardInstance(instance);
                                                                                            nl.hauntedmc.dungeons.util.lang.LangUtils.sendMessage(
                                                                                                    player,
                                                                                                    "instance.lifecycle.load-failed",
                                                                                                    LangUtils.placeholder(
                                                                                                            "dungeon", this.getDisplayName()));
                                                                                        }
                                                                                        playerSession.setAwaitingDungeon(false);
                                                                                    },
                                                                                    1L);
                                                                });
                                            } catch (InterruptedException exception) {
                                                Thread.currentThread().interrupt();
                                                this.schedulePrepareFailure(
                                                        player, playerSession, null, "instance.lifecycle.load-failed");
                                                this.logger()
                                                        .error(
                                                                "Interrupted while waiting for branching instance '{}' to initialize.",
                                                                this.getWorldName(),
                                                                exception);
                                            }
                                        }));

        return true;
    }

    /**
     * Performs schedule prepare failure.
     */
    private void schedulePrepareFailure(
            Player player,
            @Nullable DungeonPlayerSession playerSession,
            @Nullable BranchingInstance instance,
            String messageKey) {
        Bukkit.getScheduler()
                .runTask(
                        this.plugin(),
                        () -> {
                            if (instance != null) {
                                this.discardInstance(instance);
                            }

                            if (playerSession != null) {
                                playerSession.refundReservedAccessKey(this.getWorldName());
                                playerSession.setAwaitingDungeon(false);
                            }

                            LangUtils.sendMessage(
                                    player, messageKey, LangUtils.placeholder("dungeon", this.getDisplayName()));
                        });
    }

    /**
     * Creates play session.
     */
    @Override
    public PlayableInstance createPlaySession(
            CountDownLatch latch, @Nullable UUID startedTeamLeaderId) {
        BranchingInstance instance = new BranchingInstance(this, latch);
        instance.setStartedTeamLeaderId(startedTeamLeaderId);
        return instance.initialize() == null ? null : instance;
    }

    /**
     * Creates edit session.
     */
    @Override
    public EditableInstance createEditSession(CountDownLatch latch) {
        EditableInstance instance = new BranchingEditableInstance(this, latch);
        instance.initialize();
        return instance;
    }

    /**
     * Saves gamerules from.
     */
    @SuppressWarnings("removal")
    public void saveGamerulesFrom(World world) {
        for (String rule : world.getGameRules()) {
            this.ruleConfig.set("Gamerule." + rule, world.getGameRuleValue(rule));
        }

        this.ruleConfig.set("Difficulty", world.getDifficulty().name());
        Runnable task =
                () -> {
                    try {
                        this.ruleConfig.save(new File(this.folder, "gamerules.yml"));
                    } catch (IOException exception) {
                        this.logger()
                                .error("Failed to save gamerules for dungeon '{}'.", this.worldName, exception);
                    }
                };
        if (this.plugin().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin(), task);
        } else {
            task.run();
        }
    }

    /**
     * Loads gamerules to.
     */
    @SuppressWarnings("removal")
    public void loadGamerulesTo(World world) {
        for (String rule : world.getGameRules()) {
            String val = this.ruleConfig.getString("Gamerule." + rule);
            if (val != null) {
                world.setGameRuleValue(rule, val);
            }
        }

        world.setDifficulty(Difficulty.valueOf(this.ruleConfig.getString("Difficulty", "NORMAL")));
    }

    /**
     * Loads rooms.
     */
    public void loadRooms() {
        if (!this.roomsFolder.exists() && !this.roomsFolder.mkdirs()) {
            this.logger()
                    .error("Failed to create rooms folder '{}'.", this.roomsFolder.getAbsolutePath());
        }

        File[] files = this.roomsFolder.listFiles();
        if (files == null) {
            return;
        }

        for (File roomFile : files) {
            if (!FilenameUtils.getExtension(roomFile.getName()).equals("yml")) {
                continue;
            }

            try {
                BranchingRoomDefinition room = new BranchingRoomDefinition(this, roomFile);
                this.addRoom(room);
            } catch (Throwable throwable) {
                // Room files are isolated units of content, so a single corrupt room should not
                // block the rest of the dungeon from loading.
                Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
                this.logger()
                        .error(
                                "Skipping broken branching room '{}' for dungeon '{}'. The room will not be loaded, but the rest of the dungeon will continue loading.",
                                roomFile.getName().replace(".yml", ""),
                                this.getWorldName(),
                                root);

                String rootMessage = root.getMessage();
                if (rootMessage != null && !rootMessage.contains("LootTableRewardsFunction")) {
                    this.logger()
                            .warn(
                                    "Reason for skipping room '{}' in dungeon '{}': {}",
                                    roomFile.getName().replace(".yml", ""),
                                    this.getWorldName(),
                                    rootMessage);
                }
            }
        }
    }

    /**
     * Performs define room.
     */
    public BranchingRoomDefinition defineRoom(String namespace, BoundingBox bounds) {
        if (this.uniqueRooms.containsKey(namespace)) {
            return null;
        } else {
            for (BranchingRoomDefinition room : this.uniqueRooms.values()) {
                if (bounds.overlaps(room.getBounds())) {
                    return null;
                }
            }

            BranchingRoomDefinition newRoom = new BranchingRoomDefinition(this, namespace, bounds);
            this.addRoom(newRoom);
            return newRoom;
        }
    }

    /**
     * Adds room.
     */
    public void addRoom(BranchingRoomDefinition room) {
        this.uniqueRooms.put(room.getNamespace(), room);
        if (room.getSpawn() != null) {
            this.startRooms.put(room.getNamespace(), room);
        }
    }

    /**
     * Removes room.
     */
    public void removeRoom(BranchingRoomDefinition room) {
        this.uniqueRooms.remove(room.getNamespace());
        this.startRooms.remove(room.getNamespace());
        room.delete();
    }

    @Nullable public BranchingRoomDefinition getRoom(String namespace) {
        return this.uniqueRooms.get(namespace);
    }

    @Nullable public BranchingRoomDefinition getRoom(Location loc) {
        for (BranchingRoomDefinition room : this.uniqueRooms.values()) {
            if (room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).contains(loc.toVector())) {
                return room;
            }
        }

        return null;
    }

    /**
     * Adds function.
     */
    @Override
    public void addFunction(Location loc, DungeonFunction function) {
        BranchingRoomDefinition room = this.getRoom(loc);
        if (room == null) {
            this.logger()
                    .warn(
                            "Editor attempted to add a function outside of a room in dungeon '{}' at {},{},{}.",
                            this.getWorldName(),
                            loc.getBlockX(),
                            loc.getBlockY(),
                            loc.getBlockZ());
        } else {
            Location target = loc.clone();
            target.setWorld(null);
            function.setLocation(target);
            this.addFunction(target, function, room);
        }
    }

    /**
     * Adds function.
     */
    public void addFunction(Location loc, DungeonFunction function, BranchingRoomDefinition room) {
        room.addFunction(loc, function);
    }

    /**
     * Removes function.
     */
    @Override
    public void removeFunction(Location loc) {
        BranchingRoomDefinition room = this.getRoom(loc);
        if (room != null) {
            Location target = loc.clone();
            target.setWorld(null);
            this.removeFunction(target, room);
        }
    }

    /**
     * Removes function.
     */
    public void removeFunction(Location loc, BranchingRoomDefinition room) {
        room.removeFunction(loc);
    }

    /**
     * Saves functions.
     */
    @Override
    public void saveFunctions() {
        for (BranchingRoomDefinition room : this.uniqueRooms.values()) {
            room.saveFunctions();
        }
    }

    /**
     * Returns the functions.
     */
    @Override
    public Map<Location, DungeonFunction> getFunctions() {
        Map<Location, DungeonFunction> functions = new HashMap<>();

        for (BranchingRoomDefinition room : this.uniqueRooms.values()) {
            functions.putAll(room.getFunctionsMapRelative());
        }

        return functions;
    }

    /**
     * Returns the gen config.
     */
    public YamlConfiguration getGenConfig() {
        return this.genConfig;
    }

    /**
     * Returns the rule config.
     */
    public YamlConfiguration getRuleConfig() {
        return this.ruleConfig;
    }

    /**
     * Returns the layout.
     */
    public Layout getLayout() {
        return this.layout;
    }

    /**
     * Returns the rooms folder.
     */
    public File getRoomsFolder() {
        return this.roomsFolder;
    }

    /**
     * Returns the unique rooms.
     */
    public Map<String, BranchingRoomDefinition> getUniqueRooms() {
        return this.uniqueRooms;
    }

    /**
     * Returns the start rooms.
     */
    public Map<String, BranchingRoomDefinition> getStartRooms() {
        return this.startRooms;
    }
}
