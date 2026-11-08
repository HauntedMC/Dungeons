package nl.hauntedmc.dungeons.registry;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Static registry for known dungeon model types. */
public final class DungeonTypeRegistry {
    private static final Map<String, Class<? extends DungeonDefinition>> types =
            new LinkedHashMap<>();

    /** Returns a snapshot of registered dungeon type names to classes. */
    public static Map<String, Class<? extends DungeonDefinition>> getDungeonTypes() {
        return new LinkedHashMap<>(types);
    }

    /** Registers one dungeon type class under a canonical name. */
    public static <T extends DungeonDefinition> void register(Class<T> dungeonType, String name) {
        types.put(name.toLowerCase(Locale.ROOT), dungeonType);
    }

    /** Clears all registered dungeon type mappings. */
    public static void clear() {
        types.clear();
    }

    /**
     * Instantiates a dungeon from disk by reading its type from {@code config.yml} when present.
     */
    public static DungeonDefinition createDungeon(DungeonsRuntime runtime, File folder)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException,
                    IOException,
                    InvalidConfigurationException {
        File configFile = new File(folder, "config.yml");
        if (configFile.exists()) {
            YamlConfiguration config = new YamlConfiguration();
            config.load(configFile);
            return createDungeon(
                    runtime, config.getString("dungeon.type", "static"), folder, config);
        }
        return createDungeon(runtime, "static", folder, null);
    }

    /** Instantiates a dungeon model using a specific registered type name. */
    public static DungeonDefinition createDungeon(
            DungeonsRuntime runtime, String type, File folder, YamlConfiguration config)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        Class<? extends DungeonDefinition> typeClass = types.get(type.toLowerCase(Locale.ROOT));
        return typeClass == null
                ? null
                : typeClass
                        .getDeclaredConstructor(
                                DungeonsRuntime.class, File.class, YamlConfiguration.class)
                        .newInstance(runtime, folder, config);
    }
}
