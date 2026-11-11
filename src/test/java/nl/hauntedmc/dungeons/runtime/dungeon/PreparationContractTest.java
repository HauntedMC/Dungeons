package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.generation.layout.BranchingLayout;
import nl.hauntedmc.dungeons.generation.layout.Layout;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.registry.LayoutRegistry;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.world.WorldUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class PreparationContractTest {
    @TempDir Path directory;

    static Future<?> runWaiter(RunFixture fixture, ExecutorService executor) {
        var waiter = fixture.scheduler.asynchronous.getLast();
        return executor.submit(
                () -> {
                    try (var bukkit = mockStatic(Bukkit.class)) {
                        bukkit.when(Bukkit::getScheduler).thenReturn(fixture.scheduler.scheduler);
                        waiter.run();
                    }
                });
    }

    @Test
    @Timeout(10)
    void aSchedulerFailureAfterPlayerAttachmentRollsBackThePartialAdmissionBeforeDisposal()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var dungeon = new PreparedDungeon(f, directory.resolve("partial-admission"));
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            var session = f.session(state);
            assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
            finishPreparation(f);
            doThrow(new IllegalStateException("entry task rejected"))
                    .when(f.scheduler.scheduler)
                    .runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
            var manager = Bukkit.getPluginManager();
            doAnswer(
                            call -> {
                                if (call.getArgument(0) instanceof PlayerLeaveDungeonEvent) {
                                    // Player restoration invokes plugin events; cancellation may be
                                    // reentrant.
                                    f.paper.runtime.preparations().cancel(session);
                                }
                                return null;
                            })
                    .when(manager)
                    .callEvent(any(Event.class));
            f.scheduler.readyImmediate.remove().run();
            assertNull(session.getInstance());
            assertFalse(session.isAwaitingDungeon());
            assertTrue(dungeon.created.getPlayers().isEmpty());
            assertTrue(dungeon.getInstances().isEmpty());
            assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
            assertEquals(1, dungeon.refunds);
            assertEquals(GameMode.SURVIVAL, state.gameMode);
            assertEquals(7, state.contents[0].getAmount());
        }
    }

    @Test
    @Timeout(10)
    void aBranchingCreatorThatReturnsAfterCancellationDisposesItsGeneratedRun() throws Exception {
        try (var _ = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
                var f = new RunFixture(directory, new YamlConfiguration());
                var _ = new HeadlessMenus();
                var _ = mockStatic(RoomMenus.class);
                var executor = Executors.newSingleThreadExecutor()) {
            LayoutRegistry.register(BranchingLayout.class, "branching");
            var settings = new YamlConfiguration();
            settings.set("generator.seal_open_connectors_with", "AIR");
            var dungeon = new PreparedBranch(f, directory.resolve("late-branch"), settings);
            dungeon.timeout = Duration.ofSeconds(1);
            var session = f.session(new PlayerState());
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(f.world, false)).thenReturn(true);
            assertTrue(dungeon.prepareInstance(session.getPlayer(), "DEFAULT", 1, null));
            var waiting = runWaiter(f, executor);
            var creation = f.scheduler.readyImmediate.poll(2, TimeUnit.SECONDS);
            assertNotNull(creation);
            dungeon.beforeReturn =
                    () -> {
                        try {
                            waiting.get(2, TimeUnit.SECONDS);
                        } catch (Exception failure) {
                            throw new AssertionError(failure);
                        }
                    };
            creation.run();
            f.scheduler.readyImmediate.remove().run();
            assertNull(session.getInstance());
            assertFalse(session.isAwaitingDungeon());
            assertTrue(dungeon.getInstances().isEmpty());
            assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
            assertNull(dungeon.created.getInstanceWorld());
        }
    }

    @Test
    void realStaticFactoryCleansItsReservedFolderIfWorldSchedulingThrowsBeforeReturning()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            doThrow(new IllegalStateException("world initialization rejected"))
                    .when(f.scheduler.scheduler)
                    .runTask(any(Plugin.class), any(Runnable.class));
            assertThrows(
                    IllegalStateException.class,
                    () -> f.dungeon.createPlaySession(new CountDownLatch(1), null));
            assertTrue(f.dungeon.getInstances().isEmpty());
            assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
            assertFalse(Files.exists(WorldUtils.dimensionFolder("crypt_0").toPath()));
        }
    }

    @Test
    @Timeout(10)
    void timeoutCancelsAQueuedCreatorAndLateCallbacksCannotAffectTheNextAdmission()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var executor = Executors.newSingleThreadExecutor()) {
            var dungeon = new PreparedDungeon(f, directory.resolve("timeout"));
            dungeon.timeout = Duration.ofSeconds(1);
            var state = new PlayerState();
            var session = f.session(state);
            assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
            var waiting = runWaiter(f, executor);
            var creation = f.scheduler.readyImmediate.poll(2, TimeUnit.SECONDS);
            assertNotNull(creation);
            waiting.get(2, TimeUnit.SECONDS);
            var cleanup = f.scheduler.readyImmediate.remove();
            assertTrue(creation.cancelled);
            cleanup.run();
            assertEquals(1, dungeon.refunds);
            assertFalse(session.isAwaitingDungeon());
            assertNull(dungeon.created);
            assertTrue(dungeon.getInstances().isEmpty());
            assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());

            dungeon.timeout = Duration.ofSeconds(5);
            assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
            // Simulate the scheduler already having dequeued a cancelled callback, then replay
            // stale failure cleanup after the newer attempt has acquired this session.
            creation.cancelled = false;
            creation.run();
            cleanup.run();
            assertNull(dungeon.created);
            assertTrue(session.isAwaitingDungeon());
            assertEquals(1, dungeon.refunds);
            var next = runWaiter(f, executor);
            f.scheduler.readyImmediate.poll(2, TimeUnit.SECONDS).run();
            next.get(2, TimeUnit.SECONDS);
            f.scheduler.readyImmediate.remove().run();
            assertSame(dungeon.created, session.getInstance());
            assertFalse(session.isAwaitingDungeon());
        }
    }

    @Test
    @Timeout(10)
    void timeoutDuringCreationDisposesTheLatePublishedInstanceAndRetainsFailedUnloads()
            throws Exception {
        for (boolean unload : List.of(true, false)) {
            try (var f =
                            new RunFixture(
                                    directory.resolve("late-" + unload), new YamlConfiguration());
                    var executor = Executors.newSingleThreadExecutor()) {
                var dungeon = new PreparedDungeon(f, directory.resolve("late-dungeon-" + unload));
                dungeon.timeout = Duration.ofSeconds(1);
                var state = new PlayerState();
                var session = f.session(state);
                f.paper
                        .bukkit
                        .when(() -> Bukkit.unloadWorld(any(World.class), eq(false)))
                        .thenReturn(unload);
                assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
                var waiting = runWaiter(f, executor);
                var creation = f.scheduler.readyImmediate.poll(2, TimeUnit.SECONDS);
                assertNotNull(creation);
                dungeon.beforeReturn =
                        () -> {
                            try {
                                waiting.get(2, TimeUnit.SECONDS);
                            } catch (Exception failure) {
                                throw new AssertionError(failure);
                            }
                        };
                creation.run();
                assertNotNull(dungeon.created);
                assertNull(session.getInstance());
                assertFalse(session.isAwaitingDungeon());
                assertEquals(1, dungeon.refunds);
                f.scheduler.readyImmediate.remove().run();
                assertEquals(1, dungeon.refunds);
                assertEquals(unload, dungeon.getInstances().isEmpty());
                assertEquals(
                        unload, f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
                if (!unload) {
                    assertEquals(
                            DungeonInstance.DisposalState.RETRYABLE_FAILURE,
                            dungeon.created.getDisposalState());
                    f.paper
                            .bukkit
                            .when(() -> Bukkit.unloadWorld(any(World.class), eq(false)))
                            .thenReturn(true);
                    assertTrue(dungeon.created.disposeAsync().join());
                    assertTrue(dungeon.getInstances().isEmpty());
                }
            }
        }
    }

    @Test
    @Timeout(10)
    void interruptionCancelsCreationBeforeTheWaiterReturnsAndDrainsAdmissionState()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var executor = Executors.newSingleThreadExecutor()) {
            var dungeon = new PreparedDungeon(f, directory.resolve("interrupted"));
            var session = f.session(new PlayerState());
            var thread = new AtomicReference<Thread>();
            assertTrue(dungeon.prepareInstance(session.getPlayer(), "DEFAULT", 1, null));
            var waiting =
                    executor.submit(
                            () -> {
                                thread.set(Thread.currentThread());
                                try (var bukkit = mockStatic(Bukkit.class)) {
                                    bukkit.when(Bukkit::getScheduler)
                                            .thenReturn(f.scheduler.scheduler);
                                    f.scheduler.asynchronous.getLast().run();
                                    assertTrue(Thread.currentThread().isInterrupted());
                                }
                            });
            var creation = f.scheduler.readyImmediate.poll(2, TimeUnit.SECONDS);
            assertNotNull(creation);
            thread.get().interrupt();
            waiting.get(2, TimeUnit.SECONDS);
            f.scheduler.readyImmediate.remove().run();
            creation.cancelled = false;
            creation.run();
            assertNull(dungeon.created);
            assertFalse(session.isAwaitingDungeon());
            assertEquals(1, dungeon.refunds);
        }
    }

    @Test
    @Timeout(10)
    void creationExceptionsAndAsyncSchedulingRejectionCompleteFailureAndRefundOnlyOnce()
            throws Exception {
        for (boolean rejected : List.of(false, true)) {
            try (var f =
                    new RunFixture(
                            directory.resolve("error-" + rejected), new YamlConfiguration())) {
                var dungeon = new PreparedDungeon(f, directory.resolve("errors-" + rejected));
                dungeon.throwCreation = !rejected;
                var session = f.session(new PlayerState());
                if (rejected)
                    doThrow(new IllegalStateException("scheduler stopped"))
                            .when(f.scheduler.scheduler)
                            .runTaskAsynchronously(any(Plugin.class), any(Runnable.class));
                assertEquals(
                        !rejected,
                        dungeon.prepareInstance(session.getPlayer(), "DEFAULT", 1, null));
                if (!rejected) {
                    finishPreparation(f);
                    f.scheduler.readyImmediate.remove().run();
                }
                assertFalse(session.isAwaitingDungeon());
                assertEquals(1, dungeon.refunds);
                assertTrue(dungeon.getInstances().isEmpty());
                f.paper.runtime.preparations().cancel(session);
                assertEquals(1, dungeon.refunds);
            }
        }
    }

    @Test
    @Timeout(10)
    void shutdownDrainsCleanupRejectedByTheSchedulerAndPreventsLateAdmission() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var executor = Executors.newSingleThreadExecutor()) {
            var dungeon = new PreparedDungeon(f, directory.resolve("shutdown"));
            var session = f.session(new PlayerState());
            f.paper
                    .bukkit
                    .when(() -> Bukkit.unloadWorld(any(World.class), eq(false)))
                    .thenReturn(true);
            assertTrue(dungeon.prepareInstance(session.getPlayer(), "DEFAULT", 1, null));
            var waiting = runWaiter(f, executor);
            var creation = f.scheduler.readyImmediate.poll(2, TimeUnit.SECONDS);
            assertNotNull(creation);
            var creationReturned = new CountDownLatch(1);
            dungeon.beforeReturn =
                    () ->
                            doAnswer(
                                            call -> {
                                                // Reject on the worker only after owner-thread
                                                // publication
                                                // returns. Otherwise create() may legitimately
                                                // drain the
                                                // cancellation in its finally block before this
                                                // assertion.
                                                assertTrue(
                                                        creationReturned.await(
                                                                2, TimeUnit.SECONDS));
                                                throw new IllegalStateException(
                                                        "scheduler stopped");
                                            })
                                    .when(f.scheduler.scheduler)
                                    .runTask(any(Plugin.class), any(Runnable.class));
            creation.run();
            creationReturned.countDown();
            waiting.get(2, TimeUnit.SECONDS);
            assertTrue(
                    session.isAwaitingDungeon(), "Worker cannot mutate owner-thread player state");
            assertEquals(1, dungeon.getInstances().size());
            f.paper.runtime.preparations().shutdown();
            assertFalse(session.isAwaitingDungeon());
            assertTrue(dungeon.getInstances().isEmpty());
            assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
            assertEquals(1, dungeon.refunds);
            assertFalse(dungeon.prepareInstance(session.getPlayer(), "DEFAULT", 1, null));
        }
    }

    @Test
    @Timeout(10)
    void disconnectAndReloadCancelPublishedRunsBeforeAlreadyQueuedAdmissionCanRun()
            throws Exception {
        for (boolean reload : List.of(false, true)) {
            try (var f =
                    new RunFixture(
                            directory.resolve("cancel-" + reload), new YamlConfiguration())) {
                var dungeon = new PreparedDungeon(f, directory.resolve("cancel-dungeon-" + reload));
                var state = new PlayerState();
                var session = f.session(state);
                assertTrue(dungeon.prepareInstance(state.player, "DEFAULT", 1, null));
                finishPreparation(f);
                if (reload) f.paper.runtime.preparations().cancelDungeon(dungeon.getWorldName());
                else f.paper.runtime.preparations().cancel(session);
                assertFalse(session.isAwaitingDungeon());
                f.scheduler.readyImmediate.remove().run();
                assertNull(session.getInstance());
                assertTrue(dungeon.getInstances().isEmpty());
                assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
                assertEquals(1, dungeon.refunds);
            }
        }
    }

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
            try (var _ = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
                    var f = new RunFixture(directory.resolve(outcome), new YamlConfiguration());
                    var _ = new HeadlessMenus();
                    var _ = mockStatic(RoomMenus.class)) {
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
        Duration timeout = Duration.ofSeconds(5);
        Runnable beforeReturn = () -> {};

        PreparedBranch(RunFixture f, Path folder, YamlConfiguration settings) throws Exception {
            super(f.paper.runtime, Files.createDirectories(folder).toFile(), settings);
            fixture = f;
        }

        @Override
        protected Duration preparationTimeout() {
            return timeout;
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
        f.paper.bukkit.when(() -> Bukkit.unloadWorld(any(World.class), eq(false))).thenReturn(true);
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
        boolean throwCreation;
        int refunds;
        Duration timeout = Duration.ofSeconds(5);
        Runnable beforeReturn = () -> {};

        PreparedDungeon(RunFixture f, Path folder) throws Exception {
            super(
                    f.paper.runtime,
                    Files.createDirectories(folder).toFile(),
                    new YamlConfiguration());
            fixture = f;
        }

        @Override
        protected Duration preparationTimeout() {
            return timeout;
        }

        @Override
        public boolean refundReservedAccessKey(DungeonPlayerSession session) {
            refunds++;
            return super.refundReservedAccessKey(session);
        }

        @Override
        public PlayableInstance createPlaySession(CountDownLatch latch, UUID leader) {
            if (absent) return null;
            if (throwCreation) throw new IllegalStateException("creation failed");
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
            try (var _ = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
                    var f =
                            new RunFixture(
                                    directory.resolve(Boolean.toString(branching)),
                                    new YamlConfiguration());
                    var _ = new HeadlessMenus();
                    var _ = mockStatic(RoomMenus.class);
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
