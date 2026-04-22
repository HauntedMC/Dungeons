package nl.hauntedmc.dungeons.generation.room;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.content.function.TeleportFunction;
import nl.hauntedmc.dungeons.generation.structure.StructurePiece;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.util.world.DirectionUtils;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.RedstoneWire.Connection;
import org.bukkit.block.data.type.Wall;
import org.bukkit.block.data.type.Wall.Height;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Cached rotated view of a branching room definition.
 *
 * <p>Rotated rooms lazily rebuild block-state snapshots and function locations for one of the four
 * cardinal rotations supported by the branching generator.
 */
public class RotatedRoom {
    private final BranchingRoomDefinition origin;
    private final int rotation;
    private Vector rotationOffset;
    private final BoundingBox bounds;
    private WeakReference<StructurePiece> schematic;
    private Location spawn;
    private final List<Connector> connectors = new ArrayList<>();
    private final Map<Location, DungeonFunction> functions = new HashMap<>();

    /**
     * Returns the rotated schematic snapshot for this orientation.
     */
    public StructurePiece getSchematic() {
        if (this.schematic == null || this.schematic.get() == null) {
            if (this.rotation == 0) {
                this.schematic = new WeakReference<>(this.origin.getSchematic());
            } else {
                StructurePiece schematic = new StructurePiece();

                for (StructurePieceBlock originBlock : this.origin.getSchematic()) {
                    StructurePieceBlock block = originBlock.clone();
                    BlockData data = block.getBlockData().clone();
                    // Many Bukkit block-data implementations store directional state separately, so
                    // each supported directional subtype must be rotated explicitly.
                    if (data instanceof Directional ori) {
                        if (ori.getFacing() != BlockFace.UP && ori.getFacing() != BlockFace.DOWN) {
                            BlockFace direction = DirectionUtils.rotateFace(ori.getFacing(), this.rotation);
                            ori.setFacing(direction);
                        }
                    }

                    if (data instanceof Rotatable ori) {
                        BlockFace face =
                                DirectionUtils.yawToFullFace(
                                        DirectionUtils.faceToYaw(ori.getRotation()) + this.rotation);
                        if (this.rotation != 180) {
                            face = face.getOppositeFace();
                        }

                        ori.setRotation(face);
                    }

                    if (data instanceof Orientable ori) {
                        if (this.rotation != 180) {
                            if (ori.getAxis() == Axis.X) {
                                ori.setAxis(Axis.Z);
                            } else if (ori.getAxis() == Axis.Z) {
                                ori.setAxis(Axis.X);
                            }
                        }
                    }

                    if (data instanceof MultipleFacing ori) {
                        Map<BlockFace, Boolean> faces = new HashMap<>();

                        for (BlockFace face : ori.getAllowedFaces()) {
                            if (face != BlockFace.UP && face != BlockFace.DOWN) {
                                faces.put(face, ori.hasFace(face));
                            }
                        }

                        for (Entry<BlockFace, Boolean> faceEntry : faces.entrySet()) {
                            BlockFace face = faceEntry.getKey();
                            boolean set = faceEntry.getValue();
                            BlockFace next = DirectionUtils.rotateFace(face, this.rotation);
                            if (!set || !faces.get(next)) {
                                ori.setFace(next, set);
                            }
                        }
                    }

                    if (data instanceof Wall ori) {
                        BlockFace[] directions =
                                new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
                        Map<BlockFace, Height> faces = new HashMap<>();

                        for (BlockFace face : directions) {
                            faces.put(face, ori.getHeight(face));
                        }

                        for (Entry<BlockFace, Height> faceEntry : faces.entrySet()) {
                            BlockFace face = faceEntry.getKey();
                            Height height = faceEntry.getValue();
                            BlockFace next = DirectionUtils.rotateFace(face, this.rotation);
                            if (height != faces.get(next)) {
                                ori.setHeight(next, height);
                            }
                        }
                    }

                    if (data instanceof RedstoneWire ori) {
                        BlockFace[] directions =
                                new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
                        Map<BlockFace, Connection> faces = new HashMap<>();

                        for (BlockFace face : directions) {
                            faces.put(face, ori.getFace(face));
                        }

                        for (Entry<BlockFace, Connection> faceEntry : faces.entrySet()) {
                            BlockFace face = faceEntry.getKey();
                            Connection connection = faceEntry.getValue();
                            BlockFace next = DirectionUtils.rotateFace(face, this.rotation);
                            if (connection != faces.get(next)) {
                                ori.setFace(next, connection);
                            }
                        }
                    }

                    block.setBlockData(data);
                    schematic.add(block);
                }

                this.schematic = new WeakReference<>(schematic);
            }
        }
        return this.schematic.get();
    }

