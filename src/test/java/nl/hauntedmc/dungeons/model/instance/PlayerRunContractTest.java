package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerRunContractTest {
    @TempDir Path directory;
    static YamlConfiguration settings() {
        var settings=new YamlConfiguration();
        settings.set("players.keep_on_entry.inventory",false);
        settings.set("players.keep_on_entry.experience",false);
        settings.set("players.keep_on_entry.health",true);
        settings.set("players.keep_on_entry.food",true);
        return settings;
    }
    @Test
    void entryAndExitRestoreInventoryExperienceAndMembershipAndIgnoreDuplicateOperations() throws Exception {
        try(var fixture=new RunFixture(directory,settings())) {
            var state=new PlayerState();
            state.contents[0]=PlayerState.stack(7);
            state.contents[40]=PlayerState.stack(2);
            state.armor[0]=PlayerState.stack(1);
            var session=fixture.session(state);
            var origin=state.location.clone();
            fixture.run.addPlayer(session);
            fixture.run.addPlayer(session);
            assertSame(fixture.run,session.getInstance());
            assertEquals(1,fixture.run.getPlayers().size());
            assertEquals(GameMode.ADVENTURE,state.gameMode);
            fixture.scheduler.jobs.getFirst().run();
            assertNull(state.contents[0]); assertNull(state.contents[40]);
            assertEquals(0,state.level);
            fixture.run.removePlayer(session);
            fixture.run.removePlayer(session);
            assertNull(session.getInstance());
            assertTrue(fixture.run.getPlayers().isEmpty());
            assertTrue(fixture.run.getLivingPlayers().isEmpty());
            assertEquals(origin,state.location);
            assertEquals(GameMode.SURVIVAL,state.gameMode);
            assertEquals(17,state.level); assertEquals(.4f,state.exp);
            assertEquals(7,state.contents[0].getAmount());
            assertEquals(2,state.contents[40].getAmount());
            assertEquals(1,state.armor[0].getAmount());
        }
    }
}
