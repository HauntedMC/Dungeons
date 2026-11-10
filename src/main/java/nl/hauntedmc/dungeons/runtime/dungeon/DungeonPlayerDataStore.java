package nl.hauntedmc.dungeons.runtime.dungeon;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import nl.hauntedmc.dungeons.content.reward.PlayerLootData;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** Owns one dungeon's player records and cooldown indexes, independent of worlds and editor UI. */
public final class DungeonPlayerDataStore {
    private final Plugin plugin;
    private final String scope;
    private final nl.hauntedmc.dungeons.persistence.StateStore store;
    private final Map<UUID, nl.hauntedmc.dungeons.persistence.StateDocument> documents = new HashMap<>();
    private final Map<UUID, ConfigurationFile> records = new LinkedHashMap<>();
    private final Map<UUID, PlayerLootData> rewards = new LinkedHashMap<>();
    private final Map<UUID, Date> access = new HashMap<>();
    private final Set<UUID> readOnly = new HashSet<>();

    public DungeonPlayerDataStore(Plugin plugin, UUID dungeonId) {
        requireOwnerThread();
        this.plugin = plugin;
        this.scope = "dungeon-progress/" + dungeonId;
        this.store = nl.hauntedmc.dungeons.runtime.RuntimeContext.runtime().stateStore();
    }

    private void load(UUID id) {
        requireOwnerThread();
        if (records.containsKey(id)) return;
        try {
            var candidate = new ConfigurationFile(plugin);
            document(id).loadInto(candidate);
            records.put(id, candidate);
            loadCooldowns(id);
        } catch (org.bukkit.configuration.InvalidConfigurationException failure) {
            throw new IllegalStateException("Invalid database dungeon progression " + id, failure);
        }
    }

    private nl.hauntedmc.dungeons.persistence.StateDocument document(UUID id) {
        return documents.computeIfAbsent(id, ignored -> store.document(
                new nl.hauntedmc.dungeons.persistence.StateStore.Key(scope, id.toString(), id)));
    }

    /** Rebuilds indexes idempotently without duplicating records. */
    public void loadCooldowns(UUID id) {
        requireOwnerThread();
        if (!records.containsKey(id)) { load(id); return; }
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
        Object value = data.get("RewardsInfo");
        if (value instanceof PlayerLootData loot && id.equals(loot.getPlayerId()))
            rewards.put(id, loot);
        else {
            rewards.remove(id);
            if (value != null) {
                readOnly.add(id);
                plugin.getSLF4JLogger()
                        .warn(
                                "Invalid reward cooldown data for player '{}' in '{}'; other fields"
                                        + " remain readable and writes are blocked.",
                                id,
                                scope);
            }
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

    public @Nullable PlayerLootData getRewards(UUID id) {
        load(id);
        return rewards.get(id);
    }

    private PlayerLootData rewards(Player player) {
        load(player.getUniqueId());
        requireWritable(player.getUniqueId());
        return rewards.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerLootData(player));
    }

    public void addLootCooldown(Player player, RewardFunction reward, Date reset) {
        rewards(player).addLootCooldown(reward, reset);
    }

    public boolean hasLootCooldown(Player player, RewardFunction reward) {
        return rewards(player).hasLootOnCooldown(reward);
    }

    public List<PlayerLootData> getLootCooldowns() {
        requireOwnerThread();
        return List.copyOf(rewards.values());
    }

    public void stageCooldowns(Player player) {
        UUID id = player.getUniqueId();
        var data = record(id);
        data.set("RewardsInfo", rewards(player));
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
    public void correct(UUID id, java.util.function.Consumer<ConfigurationFile> change) {
        load(id);
        requireWritable(id);
        var candidate = new ConfigurationFile(plugin);
        try {
            var existing = records.get(id);
            if (existing != null) candidate.loadFromString(existing.saveToString());
            Date reset = access.get(id);
            candidate.set("AccessCooldown", reset == null ? null : reset.getTime());
            if (rewards.containsKey(id)) candidate.set("RewardsInfo", rewards.get(id));
            change.accept(candidate);
            document(id).save(candidate.saveToString());
            records.put(id, candidate);
            loadCooldowns(id);
        } catch (org.bukkit.configuration.InvalidConfigurationException failure) {
            throw new IllegalStateException("Cannot stage player correction", failure);
        }
    }

    /** Release decoded owner state after the session has durably expired; database rows remain. */
    public void evictPlayer(UUID id) {
        requireOwnerThread();
        documents.remove(id);
        records.remove(id);
        rewards.remove(id);
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
        result.put(
                "reward cooldowns", rewards.containsKey(id) && rewards.get(id).hasLootOnCooldown());
        result.put("protected record", readOnly.contains(id));
        return Map.copyOf(result);
    }

    private static void requireOwnerThread() {
        if (org.bukkit.Bukkit.getServer() != null && !org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Dungeon player records require the server thread");
        }
    }

}
