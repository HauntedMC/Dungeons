package nl.hauntedmc.dungeons.model.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorPreparationTest {
    @TempDir Path directory;

    @Test
    void anEditorCannotStealASessionThatIsQueuedForAPlayAdmission() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            var session = f.session(state);
            session.setAwaitingDungeon(true);
            var dungeon = spy(f.dungeon);
            var catalog = mock(DungeonRepository.class);
            when(catalog.get(dungeon.getWorldName())).thenReturn(dungeon);
            f.paper.runtime.setDungeonCatalog(catalog);
            new EditorPreparation(dungeon, state.player.getUniqueId(), 30).start();
            verify(dungeon, never()).createEditSession(any());
            assertTrue(session.isAwaitingDungeon());
            assertFalse(f.paper.runtime.preparations().contains(session));
        }
    }

    @Test
    void waitingForAnExistingEditorUsesItsInitializationAndReleasesAdmissionOwnership()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var registry = new PreparationRegistry();
            f.paper.runtime.setPreparations(registry);
            var state = new PlayerState();
            var session = f.session(state);
            var catalog = mock(DungeonRepository.class);
            when(catalog.get(f.dungeon.getWorldName())).thenReturn(f.dungeon);
            f.paper.runtime.setDungeonCatalog(catalog);
            var editor = mock(EditableInstance.class);
            when(editor.getDisposalState()).thenReturn(DungeonInstance.DisposalState.ACTIVE);
            when(editor.awaitInitialization(anyLong(), any())).thenReturn(true);
            doAnswer(
                            call -> {
                                session.setInstance(editor);
                                return null;
                            })
                    .when(editor)
                    .addPlayer(session);
            f.dungeon.setEditSession(editor);
            new EditorPreparation(f.dungeon, state.player.getUniqueId(), 30).start();
            assertTrue(registry.contains(session));
            when(editor.isReady()).thenReturn(true);
            f.scheduler.asynchronous.getFirst().run();
            f.scheduler.immediate.getFirst().run();
            assertSame(editor, session.getInstance());
            assertFalse(registry.contains(session));
            assertFalse(session.isAwaitingDungeon());
        }
    }

    @Test
    void anExpiredCreationRequestNeverCreatesAnEditorOrRetainsTheSession() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var registry = new PreparationRegistry();
            f.paper.runtime.setPreparations(registry);
            var state = new PlayerState();
            var session = f.session(state);
            var dungeon = spy(f.dungeon);
            var catalog = mock(DungeonRepository.class);
            when(catalog.get(dungeon.getWorldName())).thenReturn(dungeon);
            f.paper.runtime.setDungeonCatalog(catalog);
            new EditorPreparation(dungeon, state.player.getUniqueId(), 0).start();
            verify(dungeon, never()).createEditSession(any());
            assertFalse(registry.contains(session));
            assertFalse(session.isAwaitingDungeon());
        }
    }

    @Test
    void cancellationDuringCreationDiscardsOnlyItsOwnLateEditor() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var registry = new PreparationRegistry();
            f.paper.runtime.setPreparations(registry);
            var state = new PlayerState();
            var session = f.session(state);
            var dungeon = spy(f.dungeon);
            var catalog = mock(DungeonRepository.class);
            when(catalog.get(dungeon.getWorldName())).thenReturn(dungeon);
            f.paper.runtime.setDungeonCatalog(catalog);
            var late = mock(EditableInstance.class);
            doAnswer(
                            call -> {
                                registry.cancel(session);
                                return late;
                            })
                    .when(dungeon)
                    .createEditSession(any());
            doNothing().when(dungeon).discardInstance(late);
            new EditorPreparation(dungeon, state.player.getUniqueId(), 30).start();
            verify(dungeon).discardInstance(late);
            verify(late, never()).addPlayer(any());
            assertFalse(registry.contains(session));
            assertFalse(session.isAwaitingDungeon());
        }
    }

    @Test
    void aCancelledWaiterCannotAdmitThePlayerOrDiscardAReplacementEditor() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var registry = new PreparationRegistry();
            f.paper.runtime.setPreparations(registry);
            var state = new PlayerState();
            var session = f.session(state);
            var dungeon = spy(f.dungeon);
            var catalog = mock(DungeonRepository.class);
            when(catalog.get(dungeon.getWorldName())).thenReturn(dungeon);
            f.paper.runtime.setDungeonCatalog(catalog);
            var original = mock(EditableInstance.class);
            when(original.getDisposalState()).thenReturn(DungeonInstance.DisposalState.ACTIVE);
            when(original.awaitInitialization(anyLong(), any())).thenReturn(true);
            dungeon.setEditSession(original);
            new EditorPreparation(dungeon, state.player.getUniqueId(), 30).start();
            registry.cancel(session);
            state.online = false;
            f.paper.runtime.playerSessions().shutdown();
            assertTrue(session.isExpired());
            var replacement = mock(EditableInstance.class);
            dungeon.setEditSession(replacement);
            f.scheduler.asynchronous.getFirst().run();
            f.scheduler.immediate.getFirst().run();
            verify(original, never()).addPlayer(any());
            verify(dungeon, never()).discardInstance(replacement);
            assertSame(replacement, dungeon.getEditSession());
            assertFalse(registry.contains(session));
        }
    }
}
