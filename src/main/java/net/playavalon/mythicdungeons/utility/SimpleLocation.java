package net.playavalon.mythicdungeons.utility;

import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.config.AvalonSerializable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

public class SimpleLocation implements Cloneable, AvalonSerializable {
   @SavedField
   private double x;
   @SavedField
   private double y;
   @SavedField
   private double z;
   @SavedField
   private SimpleLocation.Direction direction;

   public SimpleLocation(double x, double y, double z) {
      this(x, y, z, SimpleLocation.Direction.NORTH);
   }

   public SimpleLocation(double x, double y, double z, SimpleLocation.Direction direction) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.direction = direction;
   }

   public void shift(Vector vec) {
      this.shift(vec.getX(), vec.getY(), vec.getZ());
   }

   public void add(SimpleLocation diff) {
      this.shift(diff.x, diff.y, diff.z);
   }

   public void subtract(SimpleLocation diff) {
      this.shift(-diff.x, -diff.y, -diff.z);
   }

   public void shift(double x, double y, double z) {
      this.x += x;
      this.y += y;
      this.z += z;
   }

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

   public void rotateDir(int degrees) {
      switch (degrees) {
         case -90:
         case 270:
            this.direction = this.direction.getNextClockwise();
            break;
         case 90:
            this.direction = this.direction.getNextCounterClockwise();
            break;
         case 180:
            this.direction = this.direction.getNextClockwise().getNextClockwise();
      }
   }

   public SimpleLocation reverse() {
      this.x = -this.x;
      this.y = -this.y;
      this.z = -this.z;
      return this;
   }

   public Location asLocation() {
      return new Location(null, this.x, this.y, this.z);
   }

   public Location asLocation(World world) {
      return new Location(world, this.x, this.y, this.z);
   }

   public Vector asVector() {
      return new Vector(this.x, this.y, this.z);
   }

   public static SimpleLocation from(Location loc) {
      return new SimpleLocation(loc.getX(), loc.getY(), loc.getZ());
   }

   public static SimpleLocation from(Block block) {
      return new SimpleLocation(block.getX(), block.getY(), block.getZ());
   }

   public static SimpleLocation from(Vector vector) {
      return new SimpleLocation(vector.getX(), vector.getBlockY(), vector.getBlockZ());
   }

   public SimpleLocation clone() {
      try {
         return (SimpleLocation)super.clone();
      } catch (CloneNotSupportedException var2) {
         throw new AssertionError();
      }
   }

   @Override
   public String toString() {
      return "[XYZD] " + this.x + ", " + this.y + ", " + this.z + ", " + this.direction;
   }

   @Override
   public boolean equals(Object obj) {
      return !(obj instanceof SimpleLocation loc) ? false : this.x == loc.getX() && this.y == loc.getY() && this.z == loc.getZ();
   }

   @Override
   public int hashCode() {
      return 0;
   }

   public double getX() {
      return this.x;
   }

   public void setX(double x) {
      this.x = x;
   }

   public double getY() {
      return this.y;
   }

   public void setY(double y) {
      this.y = y;
   }

   public double getZ() {
      return this.z;
   }

   public void setZ(double z) {
      this.z = z;
   }

   public SimpleLocation.Direction getDirection() {
      return this.direction;
   }

   public void setDirection(SimpleLocation.Direction direction) {
      this.direction = direction;
   }

   public static enum Direction {
      NORTH(0),
      EAST(270),
      SOUTH(180),
      WEST(90),
      UP,
      DOWN;

      private int degrees;

      private Direction() {
      }

      private Direction(int degrees) {
         this.degrees = degrees;
      }

      public SimpleLocation.Direction getOpposite() {
         switch (this) {
            case NORTH:
            default:
               return SOUTH;
            case EAST:
               return WEST;
            case SOUTH:
               return NORTH;
            case WEST:
               return EAST;
            case UP:
               return DOWN;
            case DOWN:
               return UP;
         }
      }

      public boolean isOpposite(SimpleLocation.Direction dir) {
         switch (dir) {
            case NORTH:
               return this.equals(SOUTH);
            case EAST:
               return this.equals(WEST);
            case SOUTH:
               return this.equals(NORTH);
            case WEST:
               return this.equals(EAST);
            case UP:
               return this.equals(DOWN);
            case DOWN:
               return this.equals(UP);
            default:
               return false;
         }
      }

      public SimpleLocation.Direction getNext(int degrees) {
         int addition = degrees / 90;
         int next = this.ordinal() + addition;
         if (next >= values().length) {
            next = 0;
         }

         return values()[next];
      }

      public SimpleLocation.Direction getNextClockwise() {
         int next = this.ordinal() + 1;
         if (next > 3) {
            next = 0;
         }

         return values()[next];
      }

      public SimpleLocation.Direction getNextCounterClockwise() {
         int next = this.ordinal() - 1;
         if (next < 0) {
            next = 3;
         }

         return values()[next];
      }

      public BlockFace toBlockFace() {
         return BlockFace.valueOf(this.toString());
      }

      public static SimpleLocation.Direction fromBlockFace(BlockFace face) {
         return valueOf(face.toString());
      }

      public static SimpleLocation.Direction fromYawExact(int yaw) {
         switch (yaw) {
            case -90:
            case 270:
               return EAST;
            case 90:
               return WEST;
            case 180:
               return SOUTH;
            default:
               return NORTH;
         }
      }

      public int getDegrees() {
         return this.degrees;
      }
   }
}
