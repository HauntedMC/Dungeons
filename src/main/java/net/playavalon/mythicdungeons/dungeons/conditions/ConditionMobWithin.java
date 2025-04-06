package net.playavalon.mythicdungeons.dungeons.conditions;

import io.lumine.mythic.api.mobs.MythicMob;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredCondition;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
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
      } catch (IllegalArgumentException var9) {
      }

      if (type == null) {
         if (MythicDungeons.inst().getMythicApi() != null) {
            Optional<MythicMob> mythicMob = MythicBukkit.inst().getMobManager().getMythicMob(this.entityName);
            if (!mythicMob.isPresent()) {
               return false;
            }

            for (Entity ent : entities) {
               if (ent instanceof LivingEntity) {
                  ActiveMob mob = MythicBukkit.inst().getAPIHelper().getMythicMobInstance(ent);
                  if (mob != null && mob.getType().getInternalName().equals(this.entityName)) {
                     mobs.add((LivingEntity)ent);
                  }
               }
            }

            if (this.mobsExact) {
               return mobs.size() == this.count;
            }

            return mobs.size() >= this.count;
         }

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
      if (MythicDungeons.inst().getMythicApi() != null) {
         functionButton.addLore("");
         functionButton.addLore("&dSupports MythicMobs!");
      }

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
            if (MythicDungeons.inst().getMythicApi() == null) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat kind of mob are we checking for?"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat kind of mob or MythicMob are we checking for?"));
            }
         }

         @Override
         public void onInput(Player player, String message) {
            ConditionMobWithin.this.entityName = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet mob to '&6" + message + "&a'"));
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
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat is the range of the trigger in blocks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            ConditionMobWithin.this.radius = value.orElse(ConditionMobWithin.this.radius);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet condition radius to '" + ConditionMobWithin.this.radius + "'"));
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
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many are required?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            ConditionMobWithin.this.count = value.orElse(ConditionMobWithin.this.count);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet required mobs to '" + ConditionMobWithin.this.count + "'"));
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
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to 'Required mobs is exact'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to 'Required mobs is a minimum'"));
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
