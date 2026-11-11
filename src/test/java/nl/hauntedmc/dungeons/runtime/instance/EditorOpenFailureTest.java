package nl.hauntedmc.dungeons.runtime.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorOpenFailureTest {
    @TempDir Path directory;

    @Test
    void failedEditorOpenRetainsTheEditorReferenceWhenUnloadIsRefused() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            f.paper.runtime.setPreparations(new PreparationRegistry());
            var state = new PlayerState();
            f.session(state);
            var dungeon = spy(f.dungeon);
            var catalog = mock(DungeonRepository.class);
            when(catalog.get(dungeon.getWorldName())).thenReturn(dungeon);
            f.paper.runtime.setDungeonCatalog(catalog);
            var editor = new EditableInstance(dungeon, new CountDownLatch(1));
            editor.instanceWorld = f.world;
            editor.loadFailed = true;
            doAnswer(
                            call -> {
                                ((CountDownLatch) call.getArgument(0)).countDown();
                                return editor;
                            })
                    .when(dungeon)
                    .createEditSession(any());
            when(f.scheduler.scheduler.runTask(eq(f.paper.plugin), any(Runnable.class)))
                    .thenAnswer(
                            call -> {
                                ((Runnable) call.getArgument(1)).run();
                                return mock(BukkitTask.class);
                            });
            dungeon.edit(state.player.getUniqueId());
            assertSame(editor, dungeon.getEditSession());
            assertTrue(dungeon.getInstances().contains(editor));
            assertEquals(
                    DungeonInstance.DisposalState.RETRYABLE_FAILURE, editor.getDisposalState());
        }
    }
}
