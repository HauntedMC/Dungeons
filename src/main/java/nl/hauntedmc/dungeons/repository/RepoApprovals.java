package nl.hauntedmc.dungeons.repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Owner-thread, one-use approvals: expiry is checked on admission, not after long revalidation. */
final class RepoApprovals<T> {
    record Approval<T>(UUID token, UUID release, String revision, T payload, long expires) {}

    private final Map<String, Approval<T>> approvals = new HashMap<>();
    private final Deque<T> retired = new ArrayDeque<>();
    private final LongSupplier clock;

    RepoApprovals() {
        this(System::nanoTime);
    }

    RepoApprovals(LongSupplier clock) {
        this.clock = clock;
    }

    Approval<T> issue(String actor, UUID release, String revision, T payload) {
        if (approvals.size() >= 32 && !approvals.containsKey(actor))
            throw new IllegalArgumentException(
                    "Too many pending force confirmations; retry shortly");
        var approval =
                new Approval<>(
                        UUID.randomUUID(),
                        release,
                        revision,
                        payload,
                        clock.getAsLong() + TimeUnit.SECONDS.toNanos(30));
        var previous = approvals.put(actor, approval);
        if (previous != null) retired.add(previous.payload());
        return approval;
    }

    Approval<T> claim(String actor, String token) {
        var approval = approvals.get(actor);
        if (approval == null || !approval.token().toString().equals(token))
            throw new IllegalArgumentException(
                    "Invalid repository confirmation; request a new confirmation");
        approvals.remove(actor);
        if (clock.getAsLong() - approval.expires() >= 0) {
            retired.add(approval.payload());
            throw new IllegalArgumentException(
                    "Repository confirmation expired; request a new confirmation");
        }
        return approval;
    }

    List<T> expired() {
        var iterator = approvals.values().iterator();
        while (iterator.hasNext()) {
            var approval = iterator.next();
            if (clock.getAsLong() - approval.expires() >= 0) {
                retired.add(approval.payload());
                iterator.remove();
            }
        }
        var result = List.copyOf(retired);
        retired.clear();
        return result;
    }

    boolean containsPayload(java.util.function.Predicate<T> predicate) {
        return approvals.values().stream().anyMatch(approval -> predicate.test(approval.payload()));
    }

    void clear() {
        approvals.clear();
        retired.clear();
    }
}
