package nl.hauntedmc.dungeons.content.dungeon;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.model.dungeon.DungeonLoadException;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Standard dungeon implementation backed by a copied world and a flat function map.
 *
 * <p>Static dungeons store all functions in a single {@code functions.yml} file and create one
 * isolated playable instance per run.
 */
public class StaticDungeon extends DungeonDefinition {
    private final HashMap<Location, DungeonFunction> functions;
    private boolean functionsChanged = false;

    /**
     * Creates a new StaticDungeon instance.
     */
    public StaticDungeon(
            @NotNull DungeonsRuntime runtime,
            @NotNull File folder,
            @Nullable YamlConfiguration loadedConfig)
            throws DungeonLoadException {
        super(runtime, folder, loadedConfig);
        this.functions = new HashMap<>();
        this.loadFunctions();
    }

    /**
     * Creates play session.
     */
    @Override
    public PlayableInstance createPlaySession(
            CountDownLatch latch, @Nullable UUID startedTeamLeaderId) {
        PlayableInstance instance = new StaticInstance(this, latch);
        instance.setStartedTeamLeaderId(startedTeamLeaderId);
        instance.initialize();
        return instance;
    }

    /**
     * Creates edit session.
     */
    @Override
    public EditableInstance createEditSession(CountDownLatch latch) {
        EditableInstance instance = new EditableInstance(this, latch);
        instance.initialize();
        return instance;
    }

    /**
     * Performs prepare instance.
     */
    @Override
    public boolean prepareInstance(
            Player player,
            String difficultyName,
            int requestedPlayers,
            @Nullable UUID startedTeamLeaderId) {
        DungeonPlayerSession playerSession = this.playerSessions().get(player);
        LangUtils.sendMessage(
                player,
                "instance.lifecycle.loading",
                LangUtils.placeholder("dungeon", this.getDisplayName()));
        DungeonDifficulty difficulty = this.difficultyLevels.get(difficultyName);

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        this.plugin(),
                        () ->
                                TextUtils.runTimed(
                                        "Loading Dungeon " + this.getWorldName(),
                                        () -> {
                                            CountDownLatch latch = new CountDownLatch(1);
                                            AtomicReference<PlayableInstance> instanceRef = new AtomicReference<>();

                                            Bukkit.getScheduler()
                                                    .runTask(
                                                            this.plugin(),
                                                            () -> {
                                                                // Instance creation has to happen on the
                                                                // server thread because world operations and
                                                                // Bukkit object wiring are not thread-safe.
                                                                PlayableInstance created =
                                                                        this.createPlaySession(latch, startedTeamLeaderId);
                                                                instanceRef.set(created);

                                                                if (created == null) {
                                                                    latch.countDown();
                                                                    return;
                                                                }

                                                                created.setDifficulty(difficulty);
                                                                this.instances.add(created);
                                                                this.activeInstances().registerActiveInstance(created);
                                                            });

                                            try {
                                                boolean loaded = latch.await(5L, TimeUnit.SECONDS);
                                                PlayableInstance instance = instanceRef.get();

                                                if (!loaded || instance == null) {
                                                    this.schedulePrepareFailure(
                                                            player, playerSession, instance, "instance.lifecycle.load-timeout");
                                                    return;
                                                }

                                                if (instance.hasLoadFailed() || !instance.isReady()) {
                                                    this.schedulePrepareFailure(
                                                            player, playerSession, instance, "instance.lifecycle.load-failed");
                                                    return;
                                                }

                                                Bukkit.getScheduler()
                                                        .runTask(
                                                                this.plugin(),
                                                                () -> {
                                                                    // The player may disconnect while the
                                                                    // world is still being prepared, so the
                                                                    // reservation has to be revalidated here.
                                                                    if (!player.isOnline()) {
                                                                        playerSession.refundReservedAccessKey(this.getWorldName());
                                                                        playerSession.setAwaitingDungeon(false);
                                                                        this.discardInstance(instance);
                                                                        this.logger()
                                                                                .warn(
                                                                                        "Aborted loading static instance '{}' because player '{}' disconnected before join.",
                                                                                        this.getWorldName(),
                                                                                        player.getName());
                                                                        return;
                                                                    }

                                                                    instance.addPlayer(playerSession);
                                                                    if (playerSession.getInstance() == instance) {
                                                                        playerSession.commitReservedAccessKey(this.getWorldName());
                                                                        nl.hauntedmc.dungeons.util.lang.LangUtils.sendMessage(
                                                                                player,
                                                                                "instance.lifecycle.entered",
                                                                                LangUtils.placeholder("dungeon", this.getDisplayName()));
                                                                    } else {
                                                                        playerSession.refundReservedAccessKey(this.getWorldName());
                                                                        this.discardInstance(instance);
                                                                        nl.hauntedmc.dungeons.util.lang.LangUtils.sendMessage(
                                                                                player,
                                                                                "instance.lifecycle.load-failed",
                                                                                LangUtils.placeholder("dungeon", this.getDisplayName()));
                                                                    }
                                                                    playerSession.setAwaitingDungeon(false);
                                                                });
                                            } catch (InterruptedException exception) {
                                                Thread.currentThread().interrupt();
                                                PlayableInstance instance = instanceRef.get();

                                                if (instance != null) {
                                                    this.schedulePrepareFailure(
                                                            player, playerSession, instance, "instance.lifecycle.load-failed");
                                                    this.logger()
                                                            .error(
                                                                    "Interrupted while waiting for static instance '{}' to initialize.",
                                                                    this.getWorldName(),
                                                                    exception);
                                                    return;
                                                }

                                                this.schedulePrepareFailure(
                                                        player, playerSession, null, "instance.lifecycle.load-failed");
                                                this.logger()
                                                        .error(
                                                                "Interrupted while waiting for static instance '{}' to initialize.",
                                                                this.getWorldName(),
                                                                exception);
                                            }
                                        }));

