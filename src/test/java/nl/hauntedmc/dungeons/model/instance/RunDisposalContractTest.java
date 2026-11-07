package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import nl.hauntedmc.dungeons.event.DungeonDisposeEvent;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class RunDisposalContractTest {
    @TempDir Path directory;

    @Test
    void cancellingAnObserverCannotInterruptDisposalOrLoseRegistration() throws Exception {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            World world = run.getInstanceWorld();
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            run.preparation = new CompletableFuture<>();
            var observer = run.disposeAsync();
            assertTrue(observer.cancel(false));
            assertEquals(1, paper.runtime.activeInstances().getRegisteredInstanceCount());
            run.preparation.complete(true);
            assertEquals(DungeonInstance.DisposalState.DISPOSED, run.getDisposalState());
            assertTrue(run.disposeAsync().join());
            verify(Bukkit.getPluginManager(), times(1)).callEvent(any(DungeonDisposeEvent.class));
        }
    }

    @Test
    void emptyCleanupSchedulingFailureStillDisposesTheWorld() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            World world = run.getInstanceWorld();
            when(paper.plugin.isEnabled()).thenReturn(true);
            doThrow(new IllegalStateException("scheduler stopped"))
                    .when(scheduler.scheduler)
                    .runTaskLater(
                            any(org.bukkit.plugin.Plugin.class), any(Runnable.class), anyLong());
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            assertDoesNotThrow(run::scheduleEmptyCleanup);
            assertEquals(DungeonInstance.DisposalState.DISPOSED, run.getDisposalState());
            assertTrue(paper.runtime.activeInstances().getActiveInstances().isEmpty());
        }
    }

    @Test
    void shutdownSupersedesPendingPreparationAndIgnoresItsLateCompletion() throws Exception {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            World world = run.getInstanceWorld();
            when(paper.plugin.isEnabled()).thenReturn(true);
            var pending = new CompletableFuture<Boolean>();
            run.preparation = pending;
            var first = run.disposeAsync();
            assertFalse(first.isDone());
            when(paper.plugin.isEnabled()).thenReturn(false);
            run.preparation = CompletableFuture.completedFuture(true);
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            assertTrue(run.disposeAsync().join());
            assertFalse(first.join());
            pending.complete(true);
            paper.bukkit.verify(() -> Bukkit.unloadWorld(world, false), times(1));
            verify(Bukkit.getPluginManager(), times(1)).callEvent(any(DungeonDisposeEvent.class));
            assertEquals(1, run.hooks);
            assertEquals(2, run.preparations);
        }
    }

    @Test
    void instanceListenerIsReleasedOnlyAfterUnloadSucceeds() throws Exception {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            World world = run.getInstanceWorld();
            var listener = mock(nl.hauntedmc.dungeons.listener.instance.InstanceListener.class);
            run.listener = listener;
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(false);
            assertFalse(run.disposeAsync().join());
            assertSame(listener, run.listener);
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            assertTrue(run.disposeAsync().join());
            assertNull(run.listener);
        }
    }

    @Test
    void delayedCleanupCannotDeleteFilesWhenTheWorldNameHasBeenReused() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            World world = run.getInstanceWorld();
            Path data = world.getWorldFolder().toPath().resolve("level.dat");
            when(paper.plugin.isEnabled()).thenReturn(true);
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            assertTrue(run.disposeAsync().join());
            Files.createDirectories(data.getParent());
            Files.writeString(data, "replacement world");
            paper.bukkit.when(() -> Bukkit.getWorld(world.getName())).thenReturn(mock(World.class));
            scheduler.asynchronous.getLast().run();
            assertEquals("replacement world", Files.readString(data));
            assertNull(run.getInstanceWorld());
        }
    }

    @Test
    void failedUnloadRetainsCapacityAndRuntimeUntilManualRetryCompletesExactlyOnce()
            throws Exception {
        for (boolean throwing : List.of(false, true)) {
            try (var paper = new PaperBoundary()) {
                var run = run(paper);
                World world = run.getInstanceWorld();
                var function = mock(DungeonFunction.class);
                run.getFunctions().put(new org.bukkit.Location(world, 0, 1, 0), function);
                if (throwing)
                    paper.bukkit
                            .when(() -> Bukkit.unloadWorld(world, false))
                            .thenThrow(new IllegalStateException("unload failed"));
                else paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(false);
                assertFalse(run.disposeAsync().join());
                assertEquals(
                        DungeonInstance.DisposalState.RETRYABLE_FAILURE, run.getDisposalState());
                org.junit.jupiter.api.Assertions.assertNotNull(run.getDisposalFailure());
                assertTrue(run.getDungeon().getInstances().contains(run));
                assertEquals(1, paper.runtime.activeInstances().getRegisteredInstanceCount());
                assertSame(
                        run, paper.runtime.activeInstances().getDungeonInstance(world.getName()));
                assertEquals(1, run.getFunctions().size());
                assertFalse(run.isReady());
                run.startGame();
                assertFalse(run.isStarted());
                verify(Bukkit.getPluginManager(), never())
                        .callEvent(any(DungeonDisposeEvent.class));
                var player = mock(nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession.class);
                run.addPlayer(player);
                verifyNoInteractions(player);
                paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
                assertTrue(run.disposeAsync().join());
                assertTrue(run.disposeAsync().join());
                assertEquals(DungeonInstance.DisposalState.DISPOSED, run.getDisposalState());
                assertNull(run.getDisposalFailure());
                assertTrue(run.getFunctions().isEmpty());
                assertTrue(run.getDungeon().getInstances().isEmpty());
                assertEquals(0, paper.runtime.activeInstances().getRegisteredInstanceCount());
                assertEquals(1, run.hooks);
                assertEquals(1, run.completions);
                verify(Bukkit.getPluginManager(), times(1))
                        .callEvent(any(DungeonDisposeEvent.class));
            }
        }
    }

    @Test
    void finalSaveFailureIsRetryableAndConcurrentCallersShareOneAttempt() throws Exception {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            World world = run.getInstanceWorld();
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            run.preparation = new CompletableFuture<>();
            var first = run.disposeAsync();
            var concurrent = run.disposeAsync();
            assertFalse(first.isDone());
            assertFalse(concurrent.isDone());
            assertEquals(1, run.preparations);
            run.preparation.complete(false);
            assertFalse(first.join());
            assertFalse(concurrent.join());
            assertEquals(1, paper.runtime.activeInstances().getRegisteredInstanceCount());
            paper.bukkit.verify(() -> Bukkit.unloadWorld(world, false), never());
            run.preparation = CompletableFuture.completedFuture(true);
            assertTrue(run.disposeAsync().join());
            assertEquals(2, run.preparations);
            assertEquals(1, run.hooks);
            assertEquals(1, run.completions);
        }
    }

    @Test
    void automaticRetriesAreBoundedAndManualRetryWorksAfterExhaustion() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            World world = run.getInstanceWorld();
            when(paper.plugin.isEnabled()).thenReturn(true);
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(false);
            assertFalse(run.disposeAsync().join());
            for (int i = 0; i < 3; i++) {
                assertEquals(100, scheduler.jobs.get(i).delay);
                scheduler.jobs.get(i).run();
            }
            assertEquals(3, scheduler.jobs.size());
            paper.bukkit.verify(() -> Bukkit.unloadWorld(world, false), times(4));
            assertEquals(1, paper.runtime.activeInstances().getRegisteredInstanceCount());
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            assertTrue(run.disposeAsync().join());
            assertEquals(1, run.hooks);
            assertEquals(1, run.completions);
            scheduler.asynchronous.getLast().run();
        }
    }

    @Test
    void shutdownDrainsScheduledDisposalWithoutRunningItsHookTwice() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            World world = run.getInstanceWorld();
            run.getDungeon().getConfig().set("runs.cleanup_delay_ticks", 40);
            when(paper.plugin.isEnabled()).thenReturn(true);
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            var attempt = run.disposeAsync();
            assertFalse(attempt.isDone());
            assertEquals(0, run.hooks);
            when(paper.plugin.isEnabled()).thenReturn(false);
            assertTrue(run.disposeAsync().join());
            assertTrue(attempt.join());
            scheduler.jobs.getFirst().run();
            assertEquals(1, run.hooks);
            paper.bukkit.verify(() -> Bukkit.unloadWorld(world, false), times(1));
        }
    }

    @Test
    void schedulingRejectionAndOffThreadShutdownRetainTheInstanceForServerThreadRetry()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            World world = run.getInstanceWorld();
            paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertFalse(run.disposeAsync().join());
            assertEquals(0, run.hooks);
            when(paper.plugin.isEnabled()).thenReturn(true);
            doThrow(new IllegalStateException("scheduler stopped"))
                    .when(scheduler.scheduler)
                    .runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class));
            assertFalse(run.disposeAsync().join());
            assertEquals(0, run.hooks);
            assertEquals(1, paper.runtime.activeInstances().getRegisteredInstanceCount());
            when(paper.plugin.isEnabled()).thenReturn(false);
            paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            assertTrue(run.disposeAsync().join());
        }
    }

    @Test
    void playersEnteringDuringDelayPreventTeardownUntilTheyLeave() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            World world = run.getInstanceWorld();
            run.getDungeon().getConfig().set("runs.cleanup_delay_ticks", 40);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var attempt = run.disposeAsync();
            when(world.getPlayerCount()).thenReturn(1);
            scheduler.jobs.getFirst().run();
            assertFalse(attempt.join());
            assertEquals(0, run.hooks);
            paper.bukkit.verify(() -> Bukkit.unloadWorld(world, false), never());
            when(world.getPlayerCount()).thenReturn(0);
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            assertTrue(run.disposeAsync().join());
            assertEquals(1, run.hooks);
        }
    }

    @Test
    void completionHookFailureDoesNotStrandTheFutureOrSuppressTheCompletedEvent() throws Exception {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            World world = run.getInstanceWorld();
            run.failCompletion = true;
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            assertTrue(run.disposeAsync().join());
            assertTrue(run.disposeAsync().join());
            verify(Bukkit.getPluginManager(), times(1)).callEvent(any(DungeonDisposeEvent.class));
            assertEquals(1, run.completions);
        }
    }

    @Test
    void failedCreationCanBeDisposedWithoutDereferencingANullWorld() throws Exception {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            var creator = mock(WorldCreator.class);
            run.initializeWith(creator);
            assertTrue(run.hasLoadFailed());
            assertNull(run.getInstanceWorld());
            assertDoesNotThrow(run::dispose);
            assertDoesNotThrow(run::dispose);
            assertEquals(1, run.hooks);
            assertTrue(paper.runtime.activeInstances().getActiveInstances().isEmpty());
        }
    }

    @Test
    void disposalIsIdempotentAndAHookFailureCannotPreventWorldUnloadAndFileCleanup()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            run.failHook = true;
            Path folder = run.getInstanceWorld().getWorldFolder().toPath();
            World world = run.getInstanceWorld();
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            run.dispose();
            run.dispose();
            assertEquals(1, run.hooks);
            assertTrue(scheduler.jobs.isEmpty());
            assertFalse(Files.exists(folder));
            assertNull(run.getInstanceWorld());
            assertTrue(paper.runtime.activeInstances().getActiveInstances().isEmpty());
            paper.bukkit.verify(() -> Bukkit.unloadWorld(world, false), times(1));
        }
    }

    @Test
    void refusedWorldUnloadMustNeverDeleteTheStillLoadedWorldFilesOrForgetTheWorldReference()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            World world = run.getInstanceWorld();
            Path data = world.getWorldFolder().toPath().resolve("level.dat");
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(false);
            run.dispose();
            assertTrue(scheduler.jobs.isEmpty());
            assertEquals("world state", Files.readString(data));
            assertSame(world, run.getInstanceWorld());
        }
    }

    @Test
    void initializationFailureCannotDeleteALiveWorldWhenUnloadIsRefused() throws Exception {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            World world = run.getInstanceWorld();
            Path data = world.getWorldFolder().toPath().resolve("level.dat");
            var creator = mock(WorldCreator.class);
            when(creator.createWorld()).thenReturn(world);
            paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(false);
            run.initializeWith(creator);
            assertTrue(run.hasLoadFailed());
            assertFalse(run.isReady());
            assertTrue(run.awaitInitialization(0, TimeUnit.MILLISECONDS));
            assertEquals("world state", Files.readString(data));
            assertSame(world, run.getInstanceWorld());
        }
    }

    @Test
    void unloadExceptionsCannotStrandInitializationWaitersOrDeleteWorldData() throws Exception {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            World world = run.getInstanceWorld();
            Path data = world.getWorldFolder().toPath().resolve("level.dat");
            var creator = mock(WorldCreator.class);
            when(creator.createWorld()).thenReturn(world);
            paper.bukkit
                    .when(() -> Bukkit.unloadWorld(world, false))
                    .thenThrow(new IllegalStateException("unload failed"));
            assertDoesNotThrow(() -> run.initializeWith(creator));
            assertTrue(run.hasLoadFailed());
            assertTrue(run.awaitInitialization(0, TimeUnit.MILLISECONDS));
            assertEquals("world state", Files.readString(data));
            assertSame(world, run.getInstanceWorld());
        }
    }

    @Test
    void shutdownDisposalNeverSchedulesWithTheDisabledPluginAndKeepsRefusedWorldsIntact()
            throws Exception {
        for (boolean unload : List.of(false, true)) {
            try (var paper = new PaperBoundary()) {
                var scheduler = new CapturedScheduler(paper);
                var run = run(paper);
                var world = run.getInstanceWorld();
                var folder = world.getWorldFolder().toPath();
                doThrow(new IllegalStateException("Plugin is disabled"))
                        .when(scheduler.scheduler)
                        .runTaskLater(
                                any(org.bukkit.plugin.Plugin.class),
                                any(Runnable.class),
                                anyLong());
                paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(unload);
                assertDoesNotThrow(run::dispose);
                assertEquals(!unload, Files.exists(folder));
                if (unload) assertNull(run.getInstanceWorld());
                else assertSame(world, run.getInstanceWorld());
                verifyNoInteractions(scheduler.scheduler);
            }
        }
    }

    TestRun run(PaperBoundary paper) throws Exception {
        paper.runtime.setActiveInstances(new ActiveInstanceRegistry(paper.plugin));
        var dungeon = paper.dungeon(directory.resolve("maps/crypt"), new YamlConfiguration());
        var folder = directory.resolve("maps/crypt_0");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("level.dat"), "world state");
        var world = mock(World.class);
        when(world.getName()).thenReturn("crypt_0");
        when(world.getWorldFolder()).thenReturn(folder.toFile());
        var run = new TestRun(dungeon, world);
        dungeon.getInstances().add(run);
        paper.runtime.activeInstances().registerActiveInstance(run);
        return run;
    }

    static class TestRun extends StaticInstance {
        int hooks;
        boolean failHook;
        boolean failCompletion;
        int preparations;
        int completions;
        CompletableFuture<Boolean> preparation = CompletableFuture.completedFuture(true);

        @Override
        protected CompletableFuture<Boolean> prepareDisposal() {
            preparations++;
            return preparation;
        }

        @Override
        protected void onDisposalComplete() {
            completions++;
            if (failCompletion) throw new IllegalStateException("completion failed");
        }

        TestRun(StaticDungeon dungeon, World world) {
            super(dungeon, null);
            instanceWorld = world;
            instanceWorldName = world.getName();
        }

        void initializeWith(WorldCreator creator) {
            instanceWorld = null;
            initializeWorld(creator);
        }

        @Override
        protected void applyWorldRules() {
            throw new IllegalStateException("broken world rules");
        }

        @Override
        public void onDispose() {
            hooks++;
            if (failHook) throw new IllegalStateException("broken disposal hook");
        }
    }
}
