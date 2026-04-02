package nl.hauntedmc.dungeons.dungeons.dungeontypes;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.exceptions.DungeonInitException;
import nl.hauntedmc.dungeons.api.generation.layout.Layout;
import nl.hauntedmc.dungeons.api.generation.rooms.DungeonRoomContainer;
import nl.hauntedmc.dungeons.api.parents.DungeonDifficulty;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.dungeons.instancetypes.edit.InstanceEditableProcedural;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.managers.LayoutManager;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import nl.hauntedmc.dungeons.util.tasks.ProcessTimer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

public class DungeonProcedural extends AbstractDungeon {
   private final YamlConfiguration genConfig;
   private final YamlConfiguration ruleConfig;
   private Layout layout;
   private final File roomsFolder;
   private final Map<String, DungeonRoomContainer> uniqueRooms = new HashMap<>();
   private final Map<String, DungeonRoomContainer> startRooms = new HashMap<>();

   public DungeonProcedural(@NotNull File folder, @Nullable YamlConfiguration loadedConfig) throws DungeonInitException {
      super(folder, loadedConfig);
      this.genConfig = new YamlConfiguration();

      try {
         File configFile = new File(folder, "generation.yml");
         if (!configFile.exists()) {
            Dungeons.inst().getLogger().info("Creating fresh generator config file for " + folder.getName());
            FileUtils.copyFile(new File(Dungeons.inst().getDungeonsFolder(), "default-generator.yml"), configFile);
         }

         this.genConfig.load(configFile);

         try {
            this.layout = LayoutManager.createLayoutInstance(this.genConfig.getString("Layout", "MINECRAFTY"), this, this.genConfig);
         } catch (InvocationTargetException | InstantiationException | IllegalAccessException | NoSuchMethodException var5) {
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var5.getMessage());
         }
      } catch (IOException var8) {
         throw new DungeonInitException(
            "Access of generator.yml file failed!", false, "There may be another process accessing the file, or we may not have permission."
         );
      } catch (InvalidConfigurationException var9) {
         throw new DungeonInitException("Generator config has invalid YAML! See error below...", true);
      }

      this.ruleConfig = new YamlConfiguration();

      try {
         File configFile = new File(folder, "gamerules.yml");
         if (configFile.exists()) {
            this.ruleConfig.load(configFile);
         }
      } catch (IOException var6) {
         throw new DungeonInitException(
            "Access of gamerules.yml file failed!", false, "There may be another process accessing the file, or we may not have permission."
         );
      } catch (InvalidConfigurationException var7) {
         throw new DungeonInitException("Gamerule config has invalid YAML! See error below...", true);
      }

