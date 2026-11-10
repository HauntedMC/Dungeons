package nl.hauntedmc.dungeons.model.instance;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.content.function.HologramFunction;
import nl.hauntedmc.dungeons.content.variable.VariableStore;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.listener.instance.PlayListener;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.config.DungeonConfigView;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.dungeons.util.text.MessageUtils;
import nl.hauntedmc.dungeons.util.time.TimeUtils;
import nl.hauntedmc.dungeons.util.world.DungeonMapRenderer;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.map.MapView.Scale;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
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

/**
 * Base runtime instance for playable dungeon runs.
 *
 * <p>This instance type owns match lifecycle state such as participants, lives and run
 * timers.
 */
public abstract class PlayableInstance extends DungeonInstance {
    protected boolean livesEnabled;
    protected DungeonDifficulty difficulty;
    protected BukkitRunnable instanceTicker;
    protected BukkitRunnable dragonBossBarTicker;
    protected int timeElapsed;
    protected int timeLeft;
    protected String status;
    protected final List<DungeonPlayerSession> livingPlayers = new ArrayList<>();
    protected int participants = 0;
    protected final Map<UUID, Integer> playerLives = new HashMap<>();
    protected final Map<UUID, BukkitRunnable> offlineTrackers = new HashMap<>();

    protected Location lobbyLocation;
    private final VariableStore instanceVariables;
    protected boolean started = false;
    protected boolean dungeonFinished = false;
    private Set<Entity> entities = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<UUID, BossBar> customDragonBossBars =
            new HashMap<>();

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
        if (this.disposing || this.loadFailed) return;
        if (!this.started) {
            this.started = true;
            this.initializeFunctions();

            for (DungeonPlayerSession playerSession : this.players) {
                if (this.dungeon.getConfig().getBoolean("dungeon.show_title_on_start", false)) {
                    MessageUtils.showTitle(
                            playerSession.getPlayer(),
                            this.config.getString("dungeon.display_name", "&cA Dungeon"),
                            "",
                            DungeonConfigView.getStartTitleFadeInTicks(this.config),
                            DungeonConfigView.getStartTitleStayTicks(this.config),
                            DungeonConfigView.getStartTitleFadeOutTicks(this.config));
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
            final Set<Integer> warningSeconds =
                    DungeonConfigView.getTimeLimitWarningSeconds(this.config);
            this.timeLeft = timeLimit * 60;
            this.instanceTicker =
                    new BukkitRunnable() {
                        public void run() {
                            PlayableInstance.this.timeElapsed++;
                            if (timeLimit != 0) {
                                PlayableInstance.this.timeLeft--;
                                if (warningSeconds.contains(PlayableInstance.this.timeLeft)) {
                                    PlayableInstance.this.warnTimeLimit(PlayableInstance.this.timeLeft);
                                }

                                if (PlayableInstance.this.timeLeft <= 0) {
                                    PlayableInstance.this.messagePlayers(
                                                    "instance.play.time-limit.times-up");
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
            this.dragonBossBarTicker =
                    new BukkitRunnable() {
                        public void run() {
                            PlayableInstance.this.updateCustomDragonBossBars();
                        }
                    };
            this.dragonBossBarTicker.runTaskTimer(this.plugin(), 0L, 1L);
            Bukkit.getPluginManager().callEvent(new DungeonStartEvent(this, this.players));
            this.onStart();
        }
    }

    /**
     * Hook invoked when a playable run starts.
     */
    public void onStart() {}

    private void warnTimeLimit(int remainingSeconds) {
        String warningKey =
                switch (remainingSeconds) {
                    case 600 -> "instance.play.time-limit.ten-minute-warning";
                    case 300 -> "instance.play.time-limit.five-minute-warning";
                    case 60 -> "instance.play.time-limit.one-minute-warning";
                    default -> "instance.play.time-limit.warning";
                };

        this.messagePlayers(
                warningKey,
                LangUtils.placeholder("dungeon", this.dungeon.getDisplayNameComponent()),
                LangUtils.placeholder("time", TimeUtils.formatDuration(remainingSeconds * 1000L)));
    }

    /**
     * Disables function listeners and clears runtime run-state caches.
     */
    @Override
    public void onDispose() {
        for (DungeonFunction function : this.functions.values()) {
            try {
                function.disable();
            } catch (Exception exception) {
                this.logger()
                        .error(
                                "Failed to disable function while disposing dungeon '{}'.",
                                this.dungeon.getWorldName(),
                                exception);
            }
        }

        for (BukkitRunnable tracker : this.offlineTrackers.values()) {
            tracker.cancel();
        }

        this.offlineTrackers.clear();
        if (this.instanceTicker != null && !this.instanceTicker.isCancelled()) {
            this.instanceTicker.cancel();
        }
        if (this.dragonBossBarTicker != null && !this.dragonBossBarTicker.isCancelled()) {
            this.dragonBossBarTicker.cancel();
        }

        this.playerLives.clear();
        this.clearCustomDragonBossBars();
        this.disposeEntities();
    }

    /**
     * Removes tracked runtime entities from the world during dispose.
     */
    private void disposeEntities() {
        for (Entity ent : this.entities) {
            if (ent != null) {
                try {
                    ent.remove();
                } catch (RuntimeException exception) {
                    this.logger()
                            .error(
                                    "Failed to remove an owned entity while disposing dungeon"
                                        + " '{}'.",
                                    this.dungeon.getWorldName(),
                                    exception);
                }
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
                                "Skipping null function while initializing play instance for"
                                    + " dungeon '{}'.",
                                this.dungeon.getWorldName());
                continue;
            }

            DungeonFunction newFunction = null;
            try {
                newFunction = oldFunction.clone();
                if (newFunction == null) {
                    this.logger()
                            .warn(
                                    "Skipping function '{}' in dungeon '{}' because clone()"
                                        + " returned null during instance initialization.",
                                    oldFunction.getClass().getSimpleName(),
                                    this.dungeon.getWorldName());
                    continue;
                }

                Location loc = newFunction.getLocation();
                if (loc == null) {
                    this.logger()
                            .warn(
                                    "Skipping function '{}' in dungeon '{}' because it has no"
                                        + " location during instance initialization.",
                                    oldFunction.getClass().getSimpleName(),
                                    this.dungeon.getWorldName());
                    continue;
                }

                loc = loc.clone();
                loc.setWorld(this.instanceWorld);
                newFunction.enable(this, loc);
                this.functions.put(loc, newFunction);

            } catch (Throwable throwable) {
                if (newFunction != null && newFunction.getInstance() == this) {
                    try {
                        newFunction.disable();
                    } catch (Exception cleanupFailure) {
                        throwable.addSuppressed(cleanupFailure);
                    }
                }
                Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
                Location loc = oldFunction.getLocation();
                this.logger()
                        .warn(
                                "Skipping broken function '{}' in dungeon '{}' at {} during play"
                                    + " instance initialization. Reason: {}",
                                oldFunction.getClass().getSimpleName(),
                                this.dungeon.getWorldName(),
                                loc == null
                                        ? "<no-location>"
                                        : (loc.getBlockX()
                                                + ","
                                                + loc.getBlockY()
                                                + ","
                                                + loc.getBlockZ()),
                                root.getMessage() == null
                                        ? root.getClass().getSimpleName()
                                        : root.getMessage(),
                                root);
            }
        }
    }

    /**
     * Adds a player to the playable run and applies entry state rules.
     */
    @Override
    public void addPlayer(DungeonPlayerSession playerSession) {
        if (this.disposing || this.loadFailed) return;
        if (this.runtime().staffOperations() != null && (!this.dungeon.isEnabled()
                || this.runtime().staffOperations().entryBlock(playerSession.getPlayer().getUniqueId(),
                        this.dungeon.getWorldName()) != null)) return;
        if (this.players.contains(playerSession)) {
            return;
        }

        super.addPlayer(playerSession);
        if (playerSession.getInstance() != this) {
            return;
        }
        // Commit access policy before join commands can grant money/items or change other services.
        if (playerSession.hasReservedAccessKey(this.dungeon.getWorldName())
                && !this.dungeon.completeReservedAccessKey(playerSession))
            throw new IllegalStateException("Could not commit dungeon access key usage");
        Player player = playerSession.getPlayer();
        this.livingPlayers.add(playerSession);
        this.showCustomDragonBossBars(player);
        this.executeJoinCommands(player);

        Location storedSavePoint = playerSession.getDungeonSavePoint(this.dungeon.getWorldName());
        Location savePoint = storedSavePoint == null ? null : storedSavePoint.clone();

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
                Bukkit.getScheduler()
                        .runTaskLater(
                                this.plugin(),
                                () -> {
                                    if (playerSession.getInstance() == this
                                            && this.players.contains(playerSession)) {
                                        this.startGame();
                                    }
                                },
                                1L);
            }

            Bukkit.getScheduler()
                    .runTaskLater(
                            this.plugin(),
                            () -> {
                                if (playerSession.getInstance() != this
                                        || !this.players.contains(playerSession)) {
                                    return;
                                }
                                EntityUtils.forceTeleport(player, savePoint);
                                playerSession.setDungeonRespawn(savePoint);
                                LangUtils.sendMessage(player, "instance.play.functions.savepoint");
                            },
                            2L);
        }

        try {
            GameMode gamemode =
                    GameMode.valueOf(
                            this.config
                                    .getString("players.gamemode", "ADVENTURE")
                                    .toUpperCase(Locale.ROOT));
            player.setGameMode(gamemode);
        } catch (IllegalArgumentException exception) {
            this.logger()
                    .warn(
                            "Dungeon '{}' has invalid game mode '{}'; defaulting player '{}' to"
                                + " ADVENTURE.",
                            this.dungeon.getWorldName(),
                            this.config.getString("players.gamemode", "ADVENTURE"),
                            player.getName(),
                            exception);
            player.setGameMode(GameMode.ADVENTURE);
        }

        if (this.livesEnabled) {
            this.playerLives.putIfAbsent(
                    player.getUniqueId(), this.config.getInt("players.lives", 1));
        }
    }

    /**
     * Runs configured console commands for a player entering the playable instance.
     */
    private void executeJoinCommands(Player player) {
        for (String commandTemplate : this.dungeon.getJoinCommands()) {
            String command = this.resolveJoinCommand(commandTemplate, player);
            // Old suggested commands collided with WorldGuard /god and used an obsolete toggle.
            if (command.matches("(?i)(god|fly) \\S+ disable")) {
                command =
                        "essentials:"
                                + command.substring(0, command.length() - "disable".length())
                                + "off";
            }
            if (command.isEmpty()) {
                continue;
            }

            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    this.logger()
                            .warn(
                                    "Join command failed for '{}' in dungeon '{}': {}",
                                    player.getName(),
                                    this.dungeon.getWorldName(),
                                    command);
                }
            } catch (Exception exception) {
                this.logger()
                        .error(
                                "Join command threw an exception for '{}' in dungeon '{}': {}",
                                player.getName(),
                                this.dungeon.getWorldName(),
                                command,
                                exception);
            }
        }
    }

