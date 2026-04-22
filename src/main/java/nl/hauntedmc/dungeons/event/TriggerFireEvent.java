package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a dungeon trigger is about to execute its function.
 */
public class TriggerFireEvent extends DungeonEvent implements Cancellable {
    private DungeonPlayerSession playerSession;
    private final DungeonTrigger trigger;
    private boolean cancelled;

    /**
     * Creates a trigger-fire event without a specific player source.
     *
     * @param instance playable instance that owns the trigger
     * @param trigger trigger being fired
     */
    public TriggerFireEvent(PlayableInstance instance, DungeonTrigger trigger) {
        super(instance);
        this.trigger = trigger;
        this.instance = instance;
    }

    /**
     * Creates a trigger-fire event sourced from a specific player session.
     *
     * @param playerSession player session that triggered the event
     * @param trigger trigger being fired
     */
    public TriggerFireEvent(DungeonPlayerSession playerSession, DungeonTrigger trigger) {
        super(playerSession.getInstance());
        this.playerSession = playerSession;
        this.trigger = trigger;
        this.cancelled = false;
    }

    /**
     * Returns the source player, if this trigger fire was player-initiated.
     *
     * @return triggering player or {@code null}
     */
    @Nullable public Player getPlayer() {
        return this.playerSession == null ? null : this.playerSession.getPlayer();
    }

    /**
     * Returns whether this trigger execution has been cancelled.
     */
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Sets whether this trigger execution should be cancelled.
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * Returns the source player session, if one exists.
     *
     * @return triggering player session or {@code null}
     */
    public DungeonPlayerSession getPlayerSession() {
        return this.playerSession;
    }

    /**
     * Returns the trigger that is firing.
     *
     * @return firing trigger
     */
    public DungeonTrigger getTrigger() {
        return this.trigger;
    }
}
