package nl.hauntedmc.dungeons.event;

import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.entity.Player;

/**
 * Event fired when one player finishes a playable dungeon.
 */
public class PlayerFinishDungeonEvent extends DungeonEvent {
    private final DungeonPlayerSession playerSession;
    private final Player player;

    /**
     * Creates a player-finish event.
     *
     * @param instance instance being finished
     * @param playerSession finishing player session
     */
    public PlayerFinishDungeonEvent(PlayableInstance instance, DungeonPlayerSession playerSession) {
        super(instance);
        this.playerSession = playerSession;
        this.player = playerSession.getPlayer();
    }

    /**
     * Returns the finishing player session.
     *
     * @return finishing player session
     */
    public DungeonPlayerSession getPlayerSession() {
        return this.playerSession;
    }

    /**
     * Returns the finishing player.
     *
     * @return finishing player
     */
    public Player getPlayer() {
        return this.player;
    }
}
