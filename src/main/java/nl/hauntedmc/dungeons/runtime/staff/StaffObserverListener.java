package nl.hauntedmc.dungeons.runtime.staff;

import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.*;

/**
 * Observation is tracked independently of gameplay membership and ends through durable recovery.
 */
public final class StaffObserverListener implements Listener {
    private final DungeonsRuntime runtime;

    public StaffObserverListener(DungeonsRuntime runtime) {
        this.runtime = runtime;
    }

    public static boolean isObserverEvent(Event event) {
        if (event.isAsynchronous()) return false;
        Player player = null;
        if (event instanceof PlayerEvent playerEvent) player = playerEvent.getPlayer();
        else if (event instanceof EntityEvent entityEvent
                && entityEvent.getEntity() instanceof Player target) player = target;
        if (observing(player)) return true;
        return event instanceof EntityDamageByEntityEvent damage
                && damage.getDamager() instanceof Player source
                && observing(source);
    }

    private static boolean observing(Player player) {
        if (player == null) return false;
        var session = RuntimeContext.playerSessions().get(player);
        return session != null && session.isObserving();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quit(PlayerQuitEvent event) {
        var session = runtime.playerSessions().get(event.getPlayer());
        if (session != null && session.isObserving()) session.stopObservation();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void leave(PlayerChangedWorldEvent event) {
        var session = runtime.playerSessions().get(event.getPlayer());
        if (session != null
                && session.isObserving()
                && session.getObservingInstance().getInstanceWorld()
                        != event.getPlayer().getWorld()) session.stopObservation();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mode(PlayerGameModeChangeEvent event) {
        var session = runtime.playerSessions().get(event.getPlayer());
        if (session != null
                && session.isObserving()
                && event.getNewGameMode() != GameMode.SPECTATOR) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void respawn(PlayerRespawnEvent event) {
        var session = runtime.playerSessions().get(event.getPlayer());
        if (session != null && session.isObserving())
            Bukkit.getScheduler()
                    .runTask(
                            runtime.environment().plugin(),
                            () -> {
                                if (!session.isExpired()
                                        && session.getPlayer() == event.getPlayer())
                                    session.stopObservation();
                            });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (isObserverEvent(event)) event.setCancelled(true);
    }
}
