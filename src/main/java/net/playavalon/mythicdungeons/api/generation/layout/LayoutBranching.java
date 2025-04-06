package net.playavalon.mythicdungeons.api.generation.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.generation.rooms.Connector;
import net.playavalon.mythicdungeons.api.generation.rooms.DungeonRoomContainer;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.generation.rooms.WhitelistEntry;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonProcedural;
import net.playavalon.mythicdungeons.utility.RandomCollection;
import net.playavalon.mythicdungeons.utility.SimpleLocation;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import net.playavalon.mythicdungeons.utility.numbers.RangedNumber;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;

public class LayoutBranching extends Layout {
   private double straightness;
   private RangedNumber branchDepth;
   private RangedNumber branchOccurrences;
   private int minBranchRooms;
   private int maxBranchRooms;
   private boolean strictBranches;
   private int targetSize;
   private int branchCount;
   private List<DungeonRoomContainer> endRoomWhitelist;

   public LayoutBranching(DungeonProcedural dungeon, YamlConfiguration config) {
      super(dungeon, config);
   }

   @Override
   protected Layout.GenerationResult tryLayout() {
      if (this.DEBUG) {
         MythicDungeons.inst().getLogger().info(Util.colorize("&d=== GENERATING NEW DUNGEON LAYOUT ==="));
      }

      List<String> roomStrings = this.config.getStringList("BranchSettings.TRUNK.Rooms");
      this.roomWhitelist = new ArrayList<>();

      for (String roomName : roomStrings) {
         DungeonRoomContainer room = this.dungeon.getRoom(roomName);
         if (room != null) {
            this.roomWhitelist.add(room);
         }
      }

      roomStrings = this.config.getStringList("BranchSettings.TRUNK.EndRooms");
      this.endRoomWhitelist = new ArrayList<>();

      for (String roomNamex : roomStrings) {
         DungeonRoomContainer room = this.dungeon.getRoom(roomNamex);
         if (room != null) {
            this.endRoomWhitelist.add(room);
         }
      }

      String depthString = this.config.getString("BranchSettings.TRUNK.Depth", "0+");
      this.branchDepth = new RangedNumber(depthString);
      String occurrencesString = this.config.getString("BranchSettings.TRUNK.Occurrences", "0+");
      this.branchOccurrences = new RangedNumber(occurrencesString);
      this.straightness = this.config.getDouble("BranchSettings.TRUNK.Straightness", 0.5);
      this.minBranchRooms = this.config.getInt("BranchSettings.TRUNK.MinRooms", 8);
      this.maxBranchRooms = this.config.getInt("BranchSettings.TRUNK.MaxRooms", 16);
      this.strictBranches = this.config.getBoolean("BranchSettings.TRUNK.StrictBranchCount", true);
      this.roomCount = 0;
      this.totalRooms = 0;
      this.branchCount = 0;
      this.queuedRoomCount = 0;
      this.countsByRoom.clear();
      this.roomAreas.clear();
      this.roomAnchors.clear();
      this.queue.clear();
      this.roomsToAdd = new ArrayList<>();
      this.queuedCountsByRoom = new HashMap<>();
      this.queuedRoomAreas = new ArrayList<>();
      this.queuedRoomAnchors = new HashMap<>();
      this.targetSize = MathUtils.getRandomNumberInRange(this.minBranchRooms, this.maxBranchRooms);
      List<Integer> available = new ArrayList<>();

      for (int i = 1; i <= this.targetSize; i++) {
         available.add(i);
      }

      label82:
      for (DungeonRoomContainer room : this.dungeon.getUniqueRooms().values()) {
         if (!(room.getOccurrences().getMin() <= 0.0)) {
            for (int i = 0; i < room.getOccurrences().getMin(); i++) {
               int depth = MathUtils.getRandomNumberInRange(1, this.targetSize);

               while (this.requiredRooms.containsKey(depth) && !this.cancelled) {
                  if (this.requiredRooms.size() >= this.targetSize) {
                     continue label82;
                  }

                  depth = MathUtils.getRandomNumber(available.toArray(new Integer[0]));
                  if (depth == -1) {
                     break;
                  }
               }

               this.requiredRooms.put(depth, room);
            }
         }
      }

      Layout.GenerationResult result = this.generateTrunk();
      if (!result.isPassed()) {
         return result;
      } else {
         if (this.DEBUG) {
            MythicDungeons.inst().getLogger().info(Util.colorize("&cGenerated valid trunk! " + this.roomCount + " rooms."));
         }

         result = this.generateAllBranches();
         if (!result.isPassed()) {
            return result;
         } else if (!this.verifyLayout()) {
            return Layout.GenerationResult.BAD_LAYOUT;
         } else {
            if (this.DEBUG) {
               MythicDungeons.inst().getLogger().info(Util.colorize("&a=== FOUND VALID DUNGEON LAYOUT! ==="));
            }

            MythicDungeons.inst().getLogger().info(Util.colorize("&a" + this.totalRooms + " total rooms. " + this.branchCount + " branches generated."));
            return Layout.GenerationResult.SUCCESS;
         }
      }
   }

