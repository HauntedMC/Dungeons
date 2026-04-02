package nl.hauntedmc.dungeons.api.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.entity.Player;

public class DungeonStartEvent extends DungeonEvent {
   private final List<DungeonPlayer> dungeonPlayers;

   public DungeonStartEvent(AbstractInstance instance, List<DungeonPlayer> dungeonPlayers) {
      super(instance);
      this.dungeonPlayers = dungeonPlayers;
   }

   public Collection<Player> getPlayers() {
      List<Player> players = new ArrayList<>();
      this.dungeonPlayers.forEach(dungeonPlayer -> players.add(dungeonPlayer.getPlayer()));
      return players;
   }

   public List<DungeonPlayer> getDungeonPlayers() {
      return this.dungeonPlayers;
   }
}
