package nl.hauntedmc.dungeons.runtime.dungeon;

import java.util.*;
import java.util.function.Consumer;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.persistence.StateDocument;
import nl.hauntedmc.dungeons.persistence.StateStore;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/** Owns one dungeon's player records and cooldown indexes, independent of worlds and editor UI. */
public final class DungeonPlayerDataStore {
    private final Plugin plugin;
    private final String scope;
    private final StateStore store;
    private final Map<UUID, StateDocument> documents = new HashMap<>();
    private final Map<UUID, ConfigurationFile> records = new LinkedHashMap<>();

    private final Map<UUID, Date> access = new HashMap<>();
    private final Set<UUID> readOnly = new HashSet<>();

    public DungeonPlayerDataStore(Plugin plugin, UUID dungeonId) {
        requireOwnerThread();
        this.plugin = plugin;
        this.scope = "dungeon-progress/" + dungeonId;
        this.store = RuntimeContext.runtime().stateStore();
    }

    private void load(UUID id) {
        requireOwnerThread();
        if (records.containsKey(id)) return;
        try {
            var candidate = new ConfigurationFile(plugin);
            document(id).loadInto(candidate);
            records.put(id, candidate);
            loadCooldowns(id);
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException("Invalid database dungeon progression " + id, failure);
        }
    }

    private StateDocument document(UUID id) {
        return documents.computeIfAbsent(
                id, ignored -> store.document(new StateStore.Key(scope, id.toString(), id)));
    }

    /** Rebuilds indexes idempotently without duplicating records. */
    public void loadCooldowns(UUID id) {
        requireOwnerThread();
        if (!records.containsKey(id)) {
            load(id);
            return;
        }
        var data = records.get(id);
        if (data == null) return;
        if (data.get("Finished") != null && !(data.get("Finished") instanceof Boolean)) {
            readOnly.add(id);
            plugin.getSLF4JLogger()
                    .warn(
                            "Invalid completion state for player '{}' in '{}'; writes are blocked.",
                            id,
                            scope);
        }
        Object reset = data.get("AccessCooldown");
        if (reset instanceof Long || reset instanceof Integer)
            access.put(id, new Date(((Number) reset).longValue()));
        else {
            access.remove(id);
            if (reset != null) {
                readOnly.add(id);
                plugin.getSLF4JLogger()
                        .warn(
                                "Invalid access cooldown for player '{}' in '{}'; writes are"
                                        + " blocked.",
                                id,
                                scope);
            }
        }
    }

    private void requireWritable(UUID id) {
        requireOwnerThread();
        if (readOnly.contains(id))
            throw new IllegalStateException(
                    "Repair and reload unreadable dungeon player record " + scope + "/" + id);
    }

    private ConfigurationFile record(UUID id) {
        load(id);
        requireWritable(id);
        return records.computeIfAbsent(id, ignored -> new ConfigurationFile(plugin));
    }

    public void setAccessCooldown(UUID id, Date reset) {
        load(id);
        requireWritable(id);
        correct(id, candidate -> candidate.set("AccessCooldown", reset.getTime()));
    }

    public boolean clearAccessCooldown(UUID id) {
        load(id);
        requireWritable(id);
        if (!access.containsKey(id)) return false;
        correct(id, candidate -> candidate.set("AccessCooldown", null));
        return true;
    }

    public boolean hasAccessCooldown(UUID id) {
        load(id);
        Date reset = access.get(id);
        if (reset != null && System.currentTimeMillis() > reset.getTime()) {
            access.remove(id);
            return false;
        }
        return reset != null;
    }

    public @Nullable Date getAccessCooldown(UUID id) {
        return hasAccessCooldown(id) ? new Date(access.get(id).getTime()) : null;
    }

    public void stageCooldowns(Player player) {
        UUID id = player.getUniqueId();
        var data = record(id);
        Date reset = access.get(id);
        data.set("AccessCooldown", reset == null ? null : reset.getTime());
        save(player);
    }

    public void save(Player player) {
        UUID id = player.getUniqueId();
        // Capture the YAML codec on the owning thread; the database owns durability.
        document(id).save(record(id).saveToString());
    }

    public boolean isCompleted(UUID id) {
        load(id);
        var data = records.get(id);
        return data != null && data.getBoolean("Finished", false);
    }

    public void setCompleted(UUID id, boolean completed) {
        correct(id, candidate -> candidate.set("Finished", completed));
    }

    /** Captures staged indexes, writes the candidate, then commits indexes after durability. */
    public void correct(UUID id, Consumer<ConfigurationFile> change) {
        load(id);
        requireWritable(id);
        var candidate = new ConfigurationFile(plugin);
        try {
            var existing = records.get(id);
            if (existing != null) candidate.loadFromString(existing.saveToString());
            Date reset = access.get(id);
            candidate.set("AccessCooldown", reset == null ? null : reset.getTime());
            change.accept(candidate);
            document(id).save(candidate.saveToString());
            records.put(id, candidate);
            loadCooldowns(id);
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException("Cannot stage player correction", failure);
        }
    }

    /** Release decoded owner state after the session has durably expired; database rows remain. */
    public void evictPlayer(UUID id) {
        requireOwnerThread();
        documents.remove(id);
        records.remove(id);
        access.remove(id);
        readOnly.remove(id);
    }

    public boolean isReadOnly(UUID id) {
        return readOnly.contains(id);
    }

    public Map<String, Object> inspect(UUID id) {
        var result = new LinkedHashMap<String, Object>();
        result.put("completed", isCompleted(id));
        result.put("access cooldown", Objects.toString(getAccessCooldown(id), "none"));

        result.put("protected record", readOnly.contains(id));
        return Map.copyOf(result);
    }

    private static void requireOwnerThread() {
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Dungeon player records require the server thread");
        }
    }
}
