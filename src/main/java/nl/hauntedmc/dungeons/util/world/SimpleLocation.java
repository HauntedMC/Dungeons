package nl.hauntedmc.dungeons.util.world;

import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.util.Vector;

/**
 * Lightweight serializable location value with plugin-defined cardinal direction.
 */
@TypeKey(id = "dungeons.serializable.simple_location")
@SerializableAs("dungeons.serializable.simple_location")
public class SimpleLocation implements Cloneable, ConfigSerializableModel {
    @PersistedField private double x;
    @PersistedField private double y;
    @PersistedField private double z;
    @PersistedField private SimpleLocation.Direction direction = SimpleLocation.Direction.NORTH;

    /** Creates a zero location facing north. */
    private SimpleLocation() {
        this(0.0, 0.0, 0.0, SimpleLocation.Direction.NORTH);
    }

    /** Creates a location with default north direction. */
    public SimpleLocation(double x, double y, double z) {
        this(x, y, z, SimpleLocation.Direction.NORTH);
    }

    /** Creates a location with explicit direction. */
    public SimpleLocation(double x, double y, double z, SimpleLocation.Direction direction) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.direction = direction;
    }

    /** Ensures direction is never null after deserialization. */
    @Override
    public void postDeserialize() {
        if (this.direction == null) {
            this.direction = SimpleLocation.Direction.NORTH;
        }
    }

    /** Shifts this location by a vector delta. */
    public void shift(Vector vec) {
        this.shift(vec.getX(), vec.getY(), vec.getZ());
    }

    /** Adds another simple-location delta to this location. */
    public void add(SimpleLocation diff) {
        this.shift(diff.x, diff.y, diff.z);
    }

    /** Subtracts another simple-location delta from this location. */
    public void subtract(SimpleLocation diff) {
        this.shift(-diff.x, -diff.y, -diff.z);
    }

    /** Shifts this location by explicit axis deltas. */
    public void shift(double x, double y, double z) {
        this.x += x;
        this.y += y;
        this.z += z;
    }

    /** Shifts this location forward along its current direction. */
    public void shift(double amount) {
        switch (this.direction) {
            case NORTH:
                this.z -= amount;
                break;
            case EAST:
                this.x += amount;
                break;
            case SOUTH:
                this.z += amount;
                break;
            case WEST:
                this.x -= amount;
        }
    }

    /** Converts to Bukkit location with null world. */
    public Location asLocation() {
        return new Location(null, this.x, this.y, this.z);
    }

    /** Converts to Bukkit location in the provided world. */
    public Location asLocation(World world) {
        return new Location(world, this.x, this.y, this.z);
    }

    /** Converts to Bukkit vector. */
    public Vector asVector() {
        return new Vector(this.x, this.y, this.z);
    }

    /** Creates a simple location from a Bukkit location. */
    public static SimpleLocation from(Location loc) {
        return new SimpleLocation(loc.getX(), loc.getY(), loc.getZ());
    }

    /** Creates a simple location from a block position. */
    public static SimpleLocation from(Block block) {
        return new SimpleLocation(block.getX(), block.getY(), block.getZ());
    }

    /** Creates a simple location from vector coordinates. */
    public static SimpleLocation from(Vector vector) {
        return new SimpleLocation(vector.getX(), vector.getBlockY(), vector.getBlockZ());
    }

    /** Returns a shallow clone of this value object. */
    public SimpleLocation clone() {
        try {
            return (SimpleLocation) super.clone();
        } catch (CloneNotSupportedException exception) {
            throw new AssertionError();
        }
    }

    /**
     * Returns a debug representation of coordinates and facing direction.
     */
    @Override
    public String toString() {
        return "[XYZD] " + this.x + ", " + this.y + ", " + this.z + ", " + this.direction;
    }

    /**
     * Compares coordinates only, ignoring facing direction.
     */
    @Override
    public boolean equals(Object obj) {
        return obj instanceof SimpleLocation loc
                && this.x == loc.getX()
                && this.y == loc.getY()
                && this.z == loc.getZ();
    }

    /**
     * Returns a constant hash code for compatibility with legacy behavior.
     */
    @Override
    public int hashCode() {
        return 0;
    }

    /**
     * Returns the X coordinate.
     */
    public double getX() {
        return this.x;
    }

    /**
     * Sets the X coordinate.
     */
    public void setX(double x) {
        this.x = x;
    }

    /**
     * Returns the Y coordinate.
     */
    public double getY() {
        return this.y;
    }

    /**
     * Sets the Y coordinate.
     */
    public void setY(double y) {
        this.y = y;
    }

    /**
     * Returns the Z coordinate.
     */
    public double getZ() {
        return this.z;
    }

    /**
     * Sets the Z coordinate.
     */
    public void setZ(double z) {
        this.z = z;
    }

    /**
     * Returns the current direction.
     */
    public SimpleLocation.Direction getDirection() {
        return this.direction;
    }

    /**
     * Sets the current direction.
     */
    public void setDirection(SimpleLocation.Direction direction) {
        this.direction = direction;
    }

    /** Cardinal and vertical direction enum used by {@link SimpleLocation}. */
    public enum Direction {
        NORTH(0),
        EAST(270),
        SOUTH(180),
        WEST(90),
        UP,
        DOWN;

        private int degrees;

        Direction() {}

        Direction(int degrees) {
            this.degrees = degrees;
        }

        /** Returns the opposite direction. */
        public SimpleLocation.Direction getOpposite() {
            return switch (this) {
                case EAST -> WEST;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case UP -> DOWN;
                case DOWN -> UP;
                default -> SOUTH;
            };
        }

        /** Returns whether a direction is opposite to this one. */
        public boolean isOpposite(SimpleLocation.Direction dir) {
            return switch (dir) {
                case NORTH -> this.equals(SOUTH);
                case EAST -> this.equals(WEST);
                case SOUTH -> this.equals(NORTH);
                case WEST -> this.equals(EAST);
                case UP -> this.equals(DOWN);
                case DOWN -> this.equals(UP);
            };
        }

        /** Returns next clockwise cardinal direction. */
        public SimpleLocation.Direction getNextClockwise() {
            int next = this.ordinal() + 1;
            if (next > 3) {
                next = 0;
            }

            return values()[next];
        }

        /** Returns next counter-clockwise cardinal direction. */
        public SimpleLocation.Direction getNextCounterClockwise() {
            int next = this.ordinal() - 1;
            if (next < 0) {
                next = 3;
            }

            return values()[next];
        }

        /** Converts this direction to a Bukkit block face. */
        public BlockFace toBlockFace() {
            return BlockFace.valueOf(this.toString());
        }

        /** Converts a Bukkit block face to this enum type. */
        public static SimpleLocation.Direction fromBlockFace(BlockFace face) {
            return valueOf(face.toString());
        }

        /** Returns yaw degrees for cardinal directions. */
        public int getDegrees() {
            return this.degrees;
        }
    }
}
