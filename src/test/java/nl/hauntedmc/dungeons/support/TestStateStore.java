package nl.hauntedmc.dungeons.support;

import nl.hauntedmc.dungeons.persistence.StateStore;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** In-memory database boundary for owner-thread gameplay tests; never reads or writes local state. */
public final class TestStateStore implements StateStore {
    public final Map<Key, Snapshot> records = new LinkedHashMap<>();
    public boolean failWrites;
    public final Set<Key> failedWrites = new HashSet<>();
    public final List<String> audit = new ArrayList<>();
    @Override public void requireReady() { if (failWrites) throw new IllegalStateException("Database unavailable"); }
    @Override public CompletableFuture<Void> prefetch(UUID player) { return CompletableFuture.completedFuture(null); }
    @Override public CompletableFuture<Snapshot> load(Key key) {
        return CompletableFuture.completedFuture(records.getOrDefault(key, new Snapshot(0, "")));
    }
    @Override public CompletableFuture<Map<String, Snapshot>> list(String scope) {
        var result = new LinkedHashMap<String, Snapshot>();
        records.forEach((key, value) -> { if (key.scope().equals(scope)) result.put(key.record(), value); });
        return CompletableFuture.completedFuture(Map.copyOf(result));
    }
    @Override public CompletableFuture<List<Snapshot>> saveAll(List<Write> writes) {
        var next = new LinkedHashMap<Key, Snapshot>();
        for (var write : writes) {
            var key = write.key();
            if (failWrites || failedWrites.contains(key)) return CompletableFuture.failedFuture(new IllegalStateException("Database unavailable"));
            var current = records.getOrDefault(key, new Snapshot(0, ""));
            if (current.revision() != write.revision() || next.containsKey(key))
                return CompletableFuture.failedFuture(new IllegalStateException("Stale or duplicate state revision"));
            next.put(key, new Snapshot(write.revision() + 1, write.payload()));
        }
        records.putAll(next);
        return CompletableFuture.completedFuture(List.copyOf(next.values()));
    }
    @Override public CompletableFuture<Void> audit(String message) {
        audit.add(message);
        return CompletableFuture.completedFuture(null);
    }
    @Override public <T> T await(CompletableFuture<T> operation) { return operation.join(); }
    public void seed(String scope, String record, UUID player, String payload) {
        var key = new Key(scope, record, player);
        records.put(key, new Snapshot(records.getOrDefault(key, new Snapshot(0, "")).revision() + 1, payload));
    }
    public String payload(String scope, String record) {
        return records.entrySet().stream().filter(e -> e.getKey().scope().equals(scope) && e.getKey().record().equals(record))
                .map(e -> e.getValue().payload()).findFirst().orElse("");
    }
    public void journal(Path key, String payload) {
        UUID id = player(key);
        seed("player-recovery", id.toString(), id, payload);
    }
    public YamlConfiguration yaml(String scope, String record) {
        var yaml = new YamlConfiguration();
        try { yaml.loadFromString(payload(scope, record)); }
        catch (InvalidConfigurationException failure) { throw new IllegalStateException(failure); }
        return yaml;
    }
    public static UUID player(Path key) {
        String value = key.getFileName().toString().replace(".recovery.yml", "").replace(".yml", "");
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)); }
    }
    public static UUID dungeon(Path key) { return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)); }
}
