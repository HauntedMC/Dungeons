package nl.hauntedmc.dungeons.runtime;

import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import org.bukkit.NamespacedKey;
import org.slf4j.Logger;

/**
 * Small adapter around the Bukkit plugin instance.
 *
 * <p>This keeps low-level plugin APIs grouped together so runtime and domain code do not need to
 * know about the whole {@link DungeonsPlugin} surface.
 */
public final class PluginEnvironment {
    private final DungeonsPlugin plugin;

    /**
     * Creates a new plugin environment instance.
     */
    PluginEnvironment(DungeonsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Returns the underlying Bukkit plugin instance when direct integration is required. */
    public DungeonsPlugin plugin() {
        return this.plugin;
    }

    /** Returns the plugin SLF4J logger. */
    public Logger logger() {
        return this.plugin.getSLF4JLogger();
    }

    /** Returns the clean plugin version without any build metadata suffix. */
    public String version() {
        return this.plugin.getPluginMeta().getVersion().split("-")[0];
    }

    /** Returns whether Bukkit still considers the plugin enabled. */
    public boolean isEnabled() {
        return this.plugin.isEnabled();
    }

    /** Creates a plugin-scoped namespaced key. */
    public NamespacedKey key(String key) {
        return new NamespacedKey(this.plugin, key);
    }
}
