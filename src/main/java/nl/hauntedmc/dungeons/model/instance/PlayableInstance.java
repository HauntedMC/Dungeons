package nl.hauntedmc.dungeons.model.instance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.content.function.HologramFunction;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import nl.hauntedmc.dungeons.content.reward.PlayerLootData;
import nl.hauntedmc.dungeons.content.variable.VariableStore;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.listener.instance.PlayListener;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.MessageUtils;
import nl.hauntedmc.dungeons.util.world.DungeonMapRenderer;
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

/**
 * Base runtime instance for playable dungeon runs.
 *
 * <p>This instance type owns match lifecycle state such as participants, lives, rewards, and run
 * timers.</p>
 */
public abstract class PlayableInstance extends DungeonInstance {
    protected boolean livesEnabled;
    protected DungeonDifficulty difficulty;
    protected BukkitRunnable instanceTicker;
    protected int timeElapsed;
    protected int timeLeft;
    protected String status;
    protected final List<DungeonPlayerSession> livingPlayers = new ArrayList<>();
    protected int participants = 0;
    protected final Map<UUID, Integer> playerLives = new HashMap<>();
    protected final Map<UUID, BukkitRunnable> offlineTrackers = new HashMap<>();
    protected final Map<UUID, List<ItemStack>> rewardInventories = new HashMap<>();
    protected final Map<UUID, Boolean> receivedRewards = new HashMap<>();
    protected Location lobbyLocation;
    private final VariableStore instanceVariables;
    protected boolean started = false;
    protected boolean dungeonFinished = false;
    private Set<Entity> entities = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Creates a playable instance and attaches the play listener.
     */
    public PlayableInstance(DungeonDefinition dungeon, CountDownLatch latch) {
        super(dungeon, latch);
        this.instanceVariables = new VariableStore();
        this.listener = new PlayListener(this);
        if (this.config.getInt("players.lives", 0) != 0) {
            this.livesEnabled = true;
        }
    }

