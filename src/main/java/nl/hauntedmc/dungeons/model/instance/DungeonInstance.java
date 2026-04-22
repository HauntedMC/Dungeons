package nl.hauntedmc.dungeons.model.instance;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import nl.hauntedmc.dungeons.event.DungeonDisposeEvent;
import nl.hauntedmc.dungeons.event.DungeonEndEvent;
import nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.listener.instance.InstanceListener;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.PluginEnvironment;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.entity.HologramManager;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;
import nl.hauntedmc.dungeons.util.world.WorldUtils;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
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

/**
 * Base runtime representation of a concrete dungeon world copy.
 *
 * <p>An instance owns the copied world folder, the live Bukkit world, the bound triggers and
 * functions, and the collection of players currently attached to that run.</p>
 */
public abstract class DungeonInstance {
    protected int id;
    protected String instanceWorldName;
    protected final FileConfiguration config;
    protected final DungeonDefinition dungeon;
    protected World instanceWorld;
    protected InstanceListener listener;
    protected final Map<Location, DungeonFunction> functions = new HashMap<>();
    protected final Map<Location, RewardFunction> rewardFunctions = new HashMap<>();
    protected HologramManager hologramManager;
    private final Map<Class<? extends DungeonTrigger>, List<DungeonTrigger>> triggerListeners =
            new HashMap<>();
    private final Map<Class<? extends DungeonFunction>, List<DungeonFunction>> functionListeners =
            new HashMap<>();
    protected final List<DungeonPlayerSession> players;
    protected Location startLocation;
    protected final CountDownLatch latch;
    protected boolean initialized;
    protected boolean disposing = false;
    protected volatile boolean ready;
    protected volatile boolean loadFailed;
    @Nullable protected UUID startedTeamLeaderId;

    /**
     * Creates a new instance wrapper for a dungeon and the latch used to signal async world startup.
     */
    public DungeonInstance(DungeonDefinition dungeon, CountDownLatch latch) {
        this.dungeon = dungeon;
        this.config = dungeon.getConfig();
        this.id = 0;
        this.latch = latch == null ? new CountDownLatch(1) : latch;
        this.players = new ArrayList<>();
        this.hologramManager = new HologramManager();
    }

        public final DungeonsRuntime getRuntime() {
        return this.dungeon.getRuntime();
    }

        protected final DungeonsRuntime runtime() {
        return this.dungeon.getRuntime();
    }

        public final PluginEnvironment getEnvironment() {
        return this.dungeon.getEnvironment();
    }

        protected final DungeonsPlugin plugin() {
        return this.getEnvironment().plugin();
    }

        protected final org.slf4j.Logger logger() {
        return this.getEnvironment().logger();
    }

        protected final PlayerSessionRegistry playerSessions() {
        return this.getRuntime().playerSessions();
    }

        protected final ActiveInstanceRegistry activeInstances() {
        return this.getRuntime().activeInstances();
    }

    /**
     * Starts asynchronous instance initialization by copying the source world and loading it into
     * Bukkit.
     */
    public DungeonInstance initialize() {
        if (!this.initialized) {
            this.initialized = true;

            try {
                this.copyMapToWorldsFolder();
                Bukkit.getScheduler().runTask(this.plugin(), () -> this.initializeWorld());
            } catch (IOException exception) {
                this.loadFailed = true;
                this.logger()
                        .error(
                                "Failed to copy dungeon '{}' to instance folder '{}'.",
                                this.dungeon.getWorldName(),
                                this.instanceWorldName,
                                exception);
                this.cleanupPendingWorldFolder();
                this.signalInitializationComplete();
            }
        }
        return this;
    }

    /**
     * Copies the source dungeon world into a unique instance folder and strips per-world files that
     * must be regenerated.
     */
    protected void copyMapToWorldsFolder() throws IOException {
        this.id = this.dungeon.getInstances().size();
        File instFolder = this.getUniqueWorldName();
        FileUtils.copyDirectory(this.getDungeon().getFolder(), instFolder);
                new File(instFolder, "config.yml").delete();
                new File(instFolder, "uid.dat").delete();
    }

