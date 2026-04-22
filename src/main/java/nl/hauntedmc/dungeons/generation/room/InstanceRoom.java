package nl.hauntedmc.dungeons.generation.room;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import nl.hauntedmc.dungeons.content.function.CheckpointFunction;
import nl.hauntedmc.dungeons.content.function.SpawnMobFunction;
import nl.hauntedmc.dungeons.content.function.TeleportFunction;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Vector3i;

/**
 * One generated room placed into a branching instance.
 *
 * <p>An instance room wraps a rotated room template with a concrete anchor, bounds, connector
 * state, and runtime door map.
 */
public class InstanceRoom {
    private final UUID uuid;
    private final RotatedRoom source;
    private final SimpleLocation anchor;
    private final Vector offset;
    private final BoundingBox bounds;
    private Location spawn;
    private final List<Connector> connectors = new ArrayList<>();
    private final Map<SimpleLocation, Connector> connectorsByLocation = new HashMap<>();
    private Connector startConnector;
    private List<Connector> usedConnectors = new ArrayList<>();
    private final Map<String, ConnectorDoor> doors = new HashMap<>();
    private boolean startRoom;
    private boolean endRoom;
    private int depth = 1;
    private BranchingInstance instance;

    /**
     * Creates a concrete room placement from a rotated room template and anchor connector.
     */
    public InstanceRoom(RotatedRoom room, SimpleLocation anchor, SimpleLocation nextConnector) {
        this.uuid = UUID.randomUUID();
        this.source = room;
        this.anchor = anchor;
        this.offset = anchor.asVector().clone().subtract(nextConnector.asVector());
        this.bounds = room.getBounds().clone().shift(this.offset);
        if (this.source.getSpawn() != null) {
            this.spawn = this.source.getSpawn().clone().add(this.offset);
        }

        for (Connector connector : this.source.getConnectors()) {
            Connector newCon = connector.copy(this.offset);
            this.connectors.add(newCon);
            this.connectorsByLocation.put(newCon.getLocation(), newCon);
            this.doors.put(newCon.getDoor().getNamespace(), newCon.getDoor());
            if (newCon.getLocation().equals(anchor)) {
                this.setStartConnector(newCon);
            }
        }
    }

    /**
     * Builds the final world block map for this room, excluding open-door blocks.
     */
    public Map<Vector3i, StructurePieceBlock> getBlocksToGenerate() {
        Map<Vector3i, StructurePieceBlock> blocks = new HashMap<>();
        Map<SimpleLocation, ConnectorDoor> doors = new HashMap<>();
        List<SimpleLocation> exclusions = new ArrayList<>();

        for (Connector connector : this.connectors) {
            ConnectorDoor door = connector.getDoor();
            door.setHasAdjacentRoom(this.usedConnectors.contains(connector));

            for (SimpleLocation loc : door.getLocations()) {
                doors.put(loc, door);
            }

            if (door.isHasAdjacentRoom()) {
                if (!connector.equals(this.startConnector)) {
                    door.setOpen(door.isStartOpen());
                    if (!door.isOpen()) {
                        continue;
                    }
                } else {
                    door.setOpen(true);
                }

                exclusions.addAll(door.getLocations());
            }
        }

        for (StructurePieceBlock block : this.source.getSchematic()) {
            // Room schematics are stored in local room coordinates, so each block is rotated and
            // translated into final instance coordinates before being queued for generation.
            Vector size =
                                        new Vector(this.bounds.getWidthX(), this.bounds.getHeight(), this.bounds.getWidthZ());
            Vector rotatedMin =
                    this.rotateLocation(
                            this.source.getOrigin().getBounds().getMin(), this.source.getRotation(), size);
            Vector rotatedPos =
                    this.rotateLocation(Vector.fromJOML(block.getPos()), this.source.getRotation(), size);
            Vector3i target =
                                        new Vector3i(
                            (int) this.bounds.getMinX(),
                            (int) this.bounds.getMinY(),
                            (int) this.bounds.getMinZ());
            Vector rot = this.source.getRotationOffset();
            int localX = rotatedPos.getBlockX() - rotatedMin.getBlockX();
            int localY = rotatedPos.getBlockY() - rotatedMin.getBlockY();
            int localZ = rotatedPos.getBlockZ() - rotatedMin.getBlockZ();
            target = target.add(new Vector3i(localX, localY, localZ));
            target = target.add(new Vector3i(rot.getBlockX(), rot.getBlockY(), rot.getBlockZ()));
            SimpleLocation loc = new SimpleLocation(target.x, target.y, target.z);
            ConnectorDoor connectorDoor = doors.get(loc);
            if (connectorDoor != null) {
                connectorDoor.getBlocks().put(loc, block);
            }

            if (!exclusions.contains(loc)) {
                blocks.put(target, block);
            }
        }

        return blocks;
    }