   protected Layout.GenerationResult generateTrunk() {
      if (!this.selectFirstRoom()) {
         return Layout.GenerationResult.NO_START_ROOM;
      } else {
         this.generationCheckpoint = this.first;
         this.queuedRoomCount = this.roomCount;
         this.queuedCountsByRoom = new HashMap<>(this.countsByRoom);
         this.queuedRoomAreas = new ArrayList<>(this.roomAreas);
         this.queuedRoomAnchors = new HashMap<>(this.roomAnchors);
         int segmentTries = 0;

         while (segmentTries <= 50) {
            segmentTries++;
            InstanceRoom prevCheckpoint = this.generationCheckpoint;
            List<Connector> usedCheckConnectors = new ArrayList<>(this.generationCheckpoint.getUsedConnectors());
            int savedRoomCount = this.queuedRoomCount;
            List<BoundingBox> savedRoomAreas = new ArrayList<>(this.queuedRoomAreas);
            Map<DungeonRoomContainer, Integer> savedCountsByRoom = new HashMap<>(this.queuedCountsByRoom);
            Map<SimpleLocation, InstanceRoom> savedRoomAnchors = new HashMap<>(this.queuedRoomAnchors);
            List<InstanceRoom> savedRoomsToAdd = new ArrayList<>(this.roomsToAdd);
            Layout.GenerationResult result = this.processQueue(25);
            if (this.DEBUG && !result.isPassed()) {
               MythicDungeons.inst().getLogger().warning(Util.colorize("Segment error: " + result.getMsg()));
            }

            if (this.queuedRoomCount >= this.minBranchRooms) {
               break;
            }

            if (this.generationCheckpoint == null) {
               this.generationCheckpoint = prevCheckpoint;
               if (this.generationCheckpoint != null) {
                  this.generationCheckpoint.setUsedConnectors(usedCheckConnectors);
                  this.queue.add(this.generationCheckpoint);
               }

               this.queuedRoomCount = savedRoomCount;
               this.queuedRoomAreas = savedRoomAreas;
               this.queuedCountsByRoom = savedCountsByRoom;
               this.queuedRoomAnchors = savedRoomAnchors;
               this.roomsToAdd = savedRoomsToAdd;
            } else {
               this.queue.add(this.generationCheckpoint);
               if (this.DEBUG) {
                  MythicDungeons.inst()
                     .getLogger()
                     .info(Util.colorize("&eGenerating trunk... (" + this.queuedRoomCount + " / " + this.minBranchRooms + " rooms)"));
               }

               segmentTries = 0;
            }
         }

         if (this.strictRooms && this.queuedRoomCount < this.minBranchRooms) {
            if (this.DEBUG) {
               MythicDungeons.inst()
                  .getLogger()
                  .warning(
                     Util.colorize("&cFailed to generate trunk :: Didn't match required size. (" + this.queuedRoomCount + " / " + this.minBranchRooms + ")")
                  );
            }

            return Layout.GenerationResult.BAD_LAYOUT;
         } else {
            this.generationCheckpoint = null;

            for (InstanceRoom room : this.roomsToAdd) {
               this.addRoom(room);
            }

            return Layout.GenerationResult.SUCCESS;
         }
      }
   }

