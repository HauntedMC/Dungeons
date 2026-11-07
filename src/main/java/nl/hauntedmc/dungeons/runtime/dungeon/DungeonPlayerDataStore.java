package nl.hauntedmc.dungeons.runtime.dungeon;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.function.reward.RewardFunction;
import nl.hauntedmc.dungeons.content.reward.PlayerLootData;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Owns one dungeon's player records and cooldown indexes, independent of worlds and editor UI. */
public final class DungeonPlayerDataStore {
    private final Plugin plugin;
    private final File directory;
    private final Map<UUID, ConfigurationFile> records = new LinkedHashMap<>();
    private final Map<UUID, PlayerLootData> rewards = new LinkedHashMap<>();
    private final Map<UUID, Date> access = new HashMap<>();
    private final Set<UUID> readOnly = new HashSet<>();

    public DungeonPlayerDataStore(Plugin plugin, File dungeonFolder) {
        requireOwnerThread();
        this.plugin = plugin;
        this.directory = new File(dungeonFolder, "players");
        try {
            Files.createDirectories(this.directory.toPath());
            File[] files = this.directory.listFiles();
            if (files == null) throw new java.io.IOException("Cannot list " + this.directory);
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".yml")) continue;
                UUID id;
                try {
                    id = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                } catch (IllegalArgumentException invalidName) {
                    plugin.getSLF4JLogger()
                            .warn("Ignoring player data with an invalid UUID filename '{}'.", file);
                    continue;
                }
                try {
                    var candidate = new ConfigurationFile(plugin);
                    ConfigurationFile.loadSnapshot(candidate, file);
                    records.put(id, candidate);
                    loadCooldowns(id);
                } catch (Exception failure) {
                    readOnly.add(id);
                    plugin.getSLF4JLogger()
                            .error(
                                    "Failed to load dungeon player data '{}'; writes to this record"
                                            + " are blocked.",
                                    file,
                                    failure);
                }
            }
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(
                    "Cannot initialize dungeon player storage " + directory, failure);
        }
    }

    /** Rebuilds indexes idempotently without duplicating records. */
    public void loadCooldowns(UUID id) {
        requireOwnerThread();
        var data = records.get(id);
        if (data == null) return;
        if (data.get("Finished") != null && !(data.get("Finished") instanceof Boolean)) {
            readOnly.add(id);
            plugin.getSLF4JLogger()
                    .warn(
                            "Invalid completion state for player '{}' in '{}'; writes are blocked.",
                            id,
                            directory);
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
                                directory);
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
                                directory);
            }
        }
    }

    private void requireWritable(UUID id) {
        requireOwnerThread();
        if (readOnly.contains(id))
            throw new IllegalStateException(
                    "Repair and reload unreadable dungeon player record " + target(id));
    }

    private ConfigurationFile record(UUID id) {
        requireWritable(id);
        return records.computeIfAbsent(id, ignored -> new ConfigurationFile(plugin));
    }

    /** Legacy live configuration view. Callers must mutate and save it on the server thread. */
    public @Nullable FileConfiguration getRecord(UUID id) {
        requireOwnerThread();
        return records.get(id);
    }

    public void setAccessCooldown(UUID id, Date reset) {
        requireWritable(id);
        access.put(id, new Date(reset.getTime()));
    }

    public boolean clearAccessCooldown(UUID id) {
        requireWritable(id);
        return access.remove(id) != null;
    }

    public boolean hasAccessCooldown(UUID id) {
        requireOwnerThread();
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
        requireOwnerThread();
        return rewards.get(id);
    }

    private PlayerLootData rewards(Player player) {
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
    }

    public void save(Player player) {
        UUID id = player.getUniqueId();
        // ConfigurationFile captures on the owning thread and uses the shared atomic writer.
        record(id).save(target(id));
    }

    public boolean isCompleted(UUID id) {
        requireOwnerThread();
        var data = records.get(id);
        return data != null && data.getBoolean("Finished", false);
    }

    public void setCompleted(UUID id, boolean completed) {
        record(id).set("Finished", completed);
    }

    private static void requireOwnerThread() {
        if (org.bukkit.Bukkit.getServer() != null && !org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Dungeon player records require the server thread");
        }
    }

    private File target(UUID id) {
        return new File(directory, id + ".yml");
    }
}
