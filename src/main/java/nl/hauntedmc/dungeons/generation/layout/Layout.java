package nl.hauntedmc.dungeons.generation.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nl.hauntedmc.dungeons.config.PluginConfigView;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.room.RotatedRoom;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.menu.ConnectorEditorMenus;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.math.RandomCollection;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Base class for branching layout algorithms.
 *
 * <p>A layout chooses start rooms, expands connectors into new room instances, validates the final
 * room graph, exposes the editor hotbars built by the GUI package.
 */
public abstract class Layout implements Cloneable {
    protected final BranchingDungeon dungeon;
    protected final FileConfiguration config;
    protected PlayerHotbarMenu connectorEditMenu;
    protected PlayerHotbarMenu connectorDoorMenu;
    protected Material closedConnectorBlock;
    protected final int minRooms;
    protected final int maxRooms;
    protected boolean strictRooms;
    protected List<BranchingRoomDefinition> roomWhitelist = new ArrayList<>();
    protected List<BranchingRoomDefinition> endRoomWhitelist = new ArrayList<>();
    protected int roomCount;
    protected int totalRooms;
    protected Map<BranchingRoomDefinition, Integer> countsByRoom = new HashMap<>();
    protected Map<Integer, BranchingRoomDefinition> requiredRooms = new HashMap<>();
    protected List<BoundingBox> roomAreas = new ArrayList<>();
    protected Map<SimpleLocation, InstanceRoom> roomAnchors = new HashMap<>();
    protected InstanceRoom first;
    protected volatile boolean cancelled = false;
    protected Queue<InstanceRoom> queue = new ArrayDeque<>();
    protected int queuedRoomCount;
    protected List<InstanceRoom> roomsToAdd;
    protected Map<BranchingRoomDefinition, Integer> queuedCountsByRoom;
    protected List<BoundingBox> queuedRoomAreas;
    protected Map<SimpleLocation, InstanceRoom> queuedRoomAnchors;
    protected InstanceRoom generationCheckpoint;
    protected ConcurrentMap<GenerationFailureReason, Integer> failureReasons =
            new ConcurrentHashMap<>();

    protected int getEditorTargetBlockRange() {
        return PluginConfigView.getEditorTargetBlockRangeBlocks(this.runtime().config());
    }

    /** Creates a layout instance backed by one branching dungeon and generator configuration. */
    public Layout(BranchingDungeon dungeon, FileConfiguration config) {
        this.dungeon = dungeon;
        this.config = config;
        try {
            String configuredMaterial =
                    config.getString("generator.seal_open_connectors_with", "STONE");
            this.closedConnectorBlock = Material.matchMaterial(configuredMaterial);
            if (this.closedConnectorBlock == null
                    || (this.closedConnectorBlock != Material.AIR
                            && !this.closedConnectorBlock.isBlock())) {
                throw new IllegalArgumentException(
                        "The provided connector fill material is invalid.");
            }
        } catch (IllegalArgumentException exception) {
            this.logger()
                    .warn(
                            "Invalid generator connector fill block '{}' in dungeon '{}'; defaulting to STONE.",
                            config.getString("generator.seal_open_connectors_with", "STONE"),
                            dungeon.getWorldName(),
                            exception);
            this.closedConnectorBlock = Material.STONE;
        }

        this.minRooms = config.getInt("generator.room_target.min", 8);
        this.maxRooms = config.getInt("generator.room_target.max", 16);
        this.strictRooms = config.getBoolean("generator.room_target.enforce_minimum", true);
    }

    protected final DungeonsRuntime runtime() {
        return this.dungeon.getRuntime();
    }

    protected final Logger logger() {
        return this.dungeon.getEnvironment().logger();
    }

    protected final DungeonPlayerSession playerSession(Player player) {
        return this.runtime().playerSessions().get(player);
    }

    protected abstract boolean tryConnectors(InstanceRoom room);

    protected abstract RandomCollection<BranchingRoomDefinition> filterRooms(
            Connector connector, InstanceRoom from);

    protected boolean tryConnector(Connector connector, InstanceRoom from) {
        RandomCollection<BranchingRoomDefinition> weightedRooms = this.filterRooms(connector, from);
        if (weightedRooms.size() <= 0) {
            this.recordFailure(GenerationFailureReason.NO_VALID_CANDIDATE_ROOMS);
            return false;
        }
        SimpleLocation adjusted = connector.getLocation().clone();
        adjusted.shift(1.0);
        InstanceRoom roomInst =
                this.findRoom(from.getSource().getOrigin(), connector, adjusted, weightedRooms);
        if (roomInst == null) {
            this.recordFailure(GenerationFailureReason.NO_VALID_ORIENTATION);
            return false;
        }
        roomInst.setDepth(from.getDepth() + 1);
        this.queueRoom(roomInst);
        return true;
    }

