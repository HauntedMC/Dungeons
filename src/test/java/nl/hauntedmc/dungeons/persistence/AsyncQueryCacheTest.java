package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AsyncQueryCacheTest {
    @Test
    void coalescesRequestsWithoutSharingObserverCancellation() {
        var cache = new AsyncQueryCache<String, String>(2, Duration.ofSeconds(15));
        var load = new CompletableFuture<String>();
        var calls = new AtomicInteger();
        var first = cache.get("player", () -> { calls.incrementAndGet(); return load; });
        var second = cache.get("player", () -> { fail("duplicate loader"); return load; });
        first.cancel(true);
        load.complete("snapshot");
        assertEquals("snapshot", second.join());
        assertEquals(1, calls.get());
    }

    @Test
    void invalidatedOldLoadCannotReplaceANewerSnapshot() {
        var cache = new AsyncQueryCache<String, String>(2, Duration.ofSeconds(15));
        var old = new CompletableFuture<String>();
        var observer = cache.get("player", () -> old);
        cache.invalidate("player");
        assertEquals("new", cache.get("player", () -> CompletableFuture.completedFuture("new")).join());
        old.complete("old");
        assertEquals("old", observer.join());
        assertEquals("new", cache.get("player", () -> { fail("unexpected reload"); return old; }).join());
    }

    @Test
    void failuresAreNotEmptyResultsAndFailedLoadsCanRetry() {
        var cache = new AsyncQueryCache<String, String>(2, Duration.ofSeconds(15));
        assertThrows(CompletionException.class, () -> cache.get("player", () -> {
            throw new IllegalStateException("unavailable");
        }).join());
        assertEquals(0, cache.size());
        assertEquals("recovered", cache.get("player", () -> CompletableFuture.completedFuture("recovered")).join());
    }

    @Test
    void expiresCompletedResultsAndBoundsPendingWork() {
        var clock = new AtomicLong();
        var cache = new AsyncQueryCache<String, String>(1, Duration.ofSeconds(1), clock::get);
        assertEquals("first", cache.get("player", () -> CompletableFuture.completedFuture("first")).join());
        clock.set(Duration.ofSeconds(1).toNanos());
        var pending = new CompletableFuture<String>();
        var observer = cache.get("player", () -> pending);
        assertThrows(CompletionException.class, () -> cache.get("other", () -> {
            fail("overflow must not start a loader");
            return pending;
        }).join());
        assertEquals(1, cache.size());
        pending.complete("next");
        assertEquals("next", observer.join());
        assertEquals("other", cache.get("other", () -> CompletableFuture.completedFuture("other")).join());
        assertEquals(1, cache.size());
    }
}
