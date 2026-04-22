package nl.hauntedmc.dungeons.registry;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.layout.Layout;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Static registry for branching layout generator implementations.
 */
public final class LayoutRegistry {
    private static final Map<String, Class<? extends Layout>> layouts = new HashMap<>();

    /** Registers one layout type class under a primary name and optional aliases. */
    public static <T extends Layout> void register(Class<T> layoutType, String name, String... aliases) {
        layouts.put(name.toLowerCase(), layoutType);

        for (String alias : aliases) {
            layouts.put(alias.toLowerCase(), layoutType);
        }
    }

    /** Clears all registered layout mappings. */
    public static void clear() {
        layouts.clear();
    }

    /** Creates a layout instance from the registered type alias and generator config. */
    public static Layout createLayoutInstance(String type, BranchingDungeon dungeon, YamlConfiguration config)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<? extends Layout> typeClass = layouts.get(type.toLowerCase());
        return typeClass == null
                ? null
                : typeClass.getDeclaredConstructor(BranchingDungeon.class, YamlConfiguration.class)
                        .newInstance(dungeon, config);
    }
}