    protected boolean verifyLayout() {
        boolean valid = true;
        for (BranchingRoomDefinition room : this.dungeon.getUniqueRooms().values()) {
            if (this.countsByRoom.getOrDefault(room, 0) < room.getOccurrences().getMin()) {
                this.recordFailure(GenerationFailureReason.MISSING_REQUIRED_ROOM);
                this.logger()
                        .warn(
                                "Room requirement failed in dungeon '{}': room '{}' generated {} times but minimum is {}.",
                                this.dungeon.getWorldName(),
                                room.getNamespace(),
                                this.countsByRoom.getOrDefault(room, 0),
                                room.getOccurrences().getMin());
                valid = false;
            }
        }

        return valid;
    }

    /** Validates layout and room metadata before generation starts. */
    public LayoutValidationReport validateConfiguration() {
        LayoutValidationReport report = new LayoutValidationReport();
        if (this.dungeon.getUniqueRooms().isEmpty()) {
            report.addError("Dungeon has no rooms configured.");
            return report;
        }

        if (this.dungeon.getStartRooms().isEmpty()) {
            report.addWarning(
                    "Dungeon has no configured start rooms. Generation will fall back to any room.");
        }

        for (BranchingRoomDefinition room : this.dungeon.getUniqueRooms().values()) {
            if (room.getConnectors().isEmpty()) {
                report.addWarning(
                        "Room '"
                                + room.getNamespace()
                                + "' has no connectors and can never generate except as a manually chosen start room.");
            }

            if (room.getConnectors().size() == 1
                    && !this.isConfiguredStartRoom(room)
                    && (this.endRoomWhitelist == null || this.endRoomWhitelist.isEmpty())) {
                report.addWarning(
                        "Room '"
                                + room.getNamespace()
                                + "' has exactly 1 connector and will be treated as an automatic end room candidate.");
            }

            if (room.getOccurrences().getMin() > 0
                    && room.getDepth().getMax() != -1.0
                    && room.getDepth().getMax() < room.getDepth().getMin()) {
                report.addError("Room '" + room.getNamespace() + "' has an invalid depth range.");
            }

            if (room.getOccurrences().getMin() > 0
                    && room.getConnectors().isEmpty()
                    && !this.isConfiguredStartRoom(room)) {
                report.addError(
                        "Room '"
                                + room.getNamespace()
                                + "' is required but unreachable because it has no connectors and is not a start room.");
            }
        }

        if (this.requiresGlobalRoomTargetValidation() && this.minRooms > this.maxRooms) {
            report.addError("generator.room_target.min is greater than generator.room_target.max.");
        }

        return report;
    }

    protected boolean requiresGlobalRoomTargetValidation() {
        return false;
    }

