package nl.hauntedmc.dungeons.generation.room;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.structure.StructurePiece;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.math.RangedNumber;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
import nl.hauntedmc.dungeons.util.world.SchematicUtils;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.slf4j.Logger;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Persistent definition of one branching room template.
 *
 * <p>A room definition owns its bounds, schematic, connector list, room-level whitelist/blacklist,
 * and any functions that should spawn relative to the room when generated.
 */
public class BranchingRoomDefinition {
    private final BranchingDungeon dungeon;
    private final String namespace;
    private final File configFile;
    private final ConfigurationFile roomConfig;
    private final File schemFile;
    private volatile WeakReference<StructurePiece> schematic;
    private final List<RotatedRoom> rooms = new ArrayList<>();
    private volatile boolean changedSinceLastSave;
    // Guard save admission, revisions, and completion with this room's monitor.
    private boolean saving;
    private volatile boolean deleted;
    private long schematicRevision;
    private BoundingBox bounds;
    private String occurrencesString;
    private RangedNumber occurrences;
    private String depthString;
    private RangedNumber depth;
    private double weight = 1.0;
    private Location spawn;
    private List<WhitelistEntry> roomBlacklist = new ArrayList<>();
    private List<WhitelistEntry> roomWhitelist = new ArrayList<>();
    private final List<Connector> connectors = new ArrayList<>();
    private final Map<Location, DungeonFunction> functionsMapRelative = new HashMap<>();

    private DungeonsRuntime runtime() {
        return this.dungeon.getRuntime();
    }

    private DungeonsPlugin plugin() {
        return this.dungeon.getEnvironment().plugin();
    }

    private Logger logger() {
        return this.dungeon.getEnvironment().logger();
    }

    /**
     * Returns the cached schematic for this room, loading it from disk when needed.
     */
    public synchronized StructurePiece getSchematic() {
        StructurePiece cached = this.schematic == null ? null : this.schematic.get();
        if (cached == null) {
            cached = SchematicUtils.loadStructurePiece(this.schemFile);
            this.schematic = new WeakReference<>(cached);
        }
        return cached;
    }

    /**
     * Creates a new in-memory room definition with default generation metadata.
     */
    public BranchingRoomDefinition(BranchingDungeon dungeon, String namespace, BoundingBox bounds) {
        this.dungeon = dungeon;
        this.namespace = namespace;
        this.bounds = bounds;
        this.roomConfig = new ConfigurationFile(this.plugin());
        this.configFile = new File(dungeon.getFolder(), "rooms/" + namespace + ".yml");
        this.schemFile = new File(dungeon.getFolder(), "rooms/" + namespace + ".struct");
        this.occurrences = new RangedNumber("0+");
        this.occurrencesString = this.occurrences.toIntString();
        this.depth = new RangedNumber("0+");
        this.depthString = this.depth.toIntString();

        EditableInstance editSession = dungeon.getEditSession();
        if (editSession != null && editSession.getInstanceWorld() != null) {
            Bukkit.getScheduler()
                    .runTask(
                            this.plugin(),
                            () -> this.captureSchematic(editSession.getInstanceWorld(), true));
        }

        RoomMenus.initializeRoomWhitelistMenu(dungeon, this);
        this.rooms.add(new RotatedRoom(this, 0));
        this.rooms.add(new RotatedRoom(this, 90));
        this.rooms.add(new RotatedRoom(this, 180));
        this.rooms.add(new RotatedRoom(this, 270));
    }

