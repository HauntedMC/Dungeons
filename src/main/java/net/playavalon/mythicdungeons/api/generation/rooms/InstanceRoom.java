package net.playavalon.mythicdungeons.api.generation.rooms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.playavalon.mythicdungeons.api.generation.StructurePieceBlock;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionCheckpoint;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionSpawnMythicMob;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionTeleport;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionReward;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.utility.SimpleLocation;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Vector3i;

public class InstanceRoom {
   private UUID uuid;
   private final RotatedRoom source;
   private final SimpleLocation anchor;
   private final Vector offset;
   private final BoundingBox bounds;
   private SimpleLocation pastePos;
   private Location spawn;
   private final List<Connector> connectors = new ArrayList<>();
   private final Map<SimpleLocation, Connector> connectorsByLocation = new HashMap<>();
   private Connector startConnector;
   private List<Connector> usedConnectors = new ArrayList<>();
   private final Map<String, ConnectorDoor> doors = new HashMap<>();
   private boolean startRoom;
   private boolean endRoom;
   private int depth = 1;

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

   public Map<Vector3i, StructurePieceBlock> getBlocksToGenerate() {
      Map<Vector3i, StructurePieceBlock> blocks = new HashMap<>();
      Map<SimpleLocation, ConnectorDoor> doors = new HashMap<>();
      List<SimpleLocation> exclusions = new ArrayList<>();

      for (Connector con : this.connectors) {
         ConnectorDoor door = con.getDoor();
         if (this.usedConnectors.contains(con)) {
            door.setHasAdjacentRoom(true);
         }

         for (SimpleLocation loc : door.getLocations()) {
            doors.put(loc, door);
         }

         if (door.isHasAdjacentRoom()) {
            if (!con.equals(this.startConnector)) {
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
         Vector size = new Vector(this.bounds.getWidthX(), this.bounds.getHeight(), this.bounds.getWidthZ());
         Vector rotatedMin = this.rotateLocation(this.source.getOrigin().getBounds().getMin(), this.source.getRotation(), size);
         Vector rotatedPos = this.rotateLocation(Vector.fromJOML(block.getPos()), this.source.getRotation(), size);
         Vector3i target = new Vector3i((int)this.bounds.getMinX(), (int)this.bounds.getMinY(), (int)this.bounds.getMinZ());
         Vector rot = this.source.getRotationOffset();
         int localX = rotatedPos.getBlockX() - rotatedMin.getBlockX();
         int localY = rotatedPos.getBlockY() - rotatedMin.getBlockY();
         int localZ = rotatedPos.getBlockZ() - rotatedMin.getBlockZ();
         target = target.add(new Vector3i(localX, localY, localZ));
         target = target.add(new Vector3i(rot.getBlockX(), rot.getBlockY(), rot.getBlockZ()));
         SimpleLocation loc = new SimpleLocation(target.x, target.y, target.z);
         ConnectorDoor doorx = doors.get(loc);
         if (doorx != null) {
            doorx.getBlocks().put(loc, block);
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

   public List<WhitelistEntry> getValidRooms() {
      return this.source.getValidRooms();
   }

   public List<Connector> getAvailableConnectors() {
      List<Connector> conns = new ArrayList<>();

      for (Connector connector : this.connectors) {
         if (!this.usedConnectors.contains(connector)) {
            conns.add(connector);
         }
      }

      return conns;
   }

   public List<Connector> getConnectorsByDirection(SimpleLocation.Direction direction) {
      List<Connector> conns = new ArrayList<>();

      for (Connector connector : this.source.getConnectors()) {
         if (!this.usedConnectors.contains(connector) && connector.getLocation().getDirection() == direction) {
            SimpleLocation adjusted = connector.getLocation().clone();
            adjusted.shift(this.offset);
            conns.add(new Connector(adjusted));
         }
      }

      return conns;
   }

   public void setStartConnector(Connector connector) {
      this.startConnector = connector;
      this.usedConnectors.add(connector);
   }

   public void addUsedConnector(Connector connector) {
      Connector trueCon = this.connectorsByLocation.get(connector.getLocation());
      this.usedConnectors.add(trueCon);
   }

   public void init(InstanceProcedural instance) {
      this.initFunctions(instance);
      this.initDoors(instance);
   }

   public void initFunctions(InstanceProcedural instance) {
      for (Entry<Location, DungeonFunction> pair : this.source.getFunctions().entrySet()) {
         Location loc = pair.getKey().clone();
         DungeonFunction oldFunction = pair.getValue();
         DungeonFunction newFunction = oldFunction.clone();
         if (newFunction != null) {
            loc.setWorld(instance.getInstanceWorld());
            loc.add(this.offset);
            newFunction.enable(instance, loc);
            instance.getFunctions().put(loc, newFunction);
            if (newFunction instanceof FunctionReward) {
               instance.getRewardFunctions().put(newFunction.getLocation(), (FunctionReward)newFunction);
            }

            if (newFunction instanceof FunctionSpawnMythicMob spawner) {
               double newYaw = spawner.getYaw() - this.source.getRotation();
               if (newYaw > 180.0) {
                  newYaw -= 360.0;
               } else if (newYaw <= -180.0) {
                  newYaw += 360.0;
               }

               spawner.setYaw(newYaw);
            }

            if (newFunction instanceof FunctionTeleport tp) {
               Location target = tp.getInstanceLoc();
               target.add(this.offset);
               double newYaw = target.getYaw() - this.source.getRotation();
               if (newYaw > 180.0) {
                  newYaw -= 360.0;
               } else if (newYaw <= -180.0) {
                  newYaw += 360.0;
               }

               target.setYaw((float)newYaw);
            }

            if (newFunction instanceof FunctionCheckpoint check) {
               double newYaw = check.getYaw() - this.source.getRotation();
               if (newYaw > 180.0) {
                  newYaw -= 360.0;
               } else if (newYaw <= -180.0) {
                  newYaw += 360.0;
               }

               check.setYaw((float)newYaw);
            }
         }
      }
   }

   public void initDoors(InstanceProcedural instance) {
      for (Connector con : this.connectors) {
         ConnectorDoor door = con.getDoor();
         door.init(this, instance.getInstanceWorld());
      }
   }

   public void toggleDoor(String namespace) {
      ConnectorDoor door = this.doors.get(namespace);
      if (door != null) {
         door.toggleDoor();
      }
   }

   public void openDoor(String namespace) {
      ConnectorDoor door = this.doors.get(namespace);
      if (door != null) {
         door.openDoor();
      }
   }

   public void closeDoor(String namespace) {
      ConnectorDoor door = this.doors.get(namespace);
      if (door != null) {
         door.closeDoor();
      }
   }

   public void toggleValidDoors(boolean ignoreEntrance) {
      for (Connector con : this.connectors) {
         if (!ignoreEntrance || !con.equals(this.startConnector)) {
            ConnectorDoor door = con.getDoor();
            if (door.isHasAdjacentRoom()) {
               door.toggleDoor();
            }
         }
      }
   }

   public void openValidDoors(boolean ignoreEntrance) {
      for (Connector con : this.connectors) {
         ConnectorDoor door = con.getDoor();
         if (door.isHasAdjacentRoom()) {
            door.openDoor();
         }
      }
   }

   public void closeValidDoors(boolean ignoreEntrance) {
      for (Connector con : this.connectors) {
         if (!ignoreEntrance || !con.equals(this.startConnector)) {
            ConnectorDoor door = con.getDoor();
            if (door.isHasAdjacentRoom()) {
               door.closeDoor();
            }
         }
      }
   }

   public UUID getUuid() {
      return this.uuid;
   }

   public RotatedRoom getSource() {
      return this.source;
   }

   public SimpleLocation getAnchor() {
      return this.anchor;
   }

   public BoundingBox getBounds() {
      return this.bounds;
   }

   public SimpleLocation getPastePos() {
      return this.pastePos;
   }

   public Location getSpawn() {
      return this.spawn;
   }

   public List<Connector> getConnectors() {
      return this.connectors;
   }

   public Connector getStartConnector() {
      return this.startConnector;
   }

   public List<Connector> getUsedConnectors() {
      return this.usedConnectors;
   }

   public void setUsedConnectors(List<Connector> usedConnectors) {
      this.usedConnectors = usedConnectors;
   }

   public Map<String, ConnectorDoor> getDoors() {
      return this.doors;
   }

   public boolean isStartRoom() {
      return this.startRoom;
   }

   public void setStartRoom(boolean startRoom) {
      this.startRoom = startRoom;
   }

   public boolean isEndRoom() {
      return this.endRoom;
   }

   public void setEndRoom(boolean endRoom) {
      this.endRoom = endRoom;
   }

   public int getDepth() {
      return this.depth;
   }

   public void setDepth(int depth) {
      this.depth = depth;
   }
}
