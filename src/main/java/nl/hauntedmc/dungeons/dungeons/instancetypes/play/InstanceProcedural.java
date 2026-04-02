package nl.hauntedmc.dungeons.dungeons.instancetypes.play;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.chunkgenerators.DungeonChunkGenerator;
import nl.hauntedmc.dungeons.api.generation.StructurePieceBlock;
import nl.hauntedmc.dungeons.api.generation.layout.Layout;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonProcedural;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

public class InstanceProcedural extends InstancePlayable {
   protected DungeonProcedural dungeon;
   private Map<UUID, InstanceRoom> roomsByUUID;
   private Collection<InstanceRoom> rooms;
   private Layout builder;

   public InstanceProcedural(DungeonProcedural dungeon, CountDownLatch latch) {
      super(dungeon, latch);
      this.dungeon = dungeon;
   }

   @Override
   public AbstractInstance init() {
      if (this.initialized) {
         return this;
      } else {
         this.initialized = true;
         this.id = this.dungeon.getInstances().size();
         File instFolder = this.getUniqueWorldName();
         new File(instFolder, "config.yml").delete();
         new File(instFolder, "uid.dat").delete();
         this.builder = this.dungeon.getLayout().clone();
         Layout.GenerationResult result = this.builder.generate();
         if (!result.isPassed()) {
            Dungeons.inst().getLogger().severe(HelperUtils.colorize("&c== FAILED TO LOAD DUNGEON!! " + result.getMsg() + " =="));
            return null;
         } else {
            this.initMapAndGenerate();
            return this;
         }
      }
   }

   protected void initMapAndGenerate() {
      this.roomsByUUID = new HashMap<>();
      this.rooms = this.builder.getAllRooms();
      Map<Vector3i, StructurePieceBlock> blocks = new ConcurrentHashMap<>();

      for (InstanceRoom room : this.rooms) {
         this.roomsByUUID.put(room.getUuid(), room);
         blocks.putAll(room.getBlocksToGenerate());
      }

      Bukkit.getScheduler()
         .runTask(Dungeons.inst(), () -> super.initMap(new DungeonChunkGenerator(this.builder.getClosedConnectorBlock(), this.rooms, blocks)));
   }

   @Override
   protected void onApplyWorldRules() {
      this.dungeon.loadGamerulesTo(this.instanceWorld);
   }

   @Override
   public void initFunctions() {
      for (InstanceRoom room : this.rooms) {
         room.init(this);
      }
   }

   @Override
   public void onLoadGame() {
      Bukkit.getScheduler().runTaskLater(Dungeons.inst(), this::startGame, 1L);
   }

   public void prepValidStartPoint() {
      InstanceRoom firstRoom = this.builder.getFirst();
      Location spawn = firstRoom.getSpawn();
      if (spawn != null) {
         spawn.setWorld(this.instanceWorld);
         if (spawn.getBlock().isSolid()) {
            spawn.add(0.0, 1.0, 0.0);
         }
      } else {
         spawn = HelperUtils.findSafeLocationInBox(this.instanceWorld, firstRoom.getBounds());
      }

      this.startLoc = spawn;
   }

   @Nullable
   public InstanceRoom getRoom(Location loc) {
      for (InstanceRoom room : this.rooms) {
         if (room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).contains(loc.toVector())) {
            return room;
         }
      }

      return null;
   }

   @Nullable
   public InstanceRoom getRoom(UUID uuid) {
      return this.roomsByUUID.get(uuid);
   }

   public DungeonProcedural getDungeon() {
      return this.dungeon;
   }

   public Map<UUID, InstanceRoom> getRoomsByUUID() {
      return this.roomsByUUID;
   }

   public Collection<InstanceRoom> getRooms() {
      return this.rooms;
   }
}
