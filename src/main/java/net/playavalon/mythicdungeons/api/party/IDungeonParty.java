package net.playavalon.mythicdungeons.api.party;

import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.Util;
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
         partyPlayer.sendMessage(Util.fullColor(msg));
      }
   }

   default Location getPartySavePoint(String dungeon) {
      OfflinePlayer leader = this.getLeader();
      MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(leader.getUniqueId());
      return mPlayer.getDungeonSavePoint(dungeon);
   }

   default void initDungeonParty(Plugin plugin) {
      if (MythicDungeons.inst().getPartyPluginName().equalsIgnoreCase(plugin.getName())) {
         for (Player player : this.getPlayers()) {
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            mPlayer.setDungeonParty(this);
         }
      }
   }

   default void initDungeonParty(String... names) {
      boolean success = false;

      for (String plugin : names) {
         if (MythicDungeons.inst().getPartyPluginName().equalsIgnoreCase(plugin)) {
            success = true;
         }
      }

      if (success) {
         for (Player player : this.getPlayers()) {
            MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
            mPlayer.setDungeonParty(this);
         }
      }
   }

   default void setAwaitingDungeon(boolean bool) {
      for (Player player : this.getPlayers()) {
         MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
         mPlayer.setAwaitingDungeon(bool);
      }
   }
}
