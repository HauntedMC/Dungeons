package net.playavalon.mythicdungeons.utility.helpers;

import net.playavalon.mythicdungeons.utility.SimpleLocation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

public final class DirectionUtils {
   public static final BlockFace[] axis = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
   public static final BlockFace[] radial = new BlockFace[]{
      BlockFace.NORTH, BlockFace.NORTH_EAST, BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST
   };
   public static final BlockFace[] full = new BlockFace[]{
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

   public static BlockFace getDirection(float yaw) {
      return axis[Math.round(yaw / 90.0F) & 3].getOppositeFace();
   }

   public static BlockFace rotateFace(BlockFace face, int degrees) {
      SimpleLocation.Direction direction = SimpleLocation.Direction.fromBlockFace(face);
      switch (degrees) {
         case -90:
         case 270:
            direction = direction.getNextClockwise();
            break;
         case 90:
            direction = direction.getNextCounterClockwise();
            break;
         case 180:
            direction = direction.getNextClockwise().getNextClockwise();
      }

      return direction.toBlockFace();
   }

   public static BlockFace yawToFace(float yaw) {
      return yawToFace(yaw, true);
   }

   public static BlockFace yawToFace(float yaw, boolean useSubCardinalDirections) {
      return useSubCardinalDirections ? radial[Math.round(yaw / 45.0F) & 7] : axis[Math.round(yaw / 90.0F) & 3];
   }

   public static BlockFace yawToFullFace(float yaw) {
      if (yaw >= 360.0F) {
         yaw -= 360.0F;
      }

      return full[Math.round(yaw / 22.5F) & 15];
   }

   public static float faceToYaw(BlockFace face) {
      float yaw = 0.0F;
      switch (face) {
         case NORTH:
            yaw = 0.0F;
            break;
         case NORTH_NORTH_EAST:
            yaw = 22.5F;
            break;
         case NORTH_EAST:
            yaw = 45.0F;
            break;
         case EAST_NORTH_EAST:
            yaw = 67.5F;
            break;
         case EAST:
            yaw = 90.0F;
            break;
         case EAST_SOUTH_EAST:
            yaw = 112.5F;
            break;
         case SOUTH_EAST:
            yaw = 135.0F;
            break;
         case SOUTH_SOUTH_EAST:
            yaw = 157.5F;
            break;
         case SOUTH:
            yaw = 180.0F;
            break;
         case SOUTH_SOUTH_WEST:
            yaw = 202.5F;
            break;
         case SOUTH_WEST:
            yaw = 225.0F;
            break;
         case WEST_SOUTH_WEST:
            yaw = 247.5F;
            break;
         case WEST:
            yaw = 270.0F;
            break;
         case WEST_NORTH_WEST:
            yaw = 292.5F;
            break;
         case NORTH_WEST:
            yaw = 315.0F;
            break;
         case NORTH_NORTH_WEST:
            yaw = 337.5F;
      }

      return yaw;
   }

   private static BlockFace getDirection(Vector vector) {
      BlockFace dir = BlockFace.SELF;
      float minAngle = Float.MAX_VALUE;

      for (BlockFace tested : BlockFace.values()) {
         if (tested != BlockFace.SELF) {
            float angle = vector.angle(tested.getDirection());
            if (!Float.isNaN(angle) && angle < minAngle) {
               minAngle = angle;
               dir = tested;
            }
         }
      }

      return dir;
   }
}
