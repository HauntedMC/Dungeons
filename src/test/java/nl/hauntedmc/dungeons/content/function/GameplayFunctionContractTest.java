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
    @Test
    void finishingRecordsCompletionAndTransfersQueuedRewardsOnceWithoutForcingDeparture() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var state=new PlayerState(); var session=f.session(state); f.run.addPlayer(session);
            var outsider=new PlayerState(); f.session(outsider);
            var reward=PlayerState.stack(2); f.run.addPlayerReward(state.player,reward);
            var finish=bind(new FinishDungeonFunction(Map.of("leave",false)),f);
            finish.runFunction(null,List.of(session)); finish.runFunction(null,List.of(session));
            assertTrue(f.dungeon.hasPlayerCompletedDungeon(state.player));
            assertFalse(f.dungeon.hasPlayerCompletedDungeon(outsider.player));
            assertEquals(List.of(reward),session.getRewardItems()); assertSame(f.run,session.getInstance());
        }
    }

    @Test
    void teleportResolvesTheRuntimeWorldWithoutMutatingItsTemplateAndIgnoresOtherRunTargets() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var state=new PlayerState(); var session=f.session(state); f.run.addPlayer(session);
            var outsider=new PlayerState(); var other=f.session(outsider); var otherLocation=outsider.location.clone();
            var destination=new Location(state.originWorld,30,80,40,45,10);
            var teleport=bind(new TeleportFunction(Map.of("teleportTarget",destination)),f); teleport.onEnable();
            var vehicle=mock(org.bukkit.entity.Entity.class); when(state.player.getVehicle()).thenReturn(vehicle);
            teleport.runFunction(null,List.of(session,other));
            assertEquals(new Location(f.world,30,80,40,45,10),state.location);
            assertEquals(otherLocation,outsider.location); assertSame(state.originWorld,destination.getWorld());
            verify(vehicle).removePassenger(state.player);
        }
    }

}
