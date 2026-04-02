package nl.hauntedmc.dungeons.api.parents.instances;

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
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.events.DungeonStartEvent;
import nl.hauntedmc.dungeons.api.parents.DungeonDifficulty;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.dungeons.functions.FunctionHologram;
import nl.hauntedmc.dungeons.dungeons.functions.rewards.FunctionReward;
import nl.hauntedmc.dungeons.dungeons.rewards.PlayerLootData;
import nl.hauntedmc.dungeons.dungeons.variables.VariableHolder;
import nl.hauntedmc.dungeons.listeners.dungeonlisteners.PlayListener;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.world.DungeonMapRenderer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
   protected List<DungeonPlayer> livingPlayers = new ArrayList<>();
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
      data.set(new NamespacedKey(Dungeons.inst(), "DungeonItem"), PersistentDataType.INTEGER, 1);
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

         for (DungeonPlayer aPlayer : this.players) {
            if (this.dungeon.getConfig().getBoolean("General.ShowTitleOnStart", false)) {
               HelperUtils.showTitle(aPlayer.getPlayer(), this.config.getString("General.DisplayName", "&cA Dungeon"), "", 10, 70, 10);
            }

            HelperUtils.forceTeleport(aPlayer.getPlayer(), this.startLoc);
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

                     for (DungeonPlayer mPlayer : new ArrayList<>(InstancePlayable.this.players)) {
                        InstancePlayable.this.removePlayer(mPlayer);
                     }
                  }
               }
            }
         };
         this.instanceTicker.runTaskTimer(Dungeons.inst(), 0L, 20L);
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

            ent.remove();
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
   public void addPlayer(DungeonPlayer aPlayer) {
      super.addPlayer(aPlayer);
      Player player = aPlayer.getPlayer();
      this.livingPlayers.add(aPlayer);
      if (!this.config.getBoolean("General.KeepInventoryOnEnter", true)) {
         aPlayer.saveInventory();
      }

      Location savePoint;
      IDungeonParty party = aPlayer.getiDungeonParty();
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

         HelperUtils.forceTeleport(player, destination);
         aPlayer.setDungeonRespawn(this.startLoc);
      } else {
         savePoint.setWorld(this.instanceWorld);
         if (!this.started) {
            Bukkit.getScheduler().runTaskLater(Dungeons.inst(), this::startGame, 1L);
         }

          Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> {
            HelperUtils.forceTeleport(player, savePoint);
            aPlayer.setDungeonRespawn(savePoint);
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
         Dungeons.inst()
            .getLogger()
            .info(
               Dungeons.logPrefix
                  + HelperUtils.colorize("&cERROR :: Dungeon " + this.dungeon.getWorldName() + " has invalid GameMode! Defaulting to ADVENTURE...")
            );
         player.setGameMode(GameMode.ADVENTURE);
      }

      if (this.livesEnabled) {
         this.playerLives.put(player.getUniqueId(), this.config.getInt("General.PlayerLives", 1));
      }
   }

   @Override
   public void removePlayer(DungeonPlayer aPlayer, boolean force) {
      if (this.players.contains(aPlayer)) {
         super.removePlayer(aPlayer, force);
         Player player = aPlayer.getPlayer();
         this.livingPlayers.remove(aPlayer);
         if (!HelperUtils.hasPermissionSilent(player, "dungeons.vanish")) {
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
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);

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

   public List<DungeonPlayer> getLivingPlayers() {
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

   public VariableHolder getInstanceVariables() {
      return this.instanceVariables;
   }

   public boolean isStarted() {
      return this.started;
   }

   public void setDungeonFinished(boolean dungeonFinished) {
      this.dungeonFinished = dungeonFinished;
   }

   public Set<Entity> getEntities() {
      return this.entities;
   }
}
