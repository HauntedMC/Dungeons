package nl.hauntedmc.dungeons.integration;

import java.util.Objects;
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
    private final RegistryProfiles profiles;

    public ProfileListener(RegistryProfiles profiles) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        profiles.refresh(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        profiles.forget(event.getPlayer().getUniqueId());
    }
}
