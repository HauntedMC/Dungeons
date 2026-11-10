package nl.hauntedmc.dungeons.model.instance;

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
import nl.hauntedmc.dungeons.util.config.DungeonConfigView;
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
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

/**
 * Base runtime representation of a concrete dungeon world copy.
 *
 * <p>An instance owns the copied world folder, the live Bukkit world, the bound triggers and
 * functions, and the collection of players currently attached to that run.
 */
public abstract class DungeonInstance {
    private final UUID instanceId = UUID.randomUUID();

    /** Stable across world load and unload; never reused by another instance. */
    public UUID getInstanceId() {
        return this.instanceId;
    }

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
    protected volatile boolean disposing = false;

    public enum DisposalState {
        ACTIVE,
        SCHEDULED,
        PREPARING,
        UNLOADING,
        RETRYABLE_FAILURE,
        DISPOSED
    }

    private volatile DisposalState disposalState = DisposalState.ACTIVE;
    private volatile Throwable disposalFailure;
    private CompletableFuture<Boolean> disposalAttempt;
    private BukkitTask disposalTask;
    private BukkitTask disposalRetryTask;
    private boolean disposalHookInvoked;
    private boolean disposalStartedEnabled;
    private int automaticDisposalRetries;
    private static final int MAX_DISPOSAL_RETRIES = 3;
    private static final long DISPOSAL_RETRY_TICKS = 100L;
    protected volatile boolean ready;
    protected volatile boolean loadFailed;
    private boolean pendingFolderOwned;
    @Nullable protected UUID startedTeamLeaderId;

    /**
     * Creates a new instance wrapper for a dungeon and the latch used to signal async world
     * startup.
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
        File source = this.getDungeon().getFolder();
        WorldUtils.readTemplate(
                source,
                () -> {
                    this.id = this.dungeon.getInstances().size();
                    File instFolder = this.getUniqueWorldName();
                    // Paper converts legacy level.dat + DIM folders only from the world container.
                    // Modern dimensions already use flat terrain storage and must skip old leftovers.
                    boolean legacy = legacyTemplate(source);
                    FileUtils.copyDirectory(
                            source,
                            instFolder,
                            file ->
                                    legacy || !List.of(
                                                    "level.dat",
                                                    "level.dat_old",
                                                    "DIM1",
                                                    "DIM-1",
                                                    "session.lock")
                                            .contains(file.getName()));
                    new File(instFolder, "config.yml").delete();
                    for (String identity :
                            List.of(
                                    "uid.dat",
                                    "data/paper/metadata.dat",
                                    "data/paper/metadata.dat_old")) {
                        java.nio.file.Files.deleteIfExists(new File(instFolder, identity).toPath());
                    }
                });
    }

    private static boolean legacyTemplate(File source) {
        return new File(source, "level.dat").isFile()
                && !new File(source, "data/minecraft/world_gen_settings.dat").isFile();
    }

    protected File getUniqueWorldName() throws IOException {
        return this.reserveWorldFolder(legacyTemplate(this.dungeon.getFolder()));
    }

    /** Reserves a name before asynchronous copies or generation can race another instance. */
    protected File reserveWorldFolder() throws IOException {
        return this.reserveWorldFolder(false);
    }

    private File reserveWorldFolder(boolean legacy) throws IOException {
        synchronized (WorldUtils.class) {
            while (true) {
                String name = this.getDungeon().getWorldName() + "_" + this.id;
                File conflictingRoot = new File(Bukkit.getWorldContainer(), name);
                File dimension = WorldUtils.dimensionFolder(name);
                if (!conflictingRoot.exists()
                        && !dimension.exists()
                        && Bukkit.getWorld(name) == null) {
                    File destination = legacy ? conflictingRoot : dimension;
                    java.nio.file.Files.createDirectories(destination.toPath().getParent());
                    try {
                        java.nio.file.Files.createDirectory(destination.toPath());
                        if (this.isEditInstance()) {
                            Files.writeString(
                                    destination.toPath().resolve(WorldUtils.EDITOR_RECOVERY_MARKER),
                                    "Uncommitted editor world; preserve for manual recovery.\n");
                        }
                        this.instanceWorldName = name;
                        this.pendingFolderOwned = true;
                        return destination;
                    } catch (java.nio.file.FileAlreadyExistsException collision) {
                        // Another filesystem user claimed the name between checking and reserving.
                    }
                }
                this.id++;
            }
        }
    }