        return true;
    }

    /**
     * Performs schedule prepare failure.
     */
    private void schedulePrepareFailure(
            Player player,
            @Nullable DungeonPlayerSession playerSession,
            @Nullable PlayableInstance instance,
            String messageKey) {
        Bukkit.getScheduler()
                .runTask(
                        this.plugin(),
                        () -> {
                            if (instance != null) {
                                this.discardInstance(instance);
                            }

                            if (playerSession != null) {
                                playerSession.refundReservedAccessKey(this.getWorldName());
                                playerSession.setAwaitingDungeon(false);
                            }

                            LangUtils.sendMessage(
                                    player, messageKey, LangUtils.placeholder("dungeon", this.getDisplayName()));
                        });
    }

    /**
     * Adds function.
     */
    @Override
    public void addFunction(Location loc, DungeonFunction function) {
        loc.setWorld(null);
        loc = loc.clone();
        function.setLocation(loc);
        this.functions.put(loc, function);
    }

    /**
     * Removes function.
     */
    @Override
    public void removeFunction(Location loc) {
        loc.setWorld(null);
        this.functions.remove(loc);
    }

    /**
     * Returns the functions.
     */
    @Override
    public Map<Location, DungeonFunction> getFunctions() {
        return this.functions;
    }

    /**
     * Saves functions.
     */
    @Override
    public void saveFunctions() {
        YamlConfiguration functionsYaml = new YamlConfiguration();
        File saveFile = new File(this.folder, "functions.yml");
        List<DungeonFunction> functions = new ArrayList<>(this.functions.values());
        functionsYaml.set("Functions", functions);
        if (this.plugin().isEnabled()) {
            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            this.plugin(),
                            () -> {
                                try {
                                    functionsYaml.save(saveFile);
                                } catch (IOException exception) {
                                    this.logger()
                                            .error(
                                                    "Failed to asynchronously save functions for dungeon '{}' to '{}'.",
                                                    this.getWorldName(),
                                                    saveFile.getAbsolutePath(),
                                                    exception);
                                }
                            });
        } else {
            try {
                functionsYaml.save(saveFile);
            } catch (IOException exception) {
                this.logger()
                        .error(
                                "Failed to save functions for dungeon '{}' to '{}'.",
                                this.getWorldName(),
                                saveFile.getAbsolutePath(),
                                exception);
            }
        }
    }

    /**
     * Loads functions.
     */
    public void loadFunctions() throws DungeonLoadException {
        YamlConfiguration functionsYaml = new YamlConfiguration();
        File loadFile = new File(this.folder, "functions.yml");
        if (!loadFile.exists()) {
            return;
        }

        try {
            functionsYaml.load(loadFile);
            List<DungeonFunction> functions = (List<DungeonFunction>) functionsYaml.getList("Functions");
            if (functions == null || functions.isEmpty()) {
                return;
            }

            int index = 0;
            for (DungeonFunction function : functions) {
                this.loadFunctionSafely(function, index++);
            }
        } catch (IOException exception) {
                        throw new DungeonLoadException(
                    "Access of functions.yml file failed!",
                    false,
                    "There may be another process accessing the file, or we may not have permission.");
        } catch (InvalidConfigurationException | YAMLException exception) {
                        throw getDungeonLoadException(exception);
        }
    }

    /**
     * Loads function safely.
     */
    private void loadFunctionSafely(DungeonFunction function, int index) {
        if (function == null) {
            this.logger()
                    .warn(
                            "Skipping null function entry at index {} in static dungeon '{}'.",
                            index,
                            this.getWorldName());
            return;
        }

        try {
            function.initialize();
            Location loc = function.getLocation();
            if (loc == null) {
                this.logger()
                        .warn(
                                "Skipping function '{}' in static dungeon '{}' because it has no location.",
                                function.getClass().getSimpleName(),
                                this.getWorldName());
                return;
            }

            this.addFunction(loc, function);
        } catch (Throwable throwable) {
            // Content loading is intentionally defensive here so one broken serialized function does
            // not prevent the rest of the dungeon from staying usable.
            Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
            Location loc = function.getLocation();
            this.logger()
                    .warn(
                            "Skipping broken function '{}' in static dungeon '{}' at {}. Reason: {}",
                            function.getClass().getSimpleName(),
                            this.getWorldName(),
                            loc == null
                                    ? "<no-location>"
                                    : (loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()),
                            root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(),
                            root);
        }
    }

    /**
     * Returns the dungeon load exception.
     */
    private static @NotNull DungeonLoadException getDungeonLoadException(Exception exception) {
        DungeonLoadException ex =
                                new DungeonLoadException(
                        "Functions list has invalid YAML! See error below...",
                        false,
                        "Contains an unsupported element! (Function, trigger, or condition!)",
                        "You may need to change or delete this function!");
        if (exception.getCause() != null) {
            ex.addMessage("Error: " + exception.getCause().getMessage());
            if (!exception.getCause().getMessage().contains("LootTableRewardsFunction")) {
                ex.addMessage(
                        "This usually happens if the element belonged to another plugin that is no longer present!");
            }
        } else {
            ex.addMessage("&c├─ Error: " + exception.getMessage());
            if (!exception.getMessage().contains("LootTableRewardsFunction")) {
                ex.addMessage(
                        "&c└─ This usually happens if the element belonged to another plugin that is no longer present!");
            }
        }
        return ex;
    }

    /**
     * Returns whether functions changed.
     */
    public boolean isFunctionsChanged() {
        return this.functionsChanged;
    }

    /**
     * Sets the functions changed.
     */
    public void setFunctionsChanged(boolean functionsChanged) {
        this.functionsChanged = functionsChanged;
    }
}
