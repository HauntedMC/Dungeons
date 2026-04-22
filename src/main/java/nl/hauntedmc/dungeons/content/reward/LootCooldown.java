package nl.hauntedmc.dungeons.content.reward;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/**
 * Per-location cooldown record for a reward function.
 *
 * <p>The stored location is worldless so the same reward coordinate can be matched across
 * temporary instance worlds.
 */
@TypeKey(id = "dungeons.reward.loot_cooldown")
@SerializableAs("dungeons.reward.loot_cooldown")
public class LootCooldown implements ConfigurationSerializable {
    private final Location location;
    private final Date lootedAt;
    private final Date resetTime;

    /**
     * Creates a new LootCooldown instance.
     */
    public LootCooldown(Map<String, Object> config) {
        this.location = (Location) config.get("LootLocation");
        this.lootedAt = new Date((Long) config.get("LootedTime"));
        this.resetTime = new Date((Long) config.get("ResetTime"));
    }

    /**
     * Creates a new LootCooldown instance.
     */
    public LootCooldown(@NotNull RewardFunction rewardFunction, Date resetTime) {
        this.location = rewardFunction.getLocation().clone();
        this.location.setWorld(null);
        this.lootedAt = Calendar.getInstance().getTime();
        this.resetTime = resetTime;
    }

    @NotNull public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("LootLocation", this.location);
        map.put("LootedTime", this.lootedAt.getTime());
        map.put("ResetTime", this.resetTime.getTime());
        return map;
    }

    /**
     * Returns the location.
     */
    public Location getLocation() {
        return this.location;
    }

    /**
     * Returns the looted at.
     */
    public Date getLootedAt() {
        return this.lootedAt;
    }

    /**
     * Returns the reset time.
     */
    public Date getResetTime() {
        return this.resetTime;
    }
}
