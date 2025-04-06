package net.playavalon.mythicdungeons.dungeons.rewards;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.playavalon.mythicdungeons.dungeons.functions.rewards.FunctionReward;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlayerLootData implements ConfigurationSerializable {
   private final String playerName;
   private final UUID playerID;
   private List<LootCooldown> loot;
   private Map<Location, LootCooldown> lootByLocation;

   public PlayerLootData(Map<String, Object> config) {
      this.playerName = (String)config.get("Player");
      this.playerID = UUID.fromString((String)config.get("UUID"));
      this.loot = (List<LootCooldown>)config.get("Loot");
      this.lootByLocation = new HashMap<>();

      for (LootCooldown cooldown : this.loot) {
         this.lootByLocation.put(cooldown.getLocation(), cooldown);
      }
   }

   public PlayerLootData(Player player) {
      this.playerName = player.getName();
      this.playerID = player.getUniqueId();
      this.loot = new ArrayList<>();
      this.lootByLocation = new HashMap<>();
   }

   public void addLootCooldown(FunctionReward reward, Date resetTime) {
      LootCooldown cooldown = new LootCooldown(reward, resetTime);
      this.lootByLocation.put(cooldown.getLocation(), cooldown);
      this.loot.add(cooldown);
   }

   public void removeLootCooldown(LootCooldown cooldown) {
      this.lootByLocation.remove(cooldown.getLocation());
      this.loot.remove(cooldown);
   }

   public LootCooldown getLootCooldown(FunctionReward reward) {
      Location loc = reward.getLocation().clone();
      loc.setWorld(null);
      return this.lootByLocation.get(loc);
   }

   public void checkCooldowns() {
      Player player = Bukkit.getPlayer(this.playerID);
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
            LangUtils.sendMessage(player, "misc.rewards-unavailable");
         }
      }
   }

   public boolean hasLootOnCooldown() {
      return !this.loot.isEmpty();
   }

   public boolean hasLootOnCooldown(FunctionReward reward) {
      Location loc = reward.getLocation().clone();
      loc.setWorld(null);
      return this.lootByLocation.containsKey(loc);
   }

   @NotNull
   public Map<String, Object> serialize() {
      Map<String, Object> map = new HashMap<>();
      map.put("Player", this.playerName);
      map.put("UUID", this.playerID.toString());
      map.put("Loot", this.loot);
      return map;
   }

   public String getPlayerName() {
      return this.playerName;
   }

   public UUID getPlayerID() {
      return this.playerID;
   }
}