        private Vector rotateLocation(Vector originalLocus, int rotationDegrees, Vector size) {
        int x = originalLocus.getBlockX();
        int y = originalLocus.getBlockY();
        int z = originalLocus.getBlockZ();

                return switch (rotationDegrees) {
            case -90, 270 -> new Vector(size.getZ() - z, y, x);
            case 90 -> new Vector(z, y, size.getX() - x);
            case 180 -> new Vector(size.getX() - x, y, size.getZ() - z);
            default -> originalLocus;
        };
    }

    /**
     * Returns valid destination rooms derived from the source room definition.
     */
    public List<WhitelistEntry> getValidRooms() {
        return this.source.getValidRooms();
    }

    /**
     * Returns connectors that have not been used yet.
     */
    public List<Connector> getAvailableConnectors() {
        List<Connector> conns = new ArrayList<>();

        for (Connector connector : this.connectors) {
            if (!this.usedConnectors.contains(connector)) {
                conns.add(connector);
            }
        }

        return conns;
    }

    /**
     * Returns unused connectors facing the requested direction.
     */
    public List<Connector> getConnectorsByDirection(SimpleLocation.Direction direction) {
        List<Connector> conns = new ArrayList<>();

        for (Connector connector : this.connectors) {
            if (!this.usedConnectors.contains(connector)
                    && connector.getLocation().getDirection() == direction) {
                conns.add(connector);
            }
        }

        return conns;
    }

    /**
     * Marks the connector through which this room was entered.
     */
    public void setStartConnector(Connector connector) {
        this.startConnector = connector;
        if (!this.usedConnectors.contains(connector)) {
            this.usedConnectors.add(connector);
        }
    }

    /**
     * Marks a connector as used by resolving it against this room's connector set.
     */
    public void addUsedConnector(Connector connector) {
        Connector trueCon = this.connectorsByLocation.get(connector.getLocation());
        if (trueCon != null && !this.usedConnectors.contains(trueCon)) {
            this.usedConnectors.add(trueCon);
        }
    }

    /**
     * Initializes runtime function and door state for this placed room.
     */
    public void initialize(BranchingInstance instance) {
        this.instance = instance;
        this.initializeFunctions(instance);
        this.initializeDoors(instance);
    }

    /**
     * Clones and enables all room functions at translated runtime locations.
     */
    public void initializeFunctions(BranchingInstance instance) {
        for (Entry<Location, DungeonFunction> pair : this.source.getFunctions().entrySet()) {
            Location loc = pair.getKey().clone();
            DungeonFunction oldFunction = pair.getValue();
            DungeonFunction newFunction = oldFunction.clone();
            if (newFunction != null) {
                loc.setWorld(instance.getInstanceWorld());
                loc.add(this.offset);
                newFunction.enable(instance, loc);
                instance.getFunctions().put(loc, newFunction);
                if (newFunction instanceof RewardFunction) {
                    instance
                            .getRewardFunctions()
                            .put(newFunction.getLocation(), (RewardFunction) newFunction);
                }

                if (newFunction instanceof SpawnMobFunction spawner) {
                    double newYaw = spawner.getYaw() - this.source.getRotation();
                    if (newYaw > 180.0) {
                        newYaw -= 360.0;
                    } else if (newYaw <= -180.0) {
                        newYaw += 360.0;
                    }

                    spawner.setYaw(newYaw);
                }

                if (newFunction instanceof TeleportFunction tp) {
                    Location target = tp.getInstanceLoc();
                    target.add(this.offset);
                    double newYaw = target.getYaw() - this.source.getRotation();
                    if (newYaw > 180.0) {
                        newYaw -= 360.0;
                    } else if (newYaw <= -180.0) {
                        newYaw += 360.0;
                    }

                    target.setYaw((float) newYaw);
                }

                if (newFunction instanceof CheckpointFunction check) {
                    double newYaw = check.getYaw() - this.source.getRotation();
                    if (newYaw > 180.0) {
                        newYaw -= 360.0;
                    } else if (newYaw <= -180.0) {
                        newYaw += 360.0;
                    }

                    check.setYaw((float) newYaw);
                }
            }
        }
    }

