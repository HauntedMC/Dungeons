package nl.hauntedmc.dungeons.persistence;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Bounded presentation cache. Never use a cached result to authorize a gameplay mutation. */
public final class AsyncQueryCache<K, V> {
    private static final class Entry<V> {
        final CompletableFuture<V> result = new CompletableFuture<>();
        long expiresAt;
    }

    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private final int maximumSize;
    private final long ttlNanos;
    private final LongSupplier clock;
    private int pendingLoads;

    public AsyncQueryCache(int maximumSize, Duration ttl) {
        this(maximumSize, ttl, System::nanoTime);
    }

    AsyncQueryCache(int maximumSize, Duration ttl, LongSupplier clock) {
        if (maximumSize < 1 || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Cache size and TTL must be positive");
        }
        this.maximumSize = maximumSize;
        this.ttlNanos = ttl.toNanos();
        this.clock = Objects.requireNonNull(clock);
    }

    public CompletableFuture<V> get(K key, Supplier<? extends CompletionStage<V>> loader) {
        Entry<V> entry;
        synchronized (entries) {
            long now = clock.getAsLong();
            Entry<V> current = entries.get(key);
            if (current != null && (!current.result.isDone() || now - current.expiresAt < 0)) {
                return observe(current.result);
            }
            if (current != null) entries.remove(key);
            if (pendingLoads >= maximumSize) return CompletableFuture.failedFuture(
                    new RejectedExecutionException("Presentation cache load limit reached"));
            if (entries.size() >= maximumSize) {
                Iterator<Map.Entry<K, Entry<V>>> iterator = entries.entrySet().iterator();
                boolean evicted = false;
                while (iterator.hasNext()) {
                    if (iterator.next().getValue().result.isDone()) {
                        iterator.remove();
                        evicted = true;
                        break;
                    }
                }
                if (!evicted) return CompletableFuture.failedFuture(
                        new RejectedExecutionException("Presentation cache load limit reached"));
            }
            entry = new Entry<>();
            entries.put(key, entry);
            pendingLoads++;
        }
        // Loading and arbitrary dependent callbacks must never run while holding the cache lock.
        try {
            loader.get().whenComplete((value, failure) -> {
                synchronized (entries) {
                    pendingLoads--;
                    if (failure != null) entries.remove(key, entry);
                    else entry.expiresAt = clock.getAsLong() + ttlNanos;
                }
                if (failure != null) entry.result.completeExceptionally(failure);
                else entry.result.complete(value);
            });
        } catch (RuntimeException failure) {
            synchronized (entries) { pendingLoads--; entries.remove(key, entry); }
            entry.result.completeExceptionally(failure);
        }
        return observe(entry.result);
    }

    public void invalidate(K key) {
        synchronized (entries) { entries.remove(key); }
    }

    public void clear() {
        synchronized (entries) { entries.clear(); }
    }

    public int size() {
        synchronized (entries) { return entries.size(); }
    }

    private CompletableFuture<V> observe(CompletableFuture<V> result) {
        // Observer cancellation cannot cancel work shared with other callers.
        return result.thenApply(value -> value);
    }
}
