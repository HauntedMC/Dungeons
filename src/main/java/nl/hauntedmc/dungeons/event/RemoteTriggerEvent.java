package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Event fired when a function emits a named remote trigger signal.
 *
 * <p>The event carries both the signal metadata and the originating player context, if one exists.
 */
public class RemoteTriggerEvent extends DungeonEvent implements Cancellable {
    private final String triggerName;
    private final DungeonTrigger trigger;
    private double range;
    @Nullable private final Location origin;
    @Nullable private final DungeonPlayerSession playerSession;
    private boolean cancelled;

    /**
     * Creates a remote-trigger event.
     *
     * @param triggerName emitted trigger name
     * @param trigger virtual trigger source used for matching
     * @param instance instance receiving the signal
     * @param range optional spatial range restriction
     * @param origin optional world position where the signal originated
     * @param triggerPlayer optional player context that emitted the signal
     */
    public RemoteTriggerEvent(
            @NotNull String triggerName,
            DungeonTrigger trigger,
            @NotNull PlayableInstance instance,
            double range,
            @Nullable Location origin,
            @Nullable DungeonPlayerSession triggerPlayer) {
        super(instance);
        this.triggerName = triggerName;
        this.trigger = trigger;
        this.range = range;
        this.origin = origin;
        this.playerSession = triggerPlayer;
    }

    /**
     * Returns the triggering player, if the signal originated from player context.
     *
     * @return triggering player or {@code null}
     */
    @Nullable public Player getPlayer() {
        return this.playerSession == null ? null : this.playerSession.getPlayer();
    }

    /**
     * Returns whether this remote trigger propagation has been cancelled.
     */
    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    /**
     * Sets whether this remote trigger propagation should be cancelled.
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * Returns the emitted trigger name.
     *
     * @return trigger name
     */
    public String getTriggerName() {
        return this.triggerName;
    }

    /**
     * Returns the virtual trigger that emitted the event.
     *
     * @return trigger source
     */
    public DungeonTrigger getTrigger() {
        return this.trigger;
    }

    /**
     * Returns the current range restriction for the signal.
     *
     * @return signal range in blocks
     */
    public double getRange() {
        return this.range;
    }

    /**
     * Updates the signal range restriction.
     *
     * @param range new signal range in blocks
     */
    public void setRange(double range) {
        this.range = range;
    }

    /**
     * Returns the optional origin position of the signal.
     *
     * @return signal origin or {@code null}
     */
    @Nullable public Location getOrigin() {
        return this.origin;
    }

    /**
     * Returns the player session that emitted the signal, if present.
     *
     * @return triggering player session or {@code null}
     */
    @Nullable public DungeonPlayerSession getPlayerSession() {
        return this.playerSession;
    }
}
