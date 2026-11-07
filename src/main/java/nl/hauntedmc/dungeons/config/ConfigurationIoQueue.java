package nl.hauntedmc.dungeons.config;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

/** Shared FIFO disk lanes. No Bukkit configuration or mutable model is accessed by a worker. */
final class ConfigurationIoQueue {
    private static final Map<Path, Lane> LANES = new HashMap<>();

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
                }
                operation.execute();
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
