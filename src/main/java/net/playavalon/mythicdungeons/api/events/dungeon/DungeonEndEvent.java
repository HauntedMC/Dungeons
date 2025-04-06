package net.playavalon.mythicdungeons.api.events.dungeon;

import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;

public class DungeonEndEvent extends DungeonEvent {
   private final List<MythicPlayer> gamePlayers;
   private final List<Player> players;
   private final IDungeonParty party;

   public DungeonEndEvent(AbstractInstance instance) {
      super(instance);
      this.gamePlayers = instance.getPlayers();
      this.players = new ArrayList<>();

      for (MythicPlayer mPlayer : this.gamePlayers) {
         this.players.add(mPlayer.getPlayer());
      }

      this.party = this.gamePlayers.get(0).getDungeonParty();
   }

   public boolean isEditInstance() {
      return this.instance.isEditInstance();
   }

   public boolean isPlayInstance() {
      return this.instance.isPlayInstance();
   }

   public List<MythicPlayer> getGamePlayers() {
      return this.gamePlayers;
   }

   public List<Player> getPlayers() {
      return this.players;
   }

   public IDungeonParty getParty() {
      return this.party;
   }
}
