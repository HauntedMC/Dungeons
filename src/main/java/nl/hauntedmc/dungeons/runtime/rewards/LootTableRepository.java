package nl.hauntedmc.dungeons.runtime.rewards;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Persistence-backed repository of configured loot tables.
 */
public final class LootTableRepository {
    private final DungeonsPlugin plugin;
    private final Map<String, LootTable> tables = new HashMap<>();
    private FileConfiguration tablesConfig;

    /** Creates a repository and performs an initial load from disk. */
    public LootTableRepository(DungeonsPlugin plugin) {
        this.plugin = plugin;
        this.reload();
    }

    /** Reloads loot tables from disk into this stable manager instance. */
    public void reload() {
        this.tables.clear();
        try {
            File tablesFile = new File(this.plugin.getDataFolder(), "loottables.yml");
            if (!tablesFile.exists()) {
                tablesFile.createNewFile();
            }

            this.tablesConfig = new YamlConfiguration();
            this.tablesConfig.load(tablesFile);
            ConfigurationSection tablesSection = this.tablesConfig.getConfigurationSection("Tables");
            if (tablesSection == null) {
                return;
            }

            for (String path : tablesSection.getKeys(false)) {
                this.tables.put(path, (LootTable) tablesSection.get(path));
            }
        } catch (IOException | InvalidConfigurationException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to initialize loot tables from '{}'.",
                            new File(this.plugin.getDataFolder(), "loottables.yml").getAbsolutePath(),
                            exception);
        }
    }

    /** Inserts or replaces one loot table and persists the file. */
    public void put(LootTable table) {
        this.tables.put(table.getNamespace(), table);
        this.saveTablesConfig();
    }

    /** Returns whether a loot table namespace exists. */
    public boolean contains(String namespace) {
        return this.tables.containsKey(namespace);
    }

    /** Returns one loot table by namespace, or null when absent. */
    public LootTable get(String namespace) {
        return this.tables.get(namespace);
    }

    /** Returns all loaded loot tables. */
    public Collection<LootTable> getTables() {
        return this.tables.values();
    }

    /** Removes one loot table and persists the file. */
    public void remove(String namespace) {
        this.tables.remove(namespace);
        this.saveTablesConfig();
    }

    /** Writes the current in-memory table map to {@code loottables.yml}. */
    public void saveTablesConfig() {
        this.tablesConfig.set("Tables", this.tables);
        File tablesFile = new File(this.plugin.getDataFolder(), "loottables.yml");

        try {
            this.tablesConfig.save(tablesFile);
        } catch (IOException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .error("Failed to save loot tables to '{}'.", tablesFile.getAbsolutePath(), exception);
        }
    }
}
