package net.playavalon.mythicdungeons.api.parents.instances;

import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.events.dungeon.DungeonStartEvent;
import net.playavalon.mythicdungeons.api.parents.DungeonDifficulty;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.dungeons.functions.FunctionHologram;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionReward;
import net.playavalon.mythicdungeons.dungeons.rewards.PlayerLootData;
import net.playavalon.mythicdungeons.dungeons.variables.VariableHolder;
import net.playavalon.mythicdungeons.listeners.dungeonlisteners.PlayListener;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.DungeonMapRenderer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.ReflectionUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.map.MapView.Scale;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

public abstract class InstancePlayable extends AbstractInstance {
   protected boolean livesEnabled;
   protected DungeonDifficulty difficulty;
   protected BukkitRunnable instanceTicker;
   protected int timeElapsed;
   protected int timeLeft;
   protected String status;
   protected List<MythicPlayer> livingPlayers = new ArrayList<>();
   protected int participants = 0;
   protected final Map<UUID, Integer> playerLives = new HashMap<>();
   protected Map<UUID, BukkitRunnable> offlineTrackers = new HashMap<>();
   protected final Map<UUID, List<ItemStack>> rewardInventories = new HashMap<>();
   protected final Map<UUID, Boolean> receivedRewards = new HashMap<>();
   protected Location lobbyLoc;
   private final VariableHolder instanceVariables;
   protected boolean started = false;
   protected boolean dungeonFinished = false;
   private Set<Entity> entities = Collections.newSetFromMap(new WeakHashMap<>());

   public InstancePlayable(AbstractDungeon dungeon, CountDownLatch latch) {
      super(dungeon, latch);
      this.instanceVariables = new VariableHolder();
      this.listener = new PlayListener(this);
      if (this.config.getInt("General.PlayerLives", 0) != 0) {
         this.livesEnabled = true;
      }
   }

   public void giveDungeonMap(Player player) {
      ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
      MapMeta meta = (MapMeta)mapItem.getItemMeta();
      MapView mapView = Bukkit.createMap(this.getInstanceWorld());
      mapView.setScale(Scale.CLOSEST);
      mapView.getRenderers().clear();
      mapView.addRenderer(new DungeonMapRenderer(this));
      meta.setMapView(mapView);
      PersistentDataContainer data = meta.getPersistentDataContainer();
      data.set(new NamespacedKey(MythicDungeons.inst(), "DungeonItem"), PersistentDataType.INTEGER, 1);
      mapItem.setItemMeta(meta);
      ItemUtils.giveOrDropSilently(player, mapItem);
   }

   @Override
   public final void loadGame() {
      if (this.dungeon.getLobbySpawn() != null) {
         this.lobbyLoc = this.dungeon.getLobbySpawn().clone();
         this.lobbyLoc.setWorld(this.instanceWorld);
      }

      super.loadGame();
   }

   public void startGame() {
      if (!this.started) {
         this.started = true;
         this.initFunctions();

         for (MythicPlayer aPlayer : this.players) {
            if (this.dungeon.getConfig().getBoolean("General.ShowTitleOnStart", false)) {
               aPlayer.getPlayer().sendTitle(Util.fullColor(this.config.getString("General.DisplayName", "&cA Dungeon")), "", 10, 70, 10);
            }

            Util.forceTeleport(aPlayer.getPlayer(), this.startLoc);
            aPlayer.setDungeonRespawn(this.startLoc);
            if (this.dungeon.isCooldownOnStart()) {
               this.dungeon.addAccessCooldown(aPlayer.getPlayer());
            }
         }

         this.participants = this.players.size();
         final int timeLimit = this.config.getInt("General.TimeLimit", 0);
         this.timeLeft = timeLimit * 60;
         this.instanceTicker = new BukkitRunnable() {
            public void run() {
               InstancePlayable.this.timeElapsed++;
               if (timeLimit != 0) {
                  InstancePlayable.this.timeLeft--;
                  if (InstancePlayable.this.timeLeft == 600) {
                     InstancePlayable.this.messagePlayers(
                        LangUtils.getMessage("instance.time-limit.ten-minute-warning", InstancePlayable.this.dungeon.getDisplayName())
                     );
                  }

                  if (InstancePlayable.this.timeLeft == 300) {
                     InstancePlayable.this.messagePlayers(
                        LangUtils.getMessage("instance.time-limit.five-minute-warning", InstancePlayable.this.dungeon.getDisplayName())
                     );
                  }

                  if (InstancePlayable.this.timeLeft == 60) {
                     InstancePlayable.this.messagePlayers(
                        LangUtils.getMessage("instance.time-limit.one-minute-warning", InstancePlayable.this.dungeon.getDisplayName())
                     );
                  }

                  if (InstancePlayable.this.timeLeft <= 0) {
                     InstancePlayable.this.messagePlayers(LangUtils.getMessage("instance.time-limit.times-up"));
                     this.cancel();

                     for (MythicPlayer mPlayer : new ArrayList<>(InstancePlayable.this.players)) {
                        InstancePlayable.this.removePlayer(mPlayer);
                     }
                  }
               }
            }
         };
         this.instanceTicker.runTaskTimer(MythicDungeons.inst(), 0L, 20L);
         Bukkit.getPluginManager().callEvent(new DungeonStartEvent(this, this.players));
         this.onStart();
      }
   }