        private org.slf4j.Logger logger() {
        return this.origin.getDungeon().getEnvironment().logger();
    }

    /**
     * Creates a cached rotated view of the provided room definition.
     */
    public RotatedRoom(BranchingRoomDefinition origin, int rotation) {
        this.origin = origin;
        this.rotation = rotation;
        BoundingBox originBounds = this.origin.getBounds();
        SimpleLocation size =
                                new SimpleLocation(
                        originBounds.getWidthX(), originBounds.getHeight(), originBounds.getWidthZ());
        this.setRotationOffset(rotation, size);
        if (origin.getSpawn() != null) {
            SimpleLocation simpleSpawn =
                    rotateLocation(SimpleLocation.from(origin.getSpawn()), rotation, size);
            this.spawn = simpleSpawn.asLocation();
            this.spawn.setYaw(origin.getSpawn().getYaw());
            float newYaw = this.spawn.getYaw() - rotation;
            if (newYaw > 180.0F) {
                newYaw -= 360.0F;
            } else if (newYaw <= -180.0F) {
                newYaw += 360.0F;
            }

            this.spawn.setYaw(newYaw);
        }

        for (Connector connector : this.origin.getConnectors()) {
            try {
                SimpleLocation location = connector.getLocation();
                SimpleLocation rotatedLocus = rotateLocation(location, rotation, size);
                Connector newCon = connector.copy(rotatedLocus);
                newCon.setDoor(rotateDoor(newCon.getDoor(), rotation, size));
                this.connectors.add(newCon);
            } catch (Throwable throwable) {
                Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
                this.logger()
                        .warn(
                                "Skipping broken connector while rotating room '{}' in dungeon '{}'. Rotation={}, reason={}",
                                this.origin.getNamespace(),
                                this.origin.getDungeon().getWorldName(),
                                this.rotation,
                                root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(),
                                root);
            }
        }

        for (Entry<Location, DungeonFunction> entry :
                this.origin.getFunctionsMapRelative().entrySet()) {
            this.tryRotateFunction(entry, size);
        }

        SimpleLocation rotatedMin =
                rotateLocation(SimpleLocation.from(originBounds.getMin()), rotation, size);
        SimpleLocation rotatedMax =
                rotateLocation(SimpleLocation.from(originBounds.getMax()), rotation, size);
        this.bounds =
                                new BoundingBox(
                        rotatedMin.getX(),
                        rotatedMin.getY(),
                        rotatedMin.getZ(),
                        rotatedMax.getX(),
                        rotatedMax.getY(),
                        rotatedMax.getZ());
    }

        private void tryRotateFunction(Entry<Location, DungeonFunction> entry, SimpleLocation size) {
        Location originalLocation = entry.getKey();
        DungeonFunction originalFunction = entry.getValue();
        if (originalFunction == null) {
            this.logger()
                    .warn(
                            "Skipping null function while rotating room '{}' in dungeon '{}'.",
                            this.origin.getNamespace(),
                            this.origin.getDungeon().getWorldName());
            return;
        }

        try {
            DungeonFunction function = originalFunction.clone();
            if (function == null) {
                this.logger()
                        .warn(
                                "Skipping function '{}' in room '{}' of dungeon '{}' because clone() returned null.",
                                originalFunction.getClass().getSimpleName(),
                                this.origin.getNamespace(),
                                this.origin.getDungeon().getWorldName());
                return;
            }

            SimpleLocation originalSimpleLocation = SimpleLocation.from(originalLocation);
            Location rotatedLocation =
                    rotateLocation(originalSimpleLocation, this.rotation, size).asLocation();
            function.setLocation(rotatedLocation);
            if (function instanceof TeleportFunction tp) {
                Location targetLoc = tp.getTeleportTarget();
                if (targetLoc == null) {
                    if (this.rotation == 0) {
                        this.logger()
                                .warn(
                                        "Skipping teleport function in room '{}' of dungeon '{}' at {},{},{} because no teleport target was configured.",
                                        this.origin.getNamespace(),
                                        this.origin.getDungeon().getWorldName(),
                                        originalLocation.getBlockX(),
                                        originalLocation.getBlockY(),
                                        originalLocation.getBlockZ());
                    }
                    return;
                }

                SimpleLocation target = SimpleLocation.from(targetLoc);
                tp.setTeleportTarget(rotateLocation(target, this.rotation, size).asLocation());
            }
            this.functions.put(rotatedLocation, function);
        } catch (Throwable throwable) {
            Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
            this.logger()
                    .warn(
                            "Skipping broken function '{}' while rotating room '{}' in dungeon '{}' at {},{},{}. Reason: {}",
                            originalFunction.getClass().getSimpleName(),
                            this.origin.getNamespace(),
                            this.origin.getDungeon().getWorldName(),
                            originalLocation == null ? -1 : originalLocation.getBlockX(),
                            originalLocation == null ? -1 : originalLocation.getBlockY(),
                            originalLocation == null ? -1 : originalLocation.getBlockZ(),
                            root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(),
                            root);
        }
    }

