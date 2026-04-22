package nl.hauntedmc.dungeons.runtime.instance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-safe registry of currently active dungeon instances.
 */
public final class ActiveInstanceRegistry {
    private final DungeonsPlugin plugin;
    private final CopyOnWriteArrayList<DungeonInstance> activeInstances =
            new CopyOnWriteArrayList<>();

    /** Creates a registry using plugin configuration for capacity checks. */
    public ActiveInstanceRegistry(DungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers an instance as active. */
    public void registerActiveInstance(@NotNull DungeonInstance instance) {
        this.activeInstances.addIfAbsent(instance);
    }

    /** Unregisters an instance when it is disposed. */
    public void unregisterActiveInstance(@Nullable DungeonInstance instance) {
        if (instance != null) {
            this.activeInstances.remove(instance);
        }
    }

    /** Returns a snapshot of active instances. */
    public List<DungeonInstance> getActiveInstances() {
        return new ArrayList<>(this.activeInstances);
    }

    /** Returns the number of active instances currently tracked. */
    public int getRegisteredInstanceCount() {
        return this.activeInstances.size();
    }

    /** Returns the configured global active-instance limit. */
    public int getMaxGlobalInstances() {
        return PluginConfigView.getMaxActiveInstances(this.plugin.getConfig());
    }

    /** Finds an active instance by its live world name. */
    @Nullable
    public DungeonInstance getDungeonInstance(String worldName) {
        for (DungeonInstance instance : this.activeInstances) {
            if (instance.getInstanceWorld() != null
                    && instance.getInstanceWorld().getName().equals(worldName)) {
                return instance;
            }
        }

        return null;
    }

    /** Returns whether the dungeon can start now for one player. */
    public boolean canStartDungeonNow(@Nullable DungeonDefinition dungeon) {
        return this.canStartDungeonNow(dungeon, 1, null);
    }

    /** Returns whether the dungeon can start now for the requested player count. */
    public boolean canStartDungeonNow(@Nullable DungeonDefinition dungeon, int requestedPlayers) {
        return this.canStartDungeonNow(dungeon, requestedPlayers, null);
    }

    /** Returns whether the dungeon can start now for players and optional difficulty. */
    public boolean canStartDungeonNow(
            @Nullable DungeonDefinition dungeon, int requestedPlayers, @Nullable String difficultyName) {
        if (dungeon == null) {
            return false;
        }

        return dungeon.canAcceptStartNow(
                Math.max(1, requestedPlayers),
                difficultyName,
                this.getRegisteredInstanceCount(),
                this.getMaxGlobalInstances());
    }
}
