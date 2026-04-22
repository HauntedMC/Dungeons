package nl.hauntedmc.dungeons.util.entity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Player lookup and display helper utilities. */
public final class PlayerUtils {

    /** Returns serialized display name for a player, or empty string for null. */
    public static String playerDisplayName(Player player) {
        return player == null ? "" : ComponentUtils.serialize(player.displayName());
    }

    /** Returns nearby online players within radius filtered by allowed game modes. */
    public static List<Player> getPlayersWithin(Location loc, double radius, GameMode... gameModes) {
        List<Player> players = new ArrayList<>();
        World world = loc.getWorld();
        if (world != null) {
            for (Entity ent :
                    world.getNearbyEntities(
                            loc, radius, radius, radius, entity -> entity.getType() == EntityType.PLAYER)) {
                if (ent instanceof Player player
                        && Bukkit.getOnlinePlayers().contains(player)
                        && Arrays.asList(gameModes).contains(player.getGameMode())) {
                    players.add(player);
                }
            }
        }
        return players;
    }
}
