package nl.hauntedmc.dungeons.util.version;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

public final class ReflectionUtils {
   public static final String CRAFTBUKKIT_PACKAGE = Bukkit.getServer().getClass().getPackage().getName();
   private static Class<?> dmgSrcClazz;
   private static Class<?> dmgTypeClazz;
   private static Class<?> holderClazz;
   private static Class<?> directClazz;
   private static Field vanillaPlayerField;
   private static Field trackerField;
   private static Field dmgSrcField;
   private static Field entField;
   private static Field pathfinderEntField;
   private static Method recordDamageMethod;
   private static boolean versionSupported = false;
   private static boolean entityForceCleanupSupported = false;

   public static void getAllFields(List<Field> fields, Class<?> type) {
      fields.addAll(Arrays.asList(type.getDeclaredFields()));
      if (type.getSuperclass() != null) {
         getAllFields(fields, type.getSuperclass());
      }
   }

   public static void getAnnotatedFields(List<Field> fields, Class<?> type, Class<? extends Annotation> annotation) {
      for (Field field : type.getDeclaredFields()) {
         if (field.getAnnotation(annotation) != null) {
            fields.add(field);
         }
      }

      if (type.getSuperclass() != null) {
         getAnnotatedFields(fields, type.getSuperclass(), annotation);
      }
   }

   public static void getAnnotatedMethods(List<Method> methods, Class<?> type, Class<? extends Annotation> annotation) {
      for (Method field : type.getDeclaredMethods()) {
         if (field.getAnnotation(annotation) != null) {
            methods.add(field);
         }
      }

      if (type.getSuperclass() != null) {
         getAnnotatedMethods(methods, type.getSuperclass(), annotation);
      }
   }

   public static Class<?> getNMSClass(String path) throws ClassNotFoundException {
      return Class.forName(path);
   }

   public static void prepMemoryLeakKiller() {
      try {
         String dmgSrcPath = "net.minecraft.world.damagesource.DamageSource";
         String dmgTypePath = "net.minecraft.world.damagesource.DamageType";
         String holderPath = "net.minecraft.core.Holder";
         String entityPath = "net.minecraft.world.entity.Entity";
         String playerPath = "net.minecraft.server.level.EntityPlayer";
         String trackerPath = "net.minecraft.world.damagesource.CombatTracker";
         dmgSrcClazz = getNMSClass(dmgSrcPath);
         dmgTypeClazz = getNMSClass(dmgTypePath);
         holderClazz = getNMSClass(holderPath);
         directClazz = holderClazz.getDeclaredClasses()[0];
         Class<?> entClazz = getNMSClass(entityPath);
         Class<?> cPlayerClazz = getNMSClass(CRAFTBUKKIT_PACKAGE + ".entity.CraftPlayer");
         List<Field> bukkitFields = new ArrayList<>();
         getAllFields(bukkitFields, cPlayerClazz);
         vanillaPlayerField = null;

         for (Field field : bukkitFields) {
            if (field.getType() == entClazz) {
               vanillaPlayerField = field;
            }
         }

         if (vanillaPlayerField == null) {
            return;
         }

         Class<?> clazz = getNMSClass(playerPath);
         Class<?> trackerClazz = getNMSClass(trackerPath);
         List<Field> entFields = new ArrayList<>();
         getAllFields(entFields, clazz);
         trackerField = null;
         dmgSrcField = null;

         for (Field fieldx : entFields) {
            if (fieldx.getType() == trackerClazz) {
               trackerField = fieldx;
            } else if (fieldx.getType() == dmgSrcClazz) {
               dmgSrcField = fieldx;
            }
         }

         List<Field> craftEntFields = new ArrayList<>();
         getAllFields(craftEntFields, getNMSClass(CRAFTBUKKIT_PACKAGE + ".entity.CraftEntity"));

         for (Field fieldxx : craftEntFields) {
            if (fieldxx.getType() == entClazz) {
               entField = fieldxx;
               entityForceCleanupSupported = true;
            }
         }

         List<Field> pathfinderFields = new ArrayList<>();
         getAllFields(pathfinderFields, getNMSClass("com.destroystokyo.paper.entity.PaperPathfinder"));
         Class<?> mobClazz = getNMSClass("net.minecraft.world.entity.Mob");

         for (Field fieldxxx : pathfinderFields) {
            if (fieldxxx.getType() == mobClazz) {
               pathfinderEntField = fieldxxx;
            }
         }

         if (trackerField == null) {
            return;
         }

         if (dmgSrcField == null) {
            return;
         }

         recordDamageMethod = null;

         for (Method method : trackerClazz.getMethods()) {
            if (method.getParameterCount() == 3) {
               Class<?>[] params = method.getParameterTypes();
               if (params[0] == dmgSrcClazz && params[1] == float.class) {
                  recordDamageMethod = method;
               }
            }
         }

         if (recordDamageMethod == null) {
            return;
         }

         versionSupported = true;
      } catch (Exception var18) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var18.getMessage());
      }

      if (!versionSupported) {
         Dungeons.inst()
            .getLogger()
            .info(Dungeons.logPrefix + HelperUtils.colorize("&cWARNING: Memory leak protection is limited on this Minecraft version!"));
         Dungeons.inst()
            .getLogger()
            .info(
               Dungeons.logPrefix
                  + HelperUtils.colorize(
                     "&c-- You should be fine, but if there are major memory leaks, submit an issue on the issue tracker and include the Minecraft version of your server!"
                  )
            );
      }
   }

   public static void updateCombatTracker(Player ent) {
      if (versionSupported) {
         try {
            vanillaPlayerField.setAccessible(true);
            Object entity = vanillaPlayerField.get(ent);
            vanillaPlayerField.setAccessible(false);
            Object tracker = trackerField.get(entity);
            Object dmgType = dmgTypeClazz.getDeclaredConstructor(String.class, float.class).newInstance("Memory Leak Killer", 0.0F);
            Object holder = directClazz.getDeclaredConstructors()[0].newInstance(dmgType);
            Object dmgSrc = dmgSrcClazz.getDeclaredConstructor(holderClazz).newInstance(holder);
            dmgSrcField.setAccessible(true);
            dmgSrcField.set(entity, dmgSrc);
            dmgSrcField.setAccessible(false);
            recordDamageMethod.invoke(tracker, dmgSrc, (float)ent.getHealth());
         } catch (Exception var6) {
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var6.getMessage());
         }
      }
   }

   public static void forcePurgeEntity(Entity ent) {
      if (entityForceCleanupSupported) {
         try {
            World world = Bukkit.getWorlds().getFirst();
            Entity newEnt = world.spawnEntity(new Location(world, 0.0, world.getMaxHeight() + 1000, 0.0), ent.getType());
            entField.setAccessible(true);
            Object newNMS = entField.get(newEnt);
            entField.set(ent, newNMS);
            entField.setAccessible(false);
            if (ent instanceof Mob living) {
               living.getPathfinder();
               pathfinderEntField.setAccessible(true);
               pathfinderEntField.set(living.getPathfinder(), newNMS);
               pathfinderEntField.setAccessible(false);
            }

            newEnt.remove();
         } catch (Exception var5) {
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var5.getMessage());
         }
      }
   }
}
