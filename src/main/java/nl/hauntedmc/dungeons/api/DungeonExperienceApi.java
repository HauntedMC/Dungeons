package nl.hauntedmc.dungeons.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/** Owner-thread, viewer-specific live gameplay projections. No historical or mutable world data. */
public interface DungeonExperienceApi {
    record Goal(
            String id,
            Component title,
            int progress,
            int target,
            boolean completed,
            boolean optional) {}

    record Snapshot(
            UUID dungeon,
            String state,
            Component title,
            String difficulty,
            int elapsedSeconds,
            Integer remainingSeconds,
            Integer lives,
            List<Goal> objectives) {
        public Snapshot {
            objectives = List.copyOf(objectives);
        }
    }

    record Change(UUID dungeon, String type, String id, int value) {}

    /** Current viewer projection, or empty when offline or outside a supported dungeon context. */
    Optional<Snapshot> view(UUID viewer);

    /**
     * Subscribe to revealed objective, track and encounter changes during this viewer's
     * participation, including later runs. No initial replay or HUD refresh events are sent.
     * Callbacks run on the owner thread; failures are logged without preventing delivery to other
     * subscribers. Each call creates an independent registration; its handle is idempotent and must
     * be closed on the owner thread when the consumer unloads. Closing during dispatch prevents
     * later delivery.
     */
    AutoCloseable subscribe(UUID viewer, Consumer<Change> listener);
}
