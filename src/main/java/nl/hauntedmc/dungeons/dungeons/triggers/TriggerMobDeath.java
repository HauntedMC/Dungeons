package nl.hauntedmc.dungeons.dungeons.triggers;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.TriggerCategory;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

public class TriggerMobDeath extends DungeonTrigger {
   @SavedField
   private String mob = "any";
   @SavedField
   private int count = 1;
   @SavedField
   private double radius = 0.0;
   private int status;

   public TriggerMobDeath(Map<String, Object> config) {
      super("Mob Death", config);
      this.waitForConditions = true;
      this.setCategory(TriggerCategory.GENERAL);
      this.setHasTarget(true);
   }

   public TriggerMobDeath() {
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
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.ROTTEN_FLESH);
      functionButton.setDisplayName("&5Mob Death Counter");
      functionButton.addLore("&eTriggered when a certain number of");
      functionButton.addLore("&ea mob has died.");
      return functionButton;
   }

   @EventHandler
   public void onMobDeath(EntityDeathEvent event) {
      if (event.getEntity().getWorld() == this.instance.getInstanceWorld()) {
         LivingEntity ent = event.getEntity();
         boolean mobMatches = false;

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
                        DungeonPlayer mPlayer = null;
                        if (killer != null) {
                           mPlayer = Dungeons.inst().getDungeonPlayer(killer);
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
               String uuidString = ent.getPersistentDataContainer()
                  .get(new NamespacedKey(Dungeons.inst(), "originroom"), PersistentDataType.STRING);
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
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat mob needs to be killed? ('any' for any mob.)"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eMob is currently: &6" + TriggerMobDeath.this.mob));
         }

         @Override
         public void onInput(Player player, String message) {
            TriggerMobDeath.this.mob = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet mob to '&6" + message + "&a'"));
            TriggerMobDeath.this.setDisplayName(TriggerMobDeath.this.mob + " Death");
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BONE);
            this.button.setDisplayName("&d&lAmount");
            this.button.setAmount(TriggerMobDeath.this.count);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow many of the mob needs to be killed?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            TriggerMobDeath.this.count = value.orElse(TriggerMobDeath.this.count);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet amount to '&6" + TriggerMobDeath.this.count + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REDSTONE_TORCH);
            this.button.setDisplayName("&d&lAllow Retrigger");
            this.button.setEnchanted(TriggerMobDeath.this.allowRetrigger);
         }

         @Override
         public void onSelect(Player player) {
            if (!TriggerMobDeath.this.allowRetrigger) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Triggering Again Allowed&a'"));
            } else {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSwitched to '&6Prevent Triggering Again&a'"));
            }

            TriggerMobDeath.this.allowRetrigger = !TriggerMobDeath.this.allowRetrigger;
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.DETECTOR_RAIL);
            this.button.setDisplayName("&d&lMob Radius");
            this.button.setAmount((int)TriggerMobDeath.this.radius);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow close must the mob be to this function? (0 for infinite.)"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent radius: &6" + TriggerMobDeath.this.radius));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Double> value = StringUtils.readDoubleInput(player, message);
            TriggerMobDeath.this.radius = value.orElse(TriggerMobDeath.this.radius);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet radius to '&6" + TriggerMobDeath.this.radius + "&a'"));
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
