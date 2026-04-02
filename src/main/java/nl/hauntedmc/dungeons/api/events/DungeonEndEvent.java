package nl.hauntedmc.dungeons.api.events;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.entity.Player;

public class DungeonEndEvent extends DungeonEvent {
   private final List<DungeonPlayer> gamePlayers;
   private final List<Player> players;
   private final IDungeonParty party;

   public DungeonEndEvent(AbstractInstance instance) {
      super(instance);
      this.gamePlayers = instance.getPlayers();
      this.players = new ArrayList<>();

      for (DungeonPlayer mPlayer : this.gamePlayers) {
         this.players.add(mPlayer.getPlayer());
      }

      this.party = this.gamePlayers.getFirst().getiDungeonParty();
   }

   public List<DungeonPlayer> getGamePlayers() {
      return this.gamePlayers;
   }

   public List<Player> getPlayers() {
      return this.players;
   }

   public IDungeonParty getParty() {
      return this.party;
   }
}
