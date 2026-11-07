package nl.hauntedmc.dungeons.content.dungeon;

import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.generation.layout.Layout;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonLoadException;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.registry.LayoutRegistry;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.util.world.GameRuleUtils;

import org.apache.commons.io.FilenameUtils;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Dungeon implementation that generates each run from a branching layout and room definitions.
 *
 * <p>Branching dungeons read generation settings from the dungeon config and keep
 * room-scoped function data in the {@code rooms/} folder.
 */
public class BranchingDungeon extends DungeonDefinition {
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
        try {
            this.layout = this.createConfiguredLayout(true);
            if (this.layout == null) {
                throw new DungeonLoadException(
                        "Generator config references an unknown layout.", true);
            }
        } catch (InvocationTargetException
                | InstantiationException
                | IllegalAccessException
                | NoSuchMethodException exception) {
            this.logger()
                    .error(
                            "Failed to initialize layout '{}' for dungeon '{}'.",
                            this.config.getString("generator.layout", "branching"),
                            this.getWorldName(),
                            exception);
            throw new DungeonLoadException("Generator layout failed to initialize.", true);
        }

        this.ruleConfig = new YamlConfiguration();

        try {
            File configFile = new File(folder, "gamerules.yml");
            nl.hauntedmc.dungeons.config.ConfigurationFile.loadSnapshot(
                    this.ruleConfig, configFile);
        } catch (java.nio.file.NoSuchFileException absent) {
            // New dungeons have no persisted rule overrides yet.
        } catch (IOException exception) {
            throw new DungeonLoadException(
                    "Access of gamerules.yml file failed!",
                    false,
                    "There may be another process accessing the file, or we may not have permission.");
        } catch (InvalidConfigurationException exception) {
            throw new DungeonLoadException(
                    "Gamerule config has invalid YAML! See error below...", true);
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
                            this.config.getString("generator.layout", "branching"),
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
        String configuredLayout = this.config.getString("generator.layout", "branching");
        Layout resolved = LayoutRegistry.createLayoutInstance(configuredLayout, this, this.config);
        if (resolved != null) {
            return resolved;
        }

        this.logger()
                .warn(
                        "Unknown generator layout '{}' in dungeon '{}'; falling back to 'branching'.",
                        configuredLayout,
                        this.getWorldName());
        this.config.set("generator.layout", "branching");
        if (saveCorrection) {
            this.saveConfig();
        }

        return LayoutRegistry.createLayoutInstance("branching", this, this.config);
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
        return this.prepareIsolatedInstance(
                player,
                difficultyName,
                startedTeamLeaderId,
                raw -> {
                    if (!(raw instanceof BranchingInstance instance)) {
                        throw new IllegalStateException("Expected a branching instance");
                    }
                    instance.prepareValidStartPoint();
                    if (instance.getStartLoc() == null) {
                        throw new IllegalStateException(
                                "Generated instance has no safe start point");
                    }
                },
                1L);
    }

    /**
     * Creates play session.
     */
    @Override
    public PlayableInstance createPlaySession(
            CountDownLatch latch, @Nullable UUID startedTeamLeaderId) {
        BranchingInstance instance = new BranchingInstance(this, latch);
        instance.setStartedTeamLeaderId(startedTeamLeaderId);
        try {
            return instance.initialize() == null ? null : instance;
        } catch (RuntimeException | Error failure) {
            // Initialization can throw after reserving files or scheduling world creation. Keep
            // the partially created run managed until its cleanup has actually succeeded.
            if (!this.instances.contains(instance)) this.instances.add(instance);
            this.activeInstances().registerActiveInstance(instance);
            this.discardInstance(instance);
            throw failure;
        }
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
    public void saveGamerulesFrom(World world) {
        nl.hauntedmc.dungeons.config.ConfigurationFile.onOwnerThread(
                this.plugin(),
                () -> {
                    GameRuleUtils.capture(world, this.ruleConfig);
                    this.ruleConfig.set("Difficulty", world.getDifficulty().name());
                    return nl.hauntedmc.dungeons.config.ConfigurationFile.saveAsync(
                            this.plugin(), this.ruleConfig, new File(this.folder, "gamerules.yml"));
                });
    }

    /**
     * Loads gamerules to.
     */
    public void loadGamerulesTo(World world) {
        GameRuleUtils.apply(world, this.ruleConfig);

        world.setDifficulty(Difficulty.valueOf(this.ruleConfig.getString("Difficulty", "NORMAL")));
    }

    /**
     * Loads rooms.
     */
    public void loadRooms() {
        if (!this.roomsFolder.exists() && !this.roomsFolder.mkdirs()) {
            this.logger()
                    .error(
                            "Failed to create rooms folder '{}'.",
                            this.roomsFolder.getAbsolutePath());
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
        }
        for (BranchingRoomDefinition room : this.uniqueRooms.values()) {
            if (bounds.overlaps(room.getBounds())) {
                return null;
            }
        }

        BranchingRoomDefinition newRoom = new BranchingRoomDefinition(this, namespace, bounds);
        this.addRoom(newRoom);
        return newRoom;
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

    @Nullable
    public BranchingRoomDefinition getRoom(String namespace) {
        return this.uniqueRooms.get(namespace);
    }

    @Nullable
    public BranchingRoomDefinition getRoom(Location loc) {
        for (BranchingRoomDefinition room : this.uniqueRooms.values()) {
            if (room.getBounds()
                    .clone()
                    .expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                    .contains(loc.toVector())) {
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