   protected Layout.GenerationResult generateAllBranches() {
      ConfigurationSection branchesSection = this.config.getConfigurationSection("BranchSettings");
      if (branchesSection == null) {
         return Layout.GenerationResult.SUCCESS;
      } else {
         for (String path : branchesSection.getKeys(false)) {
            if (!path.equals("TRUNK")) {
               this.roomWhitelist.clear();
               this.branchCount = 0;
               List<String> roomStrings = branchesSection.getStringList(path + ".Rooms");
               this.roomWhitelist = new ArrayList<>();

               for (String roomName : roomStrings) {
                  DungeonRoomContainer room = this.dungeon.getRoom(roomName);
                  if (room != null) {
                     this.roomWhitelist.add(room);
                  }
               }

               roomStrings = branchesSection.getStringList(path + ".EndRooms");
               this.endRoomWhitelist = new ArrayList<>();

               for (String roomNamex : roomStrings) {
                  DungeonRoomContainer room = this.dungeon.getRoom(roomNamex);
                  if (room != null) {
                     this.endRoomWhitelist.add(room);
                  }
               }

               String depthString = branchesSection.getString(path + ".Depth", "0+");
               this.branchDepth = new RangedNumber(depthString);
               String occurrencesString = branchesSection.getString(path + ".Occurrences", "0+");
               this.branchOccurrences = new RangedNumber(occurrencesString);
               this.straightness = branchesSection.getDouble(path + ".Straightness", 0.5);
               this.minBranchRooms = branchesSection.getInt(path + ".MinRooms", 4);
               this.maxBranchRooms = branchesSection.getInt(path + ".MaxRooms", 8);
               this.strictRooms = branchesSection.getBoolean(path + ".StrictRoomCount", true);
               this.strictBranches = branchesSection.getBoolean(path + ".StrictBranchCount", true);
               if (this.DEBUG) {
                  MythicDungeons.inst().getLogger().info(Util.colorize("&eGenerating branch: " + path));
               }

               Layout.GenerationResult result = this.generateBranches();
               if (!result.isPassed()) {
                  if (this.DEBUG) {
                     MythicDungeons.inst().getLogger().warning(Util.colorize("-- Failed to generate branch!"));
                  }

                  return result;
               }
            }
         }

         return Layout.GenerationResult.SUCCESS;
      }
   }

   protected Layout.GenerationResult generateBranches() {
      ArrayList<InstanceRoom> rooms = new ArrayList<>(this.getAllRooms());
      rooms.removeIf(
         roomx -> roomx.isEndRoom() || roomx.isStartRoom() || roomx.getAvailableConnectors().isEmpty() || !this.branchDepth.isValueWithin(roomx.getDepth())
      );
      int targetBranches = MathUtils.getRandomNumberInRange((int)this.branchOccurrences.getMin(), (int)this.branchOccurrences.getMax());
      int branchTries = 0;

      while (branchTries <= this.branchOccurrences.getMin() * 5.0 && !rooms.isEmpty()) {
         branchTries++;
         this.queuedRoomCount = 0;
         this.roomCount = 0;
         this.targetSize = MathUtils.getRandomNumberInRange(this.minBranchRooms, this.maxBranchRooms);
         InstanceRoom branchRoom = rooms.get(MathUtils.getRandomNumberInRange(0, rooms.size() - 1));
         Layout.GenerationResult result = this.generateBranch(branchRoom);
         if (result.isPassed() && this.queuedRoomCount > 0 && this.verifyBranch()) {
            this.branchCount++;

            for (InstanceRoom room : this.roomsToAdd) {
               this.addRoom(room);
            }

            if (this.branchCount >= targetBranches) {
               break;
            }

            branchTries = 0;
            if (this.DEBUG) {
               MythicDungeons.inst()
                  .getLogger()
                  .info(Util.colorize("&aGenerated valid branch! Branch " + this.branchCount + " with " + this.roomCount + " rooms."));
            }

            if (branchRoom.getAvailableConnectors().isEmpty()) {
               rooms.remove(branchRoom);
            }
         } else if (this.DEBUG) {
            MythicDungeons.inst().getLogger().info(Util.colorize("&cFailed to generate branch :: " + result.getMsg()));
         }
      }

      return this.strictBranches && !(this.branchCount >= this.branchOccurrences.getMin())
         ? Layout.GenerationResult.BAD_LAYOUT
         : Layout.GenerationResult.SUCCESS;
   }

   protected Layout.GenerationResult generateBranch(InstanceRoom startRoom) {
      this.queue.add(startRoom);
      this.roomsToAdd = new ArrayList<>();
      this.queuedCountsByRoom = new HashMap<>(this.countsByRoom);
      this.queuedRoomAreas = new ArrayList<>();
      this.queuedRoomAnchors = new HashMap<>();
      int segmentTries = 0;

      while (segmentTries <= 50) {
         segmentTries++;
         if (this.generationCheckpoint == null) {
            this.generationCheckpoint = startRoom;
         }

         InstanceRoom prevCheckpoint = this.generationCheckpoint;
         List<Connector> usedCheckConnectors = new ArrayList<>(this.generationCheckpoint.getUsedConnectors());
         int savedRoomCount = this.queuedRoomCount;
         List<BoundingBox> savedRoomAreas = new ArrayList<>(this.queuedRoomAreas);
         Map<DungeonRoomContainer, Integer> savedCountsByRoom = new HashMap<>(this.queuedCountsByRoom);
         Map<SimpleLocation, InstanceRoom> savedRoomAnchors = new HashMap<>(this.queuedRoomAnchors);
         List<InstanceRoom> savedRoomsToAdd = new ArrayList<>(this.roomsToAdd);
         Layout.GenerationResult result = this.processQueue(25);
         if (this.DEBUG && !result.isPassed()) {
            MythicDungeons.inst().getLogger().warning(Util.colorize("Segment error: " + result.getMsg()));
         }

         if (this.queuedRoomCount >= this.minBranchRooms) {
            return Layout.GenerationResult.SUCCESS;
         }

         if (this.generationCheckpoint == null) {
            this.generationCheckpoint = prevCheckpoint;
            this.generationCheckpoint.setUsedConnectors(usedCheckConnectors);
            this.queuedRoomCount = savedRoomCount;
            this.queuedRoomAreas = savedRoomAreas;
            this.queuedCountsByRoom = savedCountsByRoom;
            this.queuedRoomAnchors = savedRoomAnchors;
            this.roomsToAdd = savedRoomsToAdd;
            this.queue.add(this.generationCheckpoint);
         } else {
            this.queue.add(this.generationCheckpoint);
            segmentTries = 0;
         }
      }

      return Layout.GenerationResult.BAD_LAYOUT;
   }

