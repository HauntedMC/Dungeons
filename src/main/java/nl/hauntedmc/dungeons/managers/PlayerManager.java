package nl.hauntedmc.dungeons.managers;

import java.util.HashMap;
import java.util.UUID;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import org.bukkit.entity.Player;

public final class PlayerManager {
   private final HashMap<UUID, DungeonPlayer> players = new HashMap<>();

   public void put(Player player) {
      this.players.put(player.getUniqueId(), new DungeonPlayer(player));
   }

   public boolean contains(Player player) {
      return this.players.containsKey(player.getUniqueId());
   }

   public DungeonPlayer get(UUID uuid) {
      return this.players.get(uuid);
   }
}
