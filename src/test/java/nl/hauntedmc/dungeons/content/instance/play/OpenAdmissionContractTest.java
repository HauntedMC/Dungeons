package nl.hauntedmc.dungeons.content.instance.play;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import nl.hauntedmc.dungeons.content.dungeon.OpenDungeon;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenAdmissionContractTest {
    @TempDir Path directory;
    @Test
    void rejectedCrossRunAdmissionCannotConsumeAnotherTeamsReservedCapacity() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var state=new PlayerState(); var session=f.session(state); f.run.addPlayer(session);
            var dungeon=dungeon(f); var run=new ReadyRun(dungeon,f.world);
            UUID owner=UUID.randomUUID(); assertTrue(run.reserveTeamSlots(owner,1));
            assertFalse(run.addReservedPlayer(session,owner));
            assertSame(f.run,session.getInstance()); assertTrue(run.getPlayers().isEmpty());
            assertTrue(run.hasOutstandingReservations()); assertFalse(run.hasCapacityFor(1));
        }
    }
    OpenDungeon dungeon(RunFixture f) throws Exception {
        var settings=new YamlConfiguration(); settings.set("open.max_players",1);
        var dungeon=new OpenDungeon(f.paper.runtime,Files.createDirectories(directory.resolve("open")).toFile(),settings);
        var catalog=mock(DungeonRepository.class); when(catalog.get("open")).thenReturn(dungeon);
        f.paper.runtime.setDungeonCatalog(catalog); return dungeon;
    }
    static final class ReadyRun extends OpenInstance {
        ReadyRun(OpenDungeon dungeon,World world) {
            super(dungeon,null); this.instanceWorld=world; this.startLocation=new Location(world,0,70,0);
            this.ready=true; this.signalInitializationComplete();
        }
    }
}
