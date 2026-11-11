package nl.hauntedmc.dungeons.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RepoApprovalsTest {
    @Test
    void admissionConsumesAnActorBoundTokenBeforeLengthyRevalidation() {
        var clock = new AtomicLong();
        var approvals = new RepoApprovals<String>(clock::get);
        UUID release = UUID.randomUUID();
        var pending = approvals.issue("actor", release, "saved-hash", "verified-download");
        assertThrows(
                IllegalArgumentException.class,
                () -> approvals.claim("other", pending.token().toString()));
        var admitted = approvals.claim("actor", pending.token().toString());
        clock.addAndGet(TimeUnit.MINUTES.toNanos(10));
        assertEquals("saved-hash", admitted.revision());
        assertEquals(release, admitted.release());
        assertEquals("verified-download", admitted.payload());
        assertTrue(approvals.expired().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> approvals.claim("actor", pending.token().toString()));
    }

    @Test
    void expiredAndReplacedDownloadsAreRetiredWithoutAuthorizingAnything() {
        var clock = new AtomicLong();
        var approvals = new RepoApprovals<String>(clock::get);
        var first = approvals.issue("actor", UUID.randomUUID(), "old", "first-download");
        var second = approvals.issue("actor", UUID.randomUUID(), "new", "second-download");
        assertThrows(
                IllegalArgumentException.class,
                () -> approvals.claim("actor", first.token().toString()));
        clock.addAndGet(TimeUnit.SECONDS.toNanos(31));
        assertThrows(
                IllegalArgumentException.class,
                () -> approvals.claim("actor", second.token().toString()));
        assertEquals(java.util.List.of("first-download", "second-download"), approvals.expired());
    }
}
