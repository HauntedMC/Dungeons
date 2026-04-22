package nl.hauntedmc.dungeons.bootstrap;

import nl.hauntedmc.dungeons.listener.DungeonListener;
import nl.hauntedmc.dungeons.listener.HotbarMenuListener;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import org.bukkit.Bukkit;

/**
 * Registers Bukkit event listeners that are part of the plugin's core runtime.
 */
final class ListenerBootstrap {
    private final DungeonsRuntime runtime;

    /**
     * Creates the listener bootstrap stage.
     */
    ListenerBootstrap(DungeonsRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Registers long-lived listeners once their required managers have been initialized.
     */
    void registerCoreListeners() {
        DungeonsPlugin plugin = this.runtime.environment().plugin();
        Bukkit.getPluginManager()
                .registerEvents(
                        new DungeonListener(
                                plugin,
                                this.runtime.playerSessions(),
                                this.runtime.queueRegistry(),
                                this.runtime.dungeonCatalog(),
                                this.runtime.guiService()),
                        plugin);
        Bukkit.getPluginManager()
                .registerEvents(new HotbarMenuListener(plugin, this.runtime.playerSessions()), plugin);
        Bukkit.getPluginManager().registerEvents(this.runtime.queueRegistry(), plugin);
        Bukkit.getPluginManager().registerEvents(this.runtime.teamService(), plugin);
    }
}
