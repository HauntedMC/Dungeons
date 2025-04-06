package net.playavalon.mythicdungeons.dungeons.functions;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.bukkit.utils.numbers.RangedInt;
import io.lumine.mythic.core.mobs.ActiveMob;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.generation.rooms.InstanceRoom;
import net.playavalon.mythicdungeons.api.parents.DungeonDifficulty;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.FunctionCategory;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceProcedural;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.menu.menuitems.ChatMenuItem;
import net.playavalon.mythicdungeons.menu.menuitems.MenuItem;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.MathUtils;
import net.playavalon.mythicdungeons.utility.helpers.StringUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

@DeclaredFunction
public class FunctionSpawnMythicMob extends DungeonFunction {
   @SavedField
   private String mob = "zombie";
   @SavedField
   private String levelString = "0";
   @SavedField
   private int maxCount = 1;
   @SavedField
   private int delay = 0;
   @SavedField
   private int interval = 0;
   @SavedField
   private double yaw = 0.0;
   private int count;
   private BukkitRunnable spawner;
   private List<BukkitRunnable> spawners;

   public FunctionSpawnMythicMob(Map<String, Object> config) {
      super("Mob Spawner", config);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
      this.spawners = new ArrayList<>();
   }

   public FunctionSpawnMythicMob() {
      super("Mob Spawner");
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
      this.spawners = new ArrayList<>();
   }

   @Override
   public void init() {
      super.init();
      this.setDisplayName(this.mob + " Spawner");
   }

   @Override
   public void initLegacyFields(Map<String, Object> config) {
      if (config.containsKey("Mob") || config.containsKey("MythicMob")) {
         this.mob = (String)config.getOrDefault("Mob", config.get("MythicMob"));
      }

      if (config.containsKey("Count")) {
         this.maxCount = (Integer)config.get("Count");
      }

      if (config.containsKey("Delay")) {
         this.delay = (Integer)config.get("Delay");
      }

      if (config.containsKey("Interval")) {
         this.interval = (Integer)config.get("Interval");
      }

      if (config.containsKey("level")) {
         this.levelString = String.valueOf(((Integer)config.get("level")).intValue());
      }
   }

