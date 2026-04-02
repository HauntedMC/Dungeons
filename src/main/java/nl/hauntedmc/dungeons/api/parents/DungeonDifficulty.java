package nl.hauntedmc.dungeons.api.parents;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.HelperUtils;
import nl.hauntedmc.dungeons.util.math.RangedNumber;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

public class DungeonDifficulty {
   private final String namespace;
   private ItemStack icon;
   private final double mobHealthScale;
   private final double mobSpawnScale;
   private final double mobDamageScale;
   private final RangedNumber bonusLoot;

   public DungeonDifficulty(ConfigurationSection config) {
      this.namespace = config.getName();
      this.mobHealthScale = config.getDouble("MobHealth", 0.0);
      this.mobSpawnScale = config.getDouble("MobAmounts", 0.0);
      this.mobDamageScale = config.getDouble("MobDamage", 0.0);
      this.bonusLoot = new RangedNumber(config.getString("BonusLoot", "0"));

      try {
         Material mat = Material.valueOf(config.getString("Icon.Material", "STICK"));
         this.icon = new ItemStack(mat);
         ItemMeta meta = this.icon.getItemMeta();
         int model = config.getInt("Icon.CustomModelData", -1);
         if (model > -1) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float)model));
            meta.setCustomModelDataComponent(component);
         }

         meta.displayName(HelperUtils.component(config.getString("Icon.Display", this.namespace)));
         List<String> lore = new ArrayList<>(config.getStringList("Icon.Lore"));
         meta.lore(HelperUtils.components(lore));
         this.icon.setItemMeta(meta);
      } catch (IllegalArgumentException var8) {
         Dungeons.inst().getLogger().warning("Difficulty level " + this.namespace + " has an invalid icon! Please use a valid Spigot material.");
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var8.getMessage());
      }
   }

   public ItemStack getIcon() {
      return this.icon.clone();
   }

   public String getNamespace() {
      return this.namespace;
   }

   public double getMobHealthScale() {
      return this.mobHealthScale;
   }

   public double getMobSpawnScale() {
      return this.mobSpawnScale;
   }

   public double getMobDamageScale() {
      return this.mobDamageScale;
   }

   public RangedNumber getBonusLoot() {
      return this.bonusLoot;
   }
}
