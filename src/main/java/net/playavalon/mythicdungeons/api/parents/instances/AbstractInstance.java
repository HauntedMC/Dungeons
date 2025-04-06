package net.playavalon.mythicdungeons.api.parents.instances;

import com.onarandombox.multiverseinventories.MultiverseInventories;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import net.kyori.adventure.util.TriState;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.events.dungeon.DungeonDisposeEvent;
import net.playavalon.mythicdungeons.api.events.dungeon.DungeonEndEvent;
import net.playavalon.mythicdungeons.api.events.dungeon.PlayerLeaveDungeonEvent;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionReward;
import net.playavalon.mythicdungeons.listeners.dungeonlisteners.InstanceListener;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.DisplayHandler;
import net.playavalon.mythicdungeons.utility.ServerVersion;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.ReflectionUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import net.playavalon.mythicdungeons.utility.tasks.ProcessTimer;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.World.Environment;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.entity.TextDisplay;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

public abstract class AbstractInstance {
   protected int id;
   protected String instName;
   protected final FileConfiguration config;
   protected final AbstractDungeon dungeon;
   protected World instanceWorld;
   protected InstanceListener listener;
   protected final Map<Location, DungeonFunction> functions = new HashMap<>();
   protected final Map<Location, FunctionReward> rewardFunctions = new HashMap<>();
   protected DisplayHandler displayHandler;
   private Map<Class<? extends DungeonTrigger>, List<DungeonTrigger>> triggerListeners = new HashMap<>();
   private Map<Class<? extends DungeonFunction>, List<DungeonFunction>> functionListeners = new HashMap<>();
   protected final List<MythicPlayer> players;
   protected Location startLoc;
   protected CountDownLatch latch;
   protected boolean initialized;
   protected boolean disposing = false;

   public AbstractInstance(AbstractDungeon dungeon, CountDownLatch latch) {
      this.dungeon = dungeon;
      this.config = dungeon.getConfig();
      this.id = 0;
      this.latch = latch;
      this.players = new ArrayList<>();
      if (ServerVersion.get().isAfterOrEqual(ServerVersion.v1_19_3)) {
         this.displayHandler = new DisplayHandler();
      }
   }

