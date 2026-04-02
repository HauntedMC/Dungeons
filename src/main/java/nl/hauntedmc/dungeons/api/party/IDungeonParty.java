package nl.hauntedmc.dungeons.api.party;

import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public interface IDungeonParty {
   void addPlayer(Player var1);

   void removePlayer(Player var1);

   List<Player> getPlayers();

   @NotNull
   OfflinePlayer getLeader();

   default boolean hasPlayer(Player player) {
      for (Player partyPlayer : this.getPlayers()) {
         if (partyPlayer.getUniqueId() == player.getUniqueId()) {
            return true;
         }
      }

      return false;
   }

   default void partyMessage(String msg) {
      for (Player partyPlayer : this.getPlayers()) {
         partyPlayer.sendMessage(HelperUtils.fullColor(msg));
      }
   }

   default Location getPartySavePoint(String dungeon) {
      OfflinePlayer leader = this.getLeader();
      DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(leader.getUniqueId());
      return mPlayer.getDungeonSavePoint(dungeon);
   }

   default void initDungeonParty(Plugin plugin) {
      if (Dungeons.inst().getPartyPluginName().equalsIgnoreCase(plugin.getName())) {
         for (Player player : this.getPlayers()) {
            DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
            mPlayer.setiDungeonParty(this);
         }
      }
   }

   default void initDungeonParty(String... names) {
      boolean success = false;

      for (String plugin : names) {
         if (Dungeons.inst().getPartyPluginName().equalsIgnoreCase(plugin)) {
            success = true;
         }
      }

      if (success) {
         for (Player player : this.getPlayers()) {
            DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
            mPlayer.setiDungeonParty(this);
         }
      }
   }

   default void setAwaitingDungeon(boolean bool) {
      for (Player player : this.getPlayers()) {
         DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
         mPlayer.setAwaitingDungeon(bool);
      }
   }
}
