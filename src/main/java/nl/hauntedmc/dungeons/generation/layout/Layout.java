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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.RotatedRoom;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.math.RandomCollection;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for branching layout algorithms.
 *
 * <p>A layout chooses start rooms, expands connectors into new room instances, validates the final
 * room graph, and also owns the editor hotbars used to edit connector metadata.
 */
public abstract class Layout implements Cloneable {
    protected final BranchingDungeon dungeon;
    protected final YamlConfiguration config;
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
    protected final ConcurrentMap<GenerationFailureReason, Integer> failureReasons =
            new ConcurrentHashMap<>();

    /**
     * Creates a layout instance backed by one branching dungeon and generator configuration.
     */
    public Layout(BranchingDungeon dungeon, YamlConfiguration config) {
        this.dungeon = dungeon;
        this.config = config;
        this.initializeConnectorEditMenu();

        try {
            String configuredMaterial = config.getString("generator.seal_open_connectors_with", "STONE");
            this.closedConnectorBlock = Material.matchMaterial(configuredMaterial);
            if (this.closedConnectorBlock == null
                    || (this.closedConnectorBlock != Material.AIR && !this.closedConnectorBlock.isBlock())) {
                                throw new IllegalArgumentException("The provided connector fill material is invalid.");
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

        protected final org.slf4j.Logger logger() {
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
        } else {
            SimpleLocation adjusted = connector.getLocation().clone();
            adjusted.shift(1.0);
            InstanceRoom roomInst =
                    this.findRoom(from.getSource().getOrigin(), connector, adjusted, weightedRooms);
            if (roomInst == null) {
                this.recordFailure(GenerationFailureReason.NO_VALID_ORIENTATION);
                return false;
            } else {
                roomInst.setDepth(from.getDepth() + 1);
                this.queueRoom(roomInst);
                return true;
            }
        }
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

    /**
     * Validates layout and room metadata before generation starts.
     */
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

    /**
     * Runs layout generation with timeout/retry handling and returns the final outcome.
     */
    public GenerationResult generate() {
        this.cancelled = false;
        this.failureReasons.clear();
        LayoutValidationReport report = this.validateConfiguration();
        report.log(this.dungeon.getWorldName());
        if (report.hasErrors()) {
            this.recordFailure(GenerationFailureReason.INVALID_CONFIGURATION);
            return GenerationResult.INVALID_CONFIGURATION;
        }

        int timeout = PluginConfigView.getLayoutGenerationTimeoutSeconds(this.runtime().config());
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<GenerationResult> future =
                executor.submit(
                        () -> {
                            int tries = 0;

                            do {
                                // Generation retries from scratch until a valid graph is found or
                                // the configured timeout expires.
                                GenerationResult generationResult = this.tryLayout();
                                if (generationResult == GenerationResult.NO_START_ROOM) {
                                    return generationResult;
                                }

                                if (generationResult.isPassed()) {
                                    long runTime = System.currentTimeMillis() - startTime;
                                    this.logger()
                                            .info(
                                                    "Generated layout for dungeon '{}' in {}ms after {} attempts.",
                                                    this.dungeon.getWorldName(),
                                                    runTime,
                                                    tries + 1);
                                    return GenerationResult.SUCCESS;
                                }

                                tries++;
                            } while (System.currentTimeMillis() - startTime <= timeout * 1000L
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
                            "Layout generation failed for dungeon '{}'.", this.dungeon.getWorldName(), exception);
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
        } else {
            GenerationResult queueResult = this.processQueue();
            if (!queueResult.isPassed()) {
                return queueResult;
            }

            for (InstanceRoom room : this.roomsToAdd) {
                this.addRoom(room);
            }

            if (!this.verifyLayout()) {
                return GenerationResult.BAD_LAYOUT;
            } else {
                this.logger()
                        .info(
                                "Validated generated layout for dungeon '{}': {} total rooms.",
                                this.dungeon.getWorldName(),
                                this.totalRooms);
                return GenerationResult.SUCCESS;
            }
        }
    }

        protected boolean selectFirstRoom() {
        if (this.dungeon.getUniqueRooms().isEmpty()) {
            this.logger().error("Dungeon '{}' has no rooms configured.", this.dungeon.getWorldName());
            return false;
        } else {
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
            } else {
                this.addRoom(new SimpleLocation(0.0, 128.0, 0.0), first.getRandomOrientation());
                this.logger()
                        .info(
                                "Selected '{}' as the first room for dungeon '{}'.",
                                first.getNamespace(),
                                this.dungeon.getWorldName());
                return true;
            }
        }
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

                                    if (!this.getAllRooms().contains(room) && !this.roomsToAdd.contains(room)) {
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

    @Nullable protected InstanceRoom findRoom(
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
                            validConnectors.computeIfAbsent(room, k -> new ArrayList<>()).add(nextConnector);
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
                    orientations.remove(MathUtils.getRandomNumberInRange(0, orientations.size() - 1));
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

    /**
     * Returns whether the room has reached its configured maximum occurrence count.
     */
    public boolean isRoomMaxedOut(BranchingRoomDefinition room) {
        if (room.getOccurrences().getMax() == 0.0) {
            return false;
        } else {
            int count = this.getRoomCount(room);
            int max = (int) room.getOccurrences().getMax();
            return count >= (max == -1 ? Integer.MAX_VALUE : max);
        }
    }

    /**
     * Returns whether the room can be placed without colliding with existing queued/generated rooms.
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

    /**
     * Returns all rooms currently accepted into the layout.
     */
    public Collection<InstanceRoom> getAllRooms() {
        return this.roomAnchors.values();
    }

        protected boolean isConfiguredStartRoom(BranchingRoomDefinition room) {
        return room != null && this.dungeon.getStartRooms().containsKey(room.getNamespace());
    }

        protected boolean isAutomaticEndRoomCandidate(BranchingRoomDefinition room) {
        return room != null && room.getConnectors().size() == 1 && !this.isConfiguredStartRoom(room);
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

    @Nullable private BranchingRoomDefinition nextWeightedRoom(
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

    /**
     * Builds the connector editor hotbar menu used in room-edit sessions.
     */
    public void initializeConnectorEditMenu() {
        this.connectorEditMenu = PlayerHotbarMenu.createMenu();
        this.connectorEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
                        this.button.setDisplayName("&c&lBACK");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        playerSession.setActiveConnector(null);
                        playerSession.setConfirmRoomAction(false);
                        playerSession.restorePreviousHotbar(true);
                    }
                });
        this.connectorEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.OAK_DOOR);
                        this.button.setDisplayName("&e&lEdit Door");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        Connector connector = playerSession.getActiveConnector();
                        if (room != null && connector != null) {
                            ConnectorDoor door = connector.getDoor();
                            LangUtils.sendMessage(player, "editor.layout.connector-door.open-menu-info");
                            playerSession.showHotbar(Layout.this.connectorDoorMenu, true);
                            playerSession.setActiveDoor(door);
                        }
                    }
                });
        this.connectorEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.JIGSAW);
                        this.button.setDisplayName("&e&lEdit Allowed Rooms");
                        this.button.addLore("&eOpens a menu for customizing");
                        this.button.addLore("&ea whitelist of what rooms can");
                        this.button.addLore("&ebe connected to this one.");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        Layout.this.runtime().guiService().openGui(player, "connector_whitelist");
                    }
                });
        this.connectorEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.BOOK);
                        this.button.setDisplayName("&3&lCopy Connector");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        playerSession.setCutting(false);
                        playerSession.setCopying(true);
                        playerSession.setCopiedConnector(playerSession.getActiveConnector());
                        LangUtils.sendMessage(player, "editor.layout.connector.copied");
                    }
                });
        this.connectorEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.SHEARS);
                        this.button.setDisplayName("&3&lCut Connector");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        playerSession.setCopying(false);
                        playerSession.setCutting(true);
                        LangUtils.sendMessage(player, "editor.layout.connector.cut");
                    }
                });
        this.connectorEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.GLOBE_BANNER_PATTERN);
                        this.button.setDisplayName("&3&lPaste Connector");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        if (event instanceof PlayerInteractEvent interactEvent) {
                            if (interactEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
                                Player player = event.getPlayer();
                                DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                                EditableInstance instance =
                                        playerSession.getInstance().as(BranchingEditableInstance.class);
                                if (instance != null) {
                                    Location targetLocation = interactEvent.getClickedBlock().getLocation();
                                    DungeonDefinition dungeon = instance.getDungeon().as(BranchingDungeon.class);
                                    BranchingRoomDefinition room = playerSession.getActiveRoom();
                                    if (dungeon != null && room != null) {
                                        SimpleLocation simpleLoc = SimpleLocation.from(targetLocation);
                                        if (playerSession.isCopying()) {
                                            if (room.getConnector(simpleLoc) != null) {
                                                LangUtils.sendMessage(player, "editor.layout.connector.already-here");
                                            } else {
                                                Connector connector = playerSession.getCopiedConnector();
                                                if (!room.applyDirectionAtEdge(simpleLoc)) {
                                                    LangUtils.sendMessage(player, "editor.layout.connector.must-be-on-edge");
                                                } else {
                                                    Connector copiedConnector = connector.copy(simpleLoc);
                                                    room.addConnector(copiedConnector);
                                                    copiedConnector.setSuccessChance(connector.getSuccessChance());
                                                    copiedConnector.setRoomWhitelist(
                                                            new ArrayList<>(connector.getRoomWhitelist()));
                                                    copiedConnector.setRoomBlacklist(
                                                            new ArrayList<>(connector.getRoomBlacklist()));
                                                    SimpleLocation.Direction oldDir = connector.getLocation().getDirection();
                                                    SimpleLocation.Direction newDir = simpleLoc.getDirection();
                                                    int rotation = newDir.getDegrees() - oldDir.getDegrees();
                                                    Vector origin = connector.getLocation().asVector();
                                                    Vector dest = simpleLoc.asVector();
                                                    Vector offset = dest.subtract(origin);
                                                    copiedConnector.setDoor(
                                                            copiedConnector.getDoor().copy(offset, origin, rotation));
                                                    playerSession.setActiveConnector(copiedConnector);
                                                    LangUtils.sendMessage(player, "editor.layout.connector.pasted");
                                                }
                                            }
                                        } else if (playerSession.isCutting()) {
                                            if (room.getConnector(simpleLoc) != null) {
                                                LangUtils.sendMessage(player, "editor.layout.connector.already-here");
                                                return;
                                            }

                                            Connector connector = playerSession.getActiveConnector();
                                            room.applyDirectionAtEdge(simpleLoc);
                                            connector.setLocation(simpleLoc);
                                            LangUtils.sendMessage(player, "editor.layout.connector.cut-pasted");
                                            playerSession.setCutting(false);
                                        }
                                    }
                                }
                            }
                        }
                    }
                });
        this.connectorEditMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.BARRIER);
                        this.button.setDisplayName("&c&lDelete Connector");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        Connector connector = playerSession.getActiveConnector();
                        if (room != null && connector != null) {
                            if ((!connector.getRoomBlacklist().isEmpty()
                                            || !connector.getRoomWhitelist().isEmpty())
                                    && !playerSession.isConfirmRoomAction()) {
                                LangUtils.sendMessage(player, "editor.layout.connector.delete-confirm");
                                playerSession.setConfirmRoomAction(true);
                            } else {
                                playerSession.setConfirmRoomAction(false);
                                room.removeConnector(connector);
                                LangUtils.sendMessage(player, "editor.layout.connector.removed");
                                playerSession.restorePreviousHotbar(true);
                            }
                        }
                    }
                });
        this.initializeConnectorDoorMenu();
    }

    /**
     * Builds the connector-door editor hotbar menu.
     */
    public void initializeConnectorDoorMenu() {
        this.connectorDoorMenu = PlayerHotbarMenu.createMenu();
        this.connectorDoorMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
                        this.button.setDisplayName("&c&lBACK");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        playerSession.setActiveDoor(null);
                        playerSession.restorePreviousHotbar(true);
                    }
                });
        this.connectorDoorMenu.addMenuItem(
                                new ChatMenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.NAME_TAG);
                        this.button.setDisplayName("&d&lSet Door Name");
                    }

                    @Override
                                        public void onSelect(Player player) {
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        ConnectorDoor door = playerSession.getActiveDoor();
                        if (room != null && door != null) {
                            LangUtils.sendMessage(player, "editor.layout.connector-door.ask-name");
                            LangUtils.sendMessage(
                                    player,
                                    "editor.layout.connector-door.current-name",
                                    LangUtils.placeholder("name", door.getNamespace()));
                        }
                    }

                    @Override
                                        public void onInput(Player player, String message) {
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        Connector connector = playerSession.getActiveConnector();
                        if (room != null && connector != null) {
                            ConnectorDoor door = connector.getDoor();
                            if (door == null) {
                                door = new ConnectorDoor(connector);
                                connector.setDoor(door);
                            }

                            door.setNamespace(message);
                            LangUtils.sendMessage(
                                    player,
                                    "editor.layout.connector-door.name-set",
                                    LangUtils.placeholder("name", message));
                            LangUtils.sendMessage(player, "editor.layout.connector-door.name-note");
                        }
                    }
                });
        this.connectorDoorMenu.addMenuItem(
                                new MenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.EMERALD);
                        this.button.setDisplayName("&d&lAdd/Remove Block");
                    }

                    @Override
                                        public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        Block block = player.getTargetBlockExact(10);
                        if (block != null && !block.isEmpty()) {
                            DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                            BranchingRoomDefinition room = playerSession.getActiveRoom();
                            Connector connector = playerSession.getActiveConnector();
                            if (room != null && connector != null) {
                                ConnectorDoor door = connector.getDoor();
                                if (door == null) {
                                    door = new ConnectorDoor(connector);
                                    connector.setDoor(door);
                                }

                                SimpleLocation target = SimpleLocation.from(block);
                                if (door.getLocations().contains(target)) {
                                    door.removeLocation(target);
                                    LangUtils.sendMessage(player, "editor.layout.connector-door.block-removed");
                                } else {
                                    door.addLocation(target);
                                    LangUtils.sendMessage(player, "editor.layout.connector-door.block-added");
                                }
                            }
                        }
                    }
                });
        this.connectorDoorMenu.addMenuItem(
                                new ToggleMenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.IRON_DOOR);
                        this.button.setDisplayName("&d&lToggle Start Open");
                    }

                    @Override
                                        public void onSelect(Player player) {
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        Connector connector = playerSession.getActiveConnector();
                        if (room != null && connector != null) {
                            ConnectorDoor door = connector.getDoor();
                            if (door == null) {
                                door = new ConnectorDoor(connector);
                                connector.setDoor(door);
                            }

                            if (!door.isStartOpen()) {
                                LangUtils.sendMessage(player, "editor.layout.connector-door.start-open");
                            } else {
                                LangUtils.sendMessage(player, "editor.layout.connector-door.start-closed");
                            }

                            door.setStartOpen(!door.isStartOpen());
                        }
                    }
                });
        this.connectorDoorMenu.addMenuItem(
                                new ToggleMenuItem() {
                    @Override
                                        public void buildButton() {
                        this.button = new MenuButton(Material.NOTE_BLOCK);
                        this.button.setDisplayName("&d&lToggle Sound");
                    }

                    @Override
                                        public void onSelect(Player player) {
                        DungeonPlayerSession playerSession = Layout.this.playerSession(player);
                        BranchingRoomDefinition room = playerSession.getActiveRoom();
                        Connector connector = playerSession.getActiveConnector();
                        if (room != null && connector != null) {
                            ConnectorDoor door = connector.getDoor();
                            if (door == null) {
                                door = new ConnectorDoor(connector);
                                connector.setDoor(door);
                            }

                            if (door.isDisableSound()) {
                                LangUtils.sendMessage(player, "editor.layout.connector-door.sound-enabled");
                            } else {
                                LangUtils.sendMessage(player, "editor.layout.connector-door.sound-disabled");
                            }

                            door.setDisableSound(!door.isDisableSound());
                        }
                    }
                });
    }

    @Override
        public Layout clone() {
        try {
            Layout clone = (Layout) super.clone();
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
            clone.failureReasons.clear();
            return clone;
        } catch (CloneNotSupportedException exception) {
                        throw new AssertionError();
        }
    }

    /**
     * Returns the connector editor hotbar menu.
     */
    public PlayerHotbarMenu getConnectorEditMenu() {
        return this.connectorEditMenu;
    }

    /**
     * Returns the block type used to seal unconnected connector openings.
     */
    public Material getClosedConnectorBlock() {
        return this.closedConnectorBlock;
    }

    /**
     * Returns the first room in the generated layout, if generation has started.
     */
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

        /**
         * Returns whether this result represents a successful generation.
         */
        public boolean isPassed() {
            return this.passed;
        }

        /**
         * Returns a user-facing status message for this result.
         */
        public String getMsg() {
            return this.msg;
        }
    }
}
