package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class ConfigurationFileTest {
    @TempDir Path directory;

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void filesystemOperationsShareCanonicalOrderAndShutdownNeverRunsTheirActionsOnTheOwner()
            throws Exception {
        Thread owner = Thread.currentThread();
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            Path template = Files.createDirectory(directory.resolve("template"));
            var order = new java.util.concurrent.CopyOnWriteArrayList<Integer>();
            var first =
                    ConfigurationFile.filesystemAsync(
                            paper.plugin,
                            template.toFile(),
                            () -> {
                                assertNotSame(owner, Thread.currentThread());
                                order.add(1);
                                return true;
                            });
            var second =
                    ConfigurationFile.filesystemAsync(
                            paper.plugin,
                            template.resolve("../template").toFile(),
                            () -> {
                                assertNotSame(owner, Thread.currentThread());
                                order.add(2);
                                return true;
                            });
            assertEquals(1, scheduler.asynchronous.size());
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            when(paper.plugin.isEnabled()).thenReturn(false);
            ConfigurationFile.flushPendingSaves();
            assertTrue(first.join());
            assertTrue(second.join());
            assertEquals(java.util.List.of(1, 2), order);
            scheduler.asynchronous.getFirst().run();
            assertEquals(java.util.List.of(1, 2), order);
        }
    }

    @Test
    void serializerErrorsCompleteTheRequestWithoutLeavingPendingWork() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var config =
                    new ConfigurationFile(paper.plugin) {
                        @Override
                        public String saveToString() {
                            throw new AssertionError("broken serializer");
                        }
                    };
            var file = directory.resolve("error.yml");
            Files.writeString(file, "previous: value\n");
            assertFalse(config.saveAsync(file.toFile()).get(1, TimeUnit.SECONDS));
            assertTrue(scheduler.asynchronous.isEmpty());
            assertEquals("previous: value\n", Files.readString(file));
        }
    }

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void activeWorkerAndSynchronousWriterPreserveOneDestinationOrderAcrossThreads()
            throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        try (var paper = new PaperBoundary();
                var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var file = directory.resolve("concurrent.yml");
            var config = new ConfigurationFile(paper.plugin);
            config.set("value", 1);
            var first = config.saveAsync(file.toFile());
            var observer =
                    first.thenRun(
                            () -> {
                                entered.countDown();
                                try {
                                    if (!release.await(3, TimeUnit.SECONDS))
                                        throw new AssertionError("worker release timed out");
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(interrupted);
                                }
                            });
            var worker = executor.submit(scheduler.asynchronous.getFirst()::run);
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                config.set("value", 2);
                var second = config.saveAsync(file.toFile());
                var secondWritten = second.thenRun(() -> assertEquals("value: 2\n", read(file)));
                var synchronous =
                        executor.submit(
                                () ->
                                        ConfigurationIoQueue.writeNow(
                                                file.toFile().getAbsoluteFile().toPath(),
                                                "value: 3\n"));
                release.countDown();
                worker.get(3, TimeUnit.SECONDS);
                synchronous.get(3, TimeUnit.SECONDS);
                observer.get(3, TimeUnit.SECONDS);
                secondWritten.get(3, TimeUnit.SECONDS);
                assertEquals("value: 3\n", Files.readString(file));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void failedFlushNeverTruncatesTheLastGoodConfigurationAndSynchronousFailureIsVisible()
            throws Exception {
        var config = new ConfigurationFile(disabledPlugin());
        var file = directory.resolve("atomic.yml");
        Files.writeString(file, "value: previous\n");
        var channel = mock(java.nio.channels.FileChannel.class);
        when(channel.write(any(java.nio.ByteBuffer.class)))
                .thenAnswer(
                        call -> {
                            java.nio.ByteBuffer bytes = call.getArgument(0);
                            int written = bytes.remaining();
                            bytes.position(bytes.limit());
                            return written;
                        });
        doThrow(new java.io.IOException("disk full")).when(channel).force(true);
        try (var channels = mockStatic(java.nio.channels.FileChannel.class)) {
            channels.when(
                            () ->
                                    java.nio.channels.FileChannel.open(
                                            any(Path.class),
                                            eq(java.nio.file.StandardOpenOption.WRITE)))
                    .thenReturn(channel);
            config.set("value", "replacement");
            assertFalse(config.saveAsync(file.toFile()).join());
            assertThrows(java.io.UncheckedIOException.class, () -> config.save(file.toFile()));
        }
        assertEquals("value: previous\n", Files.readString(file));
        try (var files = Files.list(directory)) {
            assertEquals(java.util.List.of(file), files.toList());
        }
    }

    @Test
    void capturesNestedMutableValuesAndModelsBeforeWorkerExecution() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var serializations = new java.util.concurrent.atomic.AtomicInteger();
            var config =
                    new ConfigurationFile(paper.plugin) {
                        @Override
                        public String saveToString() {
                            assertTrue(org.bukkit.Bukkit.isPrimaryThread());
                            serializations.incrementAndGet();
                            return super.saveToString();
                        }
                    };
            var nested = new java.util.HashMap<String, Object>();
            nested.put("value", "original");
            var location = new org.bukkit.Location(null, 1, 2, 3);
            config.set("nested", java.util.List.of(nested));
            config.set("location", location);
            var file = directory.resolve("snapshot.yml");
            var saved = config.saveAsync(file.toFile());
            assertEquals(1, serializations.get());
            nested.put("value", "modified");
            location.setX(99);
            config.set("later", true);
            paper.bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(false);
            scheduler.asynchronous.getFirst().run();
            assertTrue(saved.join());
            assertEquals(1, serializations.get());
            var text = Files.readString(file);
            assertTrue(text.contains("original"));
            assertTrue(text.contains("x: 1.0"));
            assertFalse(text.contains("modified"));
            assertFalse(text.contains("later:"));
        }
    }

    @Test
    void separateWrappersAndNormalizedPathsWriteSnapshotsInAdmissionOrder() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var first = new ConfigurationFile(paper.plugin);
            var second = new ConfigurationFile(paper.plugin);
            first.set("value", "first");
            second.set("value", "second");
            var file = directory.resolve("ordered.yml");
            var observed = new java.util.ArrayList<String>();
            var one = first.saveAsync(file.toFile());
            one.thenRun(() -> observed.add(read(file)));
            var two = second.saveAsync(directory.resolve("unused/../ordered.yml").toFile());
            two.thenRun(() -> observed.add(read(file)));
            assertEquals(1, scheduler.asynchronous.size());
            scheduler.asynchronous.getFirst().run();
            assertTrue(one.join());
            assertTrue(two.join());
            assertEquals(java.util.List.of("value: first\n", "value: second\n"), observed);
            assertEquals("value: second\n", Files.readString(file));
        }
    }

    @Test
    void synchronousSaveDrainsOlderWritesAndStaleWorkerCannotOverwriteNewerLane() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var config = new ConfigurationFile(paper.plugin);
            var file = directory.resolve("sync.yml");
            config.set("value", 1);
            var first = config.saveAsync(file.toFile());
            config.set("value", 2);
            config.save(file.toFile());
            assertTrue(first.join());
            assertEquals("value: 2\n", Files.readString(file));
            config.set("value", 3);
            var latest = config.saveAsync(file.toFile());
            scheduler.asynchronous.getFirst().run();
            assertFalse(latest.isDone());
            assertEquals("value: 2\n", Files.readString(file));
            scheduler.asynchronous.getLast().run();
            assertTrue(latest.join());
            assertEquals("value: 3\n", Files.readString(file));
        }
    }

    @Test
    void loadDrainsEarlierWritesAndUnrelatedFilesCanProgressIndependently() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var first = new ConfigurationFile(paper.plugin);
            var second = new ConfigurationFile(paper.plugin);
            first.set("value", 1);
            second.set("value", 2);
            var one = first.saveAsync(directory.resolve("one.yml").toFile());
            var two = second.saveAsync(directory.resolve("two.yml").toFile());
            scheduler.asynchronous.getLast().run();
            assertTrue(two.join());
            assertFalse(one.isDone());
            var reader = new ConfigurationFile(paper.plugin);
            reader.load(directory.resolve("one.yml").toFile());
            assertTrue(one.join());
            assertEquals(1, reader.getInt("value"));
            var ordinary = new org.bukkit.configuration.file.YamlConfiguration();
            first.set("value", 3);
            var third = first.saveAsync(directory.resolve("one.yml").toFile());
            ConfigurationFile.loadSnapshot(ordinary, directory.resolve("one.yml").toFile());
            assertTrue(third.join());
            assertEquals(3, ordinary.getInt("value"));
            scheduler.asynchronous.getFirst().run();
        }
    }

    @Test
    void offThreadCaptureWaitsForOwnerAndShutdownCanExecuteACancelledOwnerTask() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var captures = new java.util.concurrent.atomic.AtomicInteger();
            var config =
                    new ConfigurationFile(paper.plugin) {
                        @Override
                        public String saveToString() {
                            assertTrue(org.bukkit.Bukkit.isPrimaryThread());
                            captures.incrementAndGet();
                            return super.saveToString();
                        }
                    };
            config.set("value", 1);
            paper.bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(false);
            var file = directory.resolve("shutdown.yml");
            var saved = config.saveAsync(file.toFile());
            assertEquals(0, captures.get());
            assertFalse(saved.isDone());
            when(paper.plugin.isEnabled()).thenReturn(false);
            paper.bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
            config.set("value", 2);
            ConfigurationFile.flushPendingSaves();
            assertTrue(saved.join());
            assertEquals(1, captures.get());
            assertEquals("value: 2\n", Files.readString(file));
            scheduler.immediate.getFirst().run();
            assertEquals(1, captures.get());
        }
    }

    @Test
    void cancelledObserverDoesNotDiscardItsQueuedWriteAndShutdownFlushPreservesOrder()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var config = new ConfigurationFile(paper.plugin);
            var file = directory.resolve("cancelled.yml");
            config.set("value", 1);
            var cancelled = config.saveAsync(file.toFile());
            assertTrue(cancelled.cancel(false));
            config.set("value", 2);
            var latest = config.saveAsync(file.toFile());
            when(paper.plugin.isEnabled()).thenReturn(false);
            ConfigurationFile.flushPendingSaves();
            assertTrue(latest.join());
            assertEquals("value: 2\n", Files.readString(file));
            scheduler.asynchronous.getFirst().run();
            assertEquals("value: 2\n", Files.readString(file));
        }
    }

    @Test
    void failedWriteDoesNotPoisonLaterSnapshotsForTheSameDestination() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var file = Files.createDirectory(directory.resolve("repair.yml"));
            Files.writeString(file.resolve("obstacle"), "keep");
            var config = new ConfigurationFile(paper.plugin);
            config.set("value", 1);
            var first = config.saveAsync(file.toFile());
            first.thenRun(
                    () -> {
                        try {
                            Files.delete(file.resolve("obstacle"));
                            Files.delete(file);
                        } catch (java.io.IOException failure) {
                            throw new java.io.UncheckedIOException(failure);
                        }
                    });
            config.set("value", 2);
            var second = config.saveAsync(file.toFile());
            scheduler.asynchronous.getFirst().run();
            assertFalse(first.join());
            assertTrue(second.join());
            assertEquals("value: 2\n", Files.readString(file));
            try (var files = Files.list(directory)) {
                assertEquals(java.util.List.of(file), files.toList());
            }
        }
    }

    @Test
    void symbolicDirectoryAliasesUseOneDestinationLane() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var real = Files.createDirectory(directory.resolve("real"));
            var alias = directory.resolve("alias");
            try {
                Files.createSymbolicLink(alias, real);
            } catch (UnsupportedOperationException | java.io.IOException unsupported) {
                org.junit.jupiter.api.Assumptions.abort("Symlinks unavailable");
            }
            var config = new ConfigurationFile(paper.plugin);
            config.set("value", 1);
            var first = config.saveAsync(real.resolve("config.yml").toFile());
            config.set("value", 2);
            var second = config.saveAsync(alias.resolve("config.yml").toFile());
            assertEquals(1, scheduler.asynchronous.size());
            scheduler.asynchronous.getFirst().run();
            assertTrue(first.join());
            assertTrue(second.join());
            assertEquals("value: 2\n", Files.readString(real.resolve("config.yml")));
            assertTrue(Files.isSymbolicLink(alias));
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    @Test
    void disabledPluginSavesInlineAndCompletesTheFuture() throws Exception {
        ConfigurationFile config = new ConfigurationFile(disabledPlugin());
        config.set("value", "saved");
        Path file = directory.resolve("config.yml");
        assertTrue(config.saveAsync(file.toFile()).get(1, TimeUnit.SECONDS));
        assertEquals("value: saved\n", Files.readString(file));
    }

    @Test
    void serializationFailuresCompleteTheFutureAsFailed() throws Exception {
        ConfigurationFile config =
                new ConfigurationFile(disabledPlugin()) {
                    @Override
                    public String saveToString() {
                        throw new IllegalArgumentException("broken payload");
                    }
                };
        assertFalse(
                config.saveAsync(directory.resolve("broken.yml").toFile())
                        .get(1, TimeUnit.SECONDS));
    }

    @Test
    void failedSavePreservesTheLastGoodFileAndItsLoadableValues() throws Exception {
        var fail = new AtomicBoolean();
        var config =
                new ConfigurationFile(disabledPlugin()) {
                    @Override
                    public String saveToString() {
                        if (fail.get()) throw new IllegalArgumentException("unserializable graph");
                        return super.saveToString();
                    }
                };
        var file = directory.resolve("durable.yml");
        config.set("progress.completed", 10);
        assertTrue(config.saveAsync(file.toFile()).join());
        byte[] previous = Files.readAllBytes(file);
        config.set("progress.completed", 20);
        fail.set(true);
        assertFalse(config.saveAsync(file.toFile()).join());
        assertArrayEquals(previous, Files.readAllBytes(file));
        var restored = new ConfigurationFile(disabledPlugin());
        restored.load(file.toFile());
        assertEquals(10, restored.getInt("progress.completed"));
    }

    @Test
    void ordinaryWriteFailuresCompleteAsFailedAndARepairedDestinationCanBeSavedAgain()
            throws Exception {
        var config = new ConfigurationFile(disabledPlugin());
        config.set("value", 23);
        var blocker = directory.resolve("blocked");
        Files.writeString(blocker, "existing file");
        var destination = blocker.resolve("config.yml");
        assertFalse(config.saveAsync(destination.toFile()).get(1, TimeUnit.SECONDS));
        assertEquals("existing file", Files.readString(blocker));
        Files.delete(blocker);
        Files.createDirectory(blocker);
        assertTrue(config.saveAsync(destination.toFile()).get(1, TimeUnit.SECONDS));
        var loaded = new ConfigurationFile(disabledPlugin());
        loaded.load(destination.toFile());
        assertEquals(23, loaded.getInt("value"));
        assertFalse(config.saveAsync(blocker.toFile()).get(1, TimeUnit.SECONDS));
        assertTrue(Files.isRegularFile(destination));
    }

    @Test
    void rejectedAsyncSaveSchedulingReturnsAFailedFutureWithoutTouchingTheLastGoodFile()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            doThrow(new IllegalPluginAccessException("plugin disabled during scheduling"))
                    .when(scheduler.scheduler)
                    .runTaskAsynchronously(eq(paper.plugin), any(Runnable.class));
            var file = directory.resolve("last-good.yml");
            Files.writeString(file, "value: 7\n");
            var config = new ConfigurationFile(paper.plugin);
            config.set("value", 9);
            var completion = assertDoesNotThrow(() -> config.saveAsync(file.toFile()));
            assertFalse(completion.get(1, TimeUnit.SECONDS));
            assertEquals("value: 7\n", Files.readString(file));
        }
    }

    private static Plugin disabledPlugin() {
        return (Plugin)
                Proxy.newProxyInstance(
                        Plugin.class.getClassLoader(),
                        new Class<?>[] {Plugin.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "isEnabled" -> false;
                                    case "getSLF4JLogger" ->
                                            LoggerFactory.getLogger(ConfigurationFileTest.class);
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