    /**
     * Returns valid destination rooms for this orientation.
     */
    public List<WhitelistEntry> getValidRooms() {
        return this.origin.getValidRooms();
    }

    /**
     * Rotates a local location around room origin for one of the 4 cardinal rotations.
     */
    public static SimpleLocation rotateLocation(
            SimpleLocation originalLocus, int rotationDegrees, SimpleLocation size) {
        int x = originalLocus.asVector().getBlockX();
        int y = originalLocus.asVector().getBlockY();
        int z = originalLocus.asVector().getBlockZ();

                return switch (rotationDegrees) {
            case -90, 270 ->
                                        new SimpleLocation(
                            size.getZ() - z, y, x, originalLocus.getDirection().getNextClockwise());
            case 90 ->
                                        new SimpleLocation(
                            z, y, size.getX() - x, originalLocus.getDirection().getNextCounterClockwise());
            case 180 ->
                                        new SimpleLocation(
                            size.getX() - x,
                            y,
                            size.getZ() - z,
                            originalLocus.getDirection().getNextClockwise().getNextClockwise());
            default -> originalLocus.clone();
        };
    }

    /**
     * Rotates a local location around a custom minimum corner.
     */
    public static SimpleLocation rotateLocation(
            Vector min, SimpleLocation originalLocus, int rotationDegrees, SimpleLocation size) {
        int x = originalLocus.asVector().getBlockX() - min.getBlockX();
        int y = originalLocus.asVector().getBlockY();
        int z = originalLocus.asVector().getBlockZ() - min.getBlockZ();

                return switch (rotationDegrees) {
            case -90, 270 ->
                                        new SimpleLocation(
                            size.getZ() - z, y, x, originalLocus.getDirection().getNextClockwise());
            case 90 ->
                                        new SimpleLocation(
                            z, y, size.getX() - x, originalLocus.getDirection().getNextCounterClockwise());
            case 180 ->
                                        new SimpleLocation(
                            size.getX() - x,
                            y,
                            size.getZ() - z,
                            originalLocus.getDirection().getNextClockwise().getNextClockwise());
            default -> originalLocus.clone();
        };
    }

    /**
     * Computes translation offset needed after rotating the room bounds.
     */
    public void setRotationOffset(int rotationDegrees, SimpleLocation size) {
        switch (rotationDegrees) {
            case -90:
            case 270:
                this.rotationOffset = new Vector(size.getZ(), 0.0, 0.0);
                break;
            case 90:
                this.rotationOffset = new Vector(0.0, 0.0, size.getX());
                break;
            case 180:
                this.rotationOffset = new Vector(size.getX(), 0.0, size.getZ());
                break;
            default:
                this.rotationOffset = new Vector(0, 0, 0);
        }
    }

    /**
     * Creates a rotated copy of a connector door.
     */
    public static ConnectorDoor rotateDoor(ConnectorDoor door, int rotation, SimpleLocation size) {
        ConnectorDoor newDoor = door.clone();
        newDoor.getLocations().clear();

        for (SimpleLocation loc : door.getLocations()) {
            newDoor.addLocation(rotateLocation(loc, rotation, size));
        }

        return newDoor;
    }

    /**
     * Returns the source room definition.
     */
    public BranchingRoomDefinition getOrigin() {
        return this.origin;
    }

    /**
     * Returns applied yaw rotation in degrees.
     */
    public int getRotation() {
        return this.rotation;
    }

    /**
     * Returns translation offset produced by the current rotation.
     */
    public Vector getRotationOffset() {
        return this.rotationOffset;
    }

    /**
     * Returns rotated bounds relative to room-local origin.
     */
    public BoundingBox getBounds() {
        return this.bounds;
    }

    /**
     * Returns rotated spawn location, if configured.
     */
    public Location getSpawn() {
        return this.spawn;
    }

    /**
     * Returns rotated connectors.
     */
    public List<Connector> getConnectors() {
        return this.connectors;
    }

    /**
     * Returns rotated function map keyed by room-local location.
     */
    public Map<Location, DungeonFunction> getFunctions() {
        return this.functions;
    }
}
