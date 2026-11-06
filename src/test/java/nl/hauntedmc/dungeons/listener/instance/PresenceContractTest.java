package nl.hauntedmc.dungeons.listener.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresenceContractTest {
    @TempDir Path directory;
    @Test
    void reconnectCancelsOfflineRemovalAndActualOfflineExpiryClearsMembershipAndItsTracker() throws Exception {
        var settings=new YamlConfiguration(); settings.set("players.offline_kick.enabled",true); settings.set("players.offline_kick.delay_seconds",3);
        try(var f=new RunFixture(directory,settings)) {
            var state=new PlayerState(); var session=f.session(state); f.run.addPlayer(session);
            var survivor=f.session(new PlayerState()); f.run.addPlayer(survivor);
            var listener=(PlayListener)f.run.getListener();
            var quit=mock(PlayerQuitEvent.class); when(quit.getPlayer()).thenReturn(state.player); state.online=false;
            listener.onPlayerDisconnectDungeon(quit); var first=f.scheduler.jobs.getLast(); assertEquals(60,first.delay);
            assertFalse(session.isDisconnecting()); assertTrue(f.run.getOfflineTrackers().containsKey(state.player.getUniqueId()));
            state.online=true; var join=mock(PlayerJoinEvent.class); when(join.getPlayer()).thenReturn(state.player);
            listener.onPlayerConnectDungeon(join); assertTrue(first.cancelled); first.run();
            assertSame(f.run,session.getInstance()); assertTrue(f.run.getOfflineTrackers().isEmpty());
            state.online=false; listener.onPlayerDisconnectDungeon(quit); f.scheduler.jobs.getLast().run();
            assertNull(session.getInstance()); assertEquals(java.util.List.of(survivor),f.run.getPlayers());
            assertSame(f.run,survivor.getInstance());
            assertTrue(f.run.getOfflineTrackers().isEmpty());
        }
    }
}
