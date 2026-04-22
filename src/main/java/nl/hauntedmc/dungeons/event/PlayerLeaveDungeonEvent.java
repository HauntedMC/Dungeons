package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;

/**
 * Event fired before a player leaves a dungeon instance.
 */
public class PlayerLeaveDungeonEvent extends DungeonEvent implements Cancellable {
    private final DungeonPlayerSession playerSession;
    private final Player player;
    final boolean editMode;
    private boolean cancelled;

    /**
     * Creates a player-leave event.
     *
     * @param instance instance the player is leaving
     * @param playerSession leaving player session
     */
    public PlayerLeaveDungeonEvent(DungeonInstance instance, DungeonPlayerSession playerSession) {
        super(instance);
        this.playerSession = playerSession;
        this.player = playerSession.getPlayer();
        this.editMode = instance instanceof EditableInstance;
    }

    /**
     * Returns the leaving player session.
     *
     * @return leaving player session
     */
    public DungeonPlayerSession getPlayerSession() {
        return this.playerSession;
    }

    /**
     * Returns the leaving player.
     *
     * @return leaving player
     */
    public Player getPlayer() {
        return this.player;
    }

    /**
     * Returns whether leaving the dungeon should be cancelled.
     */
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Sets whether leaving the dungeon should be cancelled.
     */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