    /** Builds the default world creator from the dungeon configuration. */
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

    /** Builds a world creator that uses an explicit chunk generator. */
    protected void initializeWorld(ChunkGenerator chunkHandler) {
        WorldCreator loader = new WorldCreator(this.instanceWorldName);
        loader.generateStructures(false);
        loader.type(WorldType.FLAT);
        loader.generator(chunkHandler);
        this.initializeWorld(loader);
    }

    /** Finalizes world creation and then proceeds into gameplay state loading. */
    protected void initializeWorld(WorldCreator loader) {
        if (this.disposing) {
            this.loadFailed = true;
            this.cleanupPendingWorldFolder();
            this.signalInitializationComplete();
            return;
        }
        String worldDimension =
                this.config.getString("dungeon.environment", "NORMAL").toUpperCase(Locale.ROOT);

        try {
            loader.environment(Environment.valueOf(worldDimension));
        } catch (IllegalArgumentException exception) {
            this.logger()
                    .warn(
                            "Invalid world dimension '{}' for instance '{}' of dungeon '{}'."
                                    + " Falling back to default environment.",
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
            // Game data is only loaded after the world exists and its baseline rules are configured
            // so
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
            try {
                if (this.instanceWorld != null) {
                    if (!this.unloadInstanceWorld()) {
                        return;
                    }
                    this.instanceWorld = null;
                }
                this.cleanupPendingWorldFolder();
            } finally {
                this.signalInitializationComplete();
            }
        }
    }

    private boolean unloadInstanceWorld() {
        if (this.runtime().staffOperations() != null)
            this.runtime().staffOperations().stopObservers(this);
        try {
            if (Bukkit.unloadWorld(this.instanceWorld, false)) {
                return true;
            }
            this.logger()
                    .error(
                            "Retaining files for instance world '{}' because Bukkit refused to"
                                    + " unload it.",
                            this.instanceWorld.getName());
        } catch (Exception exception) {
            this.logger()
                    .error(
                            "Retaining files for instance world '{}' because world unload failed.",
                            this.instanceWorld.getName(),
                            exception);
        }
        return false;
    }

    /**
     * Applies the world-level rules that make copied instance worlds behave like dungeon spaces.
     */
    protected void applyWorldRules() {
        WorldUtils.releaseSpawnChunk(this.instanceWorld);
        this.instanceWorld.setAutoSave(false);
        if (!DungeonConfigView.isNaturalMobSpawningEnabled(this.config)) {
            for (SpawnCategory cat : SpawnCategory.values()) {
                if (cat != SpawnCategory.MISC) {
                    this.instanceWorld.setTicksPerSpawns(cat, 0);
                }
            }
        } else {
            if (!DungeonConfigView.isAnimalSpawningEnabled(this.config)) {
                this.instanceWorld.setTicksPerSpawns(SpawnCategory.ANIMAL, 0);
                this.instanceWorld.setTicksPerSpawns(SpawnCategory.AMBIENT, 0);
                this.instanceWorld.setTicksPerSpawns(SpawnCategory.AXOLOTL, 0);
                this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_AMBIENT, 0);
                this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_ANIMAL, 0);
                this.instanceWorld.setTicksPerSpawns(SpawnCategory.WATER_UNDERGROUND_CREATURE, 0);
            }
            if (!DungeonConfigView.isMonsterSpawningEnabled(this.config)) {
                this.instanceWorld.setTicksPerSpawns(SpawnCategory.MONSTER, 0);
            }
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
        this.ready = !this.disposing;
        this.signalInitializationComplete();
    }

    protected void onLoadGame() {}

    protected void onInstanceReady() {}

    protected final void signalInitializationComplete() {
        this.latch.countDown();
    }

    public void dispose() {
        this.requestDisposal(false);
    }

