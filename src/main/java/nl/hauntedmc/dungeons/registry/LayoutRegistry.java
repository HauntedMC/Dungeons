package nl.hauntedmc.dungeons.registry;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.layout.Layout;

import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Static registry for branching layout generator implementations. */
public final class LayoutRegistry {
    private static final Map<String, Class<? extends Layout>> layouts = new HashMap<>();

    /** Registers one layout type class under a canonical name. */
    public static <T extends Layout> void register(Class<T> layoutType, String name) {
        layouts.put(name.toLowerCase(Locale.ROOT), layoutType);
    }

    /** Clears all registered layout mappings. */
    public static void clear() {
        layouts.clear();
    }

    /** Creates a layout instance from the registered type name and generator config. */
    public static Layout createLayoutInstance(
            String type, BranchingDungeon dungeon, FileConfiguration config)
            throws NoSuchMethodException,
                    InvocationTargetException,
                    InstantiationException,
                    IllegalAccessException {
        Class<? extends Layout> typeClass = layouts.get(type.toLowerCase(Locale.ROOT));
        return typeClass == null
                ? null
                : typeClass
                        .getDeclaredConstructor(BranchingDungeon.class, FileConfiguration.class)
                        .newInstance(dungeon, config);
    }
}
