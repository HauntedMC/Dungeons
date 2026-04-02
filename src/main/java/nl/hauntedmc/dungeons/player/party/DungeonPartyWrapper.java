package nl.hauntedmc.dungeons.player.party;

import java.util.ArrayList;
import java.util.List;

import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DungeonPartyWrapper implements IDungeonParty {
   private static String partyPlugin;

   public DungeonPartyWrapper(DungeonPlayer host) {
       host.setiDungeonParty(this);
   }

   @Override
   public List<Player> getPlayers() {
       return new ArrayList<>();
   }

   @Override
   public void addPlayer(Player player) {
      DungeonPlayer dungeonPlayer = Dungeons.inst().getDungeonPlayer(player);
      if (dungeonPlayer.hasParty()) {
         assert dungeonPlayer.getiDungeonParty() != null;

         dungeonPlayer.getiDungeonParty().removePlayer(player);
      }

      dungeonPlayer.setiDungeonParty(this);
   }

   @Override
   public void removePlayer(Player player) {
      DungeonPlayer dungeonPlayer = Dungeons.inst().getDungeonPlayer(player);
      dungeonPlayer.setiDungeonParty(null);

   }

   @Override
   public boolean hasPlayer(Player player) {
      return false;
   }

   @NotNull
   public Player getLeader() {
      return null;
   }

   @Override
   public void partyMessage(String msg) {

   }

   public void disband() {
   }

   public static @NotNull DungeonPartyWrapper adapt(DungeonPlayer mPlayer) {
      return new DungeonPartyWrapper(mPlayer);
   }

   public static void setPartyPlugin(String partyPlugin) {
      DungeonPartyWrapper.partyPlugin = partyPlugin;
   }
}