    /**
     * Loads an existing room definition from disk.
     */
    public BranchingRoomDefinition(BranchingDungeon dungeon, File configFile)
            throws InvalidConfigurationException {
        this.dungeon = dungeon;
        this.configFile = configFile;
        this.namespace = configFile.getName().replace(".yml", "");
        this.schemFile = new File(dungeon.getFolder(), "rooms/" + this.namespace + ".struct");
        this.roomConfig = new ConfigurationFile(this.plugin());
        this.loadRoom(configFile);
        RoomMenus.initializeRoomWhitelistMenu(dungeon, this);
        this.rooms.add(new RotatedRoom(this, 0));
        this.rooms.add(new RotatedRoom(this, 90));
        this.rooms.add(new RotatedRoom(this, 180));
        this.rooms.add(new RotatedRoom(this, 270));
    }

    /**
     * Replaces room bounds and warns if existing connectors fall outside the new area.
     */
    public void setBounds(Player player, BoundingBox bounds) {
        this.bounds = bounds;
        BoundingBox scaled = bounds.clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

        for (Connector connector : this.connectors) {
            if (!scaled.contains(connector.getLocation().asVector())) {
                LangUtils.sendMessage(player, "editor.session.room-connectors-outside");
                break;
            }
        }

        this.setChangedSinceLastSave(true);
    }

    /**
     * Expands room bounds and shifts connectors on the expanded face.
     */
    public void expand(BlockFace face, int amount) {
        this.bounds.expand(face, amount);
        SimpleLocation.Direction direction = SimpleLocation.Direction.fromBlockFace(face);

        for (Connector connector : this.connectors) {
            if (connector.getLocation().getDirection() == direction) {
                connector.getLocation().shift(amount);
            }
        }

        this.setChangedSinceLastSave(true);
    }

    /**
     * Sets or clears this room's spawn point and start-room registration.
     */
    public void setSpawn(Location loc) {
        this.dungeon.getStartRooms().remove(this.namespace);
        if (loc == null) {
            this.spawn = null;
        } else {
            this.spawn = loc.clone();
            this.spawn.setWorld(null);
            this.dungeon.getStartRooms().put(this.namespace, this);
        }
    }

    /**
     * Adds a room-local dungeon function at the given location.
     */
    public void addFunction(Location loc, DungeonFunction function) {
        this.functionsMapRelative.put(loc, function);
    }

    /**
     * Removes a room-local function at the given location.
     */
    public void removeFunction(Location loc) {
        this.functionsMapRelative.remove(loc);
    }

    /**
     * Saves function metadata to the room configuration asynchronously.
     */
    public void saveFunctions() {
        this.saveFunctionsAsync();
    }

    /**
     * Persists room functions and returns completion state.
     */
    public CompletableFuture<Boolean> saveFunctionsAsync() {
        return this.saveOnOwnerThread(
                () -> {
                    if (this.deleted) return CompletableFuture.completedFuture(false);
                    this.roomConfig.set(
                            "Functions", new ArrayList<>(this.functionsMapRelative.values()));
                    return this.captureRoomSave();
                });
    }

    public void loadFunctions() {
        List<?> functions = this.roomConfig.getList("Functions");
        if (functions == null || functions.isEmpty()) {
            return;
        }

        int index = 0;
        for (Object function : functions) {
            this.loadFunctionSafely(function, index++);
        }
    }

