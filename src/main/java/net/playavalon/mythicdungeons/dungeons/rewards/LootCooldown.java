package net.playavalon.mythicdungeons.dungeons.rewards;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionReward;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

public class LootCooldown implements ConfigurationSerializable {
   private Location location;
   private Date lootedAt;
   private Date resetTime;

   public LootCooldown(Map<String, Object> config) {
      this.location = (Location)config.get("LootLocation");
      this.lootedAt = new Date((Long)config.get("LootedTime"));
      this.resetTime = new Date((Long)config.get("ResetTime"));
   }

   public LootCooldown(@NotNull FunctionReward rewardFunction, Date resetTime) {
      this.location = rewardFunction.getLocation().clone();
      this.location.setWorld(null);
      this.lootedAt = Calendar.getInstance().getTime();
      this.resetTime = resetTime;
   }

   @NotNull
   public Map<String, Object> serialize() {
      Map<String, Object> map = new HashMap<>();
      map.put("LootLocation", this.location);
      map.put("LootedTime", this.lootedAt.getTime());
      map.put("ResetTime", this.resetTime.getTime());
      return map;
   }

   public Location getLocation() {
      return this.location;
   }

   public Date getLootedAt() {
      return this.lootedAt;
   }

   public Date getResetTime() {
      return this.resetTime;
   }
}
