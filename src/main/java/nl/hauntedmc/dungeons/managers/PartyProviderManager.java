package nl.hauntedmc.dungeons.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PartyProviderManager {
   private final Map<IDungeonParty, List<UUID>> parties = new HashMap<>();
   private final Map<UUID, IDungeonParty> partiesByPlayer = new HashMap<>();

   public void put(Player player) {
      this.remove(player);
      DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
      IDungeonParty party = mPlayer.getiDungeonParty();
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
               DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(player);
               mPlayer.setiDungeonParty(null);
               this.remove(player);
            }
         }

         for (Player partyPlayer : party.getPlayers()) {
            if (partyPlayer != null) {
               DungeonPlayer mPlayer = Dungeons.inst().getDungeonPlayer(partyPlayer);
               if (!mPlayer.hasParty()) {
                  mPlayer.setiDungeonParty(party);
               }
            }
         }
      }
   }

}