   @Override
   protected boolean tryConnectors(InstanceRoom from) {
      if (from.getAvailableConnectors().isEmpty()) {
         return false;
      } else {
         boolean straight = MathUtils.getRandomBoolean(this.straightness);
         if (straight && from.getStartConnector() != null) {
            List<Connector> connectors = from.getConnectorsByDirection(from.getStartConnector().getLocation().getDirection().getOpposite());
            Collections.shuffle(connectors);

            for (Connector connector : connectors) {
               if (this.queuedRoomCount >= this.targetSize) {
                  from.setEndRoom(true);
                  return false;
               }

               if (this.tryConnector(connector, from)) {
                  from.addUsedConnector(connector);
                  return true;
               }
            }
         }

         List<Connector> connectors = from.getAvailableConnectors();
         Collections.shuffle(connectors);

         for (Connector connector : connectors) {
            if (this.queuedRoomCount >= this.targetSize) {
               from.setEndRoom(true);
               return false;
            }

            if (this.tryConnector(connector, from)) {
               from.addUsedConnector(connector);
               return true;
            }
         }

         return false;
      }
   }

   protected boolean verifyBranch() {
      return !this.strictRooms || this.queuedRoomCount >= this.minBranchRooms;
   }

   @Override
   public RandomCollection<DungeonRoomContainer> filterRooms(Connector connector, InstanceRoom from) {
      int nextDepth = from.getDepth() + 1;
      List<DungeonRoomContainer> fullWhitelist = new ArrayList<>();
      fullWhitelist.addAll(this.roomWhitelist);
      fullWhitelist.addAll(this.endRoomWhitelist);
      List<WhitelistEntry> validRooms = connector.getValidRooms(this.dungeon, from.getSource().getOrigin());
      RandomCollection<DungeonRoomContainer> weightedRooms = new RandomCollection<>();

      for (WhitelistEntry entry : validRooms) {
         DungeonRoomContainer room = entry.getRoom(this.dungeon);
         if ((this.roomWhitelist.isEmpty() || fullWhitelist.contains(room))
            && !(nextDepth < room.getDepth().getMin())
            && (room.getDepth().getMax() == -1.0 || !(nextDepth > room.getDepth().getMax()))
            && !this.isRoomMaxedOut(room)
            && (
               this.queuedRoomCount == this.targetSize - 1
                  ? this.endRoomWhitelist.isEmpty() || this.endRoomWhitelist.contains(room)
                  : room.getConnectors().size() != 1
            )) {
            double weight = entry.getWeight();
            int count = this.getRoomCount(room);
            if (count < room.getOccurrences().getMin() && room.getDepth().getMin() == room.getDepth().getMax() && nextDepth == room.getDepth().getMin()) {
               weightedRooms.clear();
               weightedRooms.add(room.getWeight(), room);
               return weightedRooms;
            }

            weightedRooms.add(weight, room);
         }
      }

      DungeonRoomContainer requiredRoom = this.requiredRooms.get(nextDepth);
      if (requiredRoom != null) {
         if (!weightedRooms.contains(requiredRoom)) {
            this.requiredRooms.remove(nextDepth);
            this.requiredRooms.put(nextDepth + 1, requiredRoom);
         } else {
            weightedRooms.clear();
            weightedRooms.add(1.0, requiredRoom);
         }
      }

      return weightedRooms;
   }

   public double getStraightness() {
      return this.straightness;
   }

   public RangedNumber getBranchDepth() {
      return this.branchDepth;
   }

   public RangedNumber getBranchOccurrences() {
      return this.branchOccurrences;
   }

   public int getMinBranchRooms() {
      return this.minBranchRooms;
   }

   public int getMaxBranchRooms() {
      return this.maxBranchRooms;
   }

   public boolean isStrictBranches() {
      return this.strictBranches;
   }
}