   public AbstractInstance init() {
      if (this.initialized) {
         return this;
      } else {
         this.initialized = true;

         try {
            this.copyMapToWorldsFolder();
            Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> this.initMap());
         } catch (IOException var2) {
            var2.printStackTrace();
         }

         return this;
      }
   }

   protected void copyMapToWorldsFolder() throws IOException {
      this.id = this.dungeon.getInstances().size();
      File instFolder = this.getUniqueWorldName();
      FileUtils.copyDirectory(this.getDungeon().getFolder(), instFolder);
      new File(instFolder, "config.yml").delete();
      new File(instFolder, "uid.dat").delete();
   }

   protected File getUniqueWorldName() {
      this.instName = this.getDungeon().getWorldName() + "_" + this.id;
      File instFolder = new File(Bukkit.getWorldContainer(), this.instName);
      if (!instFolder.exists()) {
         return instFolder;
      } else {
         this.id++;
         return this.getUniqueWorldName();
      }
   }

   protected void initMap() {
      WorldCreator loader = new WorldCreator(this.instName);
      String genName = this.dungeon.getConfig().getString("General.ChunkGenerator", "NATURAL");
      if (!genName.equalsIgnoreCase("NATURAL")) {
         loader.generator(genName);
      } else {
         loader.type(WorldType.FLAT);
      }

      loader.generateStructures(false);
      this.initMap(loader);
   }

   protected void initMap(ChunkGenerator chunkHandler) {
      WorldCreator loader = new WorldCreator(this.instName);
      loader.generateStructures(false);
      loader.type(WorldType.FLAT);
      loader.generator(chunkHandler);
      if (ServerVersion.get().isAfterOrEqual(ServerVersion.v1_20_2)) {
         if (MythicDungeons.inst().isPaperSupport()) {
            loader.keepSpawnLoaded(TriState.TRUE);
         } else {
            loader.keepSpawnInMemory(false);
         }
      }

      this.initMap(loader);
   }

   protected void initMap(WorldCreator loader) {
      String worldDimension = this.config.getString("General.Dimension", "NORMAL").toUpperCase(Locale.ROOT);

      try {
         loader.environment(Environment.valueOf(worldDimension));
      } catch (IllegalArgumentException var4) {
         MythicDungeons.inst().getLogger().info(MythicDungeons.debugPrefix + Util.colorize("&cERROR :: World dimension is invalid: '" + worldDimension + "'"));
         MythicDungeons.inst().getLogger().info(MythicDungeons.debugPrefix + Util.colorize("&cMust be one of the following: NORMAL, NETHER, THE_END"));
      }

      new ProcessTimer().run("Loading world into memory " + this.instName, () -> this.instanceWorld = loader.createWorld());
      this.applyWorldRules();
      if (!this.isEditInstance()) {
         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), this::cleanTextDisplaysOnStart, 1L);
      }

      this.loadGame();
   }

   protected void applyWorldRules() {
      this.instanceWorld.setKeepSpawnInMemory(false);
      this.instanceWorld.setAutoSave(false);
      if (!this.config.getBoolean("Rules.SpawnMobs", false)) {
         for (SpawnCategory cat : SpawnCategory.values()) {
            if (cat != SpawnCategory.MISC) {
               this.instanceWorld.setTicksPerSpawns(cat, 0);
            }
         }
      } else if (!this.config.getBoolean("Rules.SpawnAnimals", false)) {
         this.instanceWorld.setTicksPerSpawns(SpawnCategory.ANIMAL, 0);
         this.instanceWorld.setTicksPerSpawns(SpawnCategory.AMBIENT, 0);
         this.instanceWorld.setTicksPerSpawns(SpawnCategory.AXOLOTL, 0);
         this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_AMBIENT, 0);
         this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_ANIMAL, 0);
         this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_UNDERGROUND_CREATURE, 0);
      } else if (!this.config.getBoolean("Rules.SpawnMonsters", false)) {
         this.instanceWorld.setTicksPerSpawns(SpawnCategory.MONSTER, 0);
      }

      if (this.config.getBoolean("Rules.DisableRandomTick", true)) {
         this.instanceWorld.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
      }

      this.onApplyWorldRules();
   }

   protected void onApplyWorldRules() {
   }

   protected void loadGame() {
      this.startLoc = this.dungeon.getStartSpawn();
      if (this.startLoc == null) {
         this.startLoc = this.instanceWorld.getSpawnLocation();
      }

      this.startLoc = this.startLoc.clone();
      this.startLoc.setWorld(this.instanceWorld);
      if (MythicDungeons.inst().getMultiverseInv() != null) {
         MultiverseInventories invs = MythicDungeons.inst().getMultiverseInv();
         invs.getGroupManager()
            .getGroup(MythicDungeons.inst().getConfig().getString("General.MultiverseGroup", "default"))
            .addWorld(this.instanceWorld.getName(), false);
      }

      this.onLoadGame();
      if (this.latch != null) {
         this.latch.countDown();
      }
   }

   protected void onLoadGame() {
   }

   public void dispose() {
      if (!this.disposing) {
         if (this.players.size() <= 0) {
            if (this.instanceWorld.getPlayerCount() <= 0) {
               this.disposing = true;
               Runnable disposal = () -> {
                  MythicDungeons.inst().getLogger().info(Util.colorize("&dCleaning up instance '" + this.instanceWorld.getName() + "'..."));

                  try {
                     this.onDispose();
                  } catch (Exception var2x) {
                     MythicDungeons.inst()
                        .getLogger()
                        .warning(MythicDungeons.debugPrefix + Util.colorize("&eWARNING :: Failed to clean up dungeon instance " + this.instanceWorld.getName()));
                     var2x.printStackTrace();
                  }

                  this.functions.clear();
                  this.rewardFunctions.clear();
                  this.listener = null;
                  this.cleanTextDisplays();
                  this.displayHandler = null;
                  Bukkit.getScheduler()
                     .runTaskLater(
                        MythicDungeons.inst(),
                        () -> {
                           try {
                              CountDownLatch latch = new CountDownLatch(1);
                              this.saveWorld(latch);
                              new ProcessTimer()
                                 .run("Unloading dungeon world " + this.instanceWorld.getName(), () -> Bukkit.unloadWorld(this.instanceWorld, false));
                              if (MythicDungeons.inst().isEnabled()) {
                                 Bukkit.getScheduler().runTaskLaterAsynchronously(MythicDungeons.inst(), () -> this.cleanWorldFiles(latch), 100L);
                              } else {
                                 this.cleanWorldFiles(latch);
                              }
                           } catch (Exception var2xx) {
                              if (Bukkit.getWorld(this.instanceWorld.getName()) == null) {
                                 return;
                              }

                              MythicDungeons.inst()
                                 .getLogger()
                                 .severe(
                                    MythicDungeons.debugPrefix
                                       + Util.colorize("&cERROR :: Error encountered while unloading dungeon world " + this.instanceWorld.getName())
                                 );
                              MythicDungeons.inst()
                                 .getLogger()
                                 .severe(
                                    MythicDungeons.debugPrefix
                                       + Util.colorize("&c-- The dungeon world is STILL LOADED! Please share this error with the developers!")
                                 );
                              var2xx.printStackTrace();
                           }
                        },
                        1L
                     );
                  this.dungeon.removeInstance(this);
                  Bukkit.getPluginManager().callEvent(new DungeonDisposeEvent(this));
               };
               int delay = this.dungeon.getConfig().getInt("General.CleanupDelay", 0);
               if (MythicDungeons.inst().isEnabled() && delay > 0 && !this.isEditInstance()) {
                  Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), disposal, delay);
               } else {
                  disposal.run();
               }

               if (MythicDungeons.inst().getMultiverseInv() != null) {
                  MultiverseInventories invs = MythicDungeons.inst().getMultiverseInv();
                  invs.getGroupManager()
                     .getGroup(MythicDungeons.inst().getConfig().getString("General.MultiverseGroup", "default"))
                     .removeWorld(this.instanceWorld.getName(), false);
               }
            }
         }
      }
   }

   public abstract void onDispose();

   protected void cleanWorldFiles(CountDownLatch latch) {
      try {
         if (this.isEditInstance()) {
            latch.await();
         }

         FileUtils.deleteDirectory(this.instanceWorld.getWorldFolder());
      } catch (DirectoryNotEmptyException var3) {
         Util.deleteRecursively(this.instanceWorld.getWorldFolder());
      } catch (InterruptedException | IOException var4) {
         MythicDungeons.inst()
            .getLogger()
            .warning(MythicDungeons.debugPrefix + Util.colorize("&eWARNING :: Could not clean out dungeon files for " + this.instanceWorld.getName()));
         MythicDungeons.inst()
            .getLogger()
            .warning(
               MythicDungeons.debugPrefix
                  + Util.colorize("&e-- The world is already unloaded; this will just cause a little file clutter that will be fixed next restart.")
            );
         var4.printStackTrace();
      }

      this.instanceWorld = null;
   }

   public void addPlayer(MythicPlayer aPlayer) {
      if (!this.players.contains(aPlayer)) {
         Player player = aPlayer.getPlayer();
         this.players.add(aPlayer);
         aPlayer.setInstance(this);
         aPlayer.setSavedPosition(player.getLocation());
         aPlayer.setSavedGameMode(player.getGameMode());
         aPlayer.setSavedExp(player.getExp());
         aPlayer.setSavedLevel(player.getLevel());
         ItemStack[] mvInventory = player.getInventory().getContents();
         double mvHealth = player.getHealth();
         int mvFood = player.getFoodLevel();
         float mvExp = player.getExp();
         int mvLevel = player.getLevel();
         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
            if (!this.config.getBoolean("General.KeepInventoryOnEnter", true)) {
               player.getInventory().clear();
            }

            if (!this.config.getBoolean("General.KeepHealthOnEnter", false)) {
               player.setHealth(player.getMaxHealth());
            }

            if (!this.config.getBoolean("General.KeepFoodOnEnter", false)) {
               player.setFoodLevel(30);
            }

            if (!this.config.getBoolean("General.KeepPotionEffectsOnEnter", false)) {
               for (PotionEffect effect : player.getActivePotionEffects()) {
                  player.removePotionEffect(effect.getType());
               }
            }

            if (!this.config.getBoolean("General.KeepExpOnEnter", true)) {
               player.setExp(0.0F);
               player.setLevel(0);
            }
         }, 1L);
      }
   }

   public void removePlayer(MythicPlayer mPlayer) {
      this.removePlayer(mPlayer, true);
   }

   public void removePlayer(MythicPlayer aPlayer, boolean force) {
      Player player = aPlayer.getPlayer();
      if (!this.players.contains(aPlayer)) {
         if (aPlayer.getInstance() == this && player.isOnline() && aPlayer.getSavedPosition() != null) {
            aPlayer.setInstance(null);
            Util.forceTeleport(player, aPlayer.getSavedPosition());
            aPlayer.setSavedPosition(null);
         }
      } else {
         if (this.players.size() == 1) {
            Bukkit.getPluginManager().callEvent(new DungeonEndEvent(this));
         }

         PlayerLeaveDungeonEvent leaveEvent = new PlayerLeaveDungeonEvent(this, aPlayer);
         Bukkit.getPluginManager().callEvent(leaveEvent);
         if (force || !leaveEvent.isCancelled()) {
            if (this.dungeon.isCooldownOnLeave()) {
               this.dungeon.addAccessCooldown(player);
            }

            this.players.remove(aPlayer);
            player.setGameMode(aPlayer.getSavedGameMode());
            aPlayer.setInstance(null);
            aPlayer.setDungeonRespawn(null);
            if (player.isOnline()) {
               aPlayer.clearExitLocation();
            }

            PlayerInventory inv = player.getInventory();
            Player target = null;
            boolean foundKeys = false;
            boolean foundDungeonItems = false;

            for (ItemStack item : inv) {
               if (ItemUtils.verifyKeyItem(item)) {
                  inv.remove(item);
                  if (!this.players.isEmpty()) {
                     foundKeys = true;
                     target = this.players.get(0).getPlayer();
                     ItemUtils.giveOrDrop(target, item);
                  }
               }

               if (ItemUtils.verifyDungeonItem(item)) {
                  inv.remove(item);
                  if (!this.players.isEmpty() && this.dungeon.getConfig().getBoolean("General.DungeonItemInheritance", true)) {
                     foundDungeonItems = true;
                     target = this.players.get(0).getPlayer();
                     ItemUtils.giveOrDrop(target, item);
                  }
               }
            }

            ItemStack offhand = inv.getItemInOffHand();
            if (ItemUtils.verifyKeyItem(offhand)) {
               inv.setItemInOffHand(null);
               if (!this.players.isEmpty()) {
                  foundKeys = true;
                  target = this.players.get(0).getPlayer();
                  ItemUtils.giveOrDrop(target, offhand);
               }
            }

            if (ItemUtils.verifyDungeonItem(offhand)) {
               inv.setItemInOffHand(null);
               if (!this.players.isEmpty() && this.dungeon.getConfig().getBoolean("General.DungeonItemInheritance", true)) {
                  foundDungeonItems = true;
                  target = this.players.get(0).getPlayer();
                  ItemUtils.giveOrDrop(target, offhand);
               }
            }

            if (target != null) {
               if (foundKeys) {
                  this.messagePlayers(LangUtils.getMessage("instance.events.key-inheritance", target.getName()));
               }

               if (foundDungeonItems) {
                  this.messagePlayers(LangUtils.getMessage("instance.events.dungeon-item-inheritance", target.getName()));
               }
            }

            player.updateInventory();
            aPlayer.setMvInventory(player.getInventory().getContents());
            if (player.isOnline() && aPlayer.getSavedPosition() != null) {
               Util.forceTeleport(player, aPlayer.getSavedPosition());
               aPlayer.setSavedPosition(null);
            }

            player.setLastDamageCause(null);
            if (MythicDungeons.inst().isEnabled()) {
               Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> ReflectionUtils.updateCombatTracker(player), 310L);
            }
         }
      }
   }

   public void messagePlayers(String message) {
      for (MythicPlayer aPlayer : this.players) {
         aPlayer.getPlayer().sendMessage(message);
      }
   }

   public void setTextDisplayHologram(Location loc, float range, String text, boolean isLabel) {
      if (this.displayHandler != null) {
         this.displayHandler.setHologram(loc, range, text, isLabel);
      }
   }

   public void removeTextDisplayHologram(Location loc) {
      if (this.displayHandler != null) {
         this.displayHandler.removeHologram(loc);
      }
   }

   public void showTextDisplayHologram(Location loc, float range) {
      if (this.displayHandler != null) {
         this.displayHandler.showHologram(loc, range);
      }
   }

   public void hideTextDisplayHologram(Location loc) {
      if (this.displayHandler != null) {
         this.displayHandler.hideHologram(loc);
      }
   }

   public void cleanTextDisplays() {
      if (this.displayHandler != null) {
         this.displayHandler.clear();
      }
   }

   public void cleanTextDisplaysOnStart() {
      Collection<TextDisplay> displays = this.instanceWorld.getEntitiesByClass(TextDisplay.class);
      NamespacedKey key = new NamespacedKey(MythicDungeons.inst(), "dungeonhologram");

      for (TextDisplay display : displays) {
         PersistentDataContainer data = display.getPersistentDataContainer();
         if (data.has(key, PersistentDataType.BOOLEAN)) {
            display.remove();
         }
      }
   }

   public boolean isPlayInstance() {
      return this instanceof InstancePlayable;
   }

   public boolean isEditInstance() {
      return this instanceof InstanceEditable;
   }

   @Nullable
   public InstancePlayable asPlayInstance() {
      return this.isPlayInstance() ? (InstancePlayable)this : null;
   }

   @Nullable
   public InstanceEditable asEditInstance() {
      return this.isEditInstance() ? (InstanceEditable)this : null;
   }

   @Nullable
   public <T extends AbstractInstance> T as(Class<T> clazz) {
      return (T)(clazz.isInstance(this) ? this : null);
   }

   public void saveWorld() {
   }

   public void saveWorld(@Nullable CountDownLatch latch) {
   }

   public void registerTriggerListener(DungeonTrigger element) {
      Class<? extends DungeonTrigger> elementClazz = (Class<? extends DungeonTrigger>)element.getClass();
      List<DungeonTrigger> elements = this.triggerListeners.computeIfAbsent(elementClazz, k -> new ArrayList<>());
      elements.add(element);
   }

   public void unregisterTriggerListener(DungeonTrigger element) {
      List<DungeonTrigger> elements = this.triggerListeners.get(element.getClass());
      if (elements != null) {
         elements.remove(element);
      }
   }

   @Nullable
   public List<DungeonTrigger> getTriggerListeners(Class<? extends DungeonTrigger> type) {
      return this.triggerListeners.get(type);
   }

   public void registerFunctionListener(DungeonFunction element) {
      Class<? extends DungeonFunction> elementClazz = (Class<? extends DungeonFunction>)element.getClass();
      List<DungeonFunction> elements = this.functionListeners.computeIfAbsent(elementClazz, k -> new ArrayList<>());
      elements.add(element);
   }

   public void unregisterFunctionListener(DungeonFunction element) {
      List<DungeonFunction> elements = this.functionListeners.get(element.getClass());
      if (elements != null) {
         elements.remove(element);
      }
   }

   @Nullable
   public List<DungeonFunction> getFunctionListeners(Class<? extends DungeonFunction> type) {
      return this.functionListeners.get(type);
   }

   public FileConfiguration getConfig() {
      return this.config;
   }

   public AbstractDungeon getDungeon() {
      return this.dungeon;
   }

   public World getInstanceWorld() {
      return this.instanceWorld;
   }

   public InstanceListener getListener() {
      return this.listener;
   }

   public Map<Location, DungeonFunction> getFunctions() {
      return this.functions;
   }

   public Map<Location, FunctionReward> getRewardFunctions() {
      return this.rewardFunctions;
   }

   public DisplayHandler getDisplayHandler() {
      return this.displayHandler;
   }

   public Map<Class<? extends DungeonTrigger>, List<DungeonTrigger>> getTriggerListeners() {
      return this.triggerListeners;
   }

   public Map<Class<? extends DungeonFunction>, List<DungeonFunction>> getFunctionListeners() {
      return this.functionListeners;
   }

   public List<MythicPlayer> getPlayers() {
      return this.players;
   }

   public Location getStartLoc() {
      return this.startLoc;
   }
}