      this.roomsFolder = new File(folder, "rooms");
      this.lobbyEnabled = false;
      this.loadRooms();
   }

   @Override
   public boolean prepInstance(Player player, String difficultyName) {
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      LangUtils.sendMessage(player, "instance.loading");
      DungeonDifficulty difficulty = this.difficultyLevels.get(difficultyName);
      Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> new ProcessTimer().run("Loading Dungeon " + this.getWorldName(), () -> {
         CountDownLatch latch = new CountDownLatch(1);
         InstancePlayable raw = this.createPlaySession(latch);
         if (raw == null) {
            this.cancelInstance(null, aPlayer);
            LangUtils.sendMessage(player, "instance.failed");
         } else {
            InstanceProcedural inst = raw.as(InstanceProcedural.class);
            if (inst != null) {
               try {
                  boolean loaded = latch.await(5L, TimeUnit.SECONDS);
                  if (!loaded) {
                     this.timeout(inst, aPlayer);
                     return;
                  }

                  inst.setDifficulty(difficulty);
                  this.instances.add(inst);
                  Dungeons.inst().getActiveInstances().add(inst);
                  inst.prepValidStartPoint();
                  if (inst.getStartLoc() == null) {
                     this.timeout(inst, aPlayer);
                     Bukkit.getScheduler().runTask(Dungeons.inst(), inst::dispose);
                     return;
                  }

                  LangUtils.sendMessage(player, "instance.loaded");
                  Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> {
                     inst.addPlayer(aPlayer);
                     aPlayer.setAwaitingDungeon(false);
                     if (Dungeons.inst().isPartiesEnabled() && aPlayer.getiDungeonParty() != null) {
                        for (Player partyPlayer : aPlayer.getiDungeonParty().getPlayers()) {
                           DungeonPlayer enteringPlayer = Dungeons.inst().getDungeonPlayer(partyPlayer);
                           if (enteringPlayer != aPlayer) {
                              inst.addPlayer(enteringPlayer);
                              enteringPlayer.setAwaitingDungeon(false);
                           }
                        }
                     }
                  }, 1L);
               } catch (InterruptedException var8) {
                  LangUtils.sendMessage(player, "instance.failed");
                  Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var8.getMessage());
               }
            }
         }
      }));
      return true;
   }

   @Override
   public InstancePlayable createPlaySession(CountDownLatch latch) {
      InstanceProcedural instance = new InstanceProcedural(this, latch);
      return instance.init() == null ? null : instance;
   }

   @Override
   public InstanceEditable createEditSession(CountDownLatch latch) {
      InstanceEditable instance = new InstanceEditableProcedural(this, latch);
      instance.init();
      return instance;
   }

   @SuppressWarnings("removal")
   public void saveGamerulesFrom(World world) {
      for (String rule : world.getGameRules()) {
         this.ruleConfig.set("Gamerule." + rule, world.getGameRuleValue(rule));
      }

      this.ruleConfig.set("Difficulty", world.getDifficulty().name());
      Runnable task = () -> {
         try {
            this.ruleConfig.save(new File(this.folder, "gamerules.yml"));
         } catch (IOException var2) {
            Dungeons.inst().getLogger().warning("Failed to save gamerules for dungeon " + this.worldName + "!");
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var2.getMessage());
         }
      };
      if (Dungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), task);
      } else {
         task.run();
      }
   }

   @SuppressWarnings("removal")
   public void loadGamerulesTo(World world) {
      for (String rule : world.getGameRules()) {
         String val = this.ruleConfig.getString("Gamerule." + rule);
         if (val != null) {
            world.setGameRuleValue(rule, val);
         }
      }

      world.setDifficulty(Difficulty.valueOf(this.ruleConfig.getString("Difficulty", "NORMAL")));
   }

   public void loadRooms() {
      this.roomsFolder.mkdir();
      File[] files = this.roomsFolder.listFiles();
      if (files != null) {
         for (File roomFile : files) {
            if (FilenameUtils.getExtension(roomFile.getName()).equals("yml")) {
               try {
                  DungeonRoomContainer room = new DungeonRoomContainer(this, roomFile);
                  this.addRoom(room);
               } catch (InvalidConfigurationException | IllegalArgumentException | YAMLException var7) {
                  Dungeons.inst().getLogger().warning("WARNING :: Could not load dungeon room '" + roomFile.getName().replace(".yml", "") + "'!");
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("├─ Contains an unsupported element! (Function, trigger, or condition!)"));
                  Dungeons.inst().getLogger().info(HelperUtils.colorize("├─ You may need to change or delete this function!"));
                  if (var7.getCause() != null) {
                     Dungeons.inst().getLogger().info(HelperUtils.colorize("&c├─ Error: " + var7.getCause().getMessage()));
                     if (!var7.getCause().getMessage().contains("FunctionLootTableRewards")) {
                        Dungeons.inst()
                           .getLogger()
                           .info(HelperUtils.colorize("&c└─ This usually happens if the element belonged to another plugin that is no longer present!"));
                     }
                  } else {
                     Dungeons.inst().getLogger().info(HelperUtils.colorize("&c├─ Error: " + var7.getMessage()));
                     if (!var7.getMessage().contains("FunctionLootTableRewards")) {
                        Dungeons.inst()
                           .getLogger()
                           .info(HelperUtils.colorize("&c└─ This usually happens if the element belonged to another plugin that is no longer present!"));
                     }
                  }
               }
            }
         }
      }
   }

   public DungeonRoomContainer defineRoom(String namespace, BoundingBox bounds) {
      if (this.uniqueRooms.containsKey(namespace)) {
         return null;
      } else {
         for (DungeonRoomContainer room : this.uniqueRooms.values()) {
            if (bounds.overlaps(room.getBounds())) {
               return null;
            }
         }

         DungeonRoomContainer roomx = new DungeonRoomContainer(this, namespace, bounds);
         this.addRoom(roomx);
         return roomx;
      }
   }

   public void addRoom(DungeonRoomContainer room) {
      this.uniqueRooms.put(room.getNamespace(), room);
      if (room.getSpawn() != null) {
         this.startRooms.put(room.getNamespace(), room);
      }
   }

   public void removeRoom(DungeonRoomContainer room) {
      this.uniqueRooms.remove(room.getNamespace());
      this.startRooms.remove(room.getNamespace());
      room.delete();
   }

   @Nullable
   public DungeonRoomContainer getRoom(String namespace) {
      return this.uniqueRooms.get(namespace);
   }

   @Nullable
   public DungeonRoomContainer getRoom(Location loc) {
      for (DungeonRoomContainer room : this.uniqueRooms.values()) {
         if (room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).contains(loc.toVector())) {
            return room;
         }
      }

      return null;
   }

   @Override
   public void addFunction(Location loc, DungeonFunction function) {
      DungeonRoomContainer room = this.getRoom(loc);
      if (room == null) {
         Dungeons.inst().getLogger().warning(HelperUtils.colorize("-- An editor tried to add a function outside of a dungeon room!"));
      } else {
         Location target = loc.clone();
         target.setWorld(null);
         function.setLocation(target);
         this.addFunction(target, function, room);
      }
   }

   public void addFunction(Location loc, DungeonFunction function, DungeonRoomContainer room) {
      room.addFunction(loc, function);
   }

   @Override
   public void removeFunction(Location loc) {
      DungeonRoomContainer room = this.getRoom(loc);
      if (room != null) {
         Location target = loc.clone();
         target.setWorld(null);
         this.removeFunction(target, room);
      }
   }

   public void removeFunction(Location loc, DungeonRoomContainer room) {
      room.removeFunction(loc);
   }

   @Override
   public void saveFunctions() {
      for (DungeonRoomContainer room : this.uniqueRooms.values()) {
         room.saveFunctions();
      }
   }

   @Override
   public Map<Location, DungeonFunction> getFunctions() {
      Map<Location, DungeonFunction> functions = new HashMap<>();

      for (DungeonRoomContainer room : this.uniqueRooms.values()) {
         functions.putAll(room.getFunctionsMapRelative());
      }

      return functions;
   }

   public YamlConfiguration getGenConfig() {
      return this.genConfig;
   }

   public YamlConfiguration getRuleConfig() {
      return this.ruleConfig;
   }

   public Layout getLayout() {
      return this.layout;
   }

   public File getRoomsFolder() {
      return this.roomsFolder;
   }

   public Map<String, DungeonRoomContainer> getUniqueRooms() {
      return this.uniqueRooms;
   }

   public Map<String, DungeonRoomContainer> getStartRooms() {
      return this.startRooms;
   }
}
