package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.OpenDungeon;
import nl.hauntedmc.dungeons.content.instance.play.OpenInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;

class OpenPreparationContractTest {
    @TempDir Path directory;

    static final class PreparedOpen extends OpenDungeon {
        final RunFixture fixture;
        final List<LoadingRun> created = new ArrayList<>();

        PreparedOpen(RunFixture f, Path folder, YamlConfiguration settings) throws Exception {
            super(f.paper.runtime, Files.createDirectories(folder).toFile(), settings);
            fixture = f;
            var catalog = mock(DungeonRepository.class);
            when(catalog.get(getWorldName())).thenReturn(this);
            f.paper.runtime.setDungeonCatalog(catalog);
        }

        @Override
        public PlayableInstance createPlaySession(CountDownLatch latch, UUID leader) {
            var run = new LoadingRun(this, fixture.world);
            created.add(run);
            getInstances().add(run);
            fixture.paper.runtime.activeInstances().registerActiveInstance(run);
            try {
                var field = OpenDungeon.class.getDeclaredField("loadingInstances");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                var loading = (List<OpenInstance>) field.get(this);
                loading.add(run);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
            return run;
        }
    }

    static final class LoadingRun extends OpenInstance {
        LoadingRun(OpenDungeon dungeon, World world) {
            super(dungeon, new CountDownLatch(1));
            instanceWorld = world;
            startLocation = new Location(world, 0, 70, 0);
        }

        void complete(boolean failed) {
            loadFailed = failed;
            ready = !failed;
            signalInitializationComplete();
        }
    }

    @Test
    void loadingOpenRunsReserveCapacityUntilReadyAndReleaseReservationsAfterFailedOrAbandonedJoins()
            throws Exception {
        for (String outcome : List.of("ready", "disconnect", "failed")) {
            try (var f = new RunFixture(directory.resolve(outcome), new YamlConfiguration())) {
                var settings = new YamlConfiguration();
                settings.set("open.max_players", 1);
                settings.set("runs.max_active", 1);
                var dungeon =
                        new PreparedOpen(f, directory.resolve(outcome).resolve("open"), settings);
                var state = new PlayerState();
                var session = f.session(state);
                session.setAwaitingDungeon(true);
                assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
                var run = dungeon.created.getFirst();
                assertTrue(run.hasOutstandingReservations());
                assertNull(session.getInstance());
                run.complete(outcome.equals("failed"));
                if (outcome.equals("disconnect")) state.online = false;
                f.scheduler.asynchronous.getFirst().run();
                f.scheduler.readyImmediate.remove().run();
                assertFalse(session.isAwaitingDungeon());
                assertFalse(run.hasOutstandingReservations());
                if (outcome.equals("ready")) {
                    assertSame(run, session.getInstance());
                    assertEquals(1, run.getPlayers().size());
                } else {
                    assertNull(session.getInstance());
                    assertTrue(run.getPlayers().isEmpty());
                }
                if (outcome.equals("failed")) {
                    assertTrue(dungeon.getInstances().isEmpty());
                    assertFalse(dungeon.publishReadyInstance(run));
                }
            }
        }
    }

    @Test
    void warmPreloadingCoalescesRequestsReplenishesDisposedRunsAndRejectsStalePublication()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var settings = new YamlConfiguration();
            settings.set("open.preload_world", true);
            settings.set("runs.max_active", 2);
            var dungeon = new PreparedOpen(f, directory.resolve("open"), settings);
            f.scheduler.jobs.getFirst().run();
            var preload = f.scheduler.jobs.getLast();
            int scheduled = f.scheduler.jobs.size();
            dungeon.schedulePreLoadIfNeeded();
            dungeon.schedulePreLoadIfNeeded();
            assertEquals(scheduled, f.scheduler.jobs.size());
            preload.run();
            dungeon.preLoad();
            assertEquals(1, dungeon.created.size());
            var first = dungeon.created.getFirst();
            first.complete(false);
            assertTrue(dungeon.publishReadyInstance(first));
            f.scheduler.asynchronous.getFirst().run();
            dungeon.preLoad();
            assertEquals(1, dungeon.created.size());
            dungeon.removeInstance(first);
            f.paper.runtime.activeInstances().unregisterActiveInstance(first);
            dungeon.onOpenInstanceDisposed(first);
            var replacement = f.scheduler.jobs.getLast();
            assertEquals(20, replacement.delay);
            replacement.run();
            assertEquals(2, dungeon.created.size());
            var second = dungeon.created.getLast();
            second.cancelReadyRegistration();
            second.complete(false);
            assertFalse(dungeon.publishReadyInstance(second));
            var newer = mock(OpenDungeon.class);
            when(f.paper.runtime.dungeonCatalog().get(dungeon.getWorldName())).thenReturn(newer);
            assertFalse(dungeon.publishReadyInstance(first));
            dungeon.schedulePreLoadIfNeeded();
            f.scheduler.jobs.getLast().run();
            assertEquals(2, dungeon.created.size());
        }
    }
}
