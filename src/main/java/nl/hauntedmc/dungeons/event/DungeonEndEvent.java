package nl.hauntedmc.dungeons.event;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.entity.Player;

/**
 * Event fired when an instance has ended and its player list is finalized.
 */
public class DungeonEndEvent extends DungeonEvent {
    private final List<DungeonPlayerSession> gamePlayers;
    private final List<Player> players;

    /**
     * Creates an end event for the given instance.
     *
     * @param instance instance that just ended
     */
    public DungeonEndEvent(DungeonInstance instance) {
        super(instance);
        this.gamePlayers = instance.getPlayers();
        this.players = new ArrayList<>();

        for (DungeonPlayerSession playerSession : this.gamePlayers) {
            this.players.add(playerSession.getPlayer());
        }
    }

    /**
     * Returns the dungeon player sessions that were present when the run ended.
     *
     * @return ended player sessions
     */
    public List<DungeonPlayerSession> getPlayerSessions() {
        return this.gamePlayers;
    }

    /**
     * Returns the Bukkit players extracted from the ended player sessions.
     *
     * @return ended players
     */
    public List<Player> getPlayers() {
        return this.players;
    }
}