    /**
     * Attempts disposal. Concurrent callers share the attempt; a failed attempt returns false and
     * may be retried by calling this method again. A disposed instance returns true immediately.
     */
    public CompletableFuture<Boolean> disposeAsync() {
        // Observers may cancel or complete their future without interrupting world ownership.
        return this.requestDisposal(false).copy();
    }

    private CompletableFuture<Boolean> requestDisposal(boolean automatic) {
        CompletableFuture<Boolean> attempt;
        CompletableFuture<Boolean> abandoned = null;
        boolean delayed;
        synchronized (this) {
            if (this.disposalState == DisposalState.DISPOSED)
                return CompletableFuture.completedFuture(true);
            if (this.disposalAttempt != null
                    && !this.disposalAttempt.isDone()
                    && this.disposalStartedEnabled
                    && !this.plugin().isEnabled()
                    && this.disposalState == DisposalState.PREPARING) {
                abandoned = this.disposalAttempt;
                this.disposalAttempt = null;
                this.disposalState = DisposalState.RETRYABLE_FAILURE;
            }
            if (this.disposalAttempt != null && !this.disposalAttempt.isDone()) {
                // Paper cancels delayed tasks at shutdown. Drain a queued attempt inline instead.
                if (this.plugin().isEnabled() || this.disposalState != DisposalState.SCHEDULED)
                    return this.disposalAttempt;
                this.cancelDisposalTask();
                this.disposalStartedEnabled = false;
                attempt = this.disposalAttempt;
                delayed = false;
            } else {
                if (!this.players.isEmpty()) return CompletableFuture.completedFuture(false);
                if (this.disposalRetryTask != null) {
                    this.disposalRetryTask.cancel();
                    this.disposalRetryTask = null;
                }
                if (!automatic) this.automaticDisposalRetries = 0;
                delayed = this.disposalState == DisposalState.ACTIVE;
                this.disposing = true;
                this.ready = false;
                this.disposalFailure = null;
                this.disposalState = DisposalState.SCHEDULED;
                attempt = new CompletableFuture<>();
                this.disposalAttempt = attempt;
                this.disposalStartedEnabled = this.plugin().isEnabled();
            }
        }
        if (abandoned != null) abandoned.complete(false);
        Runnable start = () -> this.beginDisposal(attempt, delayed);
        if (Bukkit.isPrimaryThread()) {
            start.run();
        } else if (this.plugin().isEnabled()) {
            try {
                Bukkit.getScheduler().runTask(this.plugin(), start);
            } catch (RuntimeException failure) {
                this.failDisposal(attempt, failure);
            }
        } else {
            this.failDisposal(
                    attempt,
                    new IllegalStateException(
                            "World disposal requires the server thread; retry during server-thread"
                                    + " shutdown"));
        }
        return attempt;
    }

    private void beginDisposal(CompletableFuture<Boolean> attempt, boolean delayed) {
        synchronized (this) {
            if (this.disposalAttempt != attempt
                    || attempt.isDone()
                    || this.disposalState != DisposalState.SCHEDULED) return;
            int delay = delayed && !this.isEditInstance() ? this.resolveCleanupDelayTicks() : 0;
            if (this.plugin().isEnabled() && delay > 0) {
                try {
                    this.disposalTask =
                            Bukkit.getScheduler()
                                    .runTaskLater(
                                            this.plugin(),
                                            () -> this.performDisposal(attempt),
                                            delay);
                } catch (RuntimeException failure) {
                    this.failDisposal(attempt, failure);
                }
                return;
            }
        }
        this.performDisposal(attempt);
    }

