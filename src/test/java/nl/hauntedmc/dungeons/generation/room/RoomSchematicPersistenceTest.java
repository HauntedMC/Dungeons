package nl.hauntedmc.dungeons.generation.room;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.layout.BranchingLayout;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.registry.LayoutRegistry;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.HeadlessMenus;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.StaticMapSnapshot;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;

class RoomSchematicPersistenceTest {
    @TempDir Path directory;

    @Test
    void deletionFollowsQueuedWritesAndStaleCallbacksCannotResurrectTheRoom() throws Exception {
        try (var f = new Fixture()) {
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var schematic = f.room.captureSchematic(f.world, true);
            var metadata = f.room.saveRoomAsync();
            f.room.delete();
            // Drive pending saves after deletion, just as a delayed autosave worker would run.
            for (var task : java.util.List.copyOf(f.scheduler.asynchronous)) task.run();
            assertTrue(schematic.join());
            assertTrue(metadata.join());
            assertFalse(Files.exists(f.target));
            assertFalse(Files.exists(directory.resolve("rooms/hall.yml")));
            assertFalse(f.room.captureSchematic(f.world, true).join());
            assertFalse(f.room.saveFunctionsAsync().join());
            assertFalse(f.room.saveRoomAsync().join());
        }
    }

    @Test
    void writeFailureIsReportedAndDirtyRoomCanBeRetried() throws Exception {
        try (var f = new Fixture()) {
            Files.createDirectories(f.target);
            Files.writeString(f.target.resolve("obstacle"), "keep");
            assertFalse(f.room.captureSchematic(f.world, true).join());
            assertTrue(f.room.isChangedSinceLastSave());
            Files.delete(f.target.resolve("obstacle"));
            Files.delete(f.target);
            assertTrue(f.room.captureSchematic(f.world, false).join());
            assertFalse(f.room.isChangedSinceLastSave());
            assertTrue(Files.isRegularFile(f.target));
        }
    }

    @Test
    void dirtyFlagStaysSetUntilWriteFinishesAndNewEditsSurviveCompletion() throws Exception {
        try (var f = new Fixture()) {
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            f.room.setChangedSinceLastSave(true);
            var save = f.room.captureSchematic(f.world, true);
            assertFalse(save.isDone());
            assertTrue(f.room.isChangedSinceLastSave());
            assertFalse(f.room.captureSchematic(f.world, true).join());
            f.room.setChangedSinceLastSave(true);
            f.scheduler.asynchronous.getFirst().run();
            assertTrue(save.join());
            assertTrue(f.room.isChangedSinceLastSave());
            var retry = f.room.captureSchematic(f.world, false);
            f.scheduler.asynchronous.get(1).run();
            assertTrue(retry.join());
            assertFalse(f.room.isChangedSinceLastSave());
        }
    }

    @Test
    void failedWriteKeepsLastPersistedCacheAndSuccessfulWriteInvalidatesRotations()
            throws Exception {
        try (var f = new Fixture()) {
            assertTrue(f.room.captureSchematic(f.world, true).join());
            var persisted = f.room.getSchematic();
            var rotation = f.room.getOrientations().getFirst();
            assertSame(persisted, rotation.getSchematic());
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var save = f.room.captureSchematic(f.world, true);
            assertSame(persisted, f.room.getSchematic());
            Files.delete(f.target);
            Files.createDirectory(f.target);
            Files.writeString(f.target.resolve("obstacle"), "keep");
            f.scheduler.asynchronous.getFirst().run();
            assertFalse(save.join());
            assertSame(persisted, f.room.getSchematic());
            Files.delete(f.target.resolve("obstacle"));
            Files.delete(f.target);
            var retry = f.room.captureSchematic(f.world, true);
            f.scheduler.asynchronous.get(1).run();
            assertTrue(retry.join());
            assertNotSame(persisted, f.room.getSchematic());
            assertSame(f.room.getSchematic(), rotation.getSchematic());
        }
    }

    @Test
    void captureFailureReleasesSaveStateForRetry() throws Exception {
        try (var f = new Fixture()) {
            when(f.world.getBlockAt(anyInt(), anyInt(), anyInt()))
                    .thenThrow(new IllegalStateException("world unavailable"));
            assertFalse(f.room.captureSchematic(f.world, true).join());
            assertTrue(f.room.isChangedSinceLastSave());
            f.airWorld();
            assertTrue(f.room.captureSchematic(f.world, false).join());
        }
    }

    @Test
    void rejectedCaptureAndWriteSchedulingBothCompleteAndAllowRetry() throws Exception {
        try (var f = new Fixture()) {
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            f.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            when(f.scheduler.scheduler.runTask(eq(f.paper.plugin), any(Runnable.class)))
                    .thenThrow(new IllegalStateException("scheduler stopped"));
            assertFalse(f.room.captureSchematic(f.world, true).join());
            assertTrue(f.room.isChangedSinceLastSave());
            f.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(f.scheduler.scheduler.runTaskAsynchronously(
                            eq(f.paper.plugin), any(Runnable.class)))
                    .thenThrow(new IllegalStateException("scheduler stopped"));
            assertFalse(f.room.captureSchematic(f.world, true).join());
            assertTrue(f.room.isChangedSinceLastSave());
            when(f.paper.plugin.isEnabled()).thenReturn(false);
            assertTrue(f.room.captureSchematic(f.world, false).join());
        }
    }

    @Test
    void nullWorldFailsAndUnchangedExistingSchematicSkipsCapture() throws Exception {
        try (var f = new Fixture()) {
            assertFalse(f.room.captureSchematic(null, true).join());
            assertTrue(f.room.captureSchematic(f.world, true).join());
            clearInvocations(f.world);
            assertTrue(f.room.captureSchematic(f.world, false).join());
            verifyNoInteractions(f.world);
        }
    }

    private final class Fixture implements AutoCloseable {
        final StaticMapSnapshot layouts = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
        final PaperBoundary paper = new PaperBoundary();
        final CapturedScheduler scheduler = new CapturedScheduler(paper);
        final MockedStatic<RoomMenus> menus = mockStatic(RoomMenus.class);
        final HeadlessMenus hotbars = new HeadlessMenus();
        final World world = mock(World.class);
        final Path target = directory.resolve("rooms/hall.struct");
        final BranchingRoomDefinition room;

        Fixture() throws Exception {
            LayoutRegistry.register(BranchingLayout.class, "branching");
            paper.runtime.setDefaultDungeonConfig(new YamlConfiguration());
            paper.bukkit.when(Bukkit::getWorldContainer).thenReturn(directory.toFile());
            paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            var settings = new YamlConfiguration();
            settings.set("generator.seal_open_connectors_with", "AIR");
            var dungeon = new BranchingDungeon(paper.runtime, directory.toFile(), settings);
            room = new BranchingRoomDefinition(dungeon, "hall", new BoundingBox(0, 0, 0, 0, 0, 0));
            airWorld();
        }

        void airWorld() {
            var air = mock(Block.class);
            when(air.getType()).thenReturn(Material.AIR);
            doReturn(air).when(world).getBlockAt(anyInt(), anyInt(), anyInt());
        }

        @Override
        public void close() {
            hotbars.close();
            menus.close();
            paper.close();
            layouts.close();
        }
    }
}
