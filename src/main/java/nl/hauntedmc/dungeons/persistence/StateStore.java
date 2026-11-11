package nl.hauntedmc.dungeons.persistence;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Database-owned gameplay snapshots. Implementations never consult local gameplay files. */
public interface StateStore {
    record Key(String scope, String record, UUID player) {
        public Key {
            if (scope == null || !scope.matches("[A-Za-z0-9._:/-]{1,128}"))
                throw new IllegalArgumentException("Invalid state scope");
            if (player != null && !player.toString().equals(record))
                throw new IllegalArgumentException("Player record must use its canonical UUID");
            if (record == null || !record.matches("[A-Za-z0-9._:-]{1,96}"))
                throw new IllegalArgumentException("Invalid state record");
        }
    }

    record Snapshot(long revision, String payload) {
        public Snapshot {
            if (revision < 0 || payload == null)
                throw new IllegalArgumentException("Invalid state snapshot");
        }
    }

    record Write(Key key, long revision, String payload, UUID operation) {}

    void requireReady();

    default void requirePlayerReady(UUID player) {
        requireReady();
    }

    CompletableFuture<Void> prefetch(UUID player);

    CompletableFuture<Snapshot> load(Key key);

    default CompletableFuture<Snapshot> refresh(Key key) {
        return load(key);
    }

    CompletableFuture<Map<String, Snapshot>> list(String scope);

    default CompletableFuture<Snapshot> save(
            Key key, long revision, String payload, UUID operation) {
        return saveAll(List.of(new Write(key, revision, payload, operation)))
                .thenApply(List::getFirst);
    }

    /** All writes commit together, or none do; results preserve input order. */
    CompletableFuture<List<Snapshot>> saveAll(List<Write> writes);

    CompletableFuture<Void> audit(String message);

    <T> T await(CompletableFuture<T> operation);

    default StateDocument document(Key key) {
        return new StateDocument(this, key, await(load(key)));
    }

    default StateDocument document(Key key, Snapshot snapshot) {
        return new StateDocument(this, key, snapshot);
    }
}
