package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerHotbarSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Event fired before a player's hotbar snapshot is swapped to a new hotbar state.
 */
public class HotbarSetEvent extends Event implements Cancellable {
    private final PlayerHotbarSnapshot newHotbar;
    private final DungeonPlayerSession playerSession;
    private boolean cancelled;
    private static final HandlerList HANDLERS_LIST = new HandlerList();

    /**
     * Creates a hotbar-set event.
     *
     * @param newHotbar snapshot that will be applied
     * @param playerSession owning player session
     */
    public HotbarSetEvent(PlayerHotbarSnapshot newHotbar, DungeonPlayerSession playerSession) {
        this.newHotbar = newHotbar;
        this.playerSession = playerSession;
    }

    /**
     * Returns the affected player.
     *
     * @return target player
     */
    public Player getPlayer() {
        return this.playerSession.getPlayer();
    }

    /**
     * Returns whether this hotbar change has been cancelled.
     */
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Sets whether this hotbar change should be cancelled.
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * Returns the shared Bukkit handler list.
     *
     * @return handler list
     */
    public static HandlerList getHandlerList() {
        return HANDLERS_LIST;
    }

    /**
     * Returns the shared Bukkit handler list.
     *
     * @return handler list
     */
    @NotNull public HandlerList getHandlers() {
        return HANDLERS_LIST;
    }

    /**
     * Returns the snapshot that will replace the player's current hotbar.
     *
     * @return incoming hotbar snapshot
     */
    public PlayerHotbarSnapshot getNewHotbar() {
        return this.newHotbar;
    }

    /**
     * Returns the player session whose hotbar is changing.
     *
     * @return owning player session
     */
    public DungeonPlayerSession getPlayerSession() {
        return this.playerSession;
    }
}