    private void performDisposal(CompletableFuture<Boolean> attempt) {
        synchronized (this) {
            if (this.disposalAttempt != attempt
                    || attempt.isDone()
                    || this.disposalState != DisposalState.SCHEDULED) return;
            this.disposalTask = null;
            this.disposalState = DisposalState.PREPARING;
        }
        try {
            if (!this.players.isEmpty()
                    || (this.instanceWorld != null && this.instanceWorld.getPlayerCount() > 0)) {
                throw new IllegalStateException(
                        "Instance world still has players; retaining registration and world files");
            }
            if (this.instanceWorld != null) this.instanceWorldName = this.instanceWorld.getName();
            if (!this.disposalHookInvoked) {
                this.disposalHookInvoked = true;
                try {
                    this.onDispose();
                } catch (Exception failure) {
                    this.logger()
                            .error(
                                    "Disposal hook failed for dungeon '{}'; continuing safe"
                                            + " unload.",
                                    this.dungeon.getWorldName(),
                                    failure);
                }
            }
            if (this.instanceWorld == null) {
                this.cleanupPendingWorldFolder();
                this.completeDisposal(attempt, null, null);
                return;
            }
            CompletableFuture<Boolean> prepared =
                    this.loadFailed
                            ? CompletableFuture.completedFuture(true)
                            : java.util.Objects.requireNonNull(this.prepareDisposal());
            prepared.whenComplete((saved, failure) -> this.finishDisposal(attempt, saved, failure));
        } catch (Exception failure) {
            this.failDisposal(attempt, failure);
        }
    }

    private void finishDisposal(
            CompletableFuture<Boolean> attempt, Boolean saved, Throwable failure) {
        if (failure != null || !Boolean.TRUE.equals(saved)) {
            this.failDisposal(
                    attempt,
                    failure == null
                            ? new IllegalStateException("Final instance save failed")
                            : failure);
            return;
        }
        Runnable finish =
                () -> {
                    synchronized (this) {
                        if (this.disposalAttempt != attempt
                                || attempt.isDone()
                                || this.disposalState != DisposalState.PREPARING) return;
                        this.disposalState = DisposalState.UNLOADING;
                    }
                    try {
                        if (!this.players.isEmpty() || this.instanceWorld.getPlayerCount() > 0) {
                            throw new IllegalStateException(
                                    "Players entered the world during disposal; unload deferred");
                        }
                        this.clearHolograms();
                        File folder = this.instanceWorld.getWorldFolder();
                        String name = this.instanceWorld.getName();
                        if (!this.unloadInstanceWorld())
                            throw new IllegalStateException(
                                    "Bukkit did not unload instance world '" + name + "'");
                        this.instanceWorld = null;
                        this.completeDisposal(attempt, folder, name);
                    } catch (Exception unloadFailure) {
                        this.failDisposal(attempt, unloadFailure);
                    }
                };
        if (Bukkit.isPrimaryThread()) {
            finish.run();
        } else if (this.plugin().isEnabled()) {
            try {
                Bukkit.getScheduler().runTask(this.plugin(), finish);
            } catch (RuntimeException schedulingFailure) {
                this.failDisposal(attempt, schedulingFailure);
            }
        } else {
            this.failDisposal(
                    attempt,
                    new IllegalStateException(
                            "Cannot finish world disposal off-thread after shutdown"));
        }
    }

