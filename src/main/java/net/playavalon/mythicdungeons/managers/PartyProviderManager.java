package net.playavalon.mythicdungeons.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PartyProviderManager {
   private final Map<IDungeonParty, List<UUID>> parties = new HashMap<>();
   private final Map<UUID, IDungeonParty> partiesByPlayer = new HashMap<>();

   public void put(Player player) {
      this.remove(player);
      MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
      IDungeonParty party = mPlayer.getDungeonParty();
      if (party != null) {
         List<UUID> players = this.parties.computeIfAbsent(party, key -> new ArrayList<>());
         players.add(player.getUniqueId());
         this.partiesByPlayer.put(player.getUniqueId(), party);
      }
   }

   @Nullable
   public IDungeonParty get(Player player) {
      IDungeonParty party = this.partiesByPlayer.get(player.getUniqueId());
      this.updatePartyPlayers(party);
      return party.hasPlayer(player) ? null : party;
   }

   public void remove(Player player) {
      IDungeonParty party = this.partiesByPlayer.remove(player.getUniqueId());
      List<UUID> players = this.parties.get(party);
      if (players != null) {
         players.remove(player.getUniqueId());
      }
   }

   public void updatePartyPlayers(@NotNull IDungeonParty party) {
      List<UUID> players = this.parties.get(party);
      if (players != null) {
         for (UUID uuid : new ArrayList<>(players)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && !party.hasPlayer(player)) {
               MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(player);
               mPlayer.setDungeonParty(null);
               this.remove(player);
            }
         }

         for (Player partyPlayer : party.getPlayers()) {
            if (partyPlayer != null) {
               MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(partyPlayer);
               if (!mPlayer.hasParty()) {
                  mPlayer.setDungeonParty(party);
               }
            }
         }
      }
   }

   @Nullable
   public Location getPartySavePoint(IDungeonParty party, String dungeon) {
      OfflinePlayer leader = party.getLeader();
      MythicPlayer mPlayer = MythicDungeons.inst().getMythicPlayer(leader.getUniqueId());
      return mPlayer.getDungeonSavePoint(dungeon);
   }
}
