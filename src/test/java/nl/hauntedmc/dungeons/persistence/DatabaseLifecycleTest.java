package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DatabaseLifecycleTest {
    static final class Fixture {
        final DungeonsPlugin plugin = mock(DungeonsPlugin.class);
        final DungeonDatabase database = mock(DungeonDatabase.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final BukkitTask heartbeat = mock(BukkitTask.class);
        final PluginManager plugins = mock(PluginManager.class);
        Fixture() {
            var server = mock(Server.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getScheduler()).thenReturn(scheduler);
            when(server.getPluginManager()).thenReturn(plugins);
            when(plugin.getSLF4JLogger()).thenReturn(mock(org.slf4j.Logger.class));
            when(scheduler.runTaskTimerAsynchronously(eq(plugin), any(Runnable.class), eq(100L), eq(100L)))
                    .thenReturn(heartbeat);
            when(database.start()).thenReturn(CompletableFuture.completedFuture(null));
            when(database.closeAsync()).thenReturn(CompletableFuture.completedFuture(null));
        }
    }

    @Test void heartbeatIsGatedByReadinessAndCloseCancelsItExactlyOnce() {
        var f = new Fixture();
        var lifecycle = new DatabaseLifecycle(f.plugin, f.database, 1);
        var work = ArgumentCaptor.forClass(Runnable.class);
        verify(f.scheduler).runTaskTimerAsynchronously(eq(f.plugin), work.capture(), eq(100L), eq(100L));
        when(f.database.state()).thenReturn(DungeonDatabase.State.STARTING);
        work.getValue().run();
        verify(f.database, never()).heartbeat();
        when(f.database.state()).thenReturn(DungeonDatabase.State.READY);
        work.getValue().run();
        verify(f.database).heartbeat();
        lifecycle.close();
        lifecycle.close();
        work.getValue().run();
        verify(f.database).heartbeat();
        verify(f.heartbeat).cancel();
        verify(f.database).closeAsync();
    }

    @Test @SuppressWarnings("unchecked")
    void failureDisablesOnTheServerThreadAndStaleCallbacksCannotDisableAfterClose() {
        var f = new Fixture();
        var lifecycle = new DatabaseLifecycle(f.plugin, f.database, 1);
        ArgumentCaptor<Consumer<Throwable>> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(f.database).onFailure(failure.capture());
        failure.getValue().accept(new IllegalStateException("lease lost"));
        verify(f.plugins, never()).disablePlugin(any());
        var disable = ArgumentCaptor.forClass(Runnable.class);
        verify(f.scheduler).runTask(eq(f.plugin), disable.capture());
        disable.getValue().run();
        verify(f.plugins).disablePlugin(f.plugin);
        lifecycle.close();
        clearInvocations(f.plugins);
        disable.getValue().run();
        failure.getValue().accept(new IllegalStateException("stale"));
        verifyNoInteractions(f.plugins);
    }

    @Test void schedulingFailureReleasesAnUnstartedDatabase() {
        var f = new Fixture();
        when(f.scheduler.runTaskTimerAsynchronously(eq(f.plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("scheduler stopped"));
        assertThrows(IllegalStateException.class, () -> new DatabaseLifecycle(f.plugin, f.database, 1));
        verify(f.database).closeAsync();
        verify(f.database, never()).start();
    }

    @Test void cancellationErrorsCannotSkipDatabaseCleanupOrReplaceStartupFailures() {
        var f = new Fixture();
        var lifecycle = new DatabaseLifecycle(f.plugin, f.database, 1);
        doThrow(new IllegalStateException("cancel failed")).when(f.heartbeat).cancel();
        assertDoesNotThrow(lifecycle::close);
        verify(f.database).closeAsync();

        var startup = new Fixture();
        var original = new IllegalStateException("start failed");
        when(startup.database.start()).thenThrow(original);
        doThrow(new IllegalStateException("cancel failed")).when(startup.heartbeat).cancel();
        assertSame(original, assertThrows(IllegalStateException.class,
                () -> new DatabaseLifecycle(startup.plugin, startup.database, 1)));
        assertEquals(1, original.getSuppressed().length);
        verify(startup.database).closeAsync();
    }

    @Test void shutdownIsBoundedAndDoesNotCancelTheCleanupWorker() {
        var f = new Fixture();
        var pending = new CompletableFuture<Void>();
        when(f.database.closeAsync()).thenReturn(pending);
        var lifecycle = new DatabaseLifecycle(f.plugin, f.database, 1);
        assertTimeout(java.time.Duration.ofSeconds(3), lifecycle::close);
        assertFalse(pending.isDone());
        verify(f.heartbeat).cancel();
        pending.complete(null);
    }
}
