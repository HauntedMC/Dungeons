package nl.hauntedmc.dungeons.gui.framework;

import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindowRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Small facade for opening registered GUI windows.
 */
public class GuiService {
    private static JavaPlugin plugin;

    /** Stores the plugin context used by GUI window registration/opening. */
    public GuiService(JavaPlugin plugin) {
        GuiService.plugin = plugin;
    }

    /** Returns the plugin instance backing the GUI framework. */
    public static JavaPlugin plugin() {
        return plugin;
    }

    /** Opens a registered GUI window for a player by namespace. */
    public void openGui(Player player, String windowNamespace) {
        GuiWindow window = GuiWindowRegistry.getWindow(windowNamespace);
        if (window != null) {
            window.open(player);
        }
    }
}
