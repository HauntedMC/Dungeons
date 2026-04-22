package nl.hauntedmc.dungeons.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.entity.Player;

/**
 * Event fired when a dungeon instance begins running for a set of players.
 */
public class DungeonStartEvent extends DungeonEvent {
    private final List<DungeonPlayerSession> dungeonPlayers;

    /**
     * Creates a start event for the given instance and player set.
     *
     * @param instance started instance
     * @param dungeonPlayers player sessions starting the run
     */
    public DungeonStartEvent(DungeonInstance instance, List<DungeonPlayerSession> dungeonPlayers) {
        super(instance);
        this.dungeonPlayers = dungeonPlayers;
    }

    /**
     * Returns the Bukkit players participating in the start event.
     *
     * @return starting players
     */
    public Collection<Player> getPlayers() {
        List<Player> players = new ArrayList<>();
        this.dungeonPlayers.forEach(dungeonPlayer -> players.add(dungeonPlayer.getPlayer()));
        return players;
    }

    /**
     * Returns the player sessions participating in the start event.
     *
     * @return starting player sessions
     */
    public List<DungeonPlayerSession> getPlayerSessions() {
        return this.dungeonPlayers;
    }
}
