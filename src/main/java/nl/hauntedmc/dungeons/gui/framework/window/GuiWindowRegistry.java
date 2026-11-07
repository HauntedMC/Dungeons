package nl.hauntedmc.dungeons.gui.framework.window;

import java.util.HashMap;

/**
 * Registry of inventory GUI windows by namespace.
 */
public final class GuiWindowRegistry {
    private static final HashMap<String, GuiWindow> windows = new HashMap<>();

    /** Utility class. */
    private GuiWindowRegistry() {}

    /** Returns a registered GUI window by namespace, or null when absent. */
    public static GuiWindow getWindow(String namespace) {
        return windows.get(namespace);
    }

    /** Removes this exact registration without affecting a newer replacement. */
    static void unregister(GuiWindow window) {
        windows.remove(window.getNamespace(), window);
    }

    /** Registers or replaces a GUI window for its namespace. */
    static void register(GuiWindow window) {
        windows.put(window.getNamespace(), window);
    }
}
