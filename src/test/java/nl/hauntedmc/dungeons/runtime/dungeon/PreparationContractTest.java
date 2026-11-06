package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class PreparationContractTest {
    @TempDir Path directory;
    @Test @Timeout(10)
    void staticPreparationPublishesAReadyRunAndAttachesThePlayerOnlyAtFinalAdmission() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var dungeon=new PreparedDungeon(f,directory.resolve("prepared"));
            var state=new PlayerState(); var session=f.session(state); session.setAwaitingDungeon(true);
            assertTrue(dungeon.prepareInstance(state.player,"DEFAULT",1,null));
            assertNull(session.getInstance());
            finishPreparation(f);
            assertNull(session.getInstance()); assertEquals(1,f.paper.runtime.activeInstances().getActiveInstances().size());
            f.scheduler.readyImmediate.remove().run();
            assertSame(dungeon.created,session.getInstance()); assertFalse(session.isAwaitingDungeon());
            assertEquals(List.of(session),dungeon.created.getPlayers());
        }
    }


    @Test @Timeout(15)
    void disconnectedPlayersAndFailedOrMissingWorldsLeaveNoPendingAdmissionOrActiveInstance() throws Exception {
        for(String failure:List.of("disconnect","failed","absent")) {
            try(var f=new RunFixture(directory,new YamlConfiguration())) {
                var dungeon=new PreparedDungeon(f,directory.resolve(failure));
                dungeon.fail=failure.equals("failed"); dungeon.absent=failure.equals("absent");
                var state=new PlayerState(); var session=f.session(state); session.setAwaitingDungeon(true);
                assertTrue(dungeon.prepareInstance(state.player,"DEFAULT",1,null));
                finishPreparation(f);
                if(failure.equals("disconnect")) state.online=false;
                f.scheduler.readyImmediate.remove().run();
                assertNull(session.getInstance()); assertFalse(session.isAwaitingDungeon());
                assertTrue(dungeon.getInstances().isEmpty());
                assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
            }
        }
    }


    @Test @Timeout(10)
    void aStalePreparedRunCannotStealAPlayerAlreadyAttachedToAnotherRun() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var dungeon=new PreparedDungeon(f,directory.resolve("stale"));
            var state=new PlayerState(); var session=f.session(state); session.setAwaitingDungeon(true);
            dungeon.prepareInstance(state.player,"DEFAULT",1,null); finishPreparation(f);
            f.run.addPlayer(session);
            var returnPosition=session.getSavedPosition().clone();
            f.scheduler.readyImmediate.remove().run();
            assertSame(f.run,session.getInstance());
            assertTrue(f.run.getPlayers().contains(session));
            assertTrue(dungeon.created.getPlayers().isEmpty());
            assertEquals(returnPosition,session.getSavedPosition());
            assertTrue(dungeon.getInstances().isEmpty());
            assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
        }
    }

    static void finishPreparation(RunFixture f) throws Exception {
        try(var executor=Executors.newSingleThreadExecutor()) {
            Future<?> loading=executor.submit(() -> {
                try(var bukkit=mockStatic(Bukkit.class)) {
                    bukkit.when(Bukkit::getScheduler).thenReturn(f.scheduler.scheduler);
                    f.scheduler.asynchronous.getFirst().run();
                }
            });
            var creation=f.scheduler.readyImmediate.poll(2,TimeUnit.SECONDS);
            assertNotNull(creation); creation.run();
            loading.get(2,TimeUnit.SECONDS);
        }
    }
    static final class PreparedDungeon extends StaticDungeon {
        final RunFixture fixture;
        RunFixture.Run created;
        boolean fail;
        boolean absent;
        PreparedDungeon(RunFixture f,Path folder) throws Exception {
            super(f.paper.runtime,Files.createDirectories(folder).toFile(),new YamlConfiguration()); fixture=f;
        }
        @Override public PlayableInstance createPlaySession(CountDownLatch latch,UUID leader) {
            if (absent) return null;
            World world=mock(World.class);
            when(world.getName()).thenReturn(getWorldName()+"_0");
            when(world.key()).thenReturn(new NamespacedKey("dungeons",getWorldName()+"_0"));
            when(world.getEntitiesByClass(org.bukkit.entity.EnderDragon.class)).thenReturn(List.of());
            created=new RunFixture.Run(this,world,latch);
            created.completeInitialization(fail); return created;
        }
    }
}
