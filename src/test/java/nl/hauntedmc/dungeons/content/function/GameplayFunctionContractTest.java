package nl.hauntedmc.dungeons.content.function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameplayFunctionContractTest {
    @TempDir Path directory;
    static <T extends DungeonFunction> T bind(T function,RunFixture f) {
        function.initializeFields(); function.setInstance(f.run); function.setLocation(new Location(f.world,0,70,0)); return function;
    }
    @Test
    void startMechanicRequiresEveryParticipantAndDuplicateReadySignalsCannotStartEarly() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var first=f.session(new PlayerState()); var second=f.session(new PlayerState());
            f.run.addPlayer(first); f.run.addPlayer(second);
            var start=bind(new StartDungeonFunction(),f);
            start.runFunction(null,List.of(first)); start.runFunction(null,List.of(first));
            assertFalse(f.run.isStarted());
            start.runFunction(null,List.of(second)); assertTrue(f.run.isStarted());
            int jobs=f.scheduler.jobs.size(); start.runFunction(null,List.of(first,second));
            assertEquals(jobs,f.scheduler.jobs.size());
            verify(Bukkit.getPluginManager(),times(1)).callEvent(any(nl.hauntedmc.dungeons.event.DungeonStartEvent.class));
        }
    }
}
