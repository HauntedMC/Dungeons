package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

class EditorPersistenceContractTest {
    @TempDir Path directory;

    @Test
    void editorSaveCopiesTheWorldAndFunctionsWithoutReplacingRoomTemplatesOrWorldIdentity()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path source = Files.createDirectories(directory.resolve("live"));
            Files.writeString(source.resolve("level.dat"), "edited world");
            Files.writeString(source.resolve("uid.dat"), "runtime identity");
            Path incoming = Files.createDirectories(source.resolve("rooms"));
            Files.writeString(incoming.resolve("room.yml"), "wrong room");
            Path rooms = Files.createDirectories(f.dungeon.getFolder().toPath().resolve("rooms"));
            Files.writeString(rooms.resolve("room.yml"), "template room");
            when(f.world.getWorldFolder()).thenReturn(source.toFile());
            var editor = new Editor(f);
            var latch = new CountDownLatch(1);
            assertTrue(editor.saveWorldAsync(latch).get(1, TimeUnit.SECONDS));
            assertEquals(0, latch.getCount());
            assertFalse(f.dungeon.isSaving());
            assertEquals(
                    "edited world",
                    Files.readString(f.dungeon.getFolder().toPath().resolve("level.dat")));
            assertEquals("template room", Files.readString(rooms.resolve("room.yml")));
            assertFalse(Files.exists(f.dungeon.getFolder().toPath().resolve("uid.dat")));
            assertTrue(Files.exists(f.dungeon.getFolder().toPath().resolve("functions.yml")));
            verify(f.world).save(true);
        }
    }

    @Test
    void overlappingSavesAreRejectedWhileTheOriginalSaveKeepsItsOwnCompletionAndLatch()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var editor = new Editor(f);
            editor.pending = new CompletableFuture<>();
            var originalLatch = new CountDownLatch(1);
            var rejectedLatch = new CountDownLatch(1);
            var original = editor.saveWorldAsync(originalLatch);
            assertFalse(original.isDone());
            assertTrue(f.dungeon.isSaving());
            assertFalse(editor.saveWorldAsync(rejectedLatch).get(1, TimeUnit.SECONDS));
            assertEquals(0, rejectedLatch.getCount());
            assertEquals(1, originalLatch.getCount());
            assertTrue(f.dungeon.isSaving());
            editor.pending.complete(true);
            assertTrue(original.get(1, TimeUnit.SECONDS));
            assertEquals(0, originalLatch.getCount());
            assertFalse(f.dungeon.isSaving());
            verify(f.world, times(1)).save(true);
        }
    }

    @Test
    void failedWorldFlushCopyAndExceptionalCompletionAllReleaseSavingStateAndWaiters()
            throws Exception {
        for (String failure : List.of("flush", "copy", "future")) {
            try (var f = new RunFixture(directory, new YamlConfiguration())) {
                var editor = new Editor(f);
                when(f.world.getWorldFolder())
                        .thenReturn(directory.resolve("missing-source").toFile());
                if (failure.equals("flush"))
                    doThrow(new IllegalStateException("flush failed")).when(f.world).save(true);
                if (failure.equals("future"))
                    editor.pending =
                            CompletableFuture.failedFuture(
                                    new IllegalStateException("commit failed"));
                var latch = new CountDownLatch(1);
                assertFalse(editor.saveWorldAsync(latch).get(1, TimeUnit.SECONDS));
                assertEquals(0, latch.getCount());
                assertFalse(f.dungeon.isSaving());
            }
        }
    }

    @Test
    void aSynchronousCommitExceptionStillCompletesTheSaveFutureAndReleasesItsLatch()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var editor = new Editor(f);
            editor.throwCommit = true;
            var latch = new CountDownLatch(1);
            var result = assertDoesNotThrow(() -> editor.saveWorldAsync(latch));
            assertFalse(result.get(1, TimeUnit.SECONDS));
            assertEquals(0, latch.getCount());
            assertFalse(f.dungeon.isSaving());
        }
    }

    @Test
    void asynchronousSerializationFailureCompletesTheEditorSaveWithoutLeavingAnUnresolvedFuture()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path source = Files.createDirectories(directory.resolve("live"));
            when(f.world.getWorldFolder()).thenReturn(source.toFile());
            var broken = mock(DungeonFunction.class);
            when(broken.serialize()).thenThrow(new IllegalStateException("broken serializer"));
            f.dungeon.getFunctions().put(new Location(f.world, 0, 70, 0), broken);
            var editor = new Editor(f);
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var latch = new CountDownLatch(1);
            var result = editor.saveWorldAsync(latch);
            assertFalse(result.isDone());
            assertTrue(f.dungeon.isSaving());
            assertDoesNotThrow(() -> f.scheduler.asynchronous.getFirst().run());
            assertFalse(result.get(1, TimeUnit.SECONDS));
            assertEquals(0, latch.getCount());
            assertFalse(f.dungeon.isSaving());
        }
    }

    @Test
    void rejectedEditorAdmissionCannotClearTheInventoryOrChangeAnotherRunsPlayerMode()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            var session = f.session(state);
            f.run.addPlayer(session);
            var editor = new Editor(f);
            assertDoesNotThrow(() -> editor.addPlayer(session));
            assertSame(f.run, session.getInstance());
            assertFalse(session.isEditMode());
            assertEquals(GameMode.ADVENTURE, state.gameMode);
            assertEquals(7, state.contents[0].getAmount());
            assertTrue(editor.getPlayers().isEmpty());
            assertTrue(f.run.getPlayers().contains(session));
        }
    }

    static class Editor extends EditableInstance {
        CompletableFuture<Boolean> pending;
        boolean throwCommit;

        Editor(RunFixture f) {
            super(f.dungeon, null);
            this.instanceWorld = f.world;
            this.startLocation = new Location(f.world, 0, 70, 0);
        }

        @Override
        protected CompletableFuture<Boolean> commitWorldAsync(World world) {
            if (throwCommit) throw new IllegalStateException("commit failed");
            return pending == null ? super.commitWorldAsync(world) : pending;
        }
    }
}