        protected File getUniqueWorldName() {
        this.instanceWorldName = this.getDungeon().getWorldName() + "_" + this.id;
        File instFolder = new File(Bukkit.getWorldContainer(), this.instanceWorldName);
        if (!instFolder.exists()) {
            return instFolder;
        } else {
            this.id++;
            return this.getUniqueWorldName();
        }
    }

    /**
     * Builds the default world creator from the dungeon configuration.
     */
    protected void initializeWorld() {
        WorldCreator loader = new WorldCreator(this.instanceWorldName);
        String genName = this.dungeon.getConfig().getString("dungeon.chunk_generator", "NATURAL");
        if (!genName.equalsIgnoreCase("NATURAL")) {
            loader.generator(genName);
        } else {
            loader.type(WorldType.FLAT);
        }

        loader.generateStructures(false);
        this.initializeWorld(loader);
    }

    /**
     * Builds a world creator that uses an explicit chunk generator.
     */
    protected void initializeWorld(ChunkGenerator chunkHandler) {
        WorldCreator loader = new WorldCreator(this.instanceWorldName);
        loader.generateStructures(false);
        loader.type(WorldType.FLAT);
        loader.generator(chunkHandler);
        this.initializeWorld(loader);
    }

    /**
     * Finalizes world creation and then proceeds into gameplay state loading.
     */
    protected void initializeWorld(WorldCreator loader) {
        String worldDimension =
                this.config.getString("dungeon.environment", "NORMAL").toUpperCase(Locale.ROOT);

        try {
            loader.environment(Environment.valueOf(worldDimension));
        } catch (IllegalArgumentException exception) {
            this.logger()
                    .warn(
                            "Invalid world dimension '{}' for instance '{}' of dungeon '{}'. Falling back to default environment.",
                            worldDimension,
                            this.instanceWorldName,
                            this.dungeon.getWorldName(),
                            exception);
        }

        try {
            TextUtils.runTimed(
                    "Loading world into memory " + this.instanceWorldName,
                    () -> this.instanceWorld = loader.createWorld());
            if (this.instanceWorld == null) {
                this.loadFailed = true;
                this.logger()
                        .error(
                                "Failed to create world '{}' for dungeon instance '{}'.",
                                this.instanceWorldName,
                                this.dungeon.getWorldName());
                this.cleanupPendingWorldFolder();
                this.signalInitializationComplete();
                return;
            }

            this.applyWorldRules();
            this.clearTaggedHologramsOnStart();
            // Game data is only loaded after the world exists and its baseline rules are configured so
            // functions, triggers, and entities see the final runtime environment.
            this.loadGame();
        } catch (Throwable throwable) {
            this.loadFailed = true;
            this.ready = false;
            this.logger()
                    .error(
                            "Failed to initialize world '{}' for dungeon '{}'.",
                            this.instanceWorldName,
                            this.dungeon.getWorldName(),
                            throwable);
            if (this.instanceWorld != null) {
                Bukkit.unloadWorld(this.instanceWorld, false);
                this.instanceWorld = null;
            }
            this.cleanupPendingWorldFolder();
            this.signalInitializationComplete();
        }
    }

