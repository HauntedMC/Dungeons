package nl.hauntedmc.dungeons.runtime.player;

import java.util.HashMap;
import java.util.UUID;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import org.bukkit.entity.Player;

/**
 * Registry of live {@link DungeonPlayerSession} objects keyed by player UUID.
 */
public final class PlayerSessionRegistry {
    private final DungeonsPlugin plugin;
    private final HashMap<UUID, DungeonPlayerSession> players = new HashMap<>();

    /** Creates a new session registry bound to the plugin runtime. */
    public PlayerSessionRegistry(DungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Creates and stores a new session for the player. */
    public DungeonPlayerSession put(Player player) {
        DungeonPlayerSession existing = this.players.get(player.getUniqueId());
        if (existing != null) {
            existing.setPlayer(player);
            return existing;
        }
        DungeonPlayerSession dungeonPlayer;
        try {
            dungeonPlayer = new DungeonPlayerSession(this.plugin, player);
        } catch (RuntimeException failure) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Cannot load durable player state for '{}'.",
                            player.getUniqueId(),
                            failure);
            player.kick(
                    net.kyori.adventure.text.Component.text(
                            "Your player data could not be loaded safely. Please contact an"
                                + " administrator."));
            throw failure;
        }
        this.players.put(player.getUniqueId(), dungeonPlayer);
        return dungeonPlayer;
    }

    /** Returns whether a session exists for the player. */
    public boolean contains(Player player) {
        return this.players.containsKey(player.getUniqueId());
    }

    /** Returns the session for a live Bukkit player. */
    public DungeonPlayerSession get(Player player) {
        return this.get(player.getUniqueId());
    }

    /** Returns the session for a UUID, or null when not tracked. */
    public DungeonPlayerSession get(UUID uuid) {
        return this.players.get(uuid);
    }
}
