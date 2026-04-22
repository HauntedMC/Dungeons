package nl.hauntedmc.dungeons.content.instance.play;

import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.function.LeaveDungeonFunction;
import nl.hauntedmc.dungeons.content.function.StartDungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/**
 * Playable instance for static dungeons.
 *
 * <p>Static instances either enter a lobby flow, where start/leave functions are cloned into the
 * live world, or start immediately when no lobby is configured.
 */
public class StaticInstance extends PlayableInstance {
    protected final StaticDungeon dungeon;

    /**
     * Creates a new StaticInstance instance.
     */
    public StaticInstance(StaticDungeon dungeon, CountDownLatch latch) {
        super(dungeon, latch);
        this.dungeon = dungeon;
    }

    /**
     * Performs on load game.
     */
    @Override
    public void onLoadGame() {
        if (this.dungeon.isLobbyEnabled()) {
            DungeonFunction startFunction = null;

            for (DungeonFunction function : this.dungeon.getFunctions().values()) {
                if (function instanceof StartDungeonFunction) {
                    startFunction = function;
                    DungeonFunction newFunction = function.clone();
                    Location loc = newFunction.getLocation();
                    loc.setWorld(this.instanceWorld);
                    newFunction.enable(this, loc);
                    this.functions.put(loc, newFunction);
                }

                if (function instanceof LeaveDungeonFunction) {
                    startFunction = function;
                    DungeonFunction newFunction = function.clone();
                    Location loc = newFunction.getLocation();
                    loc.setWorld(this.instanceWorld);
                    newFunction.enable(this, loc);
                    this.functions.put(loc, newFunction);
                }
            }

            if (startFunction == null) {
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .error(
                                "Dungeon '{}' is improperly configured: missing start/leave function for lobby flow.",
                                this.dungeon.getWorldName());
            }
        } else {
            Bukkit.getScheduler().runTaskLater(RuntimeContext.plugin(), this::startGame, 1L);
        }
    }

    /**
     * Returns the dungeon.
     */
    public StaticDungeon getDungeon() {
        return this.dungeon;
    }
}
