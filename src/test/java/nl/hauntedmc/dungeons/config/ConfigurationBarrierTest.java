package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.*;
import nl.hauntedmc.dungeons.support.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationBarrierTest {
    @TempDir Path directory;

    @Test
    @Timeout(15)
    void repositoryBarrierIncludesAlreadyRunningWritesAndPropagatesTheirFailure() throws Exception {
        try (var paper = new PaperBoundary();
                var executor = Executors.newSingleThreadExecutor()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            Path folder = Files.createDirectories(directory.resolve("crypt"));
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var work =
                    ConfigurationFile.filesystemAsync(
                            paper.plugin,
                            folder.resolve("config.yml").toFile(),
                            () -> {
                                started.countDown();
                                if (!release.await(10, TimeUnit.SECONDS))
                                    throw new AssertionError("Test write did not release");
                                throw new IOException("Disk write failed");
                            });
            var scheduled = executor.submit(scheduler.asynchronous.getFirst()::run);
            assertTrue(started.await(5, TimeUnit.SECONDS));
            var barrier = ConfigurationFile.awaitPendingSaves(folder.toFile());
            assertFalse(barrier.isDone());
            release.countDown();
            scheduled.get(5, TimeUnit.SECONDS);
            assertThrows(CompletionException.class, work::join);
            assertThrows(CompletionException.class, barrier::join);
        }
    }
}