    /**
     * Initializes door metadata against the target instance world.
     */
    public void initializeDoors(BranchingInstance instance) {
        for (Connector connector : this.connectors) {
            ConnectorDoor door = connector.getDoor();
            door.initialize(this, instance.getInstanceWorld());
        }
    }

    /**
     * Toggles the named door if present.
     */
    public void toggleDoor(String namespace) {
        ConnectorDoor door = this.doors.get(namespace);
        if (door != null) {
            door.toggleDoor();
        }
    }

    /**
     * Opens the named door if present.
     */
    public void openDoor(String namespace) {
        ConnectorDoor door = this.doors.get(namespace);
        if (door != null) {
            door.openDoor();
        }
    }

    /**
     * Closes the named door if present.
     */
    public void closeDoor(String namespace) {
        ConnectorDoor door = this.doors.get(namespace);
        if (door != null) {
            door.closeDoor();
        }
    }

    /**
     * Toggles adjacent-room doors, optionally skipping the entry connector.
     */
    public void toggleValidDoors(boolean ignoreEntrance) {
        for (Connector connector : this.connectors) {
            if (!ignoreEntrance || !connector.equals(this.startConnector)) {
                ConnectorDoor door = connector.getDoor();
                if (door.isHasAdjacentRoom()) {
                    door.toggleDoor();
                }
            }
        }
    }

    /**
     * Opens all doors with adjacent rooms.
     */
    public void openValidDoors(boolean ignoreEntrance) {
        for (Connector connector : this.connectors) {
            ConnectorDoor door = connector.getDoor();
            if (door.isHasAdjacentRoom()) {
                door.openDoor();
            }
        }
    }

    /**
     * Closes adjacent-room doors, optionally skipping the entry connector.
     */
    public void closeValidDoors(boolean ignoreEntrance) {
        for (Connector connector : this.connectors) {
            if (!ignoreEntrance || !connector.equals(this.startConnector)) {
                ConnectorDoor door = connector.getDoor();
                if (door.isHasAdjacentRoom()) {
                    door.closeDoor();
                }
            }
        }
    }

    /**
     * Returns this room instance identifier.
     */
    public UUID getUuid() {
        return this.uuid;
    }

    /**
     * Returns the rotated room template this instance was created from.
     */
    public RotatedRoom getSource() {
        return this.source;
    }

    /**
     * Returns the world anchor used to place this room.
     */
    public SimpleLocation getAnchor() {
        return this.anchor;
    }

    /**
     * Returns world-space bounds of this room instance.
     */
    public BoundingBox getBounds() {
        return this.bounds;
    }

    /**
     * Returns the translated spawn location for this room, if configured.
     */
    public Location getSpawn() {
        return this.spawn;
    }

    /**
     * Returns all connectors for this room instance.
     */
    public List<Connector> getConnectors() {
        return this.connectors;
    }

    /**
     * Returns the connector used to enter this room.
     */
    public Connector getStartConnector() {
        return this.startConnector;
    }

    /**
     * Returns connectors already consumed by generation.
     */
    public List<Connector> getUsedConnectors() {
        return this.usedConnectors;
    }

    /**
     * Replaces the set of used connectors.
     */
    public void setUsedConnectors(List<Connector> usedConnectors) {
        this.usedConnectors = new ArrayList<>(usedConnectors);
    }

    /**
     * Returns door metadata indexed by namespace.
     */
    public Map<String, ConnectorDoor> getDoors() {
        return this.doors;
    }

    /**
     * Returns the runtime instance this room belongs to.
     */
    public BranchingInstance getInstance() {
        return this.instance;
    }

    /**
     * Returns whether this room is flagged as the layout start room.
     */
    public boolean isStartRoom() {
        return this.startRoom;
    }

    /**
     * Sets whether this room is treated as start room.
     */
    public void setStartRoom(boolean startRoom) {
        this.startRoom = startRoom;
    }

    /**
     * Returns whether this room is flagged as an end room.
     */
    public boolean isEndRoom() {
        return this.endRoom;
    }

    /**
     * Sets whether this room is treated as an end room.
     */
    public void setEndRoom(boolean endRoom) {
        this.endRoom = endRoom;
    }

    /**
     * Returns generation depth for this room.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets generation depth for this room.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }
}
