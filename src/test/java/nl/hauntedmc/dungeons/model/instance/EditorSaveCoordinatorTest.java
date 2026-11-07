package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;

import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class EditorSaveCoordinatorTest {
    @Test
    @Timeout(10)
    void completingAWriteCannotOverwriteThePhaseOfASubsequentlyAdmittedSave() throws Exception {
        try (var paper = new PaperBoundary();
                var worker = Executors.newSingleThreadExecutor()) {
            var released = new CountDownLatch(1);
            var newSaveAdmitted = new CountDownLatch(1);
            var saving = new AtomicBoolean();
            var writes = new AtomicInteger();
            var firstWrite = new CompletableFuture<Boolean>();
            var secondWrite = new CompletableFuture<Boolean>();
            var coordinator =
                    new EditorSaveCoordinator(
                            paper.plugin,
                            "test",
                            new EditorSaveCoordinator.Target() {
                                public boolean canSave(boolean finalSave) {
                                    return true;
                                }

                                public boolean isSaving() {
                                    return saving.get();
                                }

                                public void markSaving(boolean value) {
                                    saving.set(value);
                                    if (!value && writes.get() == 1) {
                                        released.countDown();
                                        try {
                                            assertTrue(newSaveAdmitted.await(3, TimeUnit.SECONDS));
                                        } catch (InterruptedException failure) {
                                            throw new AssertionError(failure);
                                        }
                                    }
                                }

                                public void captureSnapshot() {}

                                public CompletableFuture<Boolean> persistSnapshot() {
                                    return writes.incrementAndGet() == 1 ? firstWrite : secondWrite;
                                }

                                public void flushRecoverySnapshot() {
                                    fail("Recovery must not be required");
                                }
                            });
            var first = coordinator.save(null);
            var completed = worker.submit(() -> firstWrite.complete(true));
            try {
                assertTrue(released.await(3, TimeUnit.SECONDS));
                var second = coordinator.save(null);
                newSaveAdmitted.countDown();
                completed.get(3, TimeUnit.SECONDS);
                assertTrue(first.join());
                assertFalse(second.isDone());
                assertTrue(saving.get());
                assertEquals(EditorSaveCoordinator.State.WRITING, coordinator.state());
                secondWrite.complete(true);
                assertTrue(second.join());
                assertFalse(saving.get());
                assertEquals(EditorSaveCoordinator.State.IDLE, coordinator.state());
            } finally {
                newSaveAdmitted.countDown();
                secondWrite.complete(true);
            }
        }
    }
}
