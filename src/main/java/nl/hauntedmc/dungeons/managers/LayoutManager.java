package nl.hauntedmc.dungeons.managers;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.api.generation.layout.Layout;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonProcedural;
import org.bukkit.configuration.file.YamlConfiguration;

public final class LayoutManager {
   private static final Map<String, Class<? extends Layout>> layouts = new HashMap<>();

   public static <T extends Layout> void register(Class<T> layoutType, String name, String... aliases) {
      layouts.put(name.toLowerCase(), layoutType);

      for (String alias : aliases) {
         layouts.put(alias.toLowerCase(), layoutType);
      }
   }

   public static void clear() {
      layouts.clear();
   }

   public static Layout createLayoutInstance(String type, DungeonProcedural dungeon, YamlConfiguration config) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
      Class<? extends Layout> typeClass = layouts.get(type.toLowerCase());
      return typeClass == null ? null : typeClass.getDeclaredConstructor(DungeonProcedural.class, YamlConfiguration.class).newInstance(dungeon, config);
   }
}