   @Override
   public void onDisable() {
      for (BukkitRunnable spawner : this.spawners) {
         if (!spawner.isCancelled()) {
            spawner.cancel();
         }
      }

      this.spawners.clear();
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      final InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         double healthMod = 1.0;
         int maxCountModded = this.maxCount;
         double damageMod = 1.0;
         int levelMod = 0;
         if (instance.getDungeon().isUseDifficultyLevels() && instance.getDifficulty() != null) {
            DungeonDifficulty difficulty = instance.getDifficulty();
            healthMod = difficulty.getMobHealthScale();
            maxCountModded = (int)(maxCountModded * difficulty.getMobSpawnScale());
            damageMod = difficulty.getMobDamageScale();
            levelMod = difficulty.getMythicMobLevel();
         }

         final int finalMaxCountMod = maxCountModded;
         final double finalDamageMod = damageMod;
         final int finalLevelMod = levelMod;
         double finalHealthMod = healthMod;
         BukkitRunnable spawner = new BukkitRunnable() {
            public void run() {
               if (FunctionSpawnMythicMob.this.count >= finalMaxCountMod) {
                  this.cancel();
                  if (FunctionSpawnMythicMob.this.trigger != null && FunctionSpawnMythicMob.this.trigger.isAllowRetrigger()) {
                     FunctionSpawnMythicMob.this.count = 0;
                  }
               } else {
                  Location spawnPoint = FunctionSpawnMythicMob.this.location.clone();
                  spawnPoint.setX(spawnPoint.getX() + 0.5);
                  spawnPoint.setY(spawnPoint.getY() + 0.1);
                  spawnPoint.setZ(spawnPoint.getZ() + 0.5);
                  spawnPoint.setYaw((float)FunctionSpawnMythicMob.this.yaw);
                  if (spawnPoint.getBlock().isSolid()) {
                     spawnPoint.setY(spawnPoint.getY() + 1.0);
                  }

                  EntityType type = null;

                  try {
                     type = EntityType.valueOf(FunctionSpawnMythicMob.this.mob.toUpperCase(Locale.ROOT));
                  } catch (IllegalArgumentException var6) {
                  }

                  if (type == null) {
                     if (MythicDungeons.inst().getMythicApi() != null) {
                        ActiveMob aMob;
                        if (FunctionSpawnMythicMob.this.levelString.equals("0") && !instance.getDungeon().isUseDifficultyLevels()) {
                           aMob = MythicDungeons.inst().getMythicApi().getMobManager().spawnMob(FunctionSpawnMythicMob.this.mob, spawnPoint);
                        } else {
                           RangedInt range = new RangedInt(FunctionSpawnMythicMob.this.levelString);
                           aMob = MythicDungeons.inst()
                              .getMythicApi()
                              .getMobManager()
                              .spawnMob(
                                 FunctionSpawnMythicMob.this.mob, spawnPoint, MathUtils.getRandomNumberInRange(range.getMin(), range.getMax()) + finalLevelMod
                              );
                        }

                        if (aMob != null) {
                           AbstractEntity ent = aMob.getEntity();
                           if (instance.getDungeon().isUseDifficultyLevels()) {
                              ent.setHealthAndMax(ent.getMaxHealth() * finalHealthMod);
                              ent.setDamage(ent.getDamage() * finalDamageMod);
                           }

                           FunctionSpawnMythicMob.this.applyRoomTag(ent.getBukkitEntity());
                        }

                        FunctionSpawnMythicMob.this.count++;
                        return;
                     }

                     type = EntityType.ZOMBIE;
                  }

                  if (instance.getInstanceWorld().spawnEntity(spawnPoint, type) instanceof LivingEntity living) {
                     living.setMaxHealth(living.getMaxHealth() * finalHealthMod);
                     living.setHealth(living.getMaxHealth());
                     FunctionSpawnMythicMob.this.applyRoomTag(living);
                     AttributeInstance attrib = living.getAttribute(Attribute.ATTACK_DAMAGE);
                     if (attrib != null) {
                        living.getAttribute(Attribute.ATTACK_DAMAGE)
                           .addModifier(new AttributeModifier("dungeon_level", finalDamageMod - 1.0, Operation.MULTIPLY_SCALAR_1));
                     }
                  }

                  FunctionSpawnMythicMob.this.count++;
               }
            }
         };
         spawner.runTaskTimer(MythicDungeons.inst(), this.delay, Math.max(this.interval, 1));
         this.spawners.add(spawner);
      }
   }

   private void applyRoomTag(Entity ent) {
      InstanceProcedural inst = this.instance.as(InstanceProcedural.class);
      if (inst != null) {
         InstanceRoom room = inst.getRoom(this.location);
         if (room != null) {
            ent.getPersistentDataContainer().set(new NamespacedKey(MythicDungeons.inst(), "originroom"), PersistentDataType.STRING, room.getUuid().toString());
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.CREEPER_HEAD);
      functionButton.setDisplayName("&dMob Spawner");
      functionButton.addLore("&eSpawns mobs at this location.");
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
            this.button.setDisplayName("&d&lMob Name");
         }

         @Override
         public void onSelect(Player player) {
            if (MythicDungeons.inst().getMythicApi() != null) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat's the type of mob or mythic mob?"));
            } else {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eWhat's the type of mob?"));
            }

            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent mob is: &6" + FunctionSpawnMythicMob.this.mob));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionSpawnMythicMob.this.mob = message;
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet mob to '&6" + message + "&a'"));
            FunctionSpawnMythicMob.this.setDisplayName(FunctionSpawnMythicMob.this.mob + " Spawner");
         }
      });
      if (MythicDungeons.inst().getMythicApi() != null) {
         this.menu.addMenuItem(new ChatMenuItem() {
            @Override
            public void buildButton() {
               this.button = new MenuButton(Material.EMERALD);
               this.button.setDisplayName("&d&lMythic Mob Level");
            }

            @Override
            public void onSelect(Player player) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eIf this is a Mythic Mob, what level should the mob be?"));
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eCurrent level: " + FunctionSpawnMythicMob.this.levelString));
            }

            @Override
            public void onInput(Player player, String message) {
               FunctionSpawnMythicMob.this.levelString = message;
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet the mob level to '&6" + FunctionSpawnMythicMob.this.levelString + "&a'"));
            }
         });
      }

      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BONE);
            this.button.setDisplayName("&d&lAmount");
            this.button.setAmount(FunctionSpawnMythicMob.this.maxCount);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow many of this mob will spawn from here?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionSpawnMythicMob.this.maxCount = value.orElse(FunctionSpawnMythicMob.this.maxCount);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet amount of mobs to '&6" + FunctionSpawnMythicMob.this.maxCount + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lSpawn Delay");
            this.button.setAmount(FunctionSpawnMythicMob.this.delay);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow long should we wait before spawning mobs in ticks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionSpawnMythicMob.this.delay = value.orElse(FunctionSpawnMythicMob.this.delay);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet delay to '&6" + FunctionSpawnMythicMob.this.delay + "&a' ticks."));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CLOCK);
            this.button.setDisplayName("&d&lSpawn Interval");
            this.button.setAmount(FunctionSpawnMythicMob.this.interval);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&eHow long should the delay between spawns be in ticks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionSpawnMythicMob.this.interval = value.orElse(FunctionSpawnMythicMob.this.interval);
            if (value.isPresent()) {
               player.sendMessage(Util.colorize(MythicDungeons.debugPrefix + "&aSet interval to '&6" + FunctionSpawnMythicMob.this.interval + "&a' ticks."));
            }
         }
      });
      this.menu
         .addMenuItem(
            new MenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.COMPASS);
                  this.button.setDisplayName("&d&lMob Direction");
               }

               @Override
               public void onSelect(PlayerEvent event) {
                  Player player = event.getPlayer();
                  FunctionSpawnMythicMob.this.yaw = MathUtils.round(player.getLocation().getYaw(), 0);
                  player.sendMessage(
                     Util.colorize(
                        MythicDungeons.debugPrefix
                           + "&aSet mob spawn direction to '&6"
                           + FunctionSpawnMythicMob.this.yaw
                           + "&a' degrees. (Where you're looking.)"
                     )
                  );
               }
            }
         );
   }

   public String getMob() {
      return this.mob;
   }

   public void setMob(String mob) {
      this.mob = mob;
   }

   public String getLevelString() {
      return this.levelString;
   }

   public void setLevelString(String levelString) {
      this.levelString = levelString;
   }

   public int getMaxCount() {
      return this.maxCount;
   }

   public void setMaxCount(int maxCount) {
      this.maxCount = maxCount;
   }

   public int getDelay() {
      return this.delay;
   }

   public void setDelay(int delay) {
      this.delay = delay;
   }

   public int getInterval() {
      return this.interval;
   }

   public void setInterval(int interval) {
      this.interval = interval;
   }

   public double getYaw() {
      return this.yaw;
   }

   public void setYaw(double yaw) {
      this.yaw = yaw;
   }
}
