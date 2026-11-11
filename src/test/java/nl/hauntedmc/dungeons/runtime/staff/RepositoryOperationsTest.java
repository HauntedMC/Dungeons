package nl.hauntedmc.dungeons.runtime.staff;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import nl.hauntedmc.dungeons.support.StaffFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryOperationsTest {
    @TempDir Path directory;

    @Test
    void successfulPullResumesPriorStateButNeverClearsIndependentMaintenance() throws Exception {
        try (var fixture = new StaffFixture(directory)) {
            var first =
                    fixture.ops.repositoryDrain(
                            fixture.actor,
                            "repo.pull",
                            "repo-pull",
                            "crypt",
                            "Repository first",
                            () -> CompletableFuture.completedFuture("installed"));
            assertEquals(StaffOperations.State.SUCCEEDED, first.state);
            assertFalse(fixture.ops.maintenance("crypt"));
            var pending = new CompletableFuture<String>();
            var second =
                    fixture.ops.repositoryDrain(
                            fixture.actor,
                            "repo.pull",
                            "repo-pull",
                            "crypt",
                            "Repository second",
                            () -> pending);
            fixture.ops.maintenance(fixture.actor, "crypt", true, "Manual review");
            pending.complete("installed");
            fixture.flush();
            assertEquals(StaffOperations.State.SUCCEEDED, second.state);
            assertEquals("Manual review", fixture.ops.maintenanceReason("crypt"));
        }
    }

    @Test
    void publicationCancellationIsRejectedOnceCommitBeginsAndCancelledJobsStayCancelled()
            throws Exception {
        try (var fixture = new StaffFixture(directory)) {
            var committing = new AtomicBoolean();
            var op =
                    fixture.ops.trackRepository(
                            fixture.actor,
                            "repo.publish",
                            "repo-publish",
                            "crypt",
                            UUID.randomUUID(),
                            () -> !committing.get());
            committing.set(true);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> fixture.ops.cancel(fixture.actor, op.id.toString(), "Stop upload"));
            committing.set(false);
            fixture.ops.cancel(fixture.actor, op.id.toString(), "Stop upload");
            fixture.ops.finishRepository(fixture.actor, op, "completed", null);
            assertEquals(StaffOperations.State.CANCELLED, op.state);
        }
    }

    @Test
    void failedPullKeepsAdmissionsClosed() throws Exception {
        try (var fixture = new StaffFixture(directory)) {
            var op =
                    fixture.ops.repositoryDrain(
                            fixture.actor,
                            "repo.pull",
                            "repo-pull",
                            "crypt",
                            "Repository broken",
                            () ->
                                    CompletableFuture.failedFuture(
                                            new IllegalStateException("Disk full")));
            assertEquals(StaffOperations.State.FAILED, op.state);
            assertTrue(fixture.ops.maintenance("crypt"));
        }
    }
}
