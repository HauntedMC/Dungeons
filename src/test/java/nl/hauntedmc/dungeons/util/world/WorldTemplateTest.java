package nl.hauntedmc.dungeons.util.world;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

class WorldTemplateTest {
    @TempDir Path directory;

    @Test
    @Timeout(10)
    void publicationFailureRestoresAllWorldPathsBeforeReleasingTemplateReaders() throws Exception {
        Path template = Files.createDirectory(directory.resolve("template"));
        Path source = Files.createDirectory(directory.resolve("live"));
        for (String entry : List.of("region", "entities")) {
            Files.createDirectory(template.resolve(entry));
            Files.writeString(template.resolve(entry + "/chunk.mca"), "old " + entry);
            Files.createDirectory(source.resolve(entry));
            Files.writeString(source.resolve(entry + "/chunk.mca"), "new " + entry);
        }
        Files.writeString(template.resolve("functions.yml"), "current metadata");
        var attemptingRead = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor();
                var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            var reader = new CompletableFuture<String>();
            files.when(() -> Files.move(any(Path.class), any(Path.class)))
                    .thenAnswer(
                            call -> {
                                Path from = call.getArgument(0);
                                Path to = call.getArgument(1);
                                if (from.getParent().getFileName().toString().equals("snapshot")
                                        && to.equals(template.resolve("entities"))) {
                                    assertEquals(
                                            "new region",
                                            Files.readString(template.resolve("region/chunk.mca")));
                                    executor.submit(
                                            () -> {
                                                attemptingRead.countDown();
                                                try {
                                                    WorldUtils.readTemplate(
                                                            template.toFile(),
                                                            () ->
                                                                    reader.complete(
                                                                            Files.readString(
                                                                                    template
                                                                                            .resolve(
                                                                                                    "region/chunk.mca"))));
                                                } catch (Throwable failure) {
                                                    reader.completeExceptionally(failure);
                                                }
                                            });
                                    assertTrue(attemptingRead.await(3, TimeUnit.SECONDS));
                                    assertFalse(reader.isDone());
                                    throw new IOException("publication failed");
                                }
                                return call.callRealMethod();
                            });
            assertThrows(
                    IOException.class,
                    () -> WorldUtils.saveTemplate(source.toFile(), template.toFile()));
            assertEquals("old region", reader.get(3, TimeUnit.SECONDS));
            assertEquals("old entities", Files.readString(template.resolve("entities/chunk.mca")));
            assertEquals("current metadata", Files.readString(template.resolve("functions.yml")));
        }
    }
}
