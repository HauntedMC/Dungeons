package nl.hauntedmc.dungeons.runtime.staff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.GameMode;
import org.bukkit.event.Event;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffObserverListenerTest {
    @TempDir Path directory;

    @Test
    void observerVictimsAndSourcesAreBothProtectedWithoutBlockingUnrelatedEvents()
            throws Exception {
        try (var f = new StaffFixture(directory)) {
            var observer = f.player("Observer");
            var normal = f.player("Normal");
            var session = f.runtime.playerSessions().get(observer.player);
            session.beginObservation(f.run.run);
            var listener = new StaffObserverListener(f.runtime);
            var hit = TestEvents.cancellable(EntityDamageByEntityEvent.class);
            when(hit.getEntity()).thenReturn(observer.player);
            when(hit.getDamager()).thenReturn(normal.player);
            assertTrue(StaffObserverListener.isObserverEvent(hit));
            listener.damage(hit);
            assertTrue(hit.isCancelled());
            when(hit.getEntity()).thenReturn(normal.player);
            when(hit.getDamager()).thenReturn(observer.player);
            assertTrue(StaffObserverListener.isObserverEvent(hit));
            when(hit.getDamager()).thenReturn(normal.player);
            assertFalse(StaffObserverListener.isObserverEvent(hit));
            assertFalse(StaffObserverListener.isObserverEvent(mock(Event.class)));
            var asynchronous = mock(PlayerEvent.class);
            when(asynchronous.getPlayer()).thenReturn(observer.player);
            when(asynchronous.isAsynchronous()).thenReturn(true);
            assertFalse(StaffObserverListener.isObserverEvent(asynchronous));
        }
    }

    @Test
    void modeChangesWorldDepartureRespawnAndDisconnectReleaseObservationThroughRecovery()
            throws Exception {
        try (var f = new StaffFixture(directory)) {
            var observer = f.player("Observer");
            var session = f.runtime.playerSessions().get(observer.player);
            var listener = new StaffObserverListener(f.runtime);
            session.beginObservation(f.run.run);
            var mode = TestEvents.cancellable(PlayerGameModeChangeEvent.class);
            when(mode.getPlayer()).thenReturn(observer.player);
            when(mode.getNewGameMode()).thenReturn(GameMode.CREATIVE);
            listener.mode(mode);
            assertTrue(mode.isCancelled());
            var valid = TestEvents.cancellable(PlayerGameModeChangeEvent.class);
            when(valid.getPlayer()).thenReturn(observer.player);
            when(valid.getNewGameMode()).thenReturn(GameMode.SPECTATOR);
            listener.mode(valid);
            assertFalse(valid.isCancelled());
            var changed = mock(PlayerChangedWorldEvent.class);
            when(changed.getPlayer()).thenReturn(observer.player);
            listener.leave(changed);
            assertFalse(session.isObserving());
            session.beginObservation(f.run.run);
            var respawn = mock(PlayerRespawnEvent.class);
            when(respawn.getPlayer()).thenReturn(observer.player);
            listener.respawn(respawn);
            f.flush();
            assertFalse(session.isObserving());
            session.beginObservation(f.run.run);
            var quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(observer.player);
            listener.quit(quit);
            assertFalse(session.isObserving());
            listener.quit(quit);
        }
    }

    @Test
    void revokedObservationPermissionsRestoreTheObserverOnTheNextTick() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var observer = f.player("Observer");
            f.run.run.completeInitialization(false);
            f.ops.observe(observer.player, f.run.run);
            when(observer.player.hasPermission("dungeons.staff.observe")).thenReturn(false);
            when(observer.player.hasPermission("dungeons.staff.observe.crypt")).thenReturn(false);
            f.ops.start();
            f.tick();
            assertFalse(f.runtime.playerSessions().get(observer.player).isObserving());
        }
    }
}