   public void onStart() {
   }

   @Override
   public void onDispose() {
      for (DungeonFunction function : this.functions.values()) {
         function.disable();
      }

      for (BukkitRunnable tracker : this.offlineTrackers.values()) {
         tracker.cancel();
      }

      this.offlineTrackers.clear();
      if (this.instanceTicker != null && !this.instanceTicker.isCancelled()) {
         this.instanceTicker.cancel();
      }

      this.playerLives.clear();
      this.rewardInventories.clear();
      this.disposeEntities();
   }

   private void disposeEntities() {
      for (Entity ent : this.entities) {
         if (ent != null) {
            if (MythicDungeons.inst().getMythicApi() != null) {
               ActiveMob aMob = MythicDungeons.inst().getMythicApi().getMobManager().getMythicMobInstance(ent);
               if (aMob == null) {
                  return;
               }

               aMob.setDespawned();
               aMob.setDead();
               aMob.setUnloaded();
            }

            ent.remove();
            Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> ReflectionUtils.forcePurgeEntity(ent), 5L);
         }
      }

      this.entities.clear();
      this.entities = null;
   }

   public void initFunctions() {
      for (DungeonFunction oldFunction : this.dungeon.getFunctions().values()) {
         DungeonFunction newFunction = oldFunction.clone();
         if (newFunction != null) {
            Location loc = newFunction.getLocation();
            loc.setWorld(this.instanceWorld);
            newFunction.enable(this, loc);
            this.functions.put(loc, newFunction);
            if (newFunction instanceof FunctionReward) {
               this.rewardFunctions.put(newFunction.getLocation(), (FunctionReward)newFunction);
            }
         }
      }
   }

   @Override
   public void addPlayer(MythicPlayer aPlayer) {
      super.addPlayer(aPlayer);
      Player player = aPlayer.getPlayer();
      this.livingPlayers.add(aPlayer);
      if (!this.config.getBoolean("General.KeepInventoryOnEnter", true)) {
         aPlayer.saveInventory();
      }

      Location savePoint = null;
      IDungeonParty party = aPlayer.getDungeonParty();
      if (party != null) {
         savePoint = party.getPartySavePoint(this.dungeon.getWorldName());
      } else {
         savePoint = aPlayer.getDungeonSavePoint(this.dungeon.getWorldName());
      }

      if (savePoint == null) {
         Location destination = this.startLoc;
         if (this.dungeon.isLobbyEnabled()) {
            destination = this.lobbyLoc;
         }

         Util.forceTeleport(player, destination);
         aPlayer.setDungeonRespawn(this.startLoc);
      } else {
         savePoint.setWorld(this.instanceWorld);
         if (!this.started) {
            Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), this::startGame, 1L);
         }

         Location finalSavePoint = savePoint;
         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
            Util.forceTeleport(player, finalSavePoint);
            aPlayer.setDungeonRespawn(finalSavePoint);
            LangUtils.sendMessage(player, "instance.functions.savepoint");
         }, 2L);
      }

      PlayerLootData lootData = this.dungeon.getPlayerLootData(player);
      if (lootData != null) {
         lootData.checkCooldowns();
      }

      try {
         GameMode gamemode = GameMode.valueOf(this.config.getString("General.Gamemode", "ADVENTURE").toUpperCase(Locale.ROOT));
         player.setGameMode(gamemode);
      } catch (IllegalArgumentException var7) {
         MythicDungeons.inst()
            .getLogger()
            .info(
               MythicDungeons.debugPrefix
                  + Util.colorize("&cERROR :: Dungeon " + this.dungeon.getWorldName() + " has invalid GameMode! Defaulting to ADVENTURE...")
            );
         player.setGameMode(GameMode.ADVENTURE);
      }

      if (this.livesEnabled) {
         this.playerLives.put(player.getUniqueId(), this.config.getInt("General.PlayerLives", 1));
      }
   }

   @Override
   public void removePlayer(MythicPlayer aPlayer, boolean force) {
      if (this.players.contains(aPlayer)) {
         super.removePlayer(aPlayer, force);
         Player player = aPlayer.getPlayer();
         this.livingPlayers.remove(aPlayer);
         if (!Util.hasPermissionSilent(player, "dungeons.vanish")) {
            this.messagePlayers(LangUtils.getMessage("instance.events.player-leave", player.getName()));
         }

         if (this.dungeon.getExitLoc() != null && this.dungeon.isAlwaysUseExit()) {
            aPlayer.setSavedPosition(this.dungeon.getExitLoc());
         }

         if (!this.config.getBoolean("General.KeepInventoryOnEnter", true)) {
            aPlayer.restoreInventory();
         }

         if (!this.config.getBoolean("General.KeepExpOnEnter", true)) {
            aPlayer.restoreExp();
         }

         if (!this.dungeon.isCooldownsPerReward() && this.receivedRewards.getOrDefault(player.getUniqueId(), false)) {
            for (FunctionReward function : this.rewardFunctions.values()) {
               if (function.overrideCooldown()) {
                  this.dungeon.addLootCooldown(player, function, function.getCooldownTime());
               } else {
                  this.dungeon.addLootCooldown(player, function);
               }
            }
         }

         this.dungeon.saveCooldowns(player);
         this.dungeon.savePlayerData(player);
         this.rewardInventories.remove(player.getUniqueId());

         for (Entry<Location, DungeonFunction> pair : this.functions.entrySet()) {
            DungeonFunction functionx = pair.getValue();
            DungeonTrigger trigger = functionx.getTrigger();
            if (trigger != null) {
               trigger.getPlayersTriggered().remove(player.getUniqueId());
            }
         }
      }
   }

   public void setReceivedRewards(Player player, boolean received) {
      this.receivedRewards.put(player.getUniqueId(), received);
   }

   public void addPlayerReward(Player player, ItemStack... items) {
      this.rewardInventories.putIfAbsent(player.getUniqueId(), new ArrayList<>());
      List<ItemStack> rewards = this.rewardInventories.get(player.getUniqueId());
      rewards.addAll(Arrays.asList(items));
   }

   public void pushPlayerRewards(Player player) {
      List<ItemStack> rewards = this.rewardInventories.get(player.getUniqueId());
      if (rewards != null) {
         this.rewardInventories.remove(player.getUniqueId());
         MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);

         for (ItemStack reward : rewards) {
            if (reward != null && reward.getType() != Material.AIR) {
               mPlayer.addReward(reward);
            }
         }
      }
   }

   public void applyLootCooldowns(Player player) {
      for (FunctionReward rewardFunction : this.rewardFunctions.values()) {
         rewardFunction.processCooldown(player);
      }
   }

   public void addHologram(FunctionHologram function) {
      if (this.displayHandler != null) {
         System.out.println("Display handler exists!");
         this.setTextDisplayHologram(function);
      }
   }

   public void updateHologram(FunctionHologram function) {
      if (this.displayHandler != null) {
         this.setTextDisplayHologram(function);
      }
   }

   public void setTextDisplayHologram(FunctionHologram func) {
      Location fLoc = func.getHologramLoc().clone();
      if (fLoc.getWorld() == null) {
         fLoc.setWorld(this.instanceWorld);
      }

      this.setTextDisplayHologram(fLoc, func.getRadius(), func.getMessage(), false);
   }

   public void removeTextDisplayHologram(FunctionHologram func) {
      Location fLoc = func.getHologramLoc().clone();
      if (fLoc.getWorld() == null) {
         fLoc.setWorld(this.instanceWorld);
      }

      this.removeTextDisplayHologram(fLoc);
   }

   public void showHologramFunction(FunctionHologram function) {
      if (this.displayHandler != null) {
         Location fLoc = function.getHologramLoc().clone();
         if (fLoc.getWorld() == null) {
            fLoc.setWorld(this.instanceWorld);
         }

         this.displayHandler.showHologram(fLoc, (float)function.getRadius());
      }
   }

   public void hideHologramFunction(FunctionHologram function) {
      if (this.displayHandler != null) {
         Location fLoc = function.getHologramLoc().clone();
         if (fLoc.getWorld() == null) {
            fLoc.setWorld(this.instanceWorld);
         }

         this.displayHandler.hideHologram(fLoc);
      }
   }

   public boolean isLivesEnabled() {
      return this.livesEnabled;
   }

   public DungeonDifficulty getDifficulty() {
      return this.difficulty;
   }

   public void setDifficulty(DungeonDifficulty difficulty) {
      this.difficulty = difficulty;
   }

   public BukkitRunnable getInstanceTicker() {
      return this.instanceTicker;
   }

   public int getTimeElapsed() {
      return this.timeElapsed;
   }

   public int getTimeLeft() {
      return this.timeLeft;
   }

   public String getStatus() {
      return this.status;
   }

   public void setStatus(String status) {
      this.status = status;
   }

   public List<MythicPlayer> getLivingPlayers() {
      return this.livingPlayers;
   }

   public int getParticipants() {
      return this.participants;
   }

   public Map<UUID, Integer> getPlayerLives() {
      return this.playerLives;
   }

   public Map<UUID, BukkitRunnable> getOfflineTrackers() {
      return this.offlineTrackers;
   }

   public Map<UUID, List<ItemStack>> getRewardInventories() {
      return this.rewardInventories;
   }

   public Location getLobbyLoc() {
      return this.lobbyLoc;
   }

   public VariableHolder getInstanceVariables() {
      return this.instanceVariables;
   }

   public boolean isStarted() {
      return this.started;
   }

   public void setStarted(boolean started) {
      this.started = started;
   }

   public boolean isDungeonFinished() {
      return this.dungeonFinished;
   }

   public void setDungeonFinished(boolean dungeonFinished) {
      this.dungeonFinished = dungeonFinished;
   }

   public Set<Entity> getEntities() {
      return this.entities;
   }
}