    private void completeDisposal(CompletableFuture<Boolean> attempt, File folder, String name) {
        List<Path> retired = folder == null ? List.of() : this.detachWorldFiles(folder, name);
        this.functions.clear();
        this.rewardFunctions.clear();
        this.triggerListeners.clear();
        this.functionListeners.clear();
        this.listener = null;
        this.hologramManager = null;
        this.dungeon.removeInstance(this);
        this.disposalState = DisposalState.DISPOSED;
        this.disposalFailure = null;
        this.signalInitializationComplete();
        try {
            this.onDisposalComplete();
        } catch (Exception failure) {
            this.logger()
                    .error(
                            "Post-disposal hook failed for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            failure);
        }
        if (!retired.isEmpty()) {
            if (this.plugin().isEnabled()) {
                try {
                    Bukkit.getScheduler()
                            .runTaskLaterAsynchronously(
                                    this.plugin(),
                                    () -> this.deleteRetiredWorldFiles(retired),
                                    100L);
                } catch (RuntimeException failure) {
                    this.logger()
                            .error(
                                    "Unable to schedule deletion of retired world folders '{}';"
                                            + " retaining them for restart cleanup.",
                                    retired,
                                    failure);
                }
            } else {
                this.deleteRetiredWorldFiles(retired);
            }
        }
        try {
            Bukkit.getPluginManager().callEvent(new DungeonDisposeEvent(this));
        } catch (Exception failure) {
            this.logger()
                    .error(
                            "Disposal event failed for dungeon '{}'.",
                            this.dungeon.getWorldName(),
                            failure);
        } finally {
            attempt.complete(true);
        }
    }

    private void failDisposal(CompletableFuture<Boolean> attempt, Throwable failure) {
        synchronized (this) {
            if (this.disposalAttempt != attempt
                    || attempt.isDone()
                    || this.disposalState == DisposalState.DISPOSED) return;
            this.disposalState = DisposalState.RETRYABLE_FAILURE;
            this.disposalFailure = failure;
            this.logger()
                    .error(
                            "Instance disposal failed for '{}' in dungeon '{}'; retaining"
                                    + " registration and files. Call disposeAsync() to retry.",
                            this.instanceWorldName,
                            this.dungeon.getWorldName(),
                            failure);
            if (this.plugin().isEnabled() && this.automaticDisposalRetries < MAX_DISPOSAL_RETRIES) {
                try {
                    this.disposalRetryTask =
                            Bukkit.getScheduler()
                                    .runTaskLater(
                                            this.plugin(),
                                            () -> {
                                                synchronized (this) {
                                                    if (this.disposalAttempt != attempt
                                                            || this.disposalState
                                                                    != DisposalState
                                                                            .RETRYABLE_FAILURE)
                                                        return;
                                                    this.disposalRetryTask = null;
                                                }
                                                this.requestDisposal(true);
                                            },
                                            DISPOSAL_RETRY_TICKS);
                    this.automaticDisposalRetries++;
                } catch (RuntimeException schedulingFailure) {
                    failure.addSuppressed(schedulingFailure);
                }
            }
            attempt.complete(false);
        }
    }

    private void cancelDisposalTask() {
        if (this.disposalTask != null) {
            this.disposalTask.cancel();
            this.disposalTask = null;
        }
    }

    public DisposalState getDisposalState() {
        return this.disposalState;
    }

    @Nullable
    public Throwable getDisposalFailure() {
        return this.disposalFailure;
    }

    public String getInstanceWorldName() {
        return this.instanceWorld == null ? this.instanceWorldName : this.instanceWorld.getName();
    }

    /** Persistence must succeed before runtime state or world files are released. */
    protected java.util.concurrent.CompletableFuture<Boolean> prepareDisposal() {
        return java.util.concurrent.CompletableFuture.completedFuture(true);
    }

    protected void onDisposalComplete() {}

    public abstract void onDispose();

    protected int resolveCleanupDelayTicks() {
        return Math.max(0, this.dungeon.getConfig().getInt("runs.cleanup_delay_ticks", 0));
    }

    /** Detaches files on the server thread before handing deletion to a worker. */
    private List<Path> detachWorldFiles(File worldFolder, String worldName) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("World folders must be detached on the server thread");
        if (Bukkit.getWorld(worldName) != null) {
            this.logger()
                    .warn("Retaining files for '{}' because that world name is loaded.", worldName);
            return List.of();
        }
        List<Path> retired = new ArrayList<>();
        for (Path source :
                java.util.stream.Stream.of(
                                worldFolder,
                                new File(Bukkit.getWorldContainer(), worldName),
                                WorldUtils.dimensionFolder(worldName))
                        .map(file -> file.toPath().toAbsolutePath().normalize())
                        .distinct()
                        .toList()) {
            if (Files.notExists(source)) continue;
            Path detached =
                    source.resolveSibling(source.getFileName() + ".disposed-" + UUID.randomUUID());
            try {
                Files.move(source, detached, StandardCopyOption.ATOMIC_MOVE);
                retired.add(detached);
            } catch (IOException | RuntimeException failure) {
                this.logger()
                        .warn(
                                "Cannot detach unloaded world folder '{}'; retaining it for restart"
                                        + " cleanup.",
                                source,
                                failure);
            }
        }
        this.pendingFolderOwned = false;
        return retired;
    }

