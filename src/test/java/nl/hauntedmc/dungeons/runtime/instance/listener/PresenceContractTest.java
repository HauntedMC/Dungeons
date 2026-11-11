package nl.hauntedmc.dungeons.runtime.instance.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresenceContractTest {
    @TempDir Path directory;

    @Test
    void reconnectWithANewPlayerObjectCannotBeRemovedByTheOldConnectionsOfflineTask()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("players.offline_kick.enabled", true);
        settings.set("players.offline_kick.delay_seconds", 3);
        try (var f = new RunFixture(directory, settings)) {
            var original = new PlayerState();
            var session = f.session(original);
            f.run.addPlayer(session);
            var listener = (PlayListener) f.run.getListener();
            original.online = false;
            var quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(original.player);
            listener.onPlayerDisconnectDungeon(quit);
            var oldTracker = f.scheduler.jobs.getLast();
            var replacement = new PlayerState();
            var playerId = original.player.getUniqueId();
            when(replacement.player.getUniqueId()).thenReturn(playerId);
            var join = mock(PlayerJoinEvent.class);
            when(join.getPlayer()).thenReturn(replacement.player);
            f.paper.runtime.playerSessions().onJoin(join);
            listener.onPlayerConnectDungeon(join);
            assertTrue(oldTracker.cancelled);
            oldTracker.cancelled = false; // simulate a callback already dequeued at cancellation
            oldTracker.run();
            assertSame(replacement.player, session.getPlayer());
            assertSame(f.run, session.getInstance());
            assertTrue(f.run.getPlayers().contains(session));
            f.run.removePlayer(session);
            f.paper.runtime.playerSessions().shutdown();
            assertTrue(session.isExpired());
            Assertions.assertDoesNotThrow(oldTracker::run);
        }
    }

    @Test
    void reconnectCancelsOfflineRemovalAndActualOfflineExpiryClearsMembershipAndItsTracker()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("players.offline_kick.enabled", true);
        settings.set("players.offline_kick.delay_seconds", 3);
        try (var f = new RunFixture(directory, settings)) {
            var state = new PlayerState();
            var session = f.session(state);
            f.run.addPlayer(session);
            var survivor = f.session(new PlayerState());
            f.run.addPlayer(survivor);
            var listener = (PlayListener) f.run.getListener();
            var quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(state.player);
            state.online = false;
            listener.onPlayerDisconnectDungeon(quit);
            var first = f.scheduler.jobs.getLast();
            assertEquals(60, first.delay);
            assertFalse(session.isDisconnecting());
            assertTrue(f.run.getOfflineTrackers().containsKey(state.player.getUniqueId()));
            state.online = true;
            var join = mock(PlayerJoinEvent.class);
            when(join.getPlayer()).thenReturn(state.player);
            listener.onPlayerConnectDungeon(join);
            assertTrue(first.cancelled);
            first.run();
            assertSame(f.run, session.getInstance());
            assertTrue(f.run.getOfflineTrackers().isEmpty());
            state.online = false;
            listener.onPlayerDisconnectDungeon(quit);
            f.scheduler.jobs.getLast().run();
            assertNull(session.getInstance());
            assertEquals(List.of(survivor), f.run.getPlayers());
            assertSame(f.run, survivor.getInstance());
            assertTrue(f.run.getOfflineTrackers().isEmpty());
        }
    }

    @Test
    void worldEntryRequiresMembershipOrExplicitBypassAndUnauthorizedArrivalIsEjected()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            var session = f.session(state);
            var listener = f.run.getListener();
            var from = state.location.clone();
            var to = new Location(f.world, 0, 70, 0);
            var event = TestEvents.cancellable(PlayerTeleportEvent.class);
            when(event.getPlayer()).thenReturn(state.player);
            when(event.getFrom()).thenReturn(from);
            when(event.getTo()).thenReturn(to);
            listener.onTeleportToDungeon(event);
            assertTrue(event.isCancelled());
            event.setCancelled(false);
            when(state.player.hasPermission("dungeons.bypassjoin")).thenReturn(true);
            listener.onTeleportToDungeon(event);
            assertFalse(event.isCancelled());
            assertTrue(f.run.getPlayers().isEmpty());
            assertNull(session.getInstance());
            when(state.player.hasPermission("dungeons.bypassjoin")).thenReturn(false);
            state.location = to;
            when(state.player.getRespawnLocation()).thenReturn(from);
            var arrival = mock(PlayerChangedWorldEvent.class);
            when(arrival.getPlayer()).thenReturn(state.player);
            when(arrival.getFrom()).thenReturn(state.originWorld);
            listener.onPlayerEnterDungeonWorld(arrival);
            assertEquals(from, state.location);
            assertTrue(f.run.getPlayers().isEmpty());
        }
    }
}
