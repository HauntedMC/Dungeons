package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
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
import java.util.concurrent.TimeUnit;

class RunDisposalContractTest {
    @TempDir Path directory;

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