    private void deleteRetiredWorldFiles(List<Path> retired) {
        for (Path path : retired) {
            try {
                FileUtils.deleteDirectory(path.toFile());
            } catch (IOException | RuntimeException failure) {
                this.logger()
                        .warn(
                                "Cannot delete retired world folder '{}'; retaining it for restart"
                                        + " cleanup.",
                                path,
                                failure);
            }
        }
    }

    protected void cleanWorldFiles(CountDownLatch latch, File worldFolder, String worldName) {
        this.deleteRetiredWorldFiles(this.detachWorldFiles(worldFolder, worldName));
    }

    public void addPlayer(DungeonPlayerSession playerSession) {
        if (this.disposing || this.loadFailed || !this.getRuntime().database().ready()) return;
        try {
            this.getRuntime().stateStore().requirePlayerReady(playerSession.getPlayer().getUniqueId());
        } catch (IllegalStateException | IllegalArgumentException notReady) {
            playerSession.getPlayer().sendMessage(net.kyori.adventure.text.Component.text(
                    "[Dungeons] Player data is unavailable; try again shortly."));
            return;
        }
        if (playerSession.getInstance() != null && playerSession.getInstance() != this) {
            return;
        }
        if (!this.players.contains(playerSession)) {
            Player player = playerSession.getPlayer();
            if (!playerSession.prepareRecovery()) return;
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
                                if (playerSession.getInstance() != this
                                        || !this.players.contains(playerSession)) {
                                    return;
                                }
                                if (!this.config.getBoolean(
                                        "players.keep_on_entry.inventory", true)) {
                                    player.getInventory().clear();
                                }

                                if (!this.config.getBoolean(
                                        "players.keep_on_entry.health", false)) {
                                    player.setHealth(EntityUtils.getMaxHealth(player));
                                }

                                if (!this.config.getBoolean("players.keep_on_entry.food", false)) {
                                    player.setFoodLevel(20);
                                }

                                if (!DungeonConfigView.shouldKeepPotionEffectsOnEntry(
                                        this.config)) {
                                    for (PotionEffect effect : player.getActivePotionEffects()) {
                                        player.removePotionEffect(effect.getType());
                                    }
                                }

                                if (!this.config.getBoolean(
                                        "players.keep_on_entry.experience", true)) {
                                    player.setExp(0.0F);
                                    player.setLevel(0);
                                }
                            },
                            1L);
        }
    }

    /** Applies subclass state changes after a departure has been accepted. */
    protected void onPlayerRemovalAccepted(DungeonPlayerSession playerSession) {}

    private final java.util.Map<UUID, DepartureCause> departureCauses = new java.util.HashMap<>();

    public final void removePlayer(DungeonPlayerSession session, DepartureCause cause) {
        UUID playerId = session.getPlayer().getUniqueId();
        departureCauses.put(playerId, cause);
        try {
            this.removePlayer(session, true);
        } finally {
            departureCauses.remove(playerId);
        }
    }

    protected boolean recovering(DungeonPlayerSession session) {
        return departureCauses
                .getOrDefault(session.getPlayer().getUniqueId(), DepartureCause.NORMAL)
                .recovery();
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
                if (this.recovering(playerSession)) {
                    playerSession.recoverPendingState();
                } else
                    playerSession.completeDeparture(
                            this.isEditInstance()
                                    || !this.config.getBoolean(
                                            "players.keep_on_entry.inventory", true),
                            this.isEditInstance()
                                    || !this.config.getBoolean(
                                            "players.keep_on_entry.experience", true));
            }
        } else {
            PlayerLeaveDungeonEvent leaveEvent = new PlayerLeaveDungeonEvent(this, playerSession);
            Bukkit.getPluginManager().callEvent(leaveEvent);
            if (force || !leaveEvent.isCancelled()) {
                if (!this.recovering(playerSession)) this.onPlayerRemovalAccepted(playerSession);
                if (!this.recovering(playerSession)
                        && this.dungeon.isCooldownOnLeave()
                        && this.dungeon.shouldApplyAccessCooldown(this, player.getUniqueId())) {
                    this.dungeon.addAccessCooldown(player);
                }

                this.players.remove(playerSession);
                if (this.players.isEmpty()) {
                    Bukkit.getPluginManager().callEvent(new DungeonEndEvent(this));
                }
                player.setGameMode(playerSession.getSavedGameMode());
                playerSession.setInstance(null);
                playerSession.setDungeonRespawn(null);

                if (this.isPlayInstance() && !this.recovering(playerSession)) {
                    this.transferProgressionItems(player);
                }

                player.updateInventory();

                if (this.recovering(playerSession)) {
                    playerSession.recoverPendingState();
                } else
                    playerSession.completeDeparture(
                            this.isEditInstance()
                                    || !this.config.getBoolean(
                                            "players.keep_on_entry.inventory", true),
                            this.isEditInstance()
                                    || !this.config.getBoolean(
                                            "players.keep_on_entry.experience", true));

                if (this.players.isEmpty()) {
                    this.scheduleEmptyCleanup();
                }
            }
        }
    }

    /** Hands a departing participant's progression items to another player in the run. */
    private void transferProgressionItems(Player player) {
        PlayerInventory inv = player.getInventory();
        Player target = null;
        boolean foundKeys = false;
        boolean foundDungeonItems = false;

        boolean transferDungeonItems =
                this.dungeon.getConfig().getBoolean("players.transfer_dungeon_items", true);
        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            boolean keyItem = ItemUtils.verifyKeyItem(item);
            boolean dungeonItem = ItemUtils.verifyDungeonItem(item);
            if (!keyItem && !dungeonItem) {
                continue;
            }
            // Slot removal preserves identical keys in other inventory or offhand slots.
            inv.setItem(slot, null);
            if (!this.players.isEmpty() && (keyItem || dungeonItem && transferDungeonItems)) {
                foundKeys |= keyItem;
                foundDungeonItems |= dungeonItem && transferDungeonItems;
                target = this.players.getFirst().getPlayer();
                ItemUtils.giveOrDrop(target, item);
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

    @Nullable
    public PlayableInstance asPlayInstance() {
        return this.isPlayInstance() ? (PlayableInstance) this : null;
    }

    @Nullable
    public EditableInstance asEditInstance() {
        return this.isEditInstance() ? (EditableInstance) this : null;
    }

    @Nullable
    public <T extends DungeonInstance> T as(Class<T> clazz) {
        return clazz.isInstance(this) ? clazz.cast(this) : null;
    }

    public void scheduleEmptyCleanup() {
        if (!this.players.isEmpty()) {
            return;
        }

        if (this.plugin().isEnabled()) {
            try {
                Bukkit.getScheduler().runTaskLater(this.plugin(), this::dispose, 1L);
            } catch (RuntimeException failure) {
                this.logger()
                        .warn(
                                "Empty-instance cleanup scheduling failed; attempting disposal"
                                        + " now.",
                                failure);
                this.dispose();
            }
        } else {
            this.dispose();
        }
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

    @Nullable
    public List<DungeonTrigger> getTriggerListeners(Class<? extends DungeonTrigger> type) {
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

    @Nullable
    public List<DungeonFunction> getFunctionListeners(Class<? extends DungeonFunction> type) {
        return this.functionListeners.get(type);
    }

    public FileConfiguration getConfig() {
        return this.config;
    }

    public DungeonDefinition getDungeon() {
        return this.dungeon;
    }

    @Nullable
    public UUID getStartedTeamLeaderId() {
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
        if (!this.pendingFolderOwned
                || this.instanceWorld != null
                || this.instanceWorldName == null
                || this.instanceWorldName.isBlank()) {
            return;
        }

        if (Bukkit.getWorld(this.instanceWorldName) != null) {
            return;
        }

        boolean cleaned = true;
        for (File worldFolder :
                List.of(
                        new File(Bukkit.getWorldContainer(), this.instanceWorldName),
                        WorldUtils.dimensionFolder(this.instanceWorldName))) {
            if (!worldFolder.isDirectory()) continue;
            try {
                FileUtils.deleteDirectory(worldFolder);
            } catch (IOException exception) {
                cleaned = false;
                this.logger()
                        .warn(
                                "Failed to delete pending world folder '{}'.",
                                worldFolder.getAbsolutePath(),
                                exception);
            }
        }
        if (cleaned) this.pendingFolderOwned = false;
    }
}
