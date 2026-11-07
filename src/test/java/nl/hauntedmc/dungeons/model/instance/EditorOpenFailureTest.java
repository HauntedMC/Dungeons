package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

class EditorOpenFailureTest {
    @TempDir Path directory;

    @Test
    void failedEditorOpenRetainsTheEditorReferenceWhenUnloadIsRefused() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            f.paper.runtime.setPreparations(new nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry());
            var state = new PlayerState();
            f.session(state);
            var dungeon = spy(f.dungeon);
            var catalog = mock(nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository.class);
            when(catalog.get(dungeon.getWorldName())).thenReturn(dungeon);
            f.paper.runtime.setDungeonCatalog(catalog);
            var editor = new EditableInstance(dungeon, new CountDownLatch(1));
            editor.instanceWorld = f.world;
            editor.loadFailed = true;
            doAnswer(call -> {
                ((CountDownLatch) call.getArgument(0)).countDown();
                return editor;
            }).when(dungeon).createEditSession(any());
            when(f.scheduler.scheduler.runTask(eq(f.paper.plugin), any(Runnable.class)))
                    .thenAnswer(call -> {
                        ((Runnable) call.getArgument(1)).run();
                        return mock(org.bukkit.scheduler.BukkitTask.class);
                    });
            dungeon.edit(state.player.getUniqueId());
            assertSame(editor, dungeon.getEditSession());
            assertTrue(dungeon.getInstances().contains(editor));
            assertEquals(DungeonInstance.DisposalState.RETRYABLE_FAILURE, editor.getDisposalState());
        }
    }
}
