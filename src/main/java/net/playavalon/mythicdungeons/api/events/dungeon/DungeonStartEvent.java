package net.playavalon.mythicdungeons.api.events.dungeon;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;

public class DungeonStartEvent extends DungeonEvent {
   private List<MythicPlayer> mythicPlayers;

   public DungeonStartEvent(AbstractInstance instance, List<MythicPlayer> mythicPlayers) {
      super(instance);
      this.mythicPlayers = mythicPlayers;
   }

   public Collection<Player> getPlayers() {
      List<Player> players = new ArrayList<>();
      this.mythicPlayers.forEach(mythicPlayer -> players.add(mythicPlayer.getPlayer()));
      return players;
   }

   public List<MythicPlayer> getMythicPlayers() {
      return this.mythicPlayers;
   }
}
