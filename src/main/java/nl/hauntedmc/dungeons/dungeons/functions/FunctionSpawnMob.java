package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.generation.rooms.InstanceRoom;
import nl.hauntedmc.dungeons.api.parents.DungeonDifficulty;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceProcedural;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
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
public class FunctionSpawnMob extends DungeonFunction {
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
   private final List<BukkitRunnable> spawners;

   public FunctionSpawnMob(Map<String, Object> config) {
      super("Mob Spawner", config);
      this.setAllowChangingTargetType(false);
      this.setCategory(FunctionCategory.LOCATION);
      this.spawners = new ArrayList<>();
   }

   public FunctionSpawnMob() {
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
      if (config.containsKey("Mob")) {
         this.mob = (String)config.get("Mob");
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      final InstancePlayable instance = this.instance.asPlayInstance();
      if (instance != null) {
         double healthMod = 1.0;
         int maxCountModded = this.maxCount;
         double damageMod = 1.0;
         if (instance.getDungeon().isUseDifficultyLevels() && instance.getDifficulty() != null) {
            DungeonDifficulty difficulty = instance.getDifficulty();
            healthMod = difficulty.getMobHealthScale();
            maxCountModded = (int)(maxCountModded * difficulty.getMobSpawnScale());
            damageMod = difficulty.getMobDamageScale();
         }

         final int finalMaxCountMod = maxCountModded;
         final double finalDamageMod = damageMod;
         double finalHealthMod = healthMod;
         BukkitRunnable spawner = new BukkitRunnable() {
            public void run() {
               if (FunctionSpawnMob.this.count >= finalMaxCountMod) {
                  this.cancel();
                  if (FunctionSpawnMob.this.trigger != null && FunctionSpawnMob.this.trigger.isAllowRetrigger()) {
                     FunctionSpawnMob.this.count = 0;
                  }
               } else {
                  Location spawnPoint = FunctionSpawnMob.this.location.clone();
                  spawnPoint.setX(spawnPoint.getX() + 0.5);
                  spawnPoint.setY(spawnPoint.getY() + 0.1);
                  spawnPoint.setZ(spawnPoint.getZ() + 0.5);
                  spawnPoint.setYaw((float) FunctionSpawnMob.this.yaw);
                  if (spawnPoint.getBlock().isSolid()) {
                     spawnPoint.setY(spawnPoint.getY() + 1.0);
                  }

                  EntityType type = null;

                  try {
                     type = EntityType.valueOf(FunctionSpawnMob.this.mob.toUpperCase(Locale.ROOT));
                  } catch (IllegalArgumentException ignored) {
                  }

                  if (type == null) {
                     type = EntityType.ZOMBIE;
                  }

                  if (instance.getInstanceWorld().spawnEntity(spawnPoint, type) instanceof LivingEntity living) {
                     HelperUtils.setMaxHealth(living, HelperUtils.getMaxHealth(living) * finalHealthMod);
                     living.setHealth(HelperUtils.getMaxHealth(living));
                     FunctionSpawnMob.this.applyRoomTag(living);
                     AttributeInstance attrib = living.getAttribute(Attribute.ATTACK_DAMAGE);
                     if (attrib != null) {
                        living.getAttribute(Attribute.ATTACK_DAMAGE)
                           .addModifier(new AttributeModifier(new NamespacedKey(Dungeons.inst(), "dungeon_level"), finalDamageMod - 1.0, Operation.MULTIPLY_SCALAR_1));
                     }
                  }

                  FunctionSpawnMob.this.count++;
               }
            }
         };
         spawner.runTaskTimer(Dungeons.inst(), this.delay, Math.max(this.interval, 1));
         this.spawners.add(spawner);
      }
   }

   private void applyRoomTag(Entity ent) {
      InstanceProcedural inst = this.instance.as(InstanceProcedural.class);
      if (inst != null) {
         InstanceRoom room = inst.getRoom(this.location);
         if (room != null) {
            ent.getPersistentDataContainer().set(new NamespacedKey(Dungeons.inst(), "originroom"), PersistentDataType.STRING, room.getUuid().toString());
         }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.CREEPER_HEAD);
      functionButton.setDisplayName("&dMob Spawner");
      functionButton.addLore("&eSpawns mobs at this location.");
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
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat's the type of mob?"));
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent mob is: &6" + FunctionSpawnMob.this.mob));
         }

         @Override
         public void onInput(Player player, String message) {
            FunctionSpawnMob.this.mob = message;
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet mob to '&6" + message + "&a'"));
            FunctionSpawnMob.this.setDisplayName(FunctionSpawnMob.this.mob + " Spawner");
         }
      });

      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BONE);
            this.button.setDisplayName("&d&lAmount");
            this.button.setAmount(FunctionSpawnMob.this.maxCount);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow many of this mob will spawn from here?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionSpawnMob.this.maxCount = value.orElse(FunctionSpawnMob.this.maxCount);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet amount of mobs to '&6" + FunctionSpawnMob.this.maxCount + "&a'"));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.REPEATER);
            this.button.setDisplayName("&d&lSpawn Delay");
            this.button.setAmount(FunctionSpawnMob.this.delay);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow long should we wait before spawning mobs in ticks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionSpawnMob.this.delay = value.orElse(FunctionSpawnMob.this.delay);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet delay to '&6" + FunctionSpawnMob.this.delay + "&a' ticks."));
            }
         }
      });
      this.menu.addMenuItem(new ChatMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.CLOCK);
            this.button.setDisplayName("&d&lSpawn Interval");
            this.button.setAmount(FunctionSpawnMob.this.interval);
         }

         @Override
         public void onSelect(Player player) {
            player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eHow long should the delay between spawns be in ticks?"));
         }

         @Override
         public void onInput(Player player, String message) {
            Optional<Integer> value = StringUtils.readIntegerInput(player, message);
            FunctionSpawnMob.this.interval = value.orElse(FunctionSpawnMob.this.interval);
            if (value.isPresent()) {
               player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&aSet interval to '&6" + FunctionSpawnMob.this.interval + "&a' ticks."));
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
                  FunctionSpawnMob.this.yaw = MathUtils.round(player.getLocation().getYaw(), 0);
                  player.sendMessage(
                     HelperUtils.colorize(
                        Dungeons.logPrefix
                           + "&aSet mob spawn direction to '&6"
                           + FunctionSpawnMob.this.yaw
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
