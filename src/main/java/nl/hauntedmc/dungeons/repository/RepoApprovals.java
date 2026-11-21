package nl.hauntedmc.dungeons.repository;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Owner-thread, one-use approvals: expiry is checked on admission, not after long revalidation. */
final class RepoApprovals<T> {
    record Approval<T>(String command, UUID release, String revision, T payload, long expires) {}

    private final Map<String, Approval<T>> approvals = new HashMap<>();
    private final Deque<T> retired = new ArrayDeque<>();
    private final LongSupplier clock;

    RepoApprovals() {
        this(System::nanoTime);
    }

    RepoApprovals(LongSupplier clock) {
        this.clock = clock;
    }

    void issue(String actor, String command, UUID release, String revision, T payload) {
        if (approvals.size() >= 32 && !approvals.containsKey(actor))
            throw new IllegalArgumentException(
                    "Too many pending force confirmations; retry shortly");
        var approval =
                new Approval<>(
                        command,
                        release,
                        revision,
                        payload,
                        clock.getAsLong() + TimeUnit.SECONDS.toNanos(10));
        var previous = approvals.put(actor, approval);
        if (previous != null) retired.add(previous.payload());
    }

    Approval<T> claim(String actor, String command) {
        var approval = approvals.get(actor);
        if (approval == null || !approval.command().equals(command)) return null;
        approvals.remove(actor);
        if (clock.getAsLong() - approval.expires() >= 0) {
            retired.add(approval.payload());
            return null;
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

    void clear() {
        approvals.clear();
        retired.clear();
    }
}