    /**
     * Resolves supported placeholders and normalizes optional leading command slashes.
     */
    private String resolveJoinCommand(String commandTemplate, Player player) {
        if (commandTemplate == null) {
            return "";
        }

        String command = commandTemplate.trim();
        while (command.startsWith("/")) {
            command = command.substring(1).trim();
        }

        String playerName = player.getName();
        String playerId = player.getUniqueId().toString();
        return command.replace("{player}", playerName)
                .replace("{player_name}", playerName)
                .replace("<player>", playerName)
                .replace("%player%", playerName)
                .replace("%player_name%", playerName)
                .replace("{uuid}", playerId)
                .replace("%uuid%", playerId);
    }

    /** Selects the configured departure destination before the base class teleports the player. */
    @Override
    protected void onPlayerRemovalAccepted(DungeonPlayerSession playerSession) {
        if (this.dungeon.getExitLoc() != null && this.dungeon.isAlwaysUseExit()) {
            playerSession.setSavedPosition(this.dungeon.getExitLoc().clone());
        }
    }

    /**
     * Removes a player from the run and applies cooldown persistence.
     */
    @Override
    public void removePlayer(DungeonPlayerSession playerSession, boolean force) {
        if (this.players.contains(playerSession)) {
            super.removePlayer(playerSession, force);
            if (this.players.contains(playerSession)) {
                return;
            }
            Player player = playerSession.getPlayer();
            BukkitRunnable offlineTracker = this.offlineTrackers.remove(player.getUniqueId());
            if (offlineTracker != null && !offlineTracker.isCancelled()) {
                offlineTracker.cancel();
            }
            this.livingPlayers.remove(playerSession);
            this.hideCustomDragonBossBars(player);
            if (!CommandUtils.hasPermissionSilent(player, "dungeons.vanish")) {
                this.messagePlayers(
                                "instance.play.events.player-leave",
                                LangUtils.placeholder("player", player.getName()));
            }

            this.dungeon.saveCooldowns(player);
            this.dungeon.savePlayerData(player);

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
    public void extendTime(int seconds) {
        if (!this.started || this.config.getInt("runs.time_limit_minutes", 0) == 0)
            throw new IllegalArgumentException("Run has no active time limit");
        if (seconds < 1 || seconds > 86400)
            throw new IllegalArgumentException("Use 1–86400 seconds");
        this.timeLeft = Math.addExact(this.timeLeft, seconds);
    }

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

    /**
     * Ensures manually spawned dragons in this instance still show a boss bar.
     */
    public void trackCustomDragonBossBar(Entity entity) {
        if (!(entity instanceof EnderDragon dragon)) {
            return;
        }

        if (this.customDragonBossBars.containsKey(dragon.getUniqueId())) {
            return;
        }

        BossBar bossBar = this.createCustomDragonBossBar();
        this.customDragonBossBars.put(dragon.getUniqueId(), bossBar);
        this.showCustomDragonBossBars();
        this.updateCustomDragonBossBars();
    }

    /**
     * Updates custom dragon boss bars and removes stale ones.
     */
    private void updateCustomDragonBossBars() {
        this.discoverCustomDragonBossBars();
        List<UUID> staleBars = new ArrayList<>();

        for (Entry<UUID, BossBar> entry :
                this.customDragonBossBars.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            BossBar bossBar = entry.getValue();
            if (!(entity instanceof EnderDragon dragon)
                    || !dragon.isValid()
                    || dragon.isDead()
                    || dragon.getWorld() != this.instanceWorld) {
                this.hideCustomDragonBossBarFromAll(bossBar);
                staleBars.add(entry.getKey());
                continue;
            }

            double maxHealth =
                    dragon.getAttribute(Attribute.MAX_HEALTH) == null
                            ? 200.0
                            : dragon.getAttribute(Attribute.MAX_HEALTH).getValue();
            double progress = maxHealth <= 0.0 ? 0.0 : dragon.getHealth() / maxHealth;
            bossBar.progress((float) Math.clamp(progress, 0.0, 1.0));
            Component customName = dragon.customName();
            if (customName != null && !ComponentUtils.plainText(customName).isBlank()) {
                bossBar.name(customName);
            } else {
                bossBar.name(Component.text("Ender Dragon"));
            }
        }

        for (UUID staleId : staleBars) {
            this.customDragonBossBars.remove(staleId);
        }
    }

    /**
     * Discovers ender dragons already present in the instance world and tracks them.
     */
    private void discoverCustomDragonBossBars() {
        if (this.instanceWorld == null) {
            return;
        }

        for (EnderDragon dragon : this.instanceWorld.getEntitiesByClass(EnderDragon.class)) {
            if (!this.customDragonBossBars.containsKey(dragon.getUniqueId())) {
                BossBar bossBar = this.createCustomDragonBossBar();
                this.customDragonBossBars.put(dragon.getUniqueId(), bossBar);
                this.showCustomDragonBossBars();
            }
        }
    }

    /**
     * Adds all instance players to every tracked custom dragon boss bar.
     */
    private void showCustomDragonBossBars() {
        for (DungeonPlayerSession playerSession : this.players) {
            this.showCustomDragonBossBars(playerSession.getPlayer());
        }
    }

    /**
     * Adds one player to every tracked custom dragon boss bar.
     */
    private void showCustomDragonBossBars(Player player) {
        for (BossBar bossBar : this.customDragonBossBars.values()) {
            player.showBossBar(bossBar);
        }
    }

    /**
     * Removes one player from every tracked custom dragon boss bar.
     */
    private void hideCustomDragonBossBars(Player player) {
        for (BossBar bossBar : this.customDragonBossBars.values()) {
            player.hideBossBar(bossBar);
        }
    }

    /**
     * Clears all custom dragon boss bars owned by this instance.
     */
    private void clearCustomDragonBossBars() {
        for (BossBar bossBar : this.customDragonBossBars.values()) {
            this.hideCustomDragonBossBarFromAll(bossBar);
        }

        this.customDragonBossBars.clear();
    }

    /**
     * Creates the modern Paper boss bar used for dungeon-managed dragons.
     */
    private BossBar createCustomDragonBossBar() {
        return BossBar.bossBar(
                Component.text("Ender Dragon"),
                1.0F,
                BossBar.Color.PURPLE,
                BossBar.Overlay.PROGRESS);
    }

    /**
     * Hides one tracked dragon bar from all players currently in this instance.
     */
    private void hideCustomDragonBossBarFromAll(BossBar bossBar) {
        for (DungeonPlayerSession playerSession : this.players) {
            playerSession.getPlayer().hideBossBar(bossBar);
        }
    }
}
