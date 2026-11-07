package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

class EditorOpenFailureTest {
    @TempDir Path directory;

    @Test
    void failedEditorOpenRetainsTheEditorReferenceWhenUnloadIsRefused() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var dungeon = spy(f.dungeon);
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
            dungeon.edit(UUID.randomUUID());
            assertSame(editor, dungeon.getEditSession());
            assertTrue(dungeon.getInstances().contains(editor));
            assertEquals(DungeonInstance.DisposalState.RETRYABLE_FAILURE, editor.getDisposalState());
        }
    }
}
