package nl.hauntedmc.dungeons.api;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Public Bukkit service. Snapshot reads perform no I/O; disclosure checks are asynchronous. */
public interface DungeonsApi {
    enum Disclosure {
        ALLOW,
        DENY,
        UNAVAILABLE
    }

    record Dungeon(
            UUID id,
            String name,
            String displayName,
            String type,
            boolean available,
            int managedInstances) {}

    String serverId();

    boolean ready();

    /**
     * Last main-thread catalog snapshot, refreshed every second. Empty when persistence is
     * unavailable.
     */
    List<Dungeon> catalog();

    /**
     * Uncached historical/statistical disclosure check. Only ALLOW permits disclosure. Self access
     * is allowed; FRIENDS requires the authoritative ServerFeatures capability. This is not an
     * authorization check for administrative or gameplay mutations.
     */
    CompletionStage<Disclosure> canViewPlayerData(UUID viewer, UUID target);

    /**
     * Returns only PUBLIC canonical player IDs, in a batch of at most 512 distinct IDs. Public
     * rankings must filter candidates before assigning ranks or applying pagination. Fails
     * exceptionally if privacy cannot be established; never substitutes PUBLIC on failure.
     */
    CompletionStage<Set<Long>> publicPlayerIds(Collection<Long> candidates);
}
