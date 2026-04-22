package nl.hauntedmc.dungeons.content.reward;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Reward cooldown state tracked for one player across dungeon runs.
 *
 * <p>The lookup map is keyed by worldless reward locations so cooldown checks stay stable across
 * per-instance world copies.
 */
@TypeKey(id = "dungeons.reward.player_loot_data")
@SerializableAs("dungeons.reward.player_loot_data")
public class PlayerLootData implements ConfigurationSerializable {
    private final String playerName;
    private final UUID playerId;
    private final List<LootCooldown> loot;
    private final Map<Location, LootCooldown> lootByLocation;

    /**
     * Creates a new PlayerLootData instance.
     */
    public PlayerLootData(Map<String, Object> config) {
        this.playerName = (String) config.get("Player");
        this.playerId = UUID.fromString((String) config.get("UUID"));
        this.loot = (List<LootCooldown>) config.get("Loot");
        this.lootByLocation = new HashMap<>();

        for (LootCooldown cooldown : this.loot) {
            this.lootByLocation.put(cooldown.getLocation(), cooldown);
        }
    }

    /**
     * Creates a new PlayerLootData instance.
     */
    public PlayerLootData(Player player) {
        this.playerName = player.getName();
        this.playerId = player.getUniqueId();
        this.loot = new ArrayList<>();
        this.lootByLocation = new HashMap<>();
    }

    /**
     * Adds loot cooldown.
     */
    public void addLootCooldown(RewardFunction reward, Date resetTime) {
        LootCooldown cooldown = new LootCooldown(reward, resetTime);
        this.lootByLocation.put(cooldown.getLocation(), cooldown);
        this.loot.add(cooldown);
    }

    /**
     * Removes loot cooldown.
     */
    public void removeLootCooldown(LootCooldown cooldown) {
        this.lootByLocation.remove(cooldown.getLocation());
        this.loot.remove(cooldown);
    }

    /**
     * Returns the loot cooldown.
     */
    public LootCooldown getLootCooldown(RewardFunction reward) {
        Location loc = reward.getLocation().clone();
        loc.setWorld(null);
        return this.lootByLocation.get(loc);
    }

    /**
     * Checks cooldowns.
     */
    public void checkCooldowns() {
        Player player = Bukkit.getPlayer(this.playerId);
        if (player != null) {
            boolean hasCooldowns = false;

            for (LootCooldown cooldown : new ArrayList<>(this.loot)) {
                if (new Date(System.currentTimeMillis()).after(cooldown.getResetTime())) {
                    this.removeLootCooldown(cooldown);
                } else {
                    hasCooldowns = true;
                }
            }

            if (hasCooldowns) {
                LangUtils.sendMessage(player, "instance.play.rewards.unavailable");
            }
        }
    }

    /**
     * Returns whether it has loot on cooldown.
     */
    public boolean hasLootOnCooldown() {
        return !this.loot.isEmpty();
    }

    /**
     * Returns whether it has loot on cooldown.
     */
    public boolean hasLootOnCooldown(RewardFunction reward) {
        Location loc = reward.getLocation().clone();
        loc.setWorld(null);
        return this.lootByLocation.containsKey(loc);
    }

    @NotNull public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("Player", this.playerName);
        map.put("UUID", this.playerId.toString());
        map.put("Loot", this.loot);
        return map;
    }

    /**
     * Returns the player name.
     */
    public String getPlayerName() {
        return this.playerName;
    }

    /**
     * Returns the player id.
     */
    public UUID getPlayerId() {
        return this.playerId;
    }
}
