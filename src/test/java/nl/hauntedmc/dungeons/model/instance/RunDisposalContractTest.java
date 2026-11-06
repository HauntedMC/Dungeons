package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunDisposalContractTest {
    @TempDir Path directory;
    @Test
    void disposalIsIdempotentAndAHookFailureCannotPreventWorldUnloadAndFileCleanup() throws Exception {
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
            assertEquals(1, scheduler.jobs.size());
            scheduler.jobs.getFirst().run();
            assertFalse(Files.exists(folder));
            assertNull(run.getInstanceWorld());
            assertTrue(paper.runtime.activeInstances().getActiveInstances().isEmpty());
            paper.bukkit.verify(() -> Bukkit.unloadWorld(world, false), times(1));
        }
    }
    TestRun run(PaperBoundary paper) throws Exception {
        paper.runtime.setActiveInstances(new ActiveInstanceRegistry(paper.plugin));
        var dungeon = paper.dungeon(directory.resolve("maps/crypt"), new YamlConfiguration());
        var folder = directory.resolve("crypt_0");
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
        TestRun(StaticDungeon dungeon, World world) { super(dungeon, null); instanceWorld = world; instanceWorldName = world.getName(); }
        @Override public void onDispose() { hooks++; if (failHook) throw new IllegalStateException("broken disposal hook"); }
    }
}
