package nl.hauntedmc.dungeons.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RepoApprovalsTest {
    @Test
    void repeatingTheSameRequestConsumesApprovalBeforeLengthyRevalidation() {
        var clock = new AtomicLong();
        var approvals = new RepoApprovals<String>(clock::get);
        UUID release = UUID.randomUUID();
        String command = "/dungeon repo pull crypt --force";
        approvals.issue("actor", command, release, "saved-hash", "verified-download");
        assertNull(approvals.claim("other", command));
        assertNull(approvals.claim("actor", command + " --version 1.0.0"));
        var admitted = approvals.claim("actor", command);
        assertNotNull(admitted);
        clock.addAndGet(TimeUnit.MINUTES.toNanos(10));
        assertEquals("saved-hash", admitted.revision());
        assertEquals(release, admitted.release());
        assertEquals("verified-download", admitted.payload());
        assertTrue(approvals.expired().isEmpty());
        assertNull(approvals.claim("actor", command));
    }

    @Test
    void expiredAndReplacedDownloadsAreRetiredWithoutAuthorizingAnything() {
        var clock = new AtomicLong();
        var approvals = new RepoApprovals<String>(clock::get);
        approvals.issue("actor", "first", UUID.randomUUID(), "old", "first-download");
        approvals.issue("actor", "second", UUID.randomUUID(), "new", "second-download");
        assertNull(approvals.claim("actor", "first"));
        clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
        assertNull(approvals.claim("actor", "second"));
        assertEquals(java.util.List.of("first-download", "second-download"), approvals.expired());
    }
}
