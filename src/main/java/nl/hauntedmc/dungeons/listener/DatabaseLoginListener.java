package nl.hauntedmc.dungeons.listener;

import nl.hauntedmc.dungeons.persistence.DatabaseSettings;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.concurrent.TimeUnit;

/** Warm immutable player snapshots off the server thread and reject unavailable persistence. */
public final class DatabaseLoginListener implements Listener {
    private final DungeonsRuntime runtime;
    private final int timeoutMillis;
    public DatabaseLoginListener(DungeonsRuntime runtime) {
        this.runtime = runtime;
        this.timeoutMillis = DatabaseSettings.read(runtime.config()).operationTimeoutMillis();
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            if (!runtime.database().ready()) throw new IllegalStateException("Database unavailable");
            runtime.stateStore().prefetch(event.getUniqueId()).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, LangUtils.notice("Player data is unavailable; reconnect shortly."));
        } catch (Exception failure) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, LangUtils.notice("Player data is unavailable; reconnect shortly."));
        }
    }
}
