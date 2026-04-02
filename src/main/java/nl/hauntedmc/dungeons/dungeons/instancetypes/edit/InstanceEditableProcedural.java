package nl.hauntedmc.dungeons.dungeons.instancetypes.edit;

import java.io.File;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.generation.rooms.Connector;
import nl.hauntedmc.dungeons.api.generation.rooms.ConnectorDoor;
import nl.hauntedmc.dungeons.api.generation.rooms.DungeonRoomContainer;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonProcedural;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.entity.ParticleUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import nl.hauntedmc.dungeons.util.tasks.ProcessTimer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public class InstanceEditableProcedural extends InstanceEditable {
   private final DungeonProcedural dungeon;

    public InstanceEditableProcedural(DungeonProcedural dungeon, CountDownLatch latch) {
      super(dungeon, latch);
      this.dungeon = dungeon;
   }

   @Override
   public void onLoadGame() {
      this.dungeon.backupDungeon();
      File roomFolder = new File(this.instanceWorld.getWorldFolder(), "rooms");
      HelperUtils.deleteRecursively(roomFolder);
      this.loadFunctions();
      this.functionParticles = new BukkitRunnable() {
         public void run() {
            for (Entry<Location, DungeonFunction> pair : InstanceEditableProcedural.this.functions.entrySet()) {
               DungeonFunction function = pair.getValue();
               Location loc = pair.getKey().clone();
               loc.setX(loc.getX() + 0.5);
               loc.setY(loc.getY() + 0.7);
               loc.setZ(loc.getZ() + 0.5);
               DustOptions dustOptions = new DustOptions(HelperUtils.hexToColor(function.getColour()), 1.0F);

               for (DungeonPlayer aPlayer : new ArrayList<>(InstanceEditableProcedural.this.players)) {
                  Player player = aPlayer.getPlayer();
                  if (!(loc.distance(player.getLocation()) > 10.0)) {
                     player.spawnParticle(Particle.DUST, loc, 12, 0.25, 0.25, 0.25, dustOptions);
                     player.spawnParticle(Particle.END_ROD, loc, 1, 0.25, 0.25, 0.25, 0.01);
                  }
               }
            }
         }
      };
      this.functionParticles.runTaskTimer(Dungeons.inst(), 0L, 20L);
       BukkitRunnable roomParticles = new BukkitRunnable() {
           public void run() {
               for (DungeonPlayer aPlayer : new ArrayList<>(InstanceEditableProcedural.this.players)) {
                   Player player = aPlayer.getPlayer();
                   if (aPlayer.getPos1() != null && aPlayer.getPos2() != null) {
                       ParticleUtils.displayBoundingBox(player, HelperUtils.captureOffsetBoundingBox(aPlayer.getPos1(), aPlayer.getPos2()));
                   }

                   DungeonRoomContainer activeRoom = aPlayer.getActiveRoom();
                   boolean holdingTool = ItemUtils.isRoomTool(player.getInventory().getItemInMainHand());

                   for (DungeonRoomContainer room : InstanceEditableProcedural.this.dungeon.getUniqueRooms().values()) {
                       if (room == activeRoom) {
                           InstanceEditableProcedural.this.displayRoomParticles(player, room);
                       } else if (holdingTool && player.getBoundingBox().overlaps(room.getBounds().clone().expand(20.0))) {
                           InstanceEditableProcedural.this.displayRoomParticles(player, room);
                       } else {
                           InstanceEditableProcedural.this.clearRoomDisplay(player, room);
                       }
                   }
               }
           }
       };
      roomParticles.runTaskTimerAsynchronously(Dungeons.inst(), 0L, 20L);
      this.latch.countDown();
   }

   @Override
   public void saveWorld() {
      new ProcessTimer().run("Saving of " + this.dungeon.getWorldName(), () -> {
         this.instanceWorld.save();
         if (Dungeons.inst().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> this.commitWorld(this.latch));
         } else {
            this.commitWorld(this.latch);
         }
      });
   }

   @Override
   public void saveWorld(CountDownLatch latch) {
      new ProcessTimer().run("Saving of " + this.dungeon.getWorldName(), () -> {
         this.instanceWorld.save();
         this.dungeon.saveGamerulesFrom(this.instanceWorld);
         if (Dungeons.inst().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> this.commitWorld(latch));
         } else {
            this.commitWorld(latch);
         }
      });
   }

   @Override
   public void onCommitWorld() {
      Runnable commit = () -> {
         for (DungeonRoomContainer room : this.dungeon.getUniqueRooms().values()) {
            room.captureSchematic(this.instanceWorld);
         }
      };
      if (Dungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTask(Dungeons.inst(), commit);
      } else {
         commit.run();
      }
   }

   public void loadFunctions() {
      for (DungeonRoomContainer room : this.dungeon.getUniqueRooms().values()) {
         for (DungeonFunction function : room.getFunctionsMapRelative().values()) {
            this.addFunction(function);
            function.setInstance(this);
            function.initMenu();
            DungeonTrigger trigger = function.getTrigger();
            if (trigger != null) {
               trigger.initMenu();
               trigger.initConditionsMenu();
               trigger.setInstance(this);
            }
         }

         this.setRoomLabel(room);
      }
   }

   public void addFunction(DungeonFunction function) {
      Location loc = function.getLocation().clone();
      loc.setWorld(this.instanceWorld);
      this.functions.put(loc, function);
      this.addFunctionLabel(function);
   }

   public void setRoomLabel(DungeonRoomContainer room) {
      if (this.displayHandler != null) {
         Vector vec = room.getBounds().getCenter();
         Location loc = new Location(this.instanceWorld, vec.getX(), vec.getY(), vec.getZ()).add(0.5, Math.abs(room.getBounds().getHeight() / 2.0) + 1.5, 0.5);
         this.displayHandler
            .setHologram(
               loc,
               50.0F,
               "&b&l"
                  + room.getNamespace()
                  + "\n&bCount: "
                  + room.getOccurrencesString()
                  + " | Weight: "
                  + room.getWeight()
                  + " | Depth: "
                  + room.getDepthString(),
               true
            );
         if (room.getSpawn() != null) {
            Location spawn = room.getSpawn().clone().add(0.0, 1.2, 0.0);
            spawn.setWorld(this.instanceWorld);
            this.displayHandler.setHologram(spawn, 10.0F, "&eRoom Spawn", true);
         }
      }
   }

   public void removeRoomLabel(DungeonRoomContainer room) {
      if (this.displayHandler != null) {
         Vector vec = room.getBounds().getCenter();
         Location loc = new Location(this.instanceWorld, vec.getX(), vec.getY(), vec.getZ()).add(0.5, Math.abs(room.getBounds().getHeight() / 2.0) + 1.5, 0.5);
         this.displayHandler.removeHologram(loc);
         if (room.getSpawn() != null) {
            Location spawn = room.getSpawn().clone().add(0.0, 1.2, 0.0);
            spawn.setWorld(this.instanceWorld);
            this.displayHandler.removeHologram(spawn);
         }
      }
   }

   public void displayRoomParticles(Player player, DungeonRoomContainer room) {
      BoundingBox box = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      if (!(box.getWidthX() > 48.0)
         && !(box.getHeight() > 48.0)
         && !(box.getWidthZ() > 48.0)
         && !(box.getMinY() - 1.0 < this.instanceWorld.getMinHeight())) {
         ParticleUtils.displayStructureBox(player, box);
      } else {
         ParticleUtils.displayBoundingBox(player, box);
      }

      if (room.getSpawn() != null) {
         Location spawn = room.getSpawn().clone();
         spawn.add(0.0, 0.5, 0.0);
         spawn.setWorld(this.instanceWorld);
         DustOptions dustOptions = new DustOptions(Color.YELLOW, 1.0F);
         player.spawnParticle(Particle.DUST, spawn, 12, 0.1, 1.0, 0.1, dustOptions);
      }

      for (Connector connector : room.getConnectors()) {
         Location loc = connector.getLocation().asLocation();
         BoundingBox blockBox = new BoundingBox(loc.getX(), loc.getY(), loc.getZ(), loc.getX() + 1.0, loc.getY() + 1.0, loc.getZ() + 1.0);
         ParticleUtils.displayBoundingBox(player, Particle.DUST, blockBox);
         loc.setX(loc.getX() + 0.5);
         loc.setY(loc.getY() + 0.7);
         loc.setZ(loc.getZ() + 0.5);
         player.spawnParticle(Particle.END_ROD, loc, 1, 0.25, 0.25, 0.25, 0.01);
      }

      DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
      ConnectorDoor door = mPlayer.getActiveDoor();
      if (door != null) {
         door.displayParticles(player);
      }
   }

   public void clearRoomDisplay(Player player, DungeonRoomContainer room) {
      BoundingBox box = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      ParticleUtils.clearStructureBox(player, box);
   }

   public void clearRoomDisplay(DungeonRoomContainer room) {
      BoundingBox box = room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
      ParticleUtils.clearStructureBox(this.instanceWorld, box);
   }

   @Override
   public void addPlayer(DungeonPlayer aPlayer) {
      super.addPlayer(aPlayer);
      Player player = aPlayer.getPlayer();
      if (aPlayer.getSavedEditInventory() == null) {
         ItemUtils.giveOrDrop(player, ItemUtils.getRoomTool());
      }
   }

   public DungeonProcedural getDungeon() {
      return this.dungeon;
   }
}
