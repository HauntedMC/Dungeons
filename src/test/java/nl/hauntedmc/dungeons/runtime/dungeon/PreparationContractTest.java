package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.generation.layout.BranchingLayout;
import nl.hauntedmc.dungeons.generation.layout.Layout;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.registry.LayoutRegistry;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

class PreparationContractTest {
    @TempDir Path directory;

    @Test
    @Timeout(10)
    void staticPreparationPublishesAReadyRunAndAttachesThePlayerOnlyAtFinalAdmission()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var dungeon = new PreparedDungeon(f, directory.resolve("prepared"));
            var state = new PlayerState();
            var session = f.session(state);
            session.setAwaitingDungeon(true);
            assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
            assertNull(session.getInstance());
            finishPreparation(f);
            assertNull(session.getInstance());
            assertEquals(1, f.paper.runtime.activeInstances().getActiveInstances().size());
            f.scheduler.readyImmediate.remove().run();
            assertSame(dungeon.created, session.getInstance());
            assertFalse(session.isAwaitingDungeon());
            assertEquals(List.of(session), dungeon.created.getPlayers());
        }
    }

    @Test
    @Timeout(15)
    void disconnectedPlayersAndFailedOrMissingWorldsLeaveNoPendingAdmissionOrActiveInstance()
            throws Exception {
        for (String failure : List.of("disconnect", "failed", "absent")) {
            try (var f = new RunFixture(directory, new YamlConfiguration())) {
                var dungeon = new PreparedDungeon(f, directory.resolve(failure));
                dungeon.fail = failure.equals("failed");
                dungeon.absent = failure.equals("absent");
                var state = new PlayerState();
                var session = f.session(state);
                session.setAwaitingDungeon(true);
                assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
                finishPreparation(f);
                if (failure.equals("disconnect")) state.online = false;
                f.scheduler.readyImmediate.remove().run();
                assertNull(session.getInstance());
                assertFalse(session.isAwaitingDungeon());
                assertTrue(dungeon.getInstances().isEmpty());
                assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
            }
        }
    }

    @Test
    @Timeout(10)
    void aStalePreparedRunCannotStealAPlayerAlreadyAttachedToAnotherRun() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var dungeon = new PreparedDungeon(f, directory.resolve("stale"));
            var state = new PlayerState();
            var session = f.session(state);
            session.setAwaitingDungeon(true);
            dungeon.prepareInstance(state.player, "DEFAULT", 1, null);
            finishPreparation(f);
            f.run.addPlayer(session);
            var returnPosition = session.getSavedPosition().clone();
            f.scheduler.readyImmediate.remove().run();
            assertSame(f.run, session.getInstance());
            assertTrue(f.run.getPlayers().contains(session));
            assertTrue(dungeon.created.getPlayers().isEmpty());
            assertEquals(returnPosition, session.getSavedPosition());
            assertTrue(dungeon.getInstances().isEmpty());
            assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
        }
    }

    @Test
    @Timeout(20)
    void branchingPreparationRevalidatesMembershipAndConnectivityAtTheFinalAdmissionTick()
            throws Exception {
        for (String outcome : List.of("ready", "disconnect", "moved")) {
            try (var layouts = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
                    var f = new RunFixture(directory.resolve(outcome), new YamlConfiguration());
                    var menus = new HeadlessMenus();
                    var rooms = mockStatic(RoomMenus.class)) {
                LayoutRegistry.register(BranchingLayout.class, "branching");
                var settings = new YamlConfiguration();
                settings.set("generator.seal_open_connectors_with", "AIR");
                var dungeon =
                        new PreparedBranch(
                                f, directory.resolve(outcome).resolve("branch"), settings);
                var block = mock(Block.class);
                when(f.world.getBlockAt(any(Location.class))).thenReturn(block);
                var state = new PlayerState();
                var session = f.session(state);
                session.setAwaitingDungeon(true);
                assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
                finishPreparation(f);
                f.scheduler.readyImmediate.remove().run();
                var join = f.scheduler.jobs.getLast();
                assertNull(session.getInstance());
                if (outcome.equals("disconnect")) state.online = false;
                if (outcome.equals("moved")) f.run.addPlayer(session);
                join.run();
                assertFalse(session.isAwaitingDungeon());
                if (outcome.equals("ready")) {
                    assertSame(dungeon.created, session.getInstance());
                    assertEquals(new Location(f.world, 2, 70, 3), dungeon.created.getStartLoc());
                } else {
                    if (outcome.equals("disconnect")) assertNull(session.getInstance());
                    else assertSame(f.run, session.getInstance());
                    assertTrue(dungeon.getInstances().isEmpty());
                    assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
                }
            }
        }
    }

    static final class PreparedBranch extends BranchingDungeon {
        final RunFixture fixture;
        BranchRun created;
        Runnable beforeReturn = () -> {};

        PreparedBranch(RunFixture f, Path folder, YamlConfiguration settings) throws Exception {
            super(f.paper.runtime, Files.createDirectories(folder).toFile(), settings);
            fixture = f;
        }

        @Override
        public PlayableInstance createPlaySession(CountDownLatch latch, UUID leader) {
            try {
                created = new BranchRun(this, fixture.world, latch);
                beforeReturn.run();
                return created;
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }

    static final class BranchRun extends BranchingInstance {
        BranchRun(PreparedBranch dungeon, World world, CountDownLatch latch)
                throws ReflectiveOperationException {
            super(dungeon, latch);
            instanceWorld = world;
            ready = true;
            var layout = mock(Layout.class);
            var first = mock(InstanceRoom.class);
            when(first.getSpawn()).thenReturn(new Location(null, 2, 70, 3));
            when(layout.getFirst()).thenReturn(first);
            var field = BranchingInstance.class.getDeclaredField("builder");
            field.setAccessible(true);
            field.set(this, layout);
            signalInitializationComplete();
        }
    }

    static void finishPreparation(RunFixture f) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> loading =
                    executor.submit(
                            () -> {
                                try (var bukkit = mockStatic(Bukkit.class)) {
                                    bukkit.when(Bukkit::getScheduler)
                                            .thenReturn(f.scheduler.scheduler);
                                    f.scheduler.asynchronous.getFirst().run();
                                }
                            });
            var creation = f.scheduler.readyImmediate.poll(2, TimeUnit.SECONDS);
            assertNotNull(creation);
            creation.run();
            loading.get(2, TimeUnit.SECONDS);
        }
    }

    static final class PreparedDungeon extends StaticDungeon {
        final RunFixture fixture;
        RunFixture.Run created;
        boolean fail;
        boolean absent;
        Runnable beforeReturn = () -> {};

        PreparedDungeon(RunFixture f, Path folder) throws Exception {
            super(
                    f.paper.runtime,
                    Files.createDirectories(folder).toFile(),
                    new YamlConfiguration());
            fixture = f;
        }

        @Override
        public PlayableInstance createPlaySession(CountDownLatch latch, UUID leader) {
            if (absent) return null;
            World world = mock(World.class);
            when(world.getName()).thenReturn(getWorldName() + "_0");
            when(world.key()).thenReturn(new NamespacedKey("dungeons", getWorldName() + "_0"));
            when(world.getEntitiesByClass(EnderDragon.class)).thenReturn(List.of());
            created = new RunFixture.Run(this, world, latch);
            created.completeInitialization(fail);
            beforeReturn.run();
            return created;
        }
    }

    @Test
    @Timeout(15)
    void initializedInstancesCannotBeAdmittedOrRejectedUntilTheirCreationHasPublished()
            throws Exception {
        for (boolean branching : List.of(false, true)) {
            try (var layouts = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
                    var f =
                            new RunFixture(
                                    directory.resolve(Boolean.toString(branching)),
                                    new YamlConfiguration());
                    var menus = new HeadlessMenus();
                    var rooms = mockStatic(RoomMenus.class);
                    var executor = Executors.newFixedThreadPool(2)) {
                var initialized = new CountDownLatch(1);
                var allowPublication = new CountDownLatch(1);
                Runnable pausePublication =
                        () -> {
                            initialized.countDown();
                            try {
                                assertTrue(allowPublication.await(2, TimeUnit.SECONDS));
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                                throw new AssertionError(exception);
                            }
                        };
                DungeonDefinition dungeon;
                if (branching) {
                    LayoutRegistry.register(BranchingLayout.class, "branching");
                    var settings = new YamlConfiguration();
                    settings.set("generator.seal_open_connectors_with", "AIR");
                    var branch =
                            new PreparedBranch(
                                    f, directory.resolve("publication-branch"), settings);
                    branch.beforeReturn = pausePublication;
                    dungeon = branch;
                    var block = mock(Block.class);
                    when(f.world.getBlockAt(any(Location.class))).thenReturn(block);
                } else {
                    var flat = new PreparedDungeon(f, directory.resolve("publication-static"));
                    flat.beforeReturn = pausePublication;
                    dungeon = flat;
                }
                var session = f.session(new PlayerState());
                session.setAwaitingDungeon(true);
                assertTrue(dungeon.prepareInstance(session.getPlayer(), "DEFAULT", 1, null));
                var loading =
                        executor.submit(
                                () -> {
                                    try (var bukkit = mockStatic(Bukkit.class)) {
                                        bukkit.when(Bukkit::getScheduler)
                                                .thenReturn(f.scheduler.scheduler);
                                        f.scheduler.asynchronous.getFirst().run();
                                    }
                                });
                var creation = f.scheduler.readyImmediate.poll(2, TimeUnit.SECONDS);
                assertNotNull(creation);
                var publishing = executor.submit(creation::run);
                try {
                    assertTrue(initialized.await(2, TimeUnit.SECONDS));
                    assertThrows(
                            TimeoutException.class,
                            () -> loading.get(200, TimeUnit.MILLISECONDS),
                            "Readiness alone must not finish preparation before publication");
                } finally {
                    allowPublication.countDown();
                }
                publishing.get(2, TimeUnit.SECONDS);
                loading.get(2, TimeUnit.SECONDS);
                f.scheduler.readyImmediate.remove().run();
                if (branching) f.scheduler.jobs.getLast().run();
                assertSame(dungeon.getInstances().getFirst(), session.getInstance());
                assertFalse(session.isAwaitingDungeon());
            }
        }
    }
}
