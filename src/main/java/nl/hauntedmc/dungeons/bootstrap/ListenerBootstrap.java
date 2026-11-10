package nl.hauntedmc.dungeons.bootstrap;

import nl.hauntedmc.dungeons.integration.ProfileListener;
import nl.hauntedmc.dungeons.listener.DatabaseLoginListener;
import nl.hauntedmc.dungeons.listener.DungeonListener;
import nl.hauntedmc.dungeons.listener.HotbarMenuListener;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

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
        register(new DatabaseLoginListener(this.runtime));
        register(new ProfileListener(this.runtime));
        register(this.runtime.playerSessions());
        this.runtime.playerSessions().start(this.runtime);
        register(
                new DungeonListener(
                        plugin,
                        this.runtime.playerSessions(),
                        this.runtime.queueRegistry(),
                        this.runtime.dungeonCatalog(),
                        this.runtime.guiService()));
        register(new HotbarMenuListener(plugin, this.runtime.playerSessions()));
        register(this.runtime.queueRegistry());
        register(this.runtime.teamService());
        register(this.runtime.lootTableEditor());
    }
    private void register(Listener listener) {
        this.runtime.resources().ownIngress(listener, HandlerList::unregisterAll);
        Bukkit.getPluginManager().registerEvents(listener, this.runtime.environment().plugin());
    }

}