    private void loadFunctionSafely(Object entry, int index) {
        if (!(entry instanceof DungeonFunction function)) {
            this.logger()
                    .warn(
                            "Skipping invalid or null function entry at index {} in room '{}' of"
                                + " dungeon '{}'.",
                            index,
                            this.namespace,
                            this.dungeon.getWorldName());
            return;
        }

        try {
            function.initialize();
            Location loc = function.getLocation();
            if (loc == null) {
                this.logger()
                        .warn(
                                "Skipping function '{}' in room '{}' of dungeon '{}' because it has"
                                    + " no location.",
                                function.getClass().getSimpleName(),
                                this.namespace,
                                this.dungeon.getWorldName());
                return;
            }

            if (!this.bounds
                    .clone()
                    .expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                    .contains(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                this.logger()
                        .warn(
                                "Skipping function '{}' in room '{}' of dungeon '{}' because it is"
                                    + " out of bounds at {},{},{}.",
                                function.getClass().getSimpleName(),
                                this.namespace,
                                this.dungeon.getWorldName(),
                                loc.getBlockX(),
                                loc.getBlockY(),
                                loc.getBlockZ());
                return;
            }

            Location normalized = loc.clone();
            normalized.setWorld(null);
            function.setLocation(normalized);
            this.functionsMapRelative.put(normalized, function);
        } catch (Throwable throwable) {
            Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
            Location loc = function.getLocation();
            this.logger()
                    .warn(
                            "Skipping broken function '{}' in room '{}' of dungeon '{}' at {}."
                                + " Reason: {}",
                            function.getClass().getSimpleName(),
                            this.namespace,
                            this.dungeon.getWorldName(),
                            loc == null
                                    ? "<no-location>"
                                    : (loc.getBlockX()
                                            + ","
                                            + loc.getBlockY()
                                            + ","
                                            + loc.getBlockZ()),
                            root.getMessage() == null
                                    ? root.getClass().getSimpleName()
                                    : root.getMessage(),
                            root);
        }
    }

    /**
     * Adds a connector at the given location if it sits on a room edge.
     */
    public Connector addConnector(SimpleLocation loc) {
        if (!this.applyDirectionAtEdge(loc)) {
            return null;
        }

        Connector connector = new Connector(loc);
        this.addConnector(connector);
        return connector;
    }

    /**
     * Adds an already created connector to this room.
     */
    public void addConnector(Connector connector) {
        connector.setDungeon(this.dungeon);
        this.connectors.add(connector);
    }

    /**
     * Assigns connector direction based on which room boundary the location touches.
     */
    public boolean applyDirectionAtEdge(SimpleLocation loc) {
        boolean success = false;
        if ((int) loc.getX() == (int) this.bounds.getMaxX()) {
            loc.setDirection(SimpleLocation.Direction.EAST);
            success = true;
        }

        if ((int) loc.getX() == (int) this.bounds.getMinX()) {
            loc.setDirection(SimpleLocation.Direction.WEST);
            success = true;
        }

        if ((int) loc.getZ() == (int) this.bounds.getMaxZ()) {
            loc.setDirection(SimpleLocation.Direction.SOUTH);
            success = true;
        }

        if ((int) loc.getZ() == (int) this.bounds.getMinZ()) {
            loc.setDirection(SimpleLocation.Direction.NORTH);
            success = true;
        }

        return success;
    }

    /**
     * Returns a connector exactly matching the given location, if any.
     */
    public Connector getConnector(SimpleLocation loc) {
        for (Connector connector : this.connectors) {
            if (connector.getLocation().equals(loc)) {
                return connector;
            }
        }

        return null;
    }

    /**
     * Removes a connector from this room definition.
     */
    public void removeConnector(Connector connector) {
        this.connectors.remove(connector);
    }

    /**
     * Returns whether this room has exactly one connector.
     */
    public boolean isTerminalRoom() {
        return this.connectors.size() == 1;
    }

    /**
     * Returns whether any orientation can accept a connection from the given direction.
     */
    public boolean canConnectFrom(SimpleLocation.Direction sourceDirection) {
        for (RotatedRoom orientation : this.rooms) {
            for (Connector connector : orientation.getConnectors()) {
                if (sourceDirection.isOpposite(connector.getLocation().getDirection())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns whether all whitelist/blacklist references resolve to existing rooms.
     */
    public boolean hasResolvableReferences() {
        for (WhitelistEntry entry : this.roomWhitelist) {
            if (entry.getRoom(this.dungeon) == null) {
                return false;
            }
        }
        for (WhitelistEntry entry : this.roomBlacklist) {
            if (entry.getRoom(this.dungeon) == null) {
                return false;
            }
        }
        for (Connector connector : this.connectors) {
            for (WhitelistEntry entry : connector.getRoomWhitelist()) {
                if (entry.getRoom(this.dungeon) == null) {
                    return false;
                }
            }
            for (WhitelistEntry entry : connector.getRoomBlacklist()) {
                if (entry.getRoom(this.dungeon) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Builds weighted candidate rooms this room can connect to.
     */
    public List<WhitelistEntry> getValidRooms() {
        List<WhitelistEntry> rooms = new ArrayList<>();
        if (this.roomWhitelist != null && !this.roomWhitelist.isEmpty()) {
            for (WhitelistEntry entry : this.roomWhitelist) {
                BranchingRoomDefinition validRoom = entry.getRoom(this.dungeon);
                if (validRoom != null
                        && !validRoom.getConnectors().isEmpty()
                        && validRoom.canGenerate(this)) {
                    rooms.add(entry);
                }
            }
        } else {
            for (BranchingRoomDefinition validRoom : this.dungeon.getUniqueRooms().values()) {
                if (validRoom != this && validRoom.canGenerate(this)) {
                    rooms.add(new WhitelistEntry(validRoom));
                }
            }
        }

        if (this.roomBlacklist != null && !this.roomBlacklist.isEmpty()) {
            rooms.removeIf(this.roomBlacklist::contains);
        }

        return rooms;
    }

    /**
     * Returns all precomputed room orientations.
     */
    public List<RotatedRoom> getOrientations() {
        return this.rooms;
    }

    /**
     * Returns one random room orientation.
     */
    public RotatedRoom getRandomOrientation() {
        return this.rooms.get(MathUtils.getRandomNumberInRange(0, this.rooms.size() - 1));
    }

    /**
     * Returns whether this room's whitelist permits generation from the source room.
     */
    public boolean canGenerate(BranchingRoomDefinition room) {
        if (this.roomWhitelist != null && !this.roomWhitelist.isEmpty()) {
            for (WhitelistEntry entry : this.roomWhitelist) {
                if (entry.getRoomName().equals(room.namespace)) {
                    return true;
                }
            }

            return false;
        }

        return true;
    }

    /**
     * Loads room config, connectors, and functions from disk.
     */
    public void loadRoom(File file) throws InvalidConfigurationException {
        this.roomConfig.load(file);
        Location firstCorner =
                LocationUtils.readLocation(
                        this.roomConfig.getConfigurationSection("Bounds.Corner1"));
        Location secondCorner =
                LocationUtils.readLocation(
                        this.roomConfig.getConfigurationSection("Bounds.Corner2"));
        if (firstCorner == null || secondCorner == null) {
            throw new InvalidConfigurationException(
                    "Config for room " + this.namespace + " has invalid selection area!");
        }

        this.bounds = BoundingBox.of(firstCorner, secondCorner);
        if (this.roomConfig.contains("Config.Occurrences")) {
            this.occurrencesString = this.roomConfig.getString("Config.Occurrences", "-1");
            this.occurrences = new RangedNumber(this.occurrencesString);
        } else {
            int minOccurrences = this.roomConfig.getInt("Config.MinOccurrences", 0);
            int maxOccurrences = this.roomConfig.getInt("Config.MaxOccurrences", -1);
            this.occurrences = new RangedNumber(minOccurrences, maxOccurrences);
            this.occurrencesString = this.occurrences.toIntString();
            this.roomConfig.set("Config.MinOccurrences", null);
            this.roomConfig.set("Config.MaxOccurrences", null);
        }

        if (this.roomConfig.contains("Config.Depth")) {
            this.depthString = this.roomConfig.getString("Config.Depth", "-1");
            this.depth = new RangedNumber(this.depthString);
        } else {
            int minDepth = this.roomConfig.getInt("Config.MinDepth", 0);
            int maxDepth = this.roomConfig.getInt("Config.MaxDepth", -1);
            this.depth = new RangedNumber(minDepth, maxDepth);
            this.depthString = this.depth.toIntString();
            this.roomConfig.set("Config.MinDepth", null);
            this.roomConfig.set("Config.MaxDepth", null);
        }

        this.weight = this.roomConfig.getDouble("Config.Weight", 1.0);
        this.spawn =
                LocationUtils.readLocation(
                        this.roomConfig.getConfigurationSection("Config.SpawnPoint"));
        if (this.spawn != null) {
            this.spawn.setWorld(null);
        }

        this.roomBlacklist = this.roomConfig.getListOf(WhitelistEntry.class, "Config.Blacklist");
        if (this.roomBlacklist == null) {
            this.roomBlacklist = new ArrayList<>();
        }
        this.roomBlacklist.forEach(v -> v.setDungeon(this.dungeon));

        this.roomWhitelist = this.roomConfig.getListOf(WhitelistEntry.class, "Config.Whitelist");
        if (this.roomWhitelist == null) {
            this.roomWhitelist = new ArrayList<>();
        }
        this.roomWhitelist.forEach(v -> v.setDungeon(this.dungeon));

        ConfigurationSection connectorSection =
                this.roomConfig.getConfigurationSection("Connectors");
        if (connectorSection != null) {
            for (String path : connectorSection.getKeys(false)) {
                Connector connector = this.roomConfig.get(Connector.class, "Connectors." + path);
                if (connector == null) {
                    this.logger()
                            .warn(
                                    "Skipping invalid connector '{}' in room '{}' of dungeon '{}'.",
                                    path,
                                    this.namespace,
                                    this.dungeon.getWorldName());
                    continue;
                }

                connector.setDungeon(this.dungeon);
                if (connector.getDoor() == null) {
                    connector.setDoor(new ConnectorDoor(connector));
                } else if (connector.getDoor().getNamespace() == null
                        || connector.getDoor().getNamespace().isBlank()) {
                    connector
                            .getDoor()
                            .setNamespace(ConnectorDoor.defaultNamespace(connector.getLocation()));
                }
                this.connectors.add(connector);
            }
        }

        this.loadFunctions();
    }

    /**
     * Saves room metadata to disk asynchronously.
     */
    public void saveRoom() {
        this.saveRoomAsync();
    }

    /**
     * Saves room metadata and returns completion state.
     */
    public CompletableFuture<Boolean> saveRoomAsync() {
        return this.saveOnOwnerThread(this::captureRoomSave);
    }

    private CompletableFuture<Boolean> saveOnOwnerThread(
            Supplier<CompletableFuture<Boolean>> capture) {
        return ConfigurationFile.onOwnerThread(this.plugin(), capture);
    }

    private synchronized CompletableFuture<Boolean> captureRoomSave() {
        if (this.deleted) return CompletableFuture.completedFuture(false);
        LocationUtils.writeLocation(
                "Bounds.Corner1",
                this.roomConfig,
                new Location(
                        null, this.bounds.getMinX(), this.bounds.getMinY(), this.bounds.getMinZ()));
        LocationUtils.writeLocation(
                "Bounds.Corner2",
                this.roomConfig,
                new Location(
                        null, this.bounds.getMaxX(), this.bounds.getMaxY(), this.bounds.getMaxZ()));
        this.roomConfig.set("Config.Occurrences", this.occurrencesString);
        this.roomConfig.set("Config.Depth", this.depthString);
        this.roomConfig.set("Config.Weight", this.weight);

        if (this.spawn != null) {
            LocationUtils.writeLocation("Config.SpawnPoint", this.roomConfig, this.spawn);
        } else {
            this.roomConfig.set("Config.SpawnPoint", null);
        }

        this.roomConfig.set("Config.Blacklist", this.roomBlacklist);
        this.roomConfig.set("Config.Whitelist", this.roomWhitelist);
        this.roomConfig.set("Connectors", null);

        for (int i = 0; i < this.connectors.size(); i++) {
            this.roomConfig.set("Connectors." + i, this.connectors.get(i));
        }

        // Orientations are derived caches of the room definition, so they are rebuilt after every
        // save to keep editor previews and future generation runs consistent with new metadata.
        this.rooms.clear();
        this.rooms.add(new RotatedRoom(this, 0));
        this.rooms.add(new RotatedRoom(this, 90));
        this.rooms.add(new RotatedRoom(this, 180));
        this.rooms.add(new RotatedRoom(this, 270));
        return this.roomConfig.saveAsync(this.configFile);
    }

    /**
     * Captures the current world blocks inside bounds into the room schematic.
     */
    public void captureSchematic(World world) {
        this.captureSchematic(world, false);
    }

    /**
     * Captures world blocks on the server thread and persists the snapshot asynchronously.
     *
     * <p>The future reports false on capture, scheduling, or write failure and when a save is
     * already in progress. The room remains dirty until a write succeeds without intervening edits.
     * Cancelling the returned future does not cancel an admitted save.
     */
    public synchronized CompletableFuture<Boolean> captureSchematic(
            World world, boolean forceSave) {
        if (this.deleted || world == null || this.saving) {
            return CompletableFuture.completedFuture(false);
        }
        if (!forceSave && !this.changedSinceLastSave && this.schemFile.isFile()) {
            return CompletableFuture.completedFuture(true);
        }

        this.saving = true;
        this.changedSinceLastSave = true;
        long revision = this.schematicRevision;
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable captureTask =
                () -> {
                    try {
                        if (this.deleted) throw new IllegalStateException("Room has been deleted");
                        StructurePiece captured = this.captureStructurePiece(world);
                        this.writeSchematicAsync(captured)
                                .whenComplete(
                                        (success, failure) ->
                                                this.finishSchematicSave(
                                                        result, captured, revision, failure));
                    } catch (Throwable failure) {
                        this.finishSchematicSave(result, null, revision, failure);
                    }
                };

        try {
            if (!Bukkit.isPrimaryThread()) {
                if (!this.plugin().isEnabled())
                    throw new IllegalStateException(
                            "Cannot capture room blocks off-thread after plugin shutdown");
                Bukkit.getScheduler().runTask(this.plugin(), captureTask);
            } else {
                captureTask.run();
            }
        } catch (RuntimeException failure) {
            this.finishSchematicSave(result, null, revision, failure);
        }
        return result;
    }

    private void finishSchematicSave(
            CompletableFuture<Boolean> result,
            StructurePiece captured,
            long revision,
            Throwable failure) {
        synchronized (this) {
            if (failure == null) {
                this.schematic = new WeakReference<>(captured);
                this.rooms.forEach(RotatedRoom::invalidateSchematic);
                this.changedSinceLastSave = this.schematicRevision != revision;
            } else {
                this.changedSinceLastSave = true;
            }
            this.saving = false;
        }
        if (failure != null) {
            this.logger()
                    .error(
                            "Failed to save schematic '{}' for room '{}' in dungeon '{}'.",
                            this.schemFile.getAbsolutePath(),
                            this.namespace,
                            this.dungeon.getWorldName(),
                            failure);
        }
        result.complete(failure == null);
    }

    private StructurePiece captureStructurePiece(World world) {
        StructurePiece capturedSchematic = new StructurePiece();

        for (double x = this.bounds.getMinX(); x < this.bounds.getMaxX() + 1.0; x++) {
            for (double y = this.bounds.getMinY(); y < this.bounds.getMaxY() + 1.0; y++) {
                for (double z = this.bounds.getMinZ(); z < this.bounds.getMaxZ() + 1.0; z++) {
                    Block target = world.getBlockAt((int) x, (int) y, (int) z);
                    if (target.getType() != Material.AIR) {
                        capturedSchematic.set(target);
                    }
                }
            }
        }

        return capturedSchematic;
    }

    private CompletableFuture<Void> writeSchematicAsync(StructurePiece capturedSchematic) {
        File destination = this.schemFile;
        return ConfigurationFile.filesystemAsync(
                this.plugin(),
                destination,
                () -> {
                    SchematicUtils.saveStructurePiece(destination, capturedSchematic);
                    return null;
                });
    }

    /**
     * Deletes this room configuration and schematic files.
     */
    public void delete() {
        this.deleted = true;
        // Deletion joins the same destination lanes as writes. A queued save must never
        // resurrect a room after the editor has removed it.
        for (File destination : List.of(this.configFile, this.schemFile)) {
            ConfigurationFile.filesystemAsync(
                            this.plugin(),
                            destination,
                            () -> {
                                Files.deleteIfExists(destination.toPath());
                                return null;
                            })
                    .whenComplete(
                            (ignored, failure) -> {
                                if (failure != null)
                                    this.logger().error("Failed to delete room file '{}'.", destination, failure);
                            });
        }
    }

    /**
     * Returns the owning branching dungeon.
     */
    public BranchingDungeon getDungeon() {
        return this.dungeon;
    }

    /**
     * Returns the room namespace.
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Returns whether room data changed since last schematic/config save.
     */
    public boolean isChangedSinceLastSave() {
        return this.changedSinceLastSave;
    }

    /**
     * Sets the dirty flag used by schematic save decisions.
     */
    public synchronized void setChangedSinceLastSave(boolean changedSinceLastSave) {
        if (changedSinceLastSave) {
            this.schematicRevision++;
        }
        // An explicit clear cannot mark an in-flight snapshot as persisted.
        this.changedSinceLastSave = changedSinceLastSave || this.saving;
    }

    /**
     * Returns room bounds in editor coordinates.
     */
    public BoundingBox getBounds() {
        return this.bounds;
    }

    /**
     * Returns serialized occurrence range string.
     */
    public String getOccurrencesString() {
        return this.occurrencesString;
    }

    /**
     * Sets serialized occurrence range string.
     */
    public void setOccurrencesString(String occurrencesString) {
        this.occurrencesString = occurrencesString;
    }

    /**
     * Returns parsed occurrence range.
     */
    public RangedNumber getOccurrences() {
        return this.occurrences;
    }

    /**
     * Sets parsed occurrence range.
     */
    public void setOccurrences(RangedNumber occurrences) {
        this.occurrences = occurrences;
    }

    /**
     * Returns serialized depth range string.
     */
    public String getDepthString() {
        return this.depthString;
    }

    /**
     * Sets serialized depth range string.
     */
    public void setDepthString(String depthString) {
        this.depthString = depthString;
    }

    /**
     * Returns parsed depth range.
     */
    public RangedNumber getDepth() {
        return this.depth;
    }

    /**
     * Sets parsed depth range.
     */
    public void setDepth(RangedNumber depth) {
        this.depth = depth;
    }

    /**
     * Returns room weight for random room selection.
     */
    public double getWeight() {
        return this.weight;
    }

    /**
     * Sets room weight for random room selection.
     */
    public void setWeight(double weight) {
        this.weight = weight;
    }

    /**
     * Returns optional room-local spawn location.
     */
    public Location getSpawn() {
        return this.spawn;
    }

    /**
     * Returns room-level allowed destination rooms.
     */
    public List<WhitelistEntry> getRoomWhitelist() {
        return this.roomWhitelist;
    }

    /**
     * Returns room-level denied destination rooms.
     */
    public List<WhitelistEntry> getRoomBlacklist() {
        return this.roomBlacklist;
    }

    /**
     * Returns connectors defined on this room.
     */
    public List<Connector> getConnectors() {
        return this.connectors;
    }

    /**
     * Returns room-local function map.
     */
    public Map<Location, DungeonFunction> getFunctionsMapRelative() {
        return this.functionsMapRelative;
    }
}
