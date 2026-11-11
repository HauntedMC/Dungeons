package nl.hauntedmc.dungeons.persistence;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Owner-thread codec with a durable compare-and-set barrier before publishing changed state. */
public final class StateDocument {
    private final StateStore store;
    private final StateStore.Key key;
    private StateStore.Snapshot snapshot;

    StateDocument(StateStore store, StateStore.Key key, StateStore.Snapshot snapshot) {
        this.store = store;
        this.key = key;
        this.snapshot = snapshot;
    }

    public void loadInto(YamlConfiguration target) throws InvalidConfigurationException {
        target.loadFromString(snapshot.payload());
    }

    public record Prepared(StateDocument document, StateStore.Write write) {}

    public Prepared prepare(String payload) {
        store.requireReady();
        return new Prepared(
                this, new StateStore.Write(key, snapshot.revision(), payload, UUID.randomUUID()));
    }

    public static void commitTogether(Prepared... changes) {
        var active = Arrays.stream(changes).filter(Objects::nonNull).toList();
        if (active.isEmpty()) return;
        var store = active.getFirst().document.store;
        if (active.stream().anyMatch(change -> change.document.store != store))
            throw new IllegalArgumentException("Documents must belong to the same database store");
        store.requireReady();
        var snapshots = store.await(store.saveAll(active.stream().map(Prepared::write).toList()));
        for (int index = 0; index < active.size(); index++)
            active.get(index).document.snapshot = snapshots.get(index);
    }

    public void save(String payload) {
        store.requireReady();
        if (payload.equals(snapshot.payload())) return;
        commitTogether(prepare(payload));
    }

    public void refresh() {
        snapshot = store.await(store.refresh(key));
    }

    public long revision() {
        return snapshot.revision();
    }
}
