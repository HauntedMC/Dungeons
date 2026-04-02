package nl.hauntedmc.dungeons.managers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

public final class DungeonTypeManager {
   private static final Map<String, Class<? extends AbstractDungeon>> types = new LinkedHashMap<>();

   public static Map<String, Class<? extends AbstractDungeon>> getDungeonTypes() {
      return new LinkedHashMap<>(types);
   }

   public static <T extends AbstractDungeon> void register(Class<T> dungeonType, String name, String... aliases) {
      types.put(name.toLowerCase(), dungeonType);

      for (String alias : aliases) {
         types.put(alias.toLowerCase(), dungeonType);
      }
   }

   public static void clear() {
      types.clear();
   }

   public static AbstractDungeon createDungeon(File folder) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException, IOException, InvalidConfigurationException {
      File configFile = new File(folder, "config.yml");
      if (configFile.exists()) {
         YamlConfiguration config = new YamlConfiguration();
         config.load(configFile);
         return createDungeon(config.getString("General.DungeonType", "classic"), folder, config);
      } else {
         return createDungeon("classic", folder, null);
      }
   }

   public static AbstractDungeon createDungeon(String type, File folder, YamlConfiguration config) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
      Class<? extends AbstractDungeon> typeClass = types.get(type.toLowerCase());
      return typeClass == null ? null : typeClass.getDeclaredConstructor(File.class, YamlConfiguration.class).newInstance(folder, config);
   }
}
