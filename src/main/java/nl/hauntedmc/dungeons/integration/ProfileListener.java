package nl.hauntedmc.dungeons.integration;

import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Language resolution begins after DataRegistry's LOWEST join listener starts identity
 * initialization.
 */
public final class ProfileListener implements Listener {
    private final DungeonsRuntime runtime;

    public ProfileListener(DungeonsRuntime runtime) {
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        runtime.profiles().refresh(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        runtime.profiles().forget(event.getPlayer().getUniqueId());
    }
}
