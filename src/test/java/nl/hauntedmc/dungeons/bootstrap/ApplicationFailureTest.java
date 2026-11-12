package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dungeons.integration.DungeonService;
import nl.hauntedmc.dungeons.persistence.DatabaseLifecycle;
import nl.hauntedmc.dungeons.persistence.DungeonDatabase;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.junit.jupiter.api.Test;

class ApplicationFailureTest {
    @Test
    void failureAfterRuntimeInitializationCleansOnceAndPreservesTheOriginalError() {
        var original = new IllegalStateException("late startup failure");
        var cleanup = new IllegalStateException("cleanup failure");
        try (var paper = new PaperBoundary();
                var _ =
                        mockConstruction(
                                DatabaseLifecycle.class,
                                (mock, context) -> {
                                    var persistence = mock(DungeonDatabase.class);
                                    when(persistence.players()).thenReturn(mock(PlayerData.class));
                                    when(mock.database()).thenReturn(persistence);
                                    when(mock.ready()).thenReturn(true);
                                });
                var _ = mockStatic(DatabaseLifecycle.class);
                var _ =
                        mockConstruction(
                                CoreBootstrap.class,
                                (mock, context) ->
                                        doThrow(original).when(mock).logStartupSummary());
                var _ = mockConstruction(RegistryBootstrap.class);
                var _ = mockConstruction(ListenerBootstrap.class);
                var _ = mockConstruction(MenuBootstrap.class);
                var _ = mockConstruction(DungeonService.class);
                var _ = mockConstruction(nl.hauntedmc.dungeons.experience.ExperienceService.class);
                var _ = mockConstruction(CommandBootstrap.class);
                var lifecycle =
                        mockConstruction(
                                LifecycleCoordinator.class,
                                (mock, context) -> doThrow(cleanup).when(mock).shutdown())) {
            var application = new DungeonsApplication(paper.plugin);
            assertSame(original, assertThrows(IllegalStateException.class, application::start));
            assertArrayEquals(new Throwable[] {cleanup}, original.getSuppressed());
            verify(lifecycle.constructed().getFirst()).shutdown();
            application.stop();
            verify(lifecycle.constructed().getFirst()).shutdown();
            assertThrows(IllegalStateException.class, RuntimeContext::runtime);
        }
    }
}
