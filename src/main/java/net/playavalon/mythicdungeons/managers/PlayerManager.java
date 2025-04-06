package net.playavalon.mythicdungeons.managers;

import java.util.HashMap;
import java.util.UUID;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import org.bukkit.entity.Player;

public final class PlayerManager {
   private final HashMap<UUID, MythicPlayer> players = new HashMap<>();

   public void put(Player player) {
      this.players.put(player.getUniqueId(), new MythicPlayer(player));
   }

   public boolean contains(Player player) {
      return this.players.containsKey(player.getUniqueId());
   }

   public MythicPlayer get(UUID uuid) {
      return this.players.get(uuid);
   }
}
