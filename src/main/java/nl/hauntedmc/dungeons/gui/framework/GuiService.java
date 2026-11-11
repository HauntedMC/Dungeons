package nl.hauntedmc.dungeons.gui.framework;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns inventory window definitions and listener registrations for one plugin lifecycle. */
public final class GuiService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Map<String, GuiWindow> windows = new LinkedHashMap<>();
    private boolean closed;

    public GuiService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
    }

    public JavaPlugin plugin() {
        return this.plugin;
    }

    public GuiWindow createWindow(String namespace, int size, String title) {
        if (this.closed) throw new IllegalStateException("GUI service has been closed");
        GuiWindow window = new GuiWindow(this, namespace, size, title);
        GuiWindow previous = this.windows.put(namespace, window);
        if (previous != null) previous.unregister();
        return window;
    }

    public GuiWindow getWindow(String namespace) {
        return this.windows.get(namespace);
    }

    /** Removes only this window; stale callbacks cannot unregister a replacement. */
    public void unregister(GuiWindow window) {
        this.windows.remove(window.getNamespace(), window);
    }

    public void openGui(Player player, String windowNamespace) {
        GuiWindow window = this.getWindow(windowNamespace);
        if (window != null) window.open(player);
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        for (GuiWindow window : List.copyOf(this.windows.values())) window.unregister();
        this.windows.clear();
    }
}
