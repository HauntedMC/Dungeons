package net.playavalon.mythicdungeons.api.parents;

import java.util.ArrayList;
import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import net.playavalon.mythicdungeons.utility.numbers.RangedNumber;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class DungeonDifficulty {
   private String namespace;
   private String display;
   private ItemStack icon;
   private double mobHealthScale = 1.0;
   private double mobSpawnScale = 1.0;
   private double mobDamageScale = 1.0;
   private int mythicMobLevel = 0;
   private RangedNumber bonusLoot;
   private double[] bonusLootChances = new double[0];

   public DungeonDifficulty(ConfigurationSection config) {
      this.namespace = config.getName();
      this.mobHealthScale = config.getDouble("MobHealth", 0.0);
      this.mobSpawnScale = config.getDouble("MobAmounts", 0.0);
      this.mobDamageScale = config.getDouble("MobDamage", 0.0);
      this.mythicMobLevel = config.getInt("BonusMythicLevels", 0);
      this.bonusLoot = new RangedNumber(config.getString("BonusLoot", "0"));
      this.bonusLootChances = config.getDoubleList("BonusLootChances").stream().mapToDouble(d -> d).toArray();

      try {
         Material mat = Material.valueOf(config.getString("Icon.Material", "STICK"));
         this.icon = new ItemStack(mat);
         ItemMeta meta = this.icon.getItemMeta();
         int model = config.getInt("Icon.CustomModelData", -1);
         if (model > -1) {
            meta.setCustomModelData(model);
         }

         meta.setDisplayName(Util.fullColor(config.getString("Icon.Display", this.namespace)));
         List<String> lore = new ArrayList<>();

         for (String line : config.getStringList("Icon.Lore")) {
            lore.add(Util.fullColor(line));
         }

         meta.setLore(lore);
         this.icon.setItemMeta(meta);
      } catch (IllegalArgumentException var8) {
         MythicDungeons.inst().getLogger().warning("Difficulty level " + this.namespace + " has an invalid icon! Please use a valid Spigot material.");
         var8.printStackTrace();
      }
   }

   public DungeonDifficulty(String namespace) {
      this.namespace = namespace;
   }

   public ItemStack getIcon() {
      return this.icon.clone();
   }

   public String getNamespace() {
      return this.namespace;
   }

   public String getDisplay() {
      return this.display;
   }

   public void setDisplay(String display) {
      this.display = display;
   }

   public double getMobHealthScale() {
      return this.mobHealthScale;
   }

   public void setMobHealthScale(double mobHealthScale) {
      this.mobHealthScale = mobHealthScale;
   }

   public double getMobSpawnScale() {
      return this.mobSpawnScale;
   }

   public void setMobSpawnScale(double mobSpawnScale) {
      this.mobSpawnScale = mobSpawnScale;
   }

   public double getMobDamageScale() {
      return this.mobDamageScale;
   }

   public void setMobDamageScale(double mobDamageScale) {
      this.mobDamageScale = mobDamageScale;
   }

   public int getMythicMobLevel() {
      return this.mythicMobLevel;
   }

   public void setMythicMobLevel(int mythicMobLevel) {
      this.mythicMobLevel = mythicMobLevel;
   }

   public RangedNumber getBonusLoot() {
      return this.bonusLoot;
   }

   public void setBonusLoot(RangedNumber bonusLoot) {
      this.bonusLoot = bonusLoot;
   }

   public double[] getBonusLootChances() {
      return this.bonusLootChances;
   }

   public void setBonusLootChances(double[] bonusLootChances) {
      this.bonusLootChances = bonusLootChances;
   }
}
