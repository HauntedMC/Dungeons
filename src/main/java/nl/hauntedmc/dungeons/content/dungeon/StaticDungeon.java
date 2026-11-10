package nl.hauntedmc.dungeons.content.dungeon;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonLoadException;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

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
        try {
            instance.initialize();
            return instance;
        } catch (RuntimeException | Error failure) {
            // Initialization can throw after reserving files or scheduling world creation. Keep
            // the partially created run managed until its cleanup has actually succeeded.
            if (!this.instances.contains(instance)) this.instances.add(instance);
            this.activeInstances().registerActiveInstance(instance);
            this.discardInstance(instance);
            throw failure;
        }
    }

    /**
     * Creates edit session.
     */
    @Override
    public EditableInstance createEditSession(CountDownLatch latch) {
        EditableInstance instance = new EditableInstance(this, latch);
        try {
            instance.initialize();
            return instance;
        } catch (RuntimeException | Error failure) {
            this.setEditSession(instance);
            if (!this.instances.contains(instance)) this.instances.add(instance);
            this.activeInstances().registerActiveInstance(instance);
            this.discardInstance(instance);
            throw failure;
        }
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
        return this.prepareIsolatedInstance(
                player, difficultyName, startedTeamLeaderId, instance -> {}, 0L);
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
        ConfigurationFile.onOwnerThread(
                this.plugin(),
                () -> {
                    YamlConfiguration functionsYaml = new YamlConfiguration();
                    functionsYaml.set("Functions", new ArrayList<>(this.functions.values()));
                    return ConfigurationFile.saveAsync(
                            this.plugin(), functionsYaml, new File(this.folder, "functions.yml"));
                });
    }

    /**
     * Loads functions.
     */
    public void loadFunctions() throws DungeonLoadException {
        YamlConfiguration functionsYaml = new YamlConfiguration();
        File loadFile = new File(this.folder, "functions.yml");
        try {
            ConfigurationFile.loadSnapshot(functionsYaml, loadFile);
            List<?> functions = functionsYaml.getList("Functions");
            if (functions == null || functions.isEmpty()) {
                return;
            }

            int index = 0;
            for (Object function : functions) {
                this.loadFunctionSafely(function, index++);
            }
        } catch (NoSuchFileException absent) {
            return;
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
    private void loadFunctionSafely(Object entry, int index) {
        if (!(entry instanceof DungeonFunction function)) {
            this.logger()
                    .warn(
                            "Skipping invalid or null function entry at index {} in static dungeon '{}'.",
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
            // Content loading is intentionally defensive here so one broken serialized function
            // does
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
