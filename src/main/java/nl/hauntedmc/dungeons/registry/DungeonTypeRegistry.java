package nl.hauntedmc.dungeons.registry;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Static registry for known dungeon model types.
 */
public final class DungeonTypeRegistry {
    private static final Map<String, Class<? extends DungeonDefinition>> types = new LinkedHashMap<>();

    /** Returns a snapshot of registered dungeon type aliases to classes. */
    public static Map<String, Class<? extends DungeonDefinition>> getDungeonTypes() {
        return new LinkedHashMap<>(types);
    }

    /** Registers one dungeon type class under a primary name and optional aliases. */
    public static <T extends DungeonDefinition> void register(Class<T> dungeonType, String name, String... aliases) {
        types.put(name.toLowerCase(), dungeonType);

        for (String alias : aliases) {
            types.put(alias.toLowerCase(), dungeonType);
        }
    }

    /** Clears all registered dungeon type mappings. */
    public static void clear() {
        types.clear();
    }

    /**
     * Instantiates a dungeon from disk by reading its type from {@code config.yml} when present.
     */
    public static DungeonDefinition createDungeon(DungeonsRuntime runtime, File folder)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException,
            IOException, InvalidConfigurationException {
        File configFile = new File(folder, "config.yml");
        if (configFile.exists()) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(configFile);
            return createDungeon(runtime, config.getString("dungeon.type", "static"), folder, config);
        } else {
            return createDungeon(runtime, "static", folder, null);
        }
    }

    /**
     * Instantiates a dungeon model using a specific registered type alias.
     */
    public static DungeonDefinition createDungeon(DungeonsRuntime runtime, String type, File folder,
            YamlConfiguration config)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<? extends DungeonDefinition> typeClass = types.get(type.toLowerCase());
        return typeClass == null
                ? null
                : typeClass.getDeclaredConstructor(DungeonsRuntime.class, File.class, YamlConfiguration.class)
                        .newInstance(runtime, folder, config);
    }
}