    /** Runs layout generation with timeout/retry handling and returns the final outcome. */
    public GenerationResult generate() {
        this.cancelled = false;
        this.failureReasons.clear();
        LayoutValidationReport report = this.validateConfiguration();
        report.log(this.dungeon.getEnvironment().logger(), this.dungeon.getWorldName());
        if (report.hasErrors()) {
            this.recordFailure(GenerationFailureReason.INVALID_CONFIGURATION);
            return GenerationResult.INVALID_CONFIGURATION;
        }

        int timeout = PluginConfigView.getLayoutGenerationTimeoutSeconds(this.runtime().config());
        long startTime = System.nanoTime();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<GenerationResult> future;
        try {
            future =
                    executor.submit(
                            () -> {
                                int tries = 0;

                                do {
                                    // Generation retries from scratch until a valid graph is found
                                    // or
                                    // the configured timeout expires.
                                    GenerationResult generationResult = this.tryLayout();
                                    if (generationResult == GenerationResult.NO_START_ROOM) {
                                        return generationResult;
                                    }

                                    if (generationResult.isPassed()) {
                                        long runTime =
                                                TimeUnit.NANOSECONDS.toMillis(
                                                        System.nanoTime() - startTime);
                                        this.logger()
                                                .info(
                                                        "Generated layout for dungeon '{}' in {}ms after {} attempts.",
                                                        this.dungeon.getWorldName(),
                                                        runTime,
                                                        tries + 1);
                                        return GenerationResult.SUCCESS;
                                    }

                                    tries++;
                                } while (System.nanoTime() - startTime
                                                <= TimeUnit.SECONDS.toNanos(timeout)
                                        && !this.cancelled);

                                if (this.cancelled) {
                                    return GenerationResult.CANCELLED;
                                }

                                this.logFailureSummary();
                                this.logger()
                                        .error(
                                                "Failed to find a valid layout for dungeon '{}' after {} attempts within {}s timeout.",
                                                this.dungeon.getWorldName(),
                                                tries,
                                                timeout);
                                return GenerationResult.TIMED_OUT;
                            });

        } catch (RejectedExecutionException exception) {
            executor.shutdownNow();
            this.logger()
                    .error(
                            "Cannot schedule layout generation for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            exception);
            return GenerationResult.GENERIC_FAILURE;
        }

        GenerationResult result;
        try {
            result = future.get(timeout + 1L, TimeUnit.SECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            result = GenerationResult.TIMED_OUT;
            this.cancelled = true;
            this.logFailureSummary();
            this.logger()
                    .error(
                            "Layout generation timed out for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            exception);
        } catch (InterruptedException exception) {
            future.cancel(true);
            result = GenerationResult.GENERIC_FAILURE;
            this.cancelled = true;
            Thread.currentThread().interrupt();
            this.logger()
                    .error(
                            "Interrupted while generating layout for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            exception);
        } catch (ExecutionException exception) {
            future.cancel(true);
            result = GenerationResult.GENERIC_FAILURE;
            this.cancelled = true;
            this.logger()
                    .error(
                            "Layout generation failed for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            exception);
        }

        executor.shutdownNow();
        return result;
    }

    protected GenerationResult tryLayout() {
        this.logger().info("Generating new layout for dungeon '{}'.", this.dungeon.getWorldName());
        this.resetGenerationState();

        if (!this.selectFirstRoom()) {
            this.recordFailure(GenerationFailureReason.INVALID_CONFIGURATION);
            return GenerationResult.NO_START_ROOM;
        }
        GenerationResult queueResult = this.processQueue();
        if (!queueResult.isPassed()) {
            return queueResult;
        }

        for (InstanceRoom room : this.roomsToAdd) {
            this.addRoom(room);
        }

        if (!this.verifyLayout()) {
            return GenerationResult.BAD_LAYOUT;
        }
        this.logger()
                .info(
                        "Validated generated layout for dungeon '{}': {} total rooms.",
                        this.dungeon.getWorldName(),
                        this.totalRooms);
        return GenerationResult.SUCCESS;
    }

    protected boolean selectFirstRoom() {
        if (this.dungeon.getUniqueRooms().isEmpty()) {
            this.logger()
                    .error("Dungeon '{}' has no rooms configured.", this.dungeon.getWorldName());
            return false;
        }
        List<BranchingRoomDefinition> possibleRooms =
                new ArrayList<>(this.dungeon.getStartRooms().values());
        if (possibleRooms.isEmpty()) {
            possibleRooms = new ArrayList<>(this.dungeon.getUniqueRooms().values());
        }

        if (!this.roomWhitelist.isEmpty()) {
            possibleRooms.removeIf(roomCandidate -> !this.roomWhitelist.contains(roomCandidate));
        }

        RandomCollection<BranchingRoomDefinition> weightedRooms = new RandomCollection<>();

        for (BranchingRoomDefinition room : possibleRooms) {
            if (!(room.getDepth().getMin() > 0.0)) {
                weightedRooms.add(room.getWeight() * (room.getOccurrences().getMin() + 1.0), room);
            }
        }

        BranchingRoomDefinition first = weightedRooms.next();
        if (first == null) {
            return false;
        }
        this.addRoom(new SimpleLocation(0.0, 128.0, 0.0), first.getRandomOrientation());
        this.logger()
                .info(
                        "Selected '{}' as the first room for dungeon '{}'.",
                        first.getNamespace(),
                        this.dungeon.getWorldName());
        return true;
    }

    protected GenerationResult processQueue() {
        this.roomsToAdd = new ArrayList<>();
        this.queuedRoomCount = this.roomCount;
        this.queuedCountsByRoom = new HashMap<>(this.countsByRoom);
        this.queuedRoomAreas = new ArrayList<>(this.roomAreas);
        this.queuedRoomAnchors = new HashMap<>(this.roomAnchors);
        return this.processQueue(0);
    }

    protected GenerationResult processQueue(int checkpointDepth) {
        this.generationCheckpoint = null;
        int timeout = PluginConfigView.getLayoutGenerationTimeoutSeconds(this.runtime().config());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = null;

        try {
            future =
                    executor.submit(
                            () -> {
                                int rooms = 0;

                                while (!this.queue.isEmpty() && !this.cancelled) {
                                    InstanceRoom room = this.queue.poll();
                                    if (room == null) {
                                        continue;
                                    }

                                    if (!this.getAllRooms().contains(room)
                                            && !this.roomsToAdd.contains(room)) {
                                        this.roomsToAdd.add(room);
                                    }

                                    this.tryConnectors(room);
                                    if (checkpointDepth > 0 && ++rooms >= checkpointDepth) {
                                        // Branching layouts snapshot a checkpoint room so they can
                                        // roll back only the latest segment instead of the entire
                                        // trunk when a branch target is missed.
                                        this.generationCheckpoint = this.queue.peek();
                                        this.queue.clear();
                                        break;
                                    }
                                }
                            });
            future.get(timeout, TimeUnit.SECONDS);
        } catch (RejectedExecutionException exception) {
            this.logger()
                    .error(
                            "Cannot schedule room queue generation for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            exception);
            return GenerationResult.GENERIC_FAILURE;
        } catch (InterruptedException exception) {
            future.cancel(true);

            Thread.currentThread().interrupt();
            this.logger()
                    .error(
                            "Queue processing was interrupted during layout generation for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            exception);
            return GenerationResult.GENERIC_FAILURE;
        } catch (TimeoutException exception) {
            future.cancel(true);

            this.logger()
                    .error(
                            "Queue processing timed out after {}s for dungeon '{}'.",
                            timeout,
                            this.dungeon.getWorldName(),
                            exception);
            return GenerationResult.GENERIC_FAILURE;
        } catch (ExecutionException exception) {
            future.cancel(true);

            this.logger()
                    .error(
                            "Queue processing failed during layout generation for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            exception);
            return GenerationResult.GENERIC_FAILURE;
        } finally {
            executor.shutdownNow();
        }

        return GenerationResult.SUCCESS;
    }

    @Nullable
    protected InstanceRoom findRoom(
            BranchingRoomDefinition from,
            Connector connector,
            SimpleLocation position,
            RandomCollection<BranchingRoomDefinition> weightedRooms) {
        List<RotatedRoom> orientations = new ArrayList<>();
        Map<RotatedRoom, List<Connector>> validConnectors = new HashMap<>();
        List<BranchingRoomDefinition> invalidRooms = new ArrayList<>();
        BranchingRoomDefinition nextRoom = null;

        while (!this.cancelled) {
            if (orientations.isEmpty()) {
                if (nextRoom != null) {
                    invalidRooms.add(nextRoom);
                }

                if (invalidRooms.size() >= weightedRooms.size()) {
                    break;
                }

                nextRoom = this.nextWeightedRoom(weightedRooms, invalidRooms);
                if (nextRoom == null) {
                    break;
                }

                validConnectors.clear();

                for (RotatedRoom room : nextRoom.getOrientations()) {
                    boolean foundConnector = false;

                    for (Connector nextConnector : room.getConnectors()) {
                        if (connector
                                        .getLocation()
                                        .getDirection()
                                        .isOpposite(nextConnector.getLocation().getDirection())
                                && nextConnector.canGenerate(from)) {
                            foundConnector = true;
                            validConnectors
                                    .computeIfAbsent(room, k -> new ArrayList<>())
                                    .add(nextConnector);
                        }
                    }

                    if (validConnectors.get(room) != null) {
                        Collections.shuffle(validConnectors.get(room));
                    }

                    if (foundConnector) {
                        orientations.add(room);
                    }
                }

                if (orientations.isEmpty()) {
                    this.recordFailure(GenerationFailureReason.NO_COMPATIBLE_CONNECTOR);
                    continue;
                }
            }

            RotatedRoom to =
                    orientations.remove(
                            MathUtils.getRandomNumberInRange(0, orientations.size() - 1));
            List<Connector> connectors = validConnectors.get(to);
            if (connectors == null) {
                continue;
            }

            for (Connector nextConnector : connectors) {
                // Candidate rooms are instantiated against each compatible connector orientation
                // and then rejected if their bounding box collides with existing rooms.
                InstanceRoom roomInst = new InstanceRoom(to, position, nextConnector.getLocation());
                if (this.canRoomGenerate(roomInst)) {
                    return roomInst;
                }
            }
        }

        return null;
    }

    protected InstanceRoom addRoom(SimpleLocation anchor, RotatedRoom room) {
        SimpleLocation offset = new SimpleLocation(0.0, 0.0, 0.0);
        offset.shift(room.getBounds().getMin());
        InstanceRoom instance = new InstanceRoom(room, anchor, offset);
        if (this.roomAnchors.isEmpty()) {
            this.first = instance;
        }

        this.countsByRoom.merge(room.getOrigin(), 1, Integer::sum);
        this.roomAnchors.put(anchor, instance);
        this.roomAreas.add(instance.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        this.roomCount++;
        this.totalRooms++;
        this.queue.add(instance);
        return instance;
    }

    protected void addRoom(InstanceRoom room) {
        if (this.roomAnchors.isEmpty()) {
            this.first = room;
        }

        this.countsByRoom.merge(room.getSource().getOrigin(), 1, Integer::sum);
        this.roomAnchors.put(room.getAnchor(), room);
        this.roomAreas.add(room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        this.roomCount++;
        this.totalRooms++;
    }

    protected void queueRoom(InstanceRoom room) {
        this.queue.add(room);
        this.queuedCountsByRoom.merge(room.getSource().getOrigin(), 1, Integer::sum);
        this.queuedRoomAnchors.put(room.getAnchor(), room);
        this.queuedRoomAreas.add(room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        this.queuedRoomCount++;
    }

    protected int getRoomCount(BranchingRoomDefinition room) {
        return this.queuedCountsByRoom.getOrDefault(room, 0);
    }

    /** Returns whether the room has reached its configured maximum occurrence count. */
    public boolean isRoomMaxedOut(BranchingRoomDefinition room) {
        if (room.getOccurrences().getMax() == 0.0) {
            return false;
        }
        int count = this.getRoomCount(room);
        int max = (int) room.getOccurrences().getMax();
        return count >= (max == -1 ? Integer.MAX_VALUE : max);
    }

    /**
     * Returns whether the room can be placed without colliding with existing queued/generated
     * rooms.
     */
    public boolean canRoomGenerate(InstanceRoom room) {
        BoundingBox roomBox = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

        for (BoundingBox area : this.roomAreas) {
            if (area.overlaps(roomBox)) {
                this.recordFailure(GenerationFailureReason.COLLIDES_WITH_EXISTING_ROOM);
                return false;
            }
        }

        for (BoundingBox area : this.queuedRoomAreas) {
            if (area.overlaps(roomBox)) {
                this.recordFailure(GenerationFailureReason.COLLIDES_WITH_EXISTING_ROOM);
                return false;
            }
        }

        return true;
    }

    /** Returns all rooms currently accepted into the layout. */
    public Collection<InstanceRoom> getAllRooms() {
        return this.roomAnchors.values();
    }

    protected boolean isConfiguredStartRoom(BranchingRoomDefinition room) {
        return room != null && this.dungeon.getStartRooms().containsKey(room.getNamespace());
    }

    protected boolean isAutomaticEndRoomCandidate(BranchingRoomDefinition room) {
        return room != null
                && room.getConnectors().size() == 1
                && !this.isConfiguredStartRoom(room);
    }

    protected boolean isValidEndRoomCandidate(BranchingRoomDefinition room) {
        if (room == null) {
            return false;
        }

        if (this.endRoomWhitelist != null && !this.endRoomWhitelist.isEmpty()) {
            return this.endRoomWhitelist.contains(room);
        }

        return this.isAutomaticEndRoomCandidate(room);
    }

    protected void recordFailure(GenerationFailureReason reason) {
        this.failureReasons.merge(reason, 1, Integer::sum);
    }

    protected void logFailureSummary() {
        if (this.failureReasons.isEmpty()) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        boolean firstEntry = true;
        for (Map.Entry<GenerationFailureReason, Integer> entry : this.failureReasons.entrySet()) {
            if (!firstEntry) {
                builder.append(", ");
            }
            firstEntry = false;
            builder.append(entry.getKey().name()).append("=").append(entry.getValue());
        }

        this.logger()
                .warn(
                        "Generation failure summary for dungeon '{}': {}",
                        this.dungeon.getWorldName(),
                        builder);
    }

    protected void resetGenerationState() {
        this.roomCount = 0;
        this.totalRooms = 0;
        this.countsByRoom.clear();
        this.requiredRooms.clear();
        this.roomAreas.clear();
        this.roomAnchors.clear();
        this.first = null;
        this.queue.clear();
        this.queuedRoomCount = 0;
        this.roomsToAdd = new ArrayList<>();
        this.queuedCountsByRoom = new HashMap<>();
        this.queuedRoomAreas = new ArrayList<>();
        this.queuedRoomAnchors = new HashMap<>();
        this.generationCheckpoint = null;
    }

    @Nullable
    private BranchingRoomDefinition nextWeightedRoom(
            RandomCollection<BranchingRoomDefinition> weightedRooms,
            List<BranchingRoomDefinition> invalidRooms) {
        int attempts = Math.max(1, weightedRooms.size() * 10);

        while (attempts-- > 0) {
            BranchingRoomDefinition room = weightedRooms.next();
            if (room == null) {
                return null;
            }

            if (!invalidRooms.contains(room)) {
                return room;
            }
        }

        return null;
    }

    /** Builds the connector editor hotbar menu used in room-edit sessions. */
    public void initializeConnectorEditMenu() {
        this.connectorEditMenu =
                ConnectorEditorMenus.connectorMenu(this.runtime(), () -> this.connectorDoorMenu);
        this.initializeConnectorDoorMenu();
    }

    /** Builds the connector-door editor hotbar menu. */
    public void initializeConnectorDoorMenu() {
        this.connectorDoorMenu =
                ConnectorEditorMenus.doorMenu(this.runtime(), this::getEditorTargetBlockRange);
    }

    @Override
    public Layout clone() {
        try {
            Layout clone = (Layout) super.clone();
            clone.connectorEditMenu = null;
            clone.connectorDoorMenu = null;
            clone.roomWhitelist = new ArrayList<>(this.roomWhitelist);
            clone.endRoomWhitelist = new ArrayList<>(this.endRoomWhitelist);
            clone.countsByRoom = new HashMap<>();
            clone.requiredRooms = new HashMap<>();
            clone.roomAreas = new ArrayList<>();
            clone.roomAnchors = new HashMap<>();
            clone.queue = new ArrayDeque<>();
            clone.roomsToAdd = new ArrayList<>();
            clone.queuedCountsByRoom = new HashMap<>();
            clone.queuedRoomAreas = new ArrayList<>();
            clone.queuedRoomAnchors = new HashMap<>();
            clone.first = null;
            clone.generationCheckpoint = null;
            clone.cancelled = false;
            clone.failureReasons = new ConcurrentHashMap<>();
            clone.roomCount = 0;
            clone.totalRooms = 0;
            clone.queuedRoomCount = 0;
            return clone;
        } catch (CloneNotSupportedException exception) {
            throw new AssertionError(exception);
        }
    }

    /** Builds editor controls on first use, after the layout has been fully constructed. */
    public PlayerHotbarMenu getConnectorEditMenu() {
        if (this.connectorEditMenu == null) this.initializeConnectorEditMenu();
        return this.connectorEditMenu;
    }

    /** Returns the block type used to seal unconnected connector openings. */
    public Material getClosedConnectorBlock() {
        return this.closedConnectorBlock;
    }

    /** Returns the first room in the generated layout, if generation has started. */
    public InstanceRoom getFirst() {
        return this.first;
    }

    public enum GenerationResult {
        SUCCESS(true),
        NO_START_ROOM(false, "No valid start room was found!"),
        TIMED_OUT(false, "Dungeon layout took too long to generate!"),
        BAD_LAYOUT(false, "Dungeon layout didn't generate with the necessary rooms!"),
        GENERIC_FAILURE(false, "Failed to generate layout."),
        CANCELLED(false, "Generation was cancelled."),
        INVALID_CONFIGURATION(false, "Dungeon generator configuration is invalid.");

        private final boolean passed;
        private final String msg;

        GenerationResult(boolean passed) {
            this(passed, "");
        }

        GenerationResult(boolean passed, String msg) {
            this.passed = passed;
            this.msg = msg;
        }

        /** Returns whether this result represents a successful generation. */
        public boolean isPassed() {
            return this.passed;
        }

        /** Returns a user-facing status message for this result. */
        public String getMsg() {
            return this.msg;
        }
    }
}
