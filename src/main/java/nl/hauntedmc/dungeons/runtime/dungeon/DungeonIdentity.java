package nl.hauntedmc.dungeons.runtime.dungeon;

import nl.hauntedmc.dungeons.config.ConfigurationFile;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Assigns a map identity once, before publishing any database or runtime references. */
public final class DungeonIdentity {
    private DungeonIdentity() {}

    /** Validates an existing ID without modifying or writing the map. */
    public static UUID existing(FileConfiguration config) {
        Object stored = config.get("dungeon.id");
        if (stored != null && !(stored instanceof String)) {
            throw new IllegalArgumentException("dungeon.id must be a UUID string");
        }
        String value = stored == null ? "" : ((String) stored).trim();
        if (!value.isEmpty()) {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("dungeon.id must be a complete UUID");
            }
            return id;
        }
        return null;
    }

    public static UUID initialize(Plugin plugin, FileConfiguration config, File destination) {
        UUID existing = existing(config);
        if (existing != null) return existing;
        Object stored = config.get("dungeon.id");
        UUID id = null;
        if (destination.isFile()) {
            var persisted = new YamlConfiguration();
            try { ConfigurationFile.loadSnapshot(persisted, destination); }
            catch (IOException | InvalidConfigurationException failure) {
                throw new IllegalStateException("Cannot recover existing dungeon identity", failure);
            }
            id = existing(persisted);
        }
        if (id == null) id = UUID.randomUUID();
        config.set("dungeon.id", id.toString());
        try {
            ConfigurationFile.saveSnapshot(plugin, destination, config.saveToString());
        } catch (RuntimeException failure) {
            config.set("dungeon.id", stored);
            throw failure;
        }
        return id;
    }
}
