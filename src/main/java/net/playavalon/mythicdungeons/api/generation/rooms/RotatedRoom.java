package net.playavalon.mythicdungeons.api.generation.rooms;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.playavalon.mythicdungeons.api.generation.StructurePiece;
import net.playavalon.mythicdungeons.api.generation.StructurePieceBlock;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionTeleport;
import net.playavalon.mythicdungeons.utility.SimpleLocation;
import net.playavalon.mythicdungeons.utility.helpers.DirectionUtils;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.block.data.type.Wall;
import org.bukkit.block.data.type.RedstoneWire.Connection;
import org.bukkit.block.data.type.Wall.Height;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class RotatedRoom {
   private final DungeonRoomContainer origin;
   private int rotation;
   private Vector rotationOffset;
   private BoundingBox bounds;
   private WeakReference<StructurePiece> schematic;
   private Location spawn;
   private final List<Connector> connectors = new ArrayList<>();
   private final Map<Location, DungeonFunction> functions = new HashMap<>();

   public StructurePiece getSchematic() {
      if (this.schematic != null && this.schematic.get() != null) {
         return this.schematic.get();
      } else {
         if (this.rotation == 0) {
            this.schematic = new WeakReference<>(this.origin.getSchematic());
         } else {
            StructurePiece schematic = new StructurePiece();

            for (StructurePieceBlock originBlock : this.origin.getSchematic()) {
               StructurePieceBlock block = originBlock.clone();
               BlockData data = block.getBlockData().clone();
               if (data instanceof Directional && this.rotation != 0) {
                  Directional ori = (Directional)data;
                  if (ori.getFacing() != BlockFace.UP && ori.getFacing() != BlockFace.DOWN) {
                     BlockFace direction = DirectionUtils.rotateFace(ori.getFacing(), this.rotation);
                     ori.setFacing(direction);
                  }
               }

               if (data instanceof Rotatable && this.rotation != 0) {
                  Rotatable ori = (Rotatable)data;
                  BlockFace face = DirectionUtils.yawToFullFace(DirectionUtils.faceToYaw(ori.getRotation()) + this.rotation);
                  if (this.rotation != 180) {
                     face = face.getOppositeFace();
                  }

                  ori.setRotation(face);
               }

               if (data instanceof Orientable && this.rotation != 0) {
                  Orientable ori = (Orientable)data;
                  if (this.rotation != 180) {
                     if (ori.getAxis() == Axis.X) {
                        ori.setAxis(Axis.Z);
                     } else if (ori.getAxis() == Axis.Z) {
                        ori.setAxis(Axis.X);
                     }
                  }
               }

               if (data instanceof MultipleFacing && this.rotation != 0) {
                  MultipleFacing ori = (MultipleFacing)data;
                  Map<BlockFace, Boolean> faces = new HashMap<>();

                  for (BlockFace face : ori.getAllowedFaces()) {
                     if (face != BlockFace.UP && face != BlockFace.DOWN) {
                        faces.put(face, ori.hasFace(face));
                     }
                  }

                  for (Entry<BlockFace, Boolean> pair : faces.entrySet()) {
                     BlockFace facex = pair.getKey();
                     boolean set = pair.getValue();
                     BlockFace next = DirectionUtils.rotateFace(facex, this.rotation);
                     if (!set || !faces.get(next)) {
                        ori.setFace(next, set);
                     }
                  }
               }

               if (data instanceof Wall && this.rotation != 0) {
                  Wall ori = (Wall)data;
                  BlockFace[] directions = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
                  Map<BlockFace, Height> faces = new HashMap<>();

                  for (BlockFace facex : directions) {
                     faces.put(facex, ori.getHeight(facex));
                  }

                  for (Entry<BlockFace, Height> pairx : faces.entrySet()) {
                     BlockFace facex = pairx.getKey();
                     Height height = pairx.getValue();
                     BlockFace next = DirectionUtils.rotateFace(facex, this.rotation);
                     if (height != faces.get(next)) {
                        ori.setHeight(next, height);
                     }
                  }
               }

               if (data instanceof RedstoneWire && this.rotation != 0) {
                  RedstoneWire ori = (RedstoneWire)data;
                  BlockFace[] directions = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
                  Map<BlockFace, Connection> faces = new HashMap<>();

                  for (BlockFace facex : directions) {
                     faces.put(facex, ori.getFace(facex));
                  }

                  for (Entry<BlockFace, Connection> pairxx : faces.entrySet()) {
                     BlockFace facex = pairxx.getKey();
                     Connection connection = pairxx.getValue();
                     BlockFace next = DirectionUtils.rotateFace(facex, this.rotation);
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

         return this.schematic.get();
      }
   }

   public void clearSchematic() {
      this.schematic = null;
   }

   public RotatedRoom(DungeonRoomContainer origin, int rotation) {
      this.origin = origin;
      this.rotation = rotation;
      BoundingBox originBounds = this.origin.getBounds();
      SimpleLocation size = new SimpleLocation(originBounds.getWidthX(), originBounds.getHeight(), originBounds.getWidthZ());
      this.setRotationOffset(rotation, size);
      if (origin.getSpawn() != null) {
         SimpleLocation simpleSpawn = rotateLocation(SimpleLocation.from(origin.getSpawn()), rotation, size);
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
         SimpleLocation location = connector.getLocation();
         SimpleLocation rotatedLocus = rotateLocation(location, rotation, size);
         Connector newCon = connector.copy(rotatedLocus);
         newCon.setDoor(rotateDoor(newCon.getDoor(), rotation, size));
         this.connectors.add(newCon);
      }

      for (Entry<Location, DungeonFunction> entry : this.origin.getFunctionsMapRelative().entrySet()) {
         DungeonFunction function = entry.getValue().clone();
         SimpleLocation loc = SimpleLocation.from(entry.getKey());
         SimpleLocation rotated = rotateLocation(loc, rotation, size);
         this.functions.put(rotated.asLocation(), function);
         if (function instanceof FunctionTeleport tp) {
            Location targetLoc = tp.getTeleportTarget();
            SimpleLocation target = SimpleLocation.from(targetLoc);
            tp.setTeleportTarget(rotateLocation(target, rotation, size).asLocation());
         }
      }

      SimpleLocation rotatedMin = rotateLocation(SimpleLocation.from(originBounds.getMin()), rotation, size);
      SimpleLocation rotatedMax = rotateLocation(SimpleLocation.from(originBounds.getMax()), rotation, size);
      this.bounds = new BoundingBox(rotatedMin.getX(), rotatedMin.getY(), rotatedMin.getZ(), rotatedMax.getX(), rotatedMax.getY(), rotatedMax.getZ());
   }

   public List<WhitelistEntry> getValidRooms() {
      return this.origin.getValidRooms();
   }

   public boolean canRoomGenerateFrom(DungeonRoomContainer room) {
      return this.getValidRooms().contains(room);
   }

   public static SimpleLocation rotateLocation(SimpleLocation originalLocus, int rotationDegrees, SimpleLocation size) {
      int x = originalLocus.asVector().getBlockX();
      int y = originalLocus.asVector().getBlockY();
      int z = originalLocus.asVector().getBlockZ();

      return switch (rotationDegrees) {
         case -90, 270 -> new SimpleLocation(size.getZ() - z, y, x, originalLocus.getDirection().getNextClockwise());
         case 90 -> new SimpleLocation(z, y, size.getX() - x, originalLocus.getDirection().getNextCounterClockwise());
         case 180 -> new SimpleLocation(size.getX() - x, y, size.getZ() - z, originalLocus.getDirection().getNextClockwise().getNextClockwise());
         default -> originalLocus.clone();
      };
   }

   public static Vector rotateVector(Vector originalLocus, int rotationDegrees, SimpleLocation size) {
      double x = originalLocus.getX();
      double y = originalLocus.getY();
      double z = originalLocus.getZ();

      return switch (rotationDegrees) {
         case -90, 270 -> new Vector(size.getZ() - z, y, x);
         case 90 -> new Vector(z, y, size.getX() - x);
         case 180 -> new Vector(size.getX() - x, y, size.getZ() - z);
         default -> originalLocus.clone();
      };
   }

   public static SimpleLocation rotateLocation(Vector min, SimpleLocation originalLocus, int rotationDegrees, SimpleLocation size) {
      int x = originalLocus.asVector().getBlockX() - min.getBlockX();
      int y = originalLocus.asVector().getBlockY();
      int z = originalLocus.asVector().getBlockZ() - min.getBlockZ();

      return switch (rotationDegrees) {
         case -90, 270 -> new SimpleLocation(size.getZ() - z, y, x, originalLocus.getDirection().getNextClockwise());
         case 90 -> new SimpleLocation(z, y, size.getX() - x, originalLocus.getDirection().getNextCounterClockwise());
         case 180 -> new SimpleLocation(size.getX() - x, y, size.getZ() - z, originalLocus.getDirection().getNextClockwise().getNextClockwise());
         default -> originalLocus.clone();
      };
   }

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

   public static ConnectorDoor rotateDoor(ConnectorDoor door, int rotation, SimpleLocation size) {
      ConnectorDoor newDoor = door.clone();
      newDoor.getLocations().clear();

      for (SimpleLocation loc : door.getLocations()) {
         newDoor.addLocation(rotateLocation(loc, rotation, size));
      }

      return newDoor;
   }

   public DungeonRoomContainer getOrigin() {
      return this.origin;
   }

   public int getRotation() {
      return this.rotation;
   }

   public Vector getRotationOffset() {
      return this.rotationOffset;
   }

   public BoundingBox getBounds() {
      return this.bounds;
   }

   public Location getSpawn() {
      return this.spawn;
   }

   public List<Connector> getConnectors() {
      return this.connectors;
   }

   public Map<Location, DungeonFunction> getFunctions() {
      return this.functions;
   }
}