    /**
     * Applies the world-level rules that make copied instance worlds behave like dungeon spaces.
     */
    protected void applyWorldRules() {
        WorldUtils.releaseSpawnChunk(this.instanceWorld);
        this.instanceWorld.setAutoSave(false);
        if (!this.config.getBoolean("rules.spawning.natural_mobs", false)) {
            for (SpawnCategory cat : SpawnCategory.values()) {
                if (cat != SpawnCategory.MISC) {
                    this.instanceWorld.setTicksPerSpawns(cat, 0);
                }
            }
        } else if (!this.config.getBoolean("rules.spawning.animals", false)) {
            this.instanceWorld.setTicksPerSpawns(SpawnCategory.ANIMAL, 0);
            this.instanceWorld.setTicksPerSpawns(SpawnCategory.AMBIENT, 0);
            this.instanceWorld.setTicksPerSpawns(SpawnCategory.AXOLOTL, 0);
            this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_AMBIENT, 0);
            this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_ANIMAL, 0);
            this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_UNDERGROUND_CREATURE, 0);
        } else if (!this.config.getBoolean("rules.spawning.monsters", false)) {
            this.instanceWorld.setTicksPerSpawns(SpawnCategory.MONSTER, 0);
        }

        if (this.config.getBoolean("rules.world.freeze_random_ticks", true)) {
            this.instanceWorld.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
        }

        this.onApplyWorldRules();
    }

        protected void onApplyWorldRules() {}

        protected void loadGame() {
        this.startLocation = this.dungeon.getStartSpawn();
        if (this.startLocation == null) {
            this.startLocation = this.instanceWorld.getSpawnLocation();
        }

        this.startLocation = this.startLocation.clone();
        this.startLocation.setWorld(this.instanceWorld);

        this.onLoadGame();
        this.onInstanceReady();
        this.ready = true;
        this.signalInitializationComplete();
    }

        protected void onLoadGame() {}

        protected void onInstanceReady() {}

        protected final void signalInitializationComplete() {
        this.latch.countDown();
    }

        public void dispose() {
        if (!this.disposing) {
            if (this.players.isEmpty()) {
                if (this.instanceWorld.getPlayerCount() <= 0) {
                    this.disposing = true;
                    Runnable disposal =
                            () -> {
                                this.logger()
                                        .info(
                                                "Disposing instance '{}' for dungeon '{}'.",
                                                this.instanceWorld.getName(),
                                                this.dungeon.getWorldName());

                                try {
                                    this.onDispose();
                                } catch (Exception exception) {
                                    this.logger()
                                            .error(
                                                    "Custom disposal hook failed for instance '{}' in dungeon '{}'.",
                                                    this.instanceWorld.getName(),
                                                    this.dungeon.getWorldName(),
                                                    exception);
                                }

                                this.functions.clear();
                                this.rewardFunctions.clear();
                                this.listener = null;
                                this.clearHolograms();
                                this.hologramManager = null;
                                Bukkit.getScheduler()
                                        .runTaskLater(
                                                this.plugin(),
                                                () -> {
                                                    try {
                                                        CountDownLatch latch = new CountDownLatch(1);
                                                        File worldFolder = this.instanceWorld.getWorldFolder();
                                                        String worldName = this.instanceWorld.getName();
                                                        this.saveWorld(latch);
                                                        TextUtils.runTimed(
                                                                "Unloading dungeon world " + worldName,
                                                                () -> Bukkit.unloadWorld(this.instanceWorld, false));
                                                        if (this.plugin().isEnabled()) {
                                                            Bukkit.getScheduler()
                                                                    .runTaskLaterAsynchronously(
                                                                            this.plugin(),
                                                                            () -> this.cleanWorldFiles(latch, worldFolder, worldName),
                                                                            100L);
                                                        } else {
                                                            this.cleanWorldFiles(latch, worldFolder, worldName);
                                                        }
                                                    } catch (Exception exception) {
                                                        if (Bukkit.getWorld(this.instanceWorld.getName()) == null) {
                                                            return;
                                                        }

                                                        this.logger()
                                                                .error(
                                                                        "Failed to unload instance world '{}' for dungeon '{}'. The world is still loaded after disposal.",
                                                                        this.instanceWorld.getName(),
                                                                        this.dungeon.getWorldName(),
                                                                        exception);
                                                    }
                                                },
                                                1L);
                                this.dungeon.removeInstance(this);
                                Bukkit.getPluginManager().callEvent(new DungeonDisposeEvent(this));
                            };
                    int delay = this.dungeon.getConfig().getInt("runs.cleanup_delay_ticks", 0);
                    if (this.plugin().isEnabled() && delay > 0 && !this.isEditInstance()) {
                        Bukkit.getScheduler().runTaskLater(this.plugin(), disposal, delay);
                    } else {
                        disposal.run();
                    }
                }
            }
        }
    }

        public abstract void onDispose();

        protected void cleanWorldFiles(CountDownLatch latch, File worldFolder, String worldName) {
        try {
            if (this.isEditInstance()) {
                latch.await();
            }

            FileUtils.deleteDirectory(worldFolder);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            this.logger()
                    .error(
                            "Interrupted while waiting to clean files for instance world '{}'.",
                            worldName,
                            exception);
        } catch (IOException exception) {
            this.logger()
                    .warn(
                            "Failed to delete files for instance world '{}'. The world is unloaded, so this leaves temporary file clutter until restart.",
                            worldName,
                            exception);
        }

        this.instanceWorld = null;
    }

        public void addPlayer(DungeonPlayerSession playerSession) {
        if (!this.players.contains(playerSession)) {
            Player player = playerSession.getPlayer();
            this.players.add(playerSession);
            playerSession.setInstance(this);
            playerSession.setAwaitingDungeon(false);
            playerSession.setSavedPosition(player.getLocation());
            playerSession.setSavedGameMode(player.getGameMode());
            playerSession.setSavedExp(player.getExp());
            playerSession.setSavedLevel(player.getLevel());
            Bukkit.getScheduler()
                    .runTaskLater(
                            this.plugin(),
                            () -> {
                                if (!this.config.getBoolean("players.keep_on_entry.inventory", true)) {
                                    player.getInventory().clear();
                                }

                                if (!this.config.getBoolean("players.keep_on_entry.health", false)) {
                                    player.setHealth(EntityUtils.getMaxHealth(player));
                                }

                                if (!this.config.getBoolean("players.keep_on_entry.food", false)) {
                                    player.setFoodLevel(30);
                                }

                                if (!this.config.getBoolean("players.keep_on_entry.potion_effects", false)) {
                                    for (PotionEffect effect : player.getActivePotionEffects()) {
                                        player.removePotionEffect(effect.getType());
                                    }
                                }

                                if (!this.config.getBoolean("players.keep_on_entry.experience", true)) {
                                    player.setExp(0.0F);
                                    player.setLevel(0);
                                }
                            },
                            1L);
        }
    }

        public void removePlayer(DungeonPlayerSession playerSession) {
        this.removePlayer(playerSession, true);
    }

        public void removePlayer(DungeonPlayerSession playerSession, boolean force) {
        Player player = playerSession.getPlayer();
        if (!this.players.contains(playerSession)) {
            if (playerSession.getInstance() == this
                    && player.isOnline()
                    && playerSession.getSavedPosition() != null) {
                playerSession.setInstance(null);
                EntityUtils.forceTeleport(player, playerSession.getSavedPosition());
                playerSession.setSavedPosition(null);
            }
        } else {
            if (this.players.size() == 1) {
                Bukkit.getPluginManager().callEvent(new DungeonEndEvent(this));
            }

            PlayerLeaveDungeonEvent leaveEvent = new PlayerLeaveDungeonEvent(this, playerSession);
            Bukkit.getPluginManager().callEvent(leaveEvent);
            if (force || !leaveEvent.isCancelled()) {
                if (this.dungeon.isCooldownOnLeave()
                        && this.dungeon.shouldApplyAccessCooldown(this, player.getUniqueId())) {
                    this.dungeon.addAccessCooldown(player);
                }

                this.players.remove(playerSession);
                player.setGameMode(playerSession.getSavedGameMode());
                playerSession.setInstance(null);
                playerSession.setDungeonRespawn(null);
                if (player.isOnline()) {
                    playerSession.clearExitLocation();
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
                            target = this.players.getFirst().getPlayer();
                            ItemUtils.giveOrDrop(target, item);
                        }
                    }

                    if (ItemUtils.verifyDungeonItem(item)) {
                        inv.remove(item);
                        if (!this.players.isEmpty()
                                && this.dungeon.getConfig().getBoolean("players.transfer_dungeon_items", true)) {
                            foundDungeonItems = true;
                            target = this.players.getFirst().getPlayer();
                            ItemUtils.giveOrDrop(target, item);
                        }
                    }
                }

                ItemStack offhand = inv.getItemInOffHand();
                if (ItemUtils.verifyKeyItem(offhand)) {
                    inv.setItemInOffHand(null);
                    if (!this.players.isEmpty()) {
                        foundKeys = true;
                        target = this.players.getFirst().getPlayer();
                        ItemUtils.giveOrDrop(target, offhand);
                    }
                }

                if (ItemUtils.verifyDungeonItem(offhand)) {
                    inv.setItemInOffHand(null);
                    if (!this.players.isEmpty()
                            && this.dungeon.getConfig().getBoolean("players.transfer_dungeon_items", true)) {
                        foundDungeonItems = true;
                        target = this.players.getFirst().getPlayer();
                        ItemUtils.giveOrDrop(target, offhand);
                    }
                }

                if (target != null) {
                    if (foundKeys) {
                        this.messagePlayers(
                                LangUtils.getMessage(
                                        "instance.play.events.key-inheritance",
                                        LangUtils.placeholder("player", target.getName())));
                    }

                    if (foundDungeonItems) {
                        this.messagePlayers(
                                LangUtils.getMessage(
                                        "instance.play.events.dungeon-item-inheritance",
                                        LangUtils.placeholder("player", target.getName())));
                    }
                }

                player.updateInventory();

                if (player.isOnline() && playerSession.getSavedPosition() != null) {
                    EntityUtils.forceTeleport(player, playerSession.getSavedPosition());
                    playerSession.setSavedPosition(null);
                }

                if (this.players.isEmpty()) {
                    if (this.plugin().isEnabled()) {
                        Bukkit.getScheduler().runTaskLater(this.plugin(), this::dispose, 1L);
                    } else {
                        this.dispose();
                    }
                }
            }
        }
    }

        public void messagePlayers(String message) {
        for (DungeonPlayerSession playerSession : this.players) {
            playerSession.getPlayer().sendMessage(message);
        }
    }

        public void updateHologram(Location loc, float range, String text, boolean isLabel) {
        if (this.hologramManager != null) {
            this.hologramManager.updateHologram(loc, range, text, isLabel);
        }
    }

        public void removeHologram(Location loc) {
        if (this.hologramManager != null) {
            this.hologramManager.removeHologram(loc);
        }
    }

        public void showHologram(Location loc, float range) {
        if (this.hologramManager != null) {
            this.hologramManager.showHologram(loc, range);
        }
    }

        public void hideHologram(Location loc) {
        if (this.hologramManager != null) {
            this.hologramManager.hideHologram(loc);
        }
    }

        public void clearHolograms() {
        if (this.hologramManager != null) {
            this.hologramManager.clearAllHolograms();
        }
    }

        public void clearTaggedHologramsOnStart() {
        if (this.instanceWorld == null) {
            return;
        }

        Collection<TextDisplay> displays = this.instanceWorld.getEntitiesByClass(TextDisplay.class);
        NamespacedKey key = new NamespacedKey(this.plugin(), "dungeonhologram");

        for (TextDisplay display : displays) {
            PersistentDataContainer data = display.getPersistentDataContainer();
            if (data.has(key, PersistentDataType.BOOLEAN)) {
                display.remove();
            }
        }
    }

        public boolean isPlayInstance() {
        return this instanceof PlayableInstance;
    }

        public boolean isEditInstance() {
        return this instanceof EditableInstance;
    }

    @Nullable public PlayableInstance asPlayInstance() {
        return this.isPlayInstance() ? (PlayableInstance) this : null;
    }

    @Nullable public EditableInstance asEditInstance() {
        return this.isEditInstance() ? (EditableInstance) this : null;
    }

    @Nullable public <T extends DungeonInstance> T as(Class<T> clazz) {
        return (T) (clazz.isInstance(this) ? this : null);
    }

        public void saveWorld() {}

        public void saveWorld(@Nullable CountDownLatch latch) {}

        public void registerTriggerListener(DungeonTrigger element) {
        Class<? extends DungeonTrigger> elementClazz = element.getClass();
        List<DungeonTrigger> elements =
                this.triggerListeners.computeIfAbsent(elementClazz, k -> new ArrayList<>());
        elements.add(element);
    }

        public void unregisterTriggerListener(DungeonTrigger element) {
        List<DungeonTrigger> elements = this.triggerListeners.get(element.getClass());
        if (elements != null) {
            elements.remove(element);
        }
    }

    @Nullable public List<DungeonTrigger> getTriggerListeners(Class<? extends DungeonTrigger> type) {
        return this.triggerListeners.get(type);
    }

        public void registerFunctionListener(DungeonFunction element) {
        Class<? extends DungeonFunction> elementClazz = element.getClass();
        List<DungeonFunction> elements =
                this.functionListeners.computeIfAbsent(elementClazz, k -> new ArrayList<>());
        elements.add(element);
    }

        public void unregisterFunctionListener(DungeonFunction element) {
        List<DungeonFunction> elements = this.functionListeners.get(element.getClass());
        if (elements != null) {
            elements.remove(element);
        }
    }

    @Nullable public List<DungeonFunction> getFunctionListeners(Class<? extends DungeonFunction> type) {
        return this.functionListeners.get(type);
    }

        public FileConfiguration getConfig() {
        return this.config;
    }

        public DungeonDefinition getDungeon() {
        return this.dungeon;
    }

    @Nullable public UUID getStartedTeamLeaderId() {
        return this.startedTeamLeaderId;
    }

        public void setStartedTeamLeaderId(@Nullable UUID startedTeamLeaderId) {
        this.startedTeamLeaderId = startedTeamLeaderId;
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

        public Map<Location, RewardFunction> getRewardFunctions() {
        return this.rewardFunctions;
    }

        public HologramManager getHologramManager() {
        return this.hologramManager;
    }

        public Map<Class<? extends DungeonTrigger>, List<DungeonTrigger>> getTriggerListeners() {
        return this.triggerListeners;
    }

        public Map<Class<? extends DungeonFunction>, List<DungeonFunction>> getFunctionListeners() {
        return this.functionListeners;
    }

        public List<DungeonPlayerSession> getPlayers() {
        return this.players;
    }

        public Location getStartLoc() {
        return this.startLocation;
    }

        public boolean awaitInitialization(long timeout, java.util.concurrent.TimeUnit unit)
            throws InterruptedException {
        return this.latch.await(timeout, unit);
    }

        public boolean isReady() {
        return this.ready;
    }

        public boolean hasLoadFailed() {
        return this.loadFailed;
    }

        public void cleanupPendingWorldFolder() {
        if (this.instanceWorld != null
                || this.instanceWorldName == null
                || this.instanceWorldName.isBlank()) {
            return;
        }

        File worldFolder = new File(Bukkit.getWorldContainer(), this.instanceWorldName);
        if (!worldFolder.isDirectory()) {
            return;
        }

        try {
            FileUtils.deleteDirectory(worldFolder);
        } catch (IOException exception) {
            this.logger()
                    .warn(
                            "Failed to delete pending world folder '{}'.",
                            worldFolder.getAbsolutePath(),
                            exception);
        }
    }
}
