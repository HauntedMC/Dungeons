package nl.hauntedmc.dungeons.dungeons.conditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredCondition;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

@DeclaredCondition
public class ConditionMobWithin extends TriggerCondition {
   @SavedField
   private String entityName = "zombie";
   @SavedField
   private double radius = 3.0;
   @SavedField
   private int count = 1;
   @SavedField
   private boolean mobsExact = false;

   public ConditionMobWithin(Map<String, Object> config) {
      super("mobswithin", config);
   }

   public ConditionMobWithin() {
      super("mobswithin");
   }

   @Override
   public boolean check(TriggerFireEvent event) {
      Collection<Entity> entities = this.location.getWorld().getNearbyEntities(this.location, this.radius, this.radius, this.radius);
      List<LivingEntity> mobs = new ArrayList<>();
      EntityType type = null;

      try {
         type = EntityType.valueOf(this.entityName.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
      }

      if (type == null) {
         type = EntityType.ZOMBIE;
      }

      for (Entity entx : entities) {
         if (entx instanceof LivingEntity && entx.getType() == type) {
            mobs.add((LivingEntity)entx);
         }
      }

      return this.mobsExact ? mobs.size() == this.count : mobs.size() >= this.count;
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.CREEPER_HEAD);
      functionButton.setDisplayName("&dMobs Within");
      functionButton.addLore("&eChecks for a number of specified");
      functionButton.addLore("&emobs within a configured radius");
      functionButton.addLore("&efrom this function.");

      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.ZOMBIE_HEAD);
            this.button.setDisplayName("&d&lSet Mob Type");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat kind of mob are we checking for?"));
         }

         @Override
         public void onInput(Player player, String message) {
            ConditionMobWithin.this.entityName = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet mob to '&6" + message + "&a'"));
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DETECTOR_RAIL);
            this.button.setDisplayName("&d&lDistance");
            this.button.setAmount((int)MathUtils.round(ConditionMobWithin.this.radius, 0));
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat is the range of the trigger in blocks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            ConditionMobWithin.this.radius = value.orElse(ConditionMobWithin.this.radius);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet condition radius to '" + ConditionMobWithin.this.radius + "'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName(ConditionMobWithin.this.mobsExact ? "&d&lMobs Required" : "&d&lMinimum Mobs Required");
            this.button.setAmount(ConditionMobWithin.this.count);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow many are required?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            ConditionMobWithin.this.count = value.orElse(ConditionMobWithin.this.count);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet required mobs to '" + ConditionMobWithin.this.count + "'"));
            }
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lRequired Mobs Exact");
            this.button.setEnchanted(ConditionMobWithin.this.mobsExact);
         }

         @Override
         public void onSelect(Player player) {
            if (!ConditionMobWithin.this.mobsExact) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to 'Required mobs is exact'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to 'Required mobs is a minimum'"));
            }

            ConditionMobWithin.this.mobsExact = !ConditionMobWithin.this.mobsExact;
         }
      });
   }

   public String getEntityName() {
      return this.entityName;
   }

   public void setEntityName(String entityName) {
      this.entityName = entityName;
   }

   public double getRadius() {
      return this.radius;
   }

   public void setRadius(double radius) {
      this.radius = radius;
   }

   public int getCount() {
      return this.count;
   }

   public void setCount(int count) {
      this.count = count;
   }
}
