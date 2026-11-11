package nl.hauntedmc.dungeons.config;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Shared FIFO disk lanes. No Bukkit configuration or mutable model is accessed by a worker. */
final class ConfigurationIoQueue {
    private static final Map<Path, Lane> LANES = new HashMap<>();

    private static final Semaphore FILESYSTEM_WORKERS = new Semaphore(2);

    private ConfigurationIoQueue() {}

    static Path destination(File file) throws IOException {
        // Follow existing symlinks too, so aliases and separate wrappers use the same lane.
        return file.getCanonicalFile().toPath();
    }

    static CompletableFuture<Void> writeAsync(Path file, String snapshot, Plugin plugin) {
        var operation =
                new Operation<Void>(
                        () -> {
                            writeAtomically(file, snapshot);
                            return null;
                        });
        return enqueue(file, operation, plugin);
    }

    static <T> CompletableFuture<T> filesystemAsync(
            Path destination, Callable<T> action, Plugin plugin) {
        return enqueue(
                destination,
                new Operation<>(
                        () -> {
                            // Shutdown draining waits for filesystem work on a worker.
                            // The action cannot depend on server-thread callbacks.
                            var work =
                                    new FutureTask<T>(
                                            () -> {
                                                FILESYSTEM_WORKERS.acquire();
                                                try {
                                                    return action.call();
                                                } finally {
                                                    FILESYSTEM_WORKERS.release();
                                                }
                                            });
                            Thread.ofVirtual().name("Dungeons-filesystem").start(work);
                            boolean interrupted = false;
                            try {
                                while (true) {
                                    try {
                                        return work.get();
                                    } catch (InterruptedException ignored) {
                                        interrupted = true;
                                    }
                                }
                            } catch (ExecutionException failure) {
                                Throwable cause = failure.getCause();
                                if (cause instanceof Exception exception) throw exception;
                                if (cause instanceof Error error) throw error;
                                throw new IllegalStateException(cause);
                            } finally {
                                if (interrupted) Thread.currentThread().interrupt();
                            }
                        }),
                plugin);
    }

    private static <T> CompletableFuture<T> enqueue(
            Path file, Operation<T> operation, Plugin plugin) {
        Lane lane;
        boolean schedule;
        synchronized (LANES) {
            schedule = !LANES.containsKey(file);
            lane = LANES.computeIfAbsent(file, Lane::new);
            lane.pending.addLast(operation);
        }
        if (!plugin.isEnabled()) {
            drain(lane);
        } else if (schedule) {
            try {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> drain(lane));
            } catch (RuntimeException rejection) {
                reject(lane, rejection);
            }
        }
        return operation.result.copy();
    }

    static void writeNow(Path file, String snapshot) {
        executeNow(
                file,
                () -> {
                    writeAtomically(file, snapshot);
                    return null;
                });
    }

    static String readNow(Path file) {
        return executeNow(file, () -> Files.readString(file, StandardCharsets.UTF_8));
    }

    private static <T> T executeNow(Path file, Callable<T> action) {
        var operation = new Operation<>(action);
        Lane lane;
        synchronized (LANES) {
            lane = LANES.computeIfAbsent(file, Lane::new);
            lane.pending.addLast(operation);
        }
        // A queued Bukkit task need not run for a synchronous save or shutdown flush to progress.
        drain(lane);
        return operation.result.join();
    }

    static CompletableFuture<Void> barrier(Path directory) {
        synchronized (LANES) {
            var barriers = new ArrayList<CompletableFuture<?>>();
            for (var entry : LANES.entrySet()) {
                if (!entry.getKey().startsWith(directory)) continue;
                if (entry.getValue().running != null) barriers.add(entry.getValue().running.result);
                for (var pending : entry.getValue().pending) barriers.add(pending.result);
                var operation = new Operation<Void>(() -> null);
                entry.getValue().pending.addLast(operation);
                barriers.add(operation.result);
            }
            return CompletableFuture.allOf(barriers.toArray(CompletableFuture[]::new));
        }
    }

    static void flush() {
        while (true) {
            List<Lane> pending;
            synchronized (LANES) {
                if (LANES.isEmpty()) return;
                pending = new ArrayList<>(LANES.values());
            }
            for (Lane lane : pending) drain(lane);
        }
    }

    private static void drain(Lane lane) {
        lane.disk.lock();
        try {
            while (true) {
                Operation<?> operation;
                synchronized (LANES) {
                    operation = lane.pending.pollFirst();
                    if (operation == null) {
                        LANES.remove(lane.file, lane);
                        return;
                    }
                    lane.running = operation;
                }
                operation.execute();
                synchronized (LANES) {
                    lane.running = null;
                }
            }
        } finally {
            lane.disk.unlock();
        }
    }

    private static void reject(Lane lane, RuntimeException failure) {
        List<Operation<?>> rejected;
        lane.disk.lock();
        try {
            synchronized (LANES) {
                rejected = new ArrayList<>(lane.pending);
                lane.pending.clear();
                LANES.remove(lane.file, lane);
            }
        } finally {
            lane.disk.unlock();
        }
        for (Operation<?> operation : rejected) operation.result.completeExceptionally(failure);
    }

    private static void writeAtomically(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".configuration-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException | RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static final class Lane {
        final Path file;
        final ReentrantLock disk = new ReentrantLock();
        final Deque<Operation<?>> pending = new ArrayDeque<>();
        Operation<?> running;

        Lane(Path file) {
            this.file = file;
        }
    }

    private static final class Operation<T> {
        final Callable<T> action;
        final CompletableFuture<T> result = new CompletableFuture<>();

        Operation(Callable<T> action) {
            this.action = action;
        }

        void execute() {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        }
    }
}
