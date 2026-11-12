package nl.hauntedmc.dungeons.experience;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import nl.hauntedmc.dungeons.api.DungeonExperienceApi.Change;

/** Owner-thread subscriptions with independent, idempotent registration handles. */
final class ExperienceSubscriptions implements AutoCloseable {
    private final Map<UUID, List<Registration>> viewers = new HashMap<>();
    private final Runnable checkThread;
    private boolean closed;

    ExperienceSubscriptions(Runnable checkThread) {
        this.checkThread = Objects.requireNonNull(checkThread);
    }

    AutoCloseable subscribe(UUID viewer, Consumer<Change> listener) {
        checkThread.run();
        if (closed) throw new IllegalStateException("Experience subscriptions closed");
        var registration =
                new Registration(Objects.requireNonNull(viewer), Objects.requireNonNull(listener));
        viewers.computeIfAbsent(viewer, ignored -> new ArrayList<>()).add(registration);
        return registration;
    }

    void publish(UUID viewer, Change change, Consumer<RuntimeException> failureHandler) {
        checkThread.run();
        for (var registration : List.copyOf(viewers.getOrDefault(viewer, List.of()))) {
            if (!registration.active) continue;
            try {
                registration.listener.accept(change);
            } catch (RuntimeException failure) {
                failureHandler.accept(failure);
            }
        }
    }

    @Override
    public void close() {
        checkThread.run();
        closed = true;
        viewers.values().forEach(list -> list.forEach(value -> value.active = false));
        viewers.clear();
    }

    private final class Registration implements AutoCloseable {
        private final UUID viewer;
        private final Consumer<Change> listener;
        private boolean active = true;

        private Registration(UUID viewer, Consumer<Change> listener) {
            this.viewer = viewer;
            this.listener = listener;
        }

        @Override
        public void close() {
            checkThread.run();
            if (!active) return;
            active = false;
            var registrations = viewers.get(viewer);
            if (registrations != null) {
                registrations.remove(this);
                if (registrations.isEmpty()) viewers.remove(viewer);
            }
        }
    }
}