    /**
     * Gives the player a live minimap item for this instance world.
     */
    public void giveDungeonMap(Player player) {
        ItemStack mapItem = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) mapItem.getItemMeta();
        MapView mapView = Bukkit.createMap(this.getInstanceWorld());
        mapView.setScale(Scale.CLOSEST);
        mapView.getRenderers().clear();
        mapView.addRenderer(new DungeonMapRenderer(this));
        meta.setMapView(mapView);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(this.plugin(), "DungeonItem"), PersistentDataType.INTEGER, 1);
        mapItem.setItemMeta(meta);
        ItemUtils.giveOrDropSilently(player, mapItem);
    }

    /**
     * Loads the game and resolves a world-bound lobby location if configured.
     */
    @Override
    public final void loadGame() {
        if (this.dungeon.getLobbySpawn() != null) {
            this.lobbyLocation = this.dungeon.getLobbySpawn().clone();
            this.lobbyLocation.setWorld(this.instanceWorld);
        }

        super.loadGame();
    }

    /**
     * Starts the playable run, teleports players, and starts the run timer.
     */
    public void startGame() {
        if (!this.started) {
            this.started = true;
            this.initializeFunctions();

            for (DungeonPlayerSession playerSession : this.players) {
                if (this.dungeon.getConfig().getBoolean("dungeon.show_title_on_start", false)) {
                    MessageUtils.showTitle(
                            playerSession.getPlayer(),
                            this.config.getString("dungeon.display_name", "&cA Dungeon"),
                            "",
                            10,
                            70,
                            10);
                }

                EntityUtils.forceTeleport(playerSession.getPlayer(), this.startLocation);
                playerSession.setDungeonRespawn(this.startLocation);
                if (this.dungeon.isCooldownOnStart()
                        && this.dungeon.shouldApplyAccessCooldown(
                                this, playerSession.getPlayer().getUniqueId())) {
                    this.dungeon.addAccessCooldown(playerSession.getPlayer());
                }
            }

            this.participants = this.players.size();
            final int timeLimit = this.config.getInt("runs.time_limit_minutes", 0);
            this.timeLeft = timeLimit * 60;
            this.instanceTicker =
                                        new BukkitRunnable() {
                                                public void run() {
                            PlayableInstance.this.timeElapsed++;
                            if (timeLimit != 0) {
                                PlayableInstance.this.timeLeft--;
                                // Warnings are emitted at fixed thresholds so teams can react
                                // before forced close when the limit expires.
                                if (PlayableInstance.this.timeLeft == 600) {
                                    PlayableInstance.this.messagePlayers(
                                            LangUtils.getMessage(
                                                    "instance.play.time-limit.ten-minute-warning",
                                                    LangUtils.placeholder(
                                                            "dungeon", PlayableInstance.this.dungeon.getDisplayName())));
                                }

                                if (PlayableInstance.this.timeLeft == 300) {
                                    PlayableInstance.this.messagePlayers(
                                            LangUtils.getMessage(
                                                    "instance.play.time-limit.five-minute-warning",
                                                    LangUtils.placeholder(
                                                            "dungeon", PlayableInstance.this.dungeon.getDisplayName())));
                                }

                                if (PlayableInstance.this.timeLeft == 60) {
                                    PlayableInstance.this.messagePlayers(
                                            LangUtils.getMessage(
                                                    "instance.play.time-limit.one-minute-warning",
                                                    LangUtils.placeholder(
                                                            "dungeon", PlayableInstance.this.dungeon.getDisplayName())));
                                }

                                if (PlayableInstance.this.timeLeft <= 0) {
                                    PlayableInstance.this.messagePlayers(
                                            LangUtils.getMessage("instance.play.time-limit.times-up"));
                                    this.cancel();

                                    for (DungeonPlayerSession playerSession :
                                            new ArrayList<>(PlayableInstance.this.players)) {
                                        PlayableInstance.this.removePlayer(playerSession);
                                    }
                                }
                            }
                        }
                    };
            this.instanceTicker.runTaskTimer(this.plugin(), 0L, 20L);
            Bukkit.getPluginManager().callEvent(new DungeonStartEvent(this, this.players));
            this.onStart();
        }
    }

    /**
     * Hook invoked when a playable run starts.
     */
    public void onStart() {}

    /**
     * Disables function listeners and clears runtime run-state caches.
     */
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

    /**
     * Removes tracked runtime entities from the world during dispose.
     */
    private void disposeEntities() {
        for (Entity ent : this.entities) {
            if (ent != null) {
                ent.remove();
            }
        }

        this.entities.clear();
        this.entities = null;
    }

    /**
     * Clones and enables all dungeon functions for this playable instance world.
     */
    public void initializeFunctions() {
        for (DungeonFunction oldFunction : this.dungeon.getFunctions().values()) {
            if (oldFunction == null) {
                this.logger()
                        .warn(
                                "Skipping null function while initializing play instance for dungeon '{}'.",
                                this.dungeon.getWorldName());
                continue;
            }

            try {
                DungeonFunction newFunction = oldFunction.clone();
                if (newFunction == null) {
                    this.logger()
                            .warn(
                                    "Skipping function '{}' in dungeon '{}' because clone() returned null during instance initialization.",
                                    oldFunction.getClass().getSimpleName(),
                                    this.dungeon.getWorldName());
                    continue;
                }

                Location loc = newFunction.getLocation();
                if (loc == null) {
                    this.logger()
                            .warn(
                                    "Skipping function '{}' in dungeon '{}' because it has no location during instance initialization.",
                                    oldFunction.getClass().getSimpleName(),
                                    this.dungeon.getWorldName());
                    continue;
                }

                loc = loc.clone();
                loc.setWorld(this.instanceWorld);
                newFunction.enable(this, loc);
                this.functions.put(loc, newFunction);
                if (newFunction instanceof RewardFunction rewardFunction) {
                    this.rewardFunctions.put(newFunction.getLocation(), rewardFunction);
                }
            } catch (Throwable throwable) {
                Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
                Location loc = oldFunction.getLocation();
                this.logger()
                        .warn(
                                "Skipping broken function '{}' in dungeon '{}' at {} during play instance initialization. Reason: {}",
                                oldFunction.getClass().getSimpleName(),
                                this.dungeon.getWorldName(),
                                loc == null
                                        ? "<no-location>"
                                        : (loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()),
                                root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(),
                                root);
            }
        }
    }

    /**
     * Adds a player to the playable run and applies entry state rules.
     */
    @Override
    public void addPlayer(DungeonPlayerSession playerSession) {
        super.addPlayer(playerSession);
        Player player = playerSession.getPlayer();
        this.livingPlayers.add(playerSession);
        if (!this.config.getBoolean("players.keep_on_entry.inventory", true)) {
            playerSession.saveInventory();
        }

        Location savePoint;
        savePoint = playerSession.getDungeonSavePoint(this.dungeon.getWorldName());

        if (savePoint == null) {
            Location destination = this.startLocation;
            if (this.dungeon.isLobbyEnabled()) {
                destination = this.lobbyLocation;
            }

            EntityUtils.forceTeleport(player, destination);
            playerSession.setDungeonRespawn(this.startLocation);
        } else {
            savePoint.setWorld(this.instanceWorld);
            if (!this.started) {
                Bukkit.getScheduler().runTaskLater(this.plugin(), this::startGame, 1L);
            }

            Bukkit.getScheduler()
                    .runTaskLater(
                            this.plugin(),
                            () -> {
                                EntityUtils.forceTeleport(player, savePoint);
                                playerSession.setDungeonRespawn(savePoint);
                                LangUtils.sendMessage(player, "instance.play.functions.savepoint");
                            },
                            2L);
        }

        PlayerLootData lootData = this.dungeon.getPlayerLootData(player);
        if (lootData != null) {
            lootData.checkCooldowns();
        }

        try {
            GameMode gamemode =
                    GameMode.valueOf(
                            this.config.getString("players.gamemode", "ADVENTURE").toUpperCase(Locale.ROOT));
            player.setGameMode(gamemode);
        } catch (IllegalArgumentException exception) {
            this.logger()
                    .warn(
                            "Dungeon '{}' has invalid game mode '{}'; defaulting player '{}' to ADVENTURE.",
                            this.dungeon.getWorldName(),
                            this.config.getString("players.gamemode", "ADVENTURE"),
                            player.getName(),
                            exception);
            player.setGameMode(GameMode.ADVENTURE);
        }

        if (this.livesEnabled) {
            this.playerLives.putIfAbsent(player.getUniqueId(), this.config.getInt("players.lives", 1));
        }
    }

    /**
     * Removes a player from the run and applies reward/cooldown persistence.
     */
    @Override
    public void removePlayer(DungeonPlayerSession playerSession, boolean force) {
        if (this.players.contains(playerSession)) {
            super.removePlayer(playerSession, force);
            Player player = playerSession.getPlayer();
            this.livingPlayers.remove(playerSession);
            if (!CommandUtils.hasPermissionSilent(player, "dungeons.vanish")) {
                this.messagePlayers(
                        LangUtils.getMessage(
                                "instance.play.events.player-leave",
                                LangUtils.placeholder("player", player.getName())));
            }

            if (this.dungeon.getExitLoc() != null && this.dungeon.isAlwaysUseExit()) {
                playerSession.setSavedPosition(this.dungeon.getExitLoc());
            }

            if (!this.config.getBoolean("players.keep_on_entry.inventory", true)) {
                playerSession.restoreInventory();
            }

            if (!this.config.getBoolean("players.keep_on_entry.experience", true)) {
                playerSession.restoreExp();
            }

            if (!this.dungeon.isCooldownsPerReward()
                    && this.receivedRewards.getOrDefault(player.getUniqueId(), false)) {
                for (RewardFunction function : this.rewardFunctions.values()) {
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
                DungeonFunction dungeonFunction = pair.getValue();
                DungeonTrigger trigger = dungeonFunction.getTrigger();
                if (trigger != null) {
                    trigger.getPlayersTriggered().remove(player.getUniqueId());
                }
            }
        }
    }

    /**
     * Records whether this player has already claimed run rewards.
     */
    public void setReceivedRewards(Player player, boolean received) {
        this.receivedRewards.put(player.getUniqueId(), received);
    }

    /**
     * Queues reward items for later delivery to a player session.
     */
    public void addPlayerReward(Player player, ItemStack... items) {
        this.rewardInventories.putIfAbsent(player.getUniqueId(), new ArrayList<>());
        List<ItemStack> rewards = this.rewardInventories.get(player.getUniqueId());
        rewards.addAll(Arrays.asList(items));
    }

    /**
     * Pushes queued rewards into the player's claimable reward list.
     */
    public void pushPlayerRewards(Player player) {
        List<ItemStack> rewards = this.rewardInventories.get(player.getUniqueId());
        if (rewards != null) {
            this.rewardInventories.remove(player.getUniqueId());
            DungeonPlayerSession playerSession = this.playerSessions().get(player);

            for (ItemStack reward : rewards) {
                if (reward != null && reward.getType() != Material.AIR) {
                    playerSession.addReward(reward);
                }
            }
        }
    }

    /**
     * Applies cooldown bookkeeping for all reward functions for one player.
     */
    public void applyLootCooldowns(Player player) {
        for (RewardFunction rewardFunction : this.rewardFunctions.values()) {
            rewardFunction.processCooldown(player);
        }
    }

    /**
     * Adds or updates a hologram function for this instance.
     */
    public void addHologram(HologramFunction function) {
        if (this.hologramManager != null) {
            this.updateHologram(function);
        }
    }

    /**
     * Re-renders the hologram text/radius for a hologram function.
     */
    public void updateHologram(HologramFunction func) {
        Location fLoc = func.getHologramLoc().clone();
        if (fLoc.getWorld() == null) {
            fLoc.setWorld(this.instanceWorld);
        }

        this.updateHologram(fLoc, func.getRadius(), func.getMessage(), false);
    }

    /**
     * Shows a hologram function to players in the instance.
     */
    public void showHologramFunction(HologramFunction function) {
        if (this.hologramManager != null) {
            Location fLoc = function.getHologramLoc().clone();
            if (fLoc.getWorld() == null) {
                fLoc.setWorld(this.instanceWorld);
            }

            this.hologramManager.showHologram(fLoc, (float) function.getRadius());
        }
    }

    /**
     * Hides a hologram function from players in the instance.
     */
    public void hideHologramFunction(HologramFunction function) {
        if (this.hologramManager != null) {
            Location fLoc = function.getHologramLoc().clone();
            if (fLoc.getWorld() == null) {
                fLoc.setWorld(this.instanceWorld);
            }

            this.hologramManager.hideHologram(fLoc);
        }
    }

    /**
     * Returns whether finite lives are enabled for this run.
     */
    public boolean isLivesEnabled() {
        return this.livesEnabled;
    }

    /**
     * Returns the selected difficulty for this run.
     */
    public DungeonDifficulty getDifficulty() {
        return this.difficulty;
    }

    /**
     * Sets the selected difficulty for this run.
     */
    public void setDifficulty(DungeonDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * Returns elapsed run time in seconds.
     */
    public int getTimeElapsed() {
        return this.timeElapsed;
    }

    /**
     * Returns remaining run time in seconds, or zero when unlimited/expired.
     */
    public int getTimeLeft() {
        return this.timeLeft;
    }

    /**
     * Returns the current run status text.
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Updates the current run status text.
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the currently alive player sessions in this run.
     */
    public List<DungeonPlayerSession> getLivingPlayers() {
        return this.livingPlayers;
    }

    /**
     * Returns the participant count captured at run start.
     */
    public int getParticipants() {
        return this.participants;
    }

    /**
     * Returns mutable player-lives tracking for the run.
     */
    public Map<UUID, Integer> getPlayerLives() {
        return this.playerLives;
    }

    /**
     * Returns offline-kick tracker tasks for disconnected players.
     */
    public Map<UUID, BukkitRunnable> getOfflineTrackers() {
        return this.offlineTrackers;
    }

    /**
     * Returns queued reward inventories keyed by player id.
     */
    public Map<UUID, List<ItemStack>> getRewardInventories() {
        return this.rewardInventories;
    }

    /**
     * Returns the instance-scoped variable store.
     */
    public VariableStore getInstanceVariables() {
        return this.instanceVariables;
    }

    /**
     * Returns whether the run has already started.
     */
    public boolean isStarted() {
        return this.started;
    }

    /**
     * Updates whether this run should be treated as completed.
     */
    public void setDungeonFinished(boolean dungeonFinished) {
        this.dungeonFinished = dungeonFinished;
    }

    /**
     * Returns the tracked runtime entities spawned during this run.
     */
    public Set<Entity> getEntities() {
        return this.entities;
    }
}
