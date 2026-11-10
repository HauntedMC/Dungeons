package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.junit.jupiter.api.Test;

class ApplicationFailureTest {
    @Test void failureAfterRuntimeInitializationCleansOnceAndPreservesTheOriginalError() {
        var original = new IllegalStateException("late startup failure");
        var cleanup = new IllegalStateException("cleanup failure");
        try (var paper = new PaperBoundary();
                var core = mockConstruction(CoreBootstrap.class,
                        (mock, context) -> doThrow(original).when(mock).logStartupSummary());
                var registry = mockConstruction(RegistryBootstrap.class);
                var listeners = mockConstruction(ListenerBootstrap.class);
                var menus = mockConstruction(MenuBootstrap.class);
                var commands = mockConstruction(CommandBootstrap.class);
                var lifecycle = mockConstruction(LifecycleCoordinator.class,
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
