package net.playavalon.mythicdungeons.dungeons.triggers;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.TriggerCategory;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.ToggleMenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

public class TriggerMythicMobDeath extends DungeonTrigger {
   @SavedField
   private String mob = "any";
   @SavedField
   private int count = 1;
   @SavedField
   private double radius = 0.0;
   private int status;

   public TriggerMythicMobDeath(Map<String, Object> config) {
      super("Mob Death", config);
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.GENERAL);
      this.setHasTarget(true);
   }

   public TriggerMythicMobDeath() {
      super("Mob Death");
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.GENERAL);
      this.setHasTarget(true);
   }

   @Override
   public void init() {
      super.init();
      this.setDisplayName(this.mob + " Death");
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      if (config.containsKey("MythicMob")) {
         this.mob = (String)config.get("MythicMob");
      }

      if (config.containsKey("mythicMob")) {
         this.mob = (String)config.get("mythicMob");
      }

      if (config.containsKey("Count")) {
         this.count = (Integer)config.get("Count");
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.ROTTEN_FLESH);
      functionButton.setDisplayName("&5Mob Death Counter");
      functionButton.addLore("&eTriggered when a certain number of");
      functionButton.addLore("&ea mob has died.");
      functionButton.addLore("&dSupports MythicMobs!");
      return functionButton;
   }

   @EventHandler
   public void onMobDeath(EntityDeathEvent event) {
      if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
         LivingEntity ent = event.getEntity();
         boolean mobMatches = false;
         if (MythicDungeons.inst().getMythicApi() != null) {
            ActiveMob aMob = MythicBukkit.inst().getMobManager().getMythicMobInstance(ent);
            if (aMob != null && (this.mob.equalsIgnoreCase("any") || aMob.getMobType().equals(this.mob))) {
               mobMatches = true;
            }
         }

         if (!mobMatches && (this.mob.equalsIgnoreCase("any") || event.getEntity().getType().name().equalsIgnoreCase(this.mob))) {
            mobMatches = true;
         }

         if (mobMatches) {
            if (this.matchesRoom(ent)) {
               if (this.radius == 0.0 || !(this.location.distance(ent.getLocation()) > this.radius)) {
                  if (this.status < this.count) {
                     this.status++;
                     if (this.status >= this.count) {
                        Player killer = ent.getKiller();
                        MythicPlayer mPlayer = null;
                        if (killer != null) {
                           mPlayer = MythicDungeons.inst().getMythicPlayer(killer);
                        }

                        this.trigger(mPlayer);
                        if (this.allowRetrigger) {
                           this.status = 0;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public boolean matchesRoom(Entity ent) {
      if (!this.limitToRoom) {
         return true;
      } else {
         InstanceProcedural inst = this.instance.as(InstanceProcedural.class);
         if (inst == null) {
            return false;
         } else {
            InstanceRoom room = inst.getRoom(this.location);
            if (room == null) {
               return false;
            } else {
               String uuidString = (String)ent.getPersistentDataContainer()
                  .get(new NamespacedKey(MythicDungeons.inst(), "originroom"), PersistentDataType.STRING);
               if (uuidString == null) {
                  return false;
               } else {
                  UUID uuid = UUID.fromString(uuidString);
                  return uuid.equals(room.getUuid());
               }
            }
         }
      }
   }

   @Override
   public void buildHotbarMenu() {
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.PAPER);
            this.button.setDisplayName("&d&lMob");
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat mob needs to be killed? ('any' for any mob.)"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eMob is currently: &6" + TriggerMythicMobDeath.this.mob));
         }

         @Override
         public void onInput(Player player, String message) {
            TriggerMythicMobDeath.this.mob = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet mob to '&6" + message + "&a'"));
            TriggerMythicMobDeath.this.setDisplayName(TriggerMythicMobDeath.this.mob + " Death");
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BONE);
            this.button.setDisplayName("&d&lAmount");
            this.button.setAmount(TriggerMythicMobDeath.this.count);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many of the mob needs to be killed?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            TriggerMythicMobDeath.this.count = value.orElse(TriggerMythicMobDeath.this.count);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet amount to '&6" + TriggerMythicMobDeath.this.count + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerMythicMobDeath.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerMythicMobDeath.this.allowRetrigger) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerMythicMobDeath.this.allowRetrigger = !TriggerMythicMobDeath.this.allowRetrigger;
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DETECTOR_RAIL);
            this.button.setDisplayName("&d&lMob Radius");
            this.button.setAmount((int)TriggerMythicMobDeath.this.radius);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow close must the mob be to this function? (0 for infinite.)"));
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent radius: &6" + TriggerMythicMobDeath.this.radius));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            TriggerMythicMobDeath.this.radius = value.orElse(TriggerMythicMobDeath.this.radius);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet radius to '&6" + TriggerMythicMobDeath.this.radius + "&a'"));
            }
         }
      });
      this.addRoomLimitToggleButton();
   }

   public String getMob() {
      return this.mob;
   }

   public void setMob(String mob) {
      this.mob = mob;
   }

   public int getCount() {
      return this.count;
   }

   public void setCount(int count) {
      this.count = count;
   }

   public double getRadius() {
      return this.radius;
   }

   public void setRadius(double radius) {
      this.radius = radius;
   }
}
