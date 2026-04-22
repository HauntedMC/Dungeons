package nl.hauntedmc.dungeons.util.world;

import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;

/**
 * Direction conversion helpers between yaw, faces, and plugin direction models.
 */
public final class DirectionUtils {
    public static final BlockFace[] axis =
            new BlockFace[] {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
    public static final BlockFace[] full =
            new BlockFace[] {
                BlockFace.NORTH,
                BlockFace.NORTH_NORTH_EAST,
                BlockFace.NORTH_EAST,
                BlockFace.EAST_NORTH_EAST,
                BlockFace.EAST,
                BlockFace.EAST_SOUTH_EAST,
                BlockFace.SOUTH_EAST,
                BlockFace.SOUTH_SOUTH_EAST,
                BlockFace.SOUTH,
                BlockFace.SOUTH_SOUTH_WEST,
                BlockFace.SOUTH_WEST,
                BlockFace.WEST_SOUTH_WEST,
                BlockFace.WEST,
                BlockFace.WEST_NORTH_WEST,
                BlockFace.NORTH_WEST,
                BlockFace.NORTH_NORTH_WEST
            };

    /** Resolves a coarse facing direction from an entity's yaw and pitch. */
    public static BlockFace getFacingDirection(Entity ent) {
        float pitch = ent.getLocation().getPitch();
        if (pitch <= -45.0F && pitch >= -90.0F) {
            return BlockFace.UP;
        } else if (pitch >= 45.0F && pitch <= 90.0F) {
            return BlockFace.DOWN;
        } else {
            float yaw = ent.getLocation().getYaw();
            return axis[Math.round(yaw / 90.0F) & 3].getOppositeFace();
        }
    }

    /** Rotates a cardinal block face by a given degree increment. */
    public static BlockFace rotateFace(BlockFace face, int degrees) {
        SimpleLocation.Direction direction = SimpleLocation.Direction.fromBlockFace(face);
        direction =
                switch (degrees) {
                    case -90, 270 -> direction.getNextClockwise();
                    case 90 -> direction.getNextCounterClockwise();
                    case 180 -> direction.getNextClockwise().getNextClockwise();
                    default -> direction;
                };

        return direction.toBlockFace();
    }

    /** Converts yaw to a 16-direction block face. */
    public static BlockFace yawToFullFace(float yaw) {
        if (yaw >= 360.0F) {
            yaw -= 360.0F;
        }

        return full[Math.round(yaw / 22.5F) & 15];
    }

    /** Converts a 16-direction block face to a yaw angle. */
    public static float faceToYaw(BlockFace face) {
        return switch (face) {
            case NORTH_NORTH_EAST -> 22.5F;
            case NORTH_EAST -> 45.0F;
            case EAST_NORTH_EAST -> 67.5F;
            case EAST -> 90.0F;
            case EAST_SOUTH_EAST -> 112.5F;
            case SOUTH_EAST -> 135.0F;
            case SOUTH_SOUTH_EAST -> 157.5F;
            case SOUTH -> 180.0F;
            case SOUTH_SOUTH_WEST -> 202.5F;
            case SOUTH_WEST -> 225.0F;
            case WEST_SOUTH_WEST -> 247.5F;
            case WEST -> 270.0F;
            case WEST_NORTH_WEST -> 292.5F;
            case NORTH_WEST -> 315.0F;
            case NORTH_NORTH_WEST -> 337.5F;
            default -> 0.0F;
        };
    }
}
