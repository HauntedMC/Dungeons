package nl.hauntedmc.dungeons.runtime.rewards;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transactional loot-table storage. Mutable table models are owned by the server thread. */
public final class LootTableRepository {
    private final DungeonsPlugin plugin;
    private final File file;
    private volatile State state;
    private volatile boolean writable;

    private record State(Map<String, LootTable> tables, String document) {}

    /** Creates a repository and attempts its initial load without overwriting unreadable data. */
    public LootTableRepository(DungeonsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "loottables.yml");
        this.reload();
    }

    /**
     * Publishes a replacement only after the complete document and every table validate. On failure
     * the last working models remain available, and writes require a successful reload first.
     */
    public boolean reload() {
        requireOwnerThread();
        try {
            String document;
            try {
                document = ConfigurationFile.readSnapshot(this.file);
            } catch (NoSuchFileException absent) {
                // An absent file is a new installation only before any state has been accepted.
                if (this.state != null) throw absent;
                document = "Tables: {}\n";
            }
            var names = expectedTables(document);
            ConfigurationFile candidate = configuration();
            candidate.loadFromString(document);
            if (candidate.getConfigurationSection("Tables") == null && names.isEmpty()) {
                candidate.createSection("Tables");
            }
            var section = candidate.getConfigurationSection("Tables");
            var replacement = new LinkedHashMap<String, LootTable>();
            for (String name : names) {
                if (section == null || !(section.get(name) instanceof LootTable table)) {
                    throw new IllegalArgumentException("Invalid loot table entry: " + name);
                }
                validate(name, table);
                replacement.put(name, table);
            }
            State loaded =
                    new State(Collections.unmodifiableMap(replacement), candidate.saveToString());
            this.state = loaded;
            this.writable = true;
            return true;
        } catch (IOException | InvalidConfigurationException | RuntimeException failure) {
            this.writable = false;
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to reload loot tables from '{}'; keeping the last working data."
                                + " Writes are blocked until a successful reload.",
                            this.file.getAbsolutePath(),
                            failure);
            return false;
        }
    }

    /** Whether edits can be persisted without overwriting an unreadable replacement document. */
    public boolean isWritable() {
        return this.writable;
    }

    /** Inserts or replaces a table only after its complete configuration is durably saved. */
    public boolean put(LootTable table) {
        requireOwnerThread();
        if (!this.writable) return refuseWrite();
        var replacement = new LinkedHashMap<>(this.state.tables());
        replacement.put(table.getNamespace(), table);
        return persist(replacement);
    }

    public boolean contains(String namespace) {
        return this.get(namespace) != null;
    }

    public LootTable get(String namespace) {
        State current = this.state;
        return current == null ? null : current.tables().get(namespace);
    }

    /** A detached, unmodifiable collection; callers cannot remove repository entries through it. */
    public Collection<LootTable> getTables() {
        State current = this.state;
        return current == null ? List.of() : List.copyOf(current.tables().values());
    }

    /** Removes a table only after its replacement document is durably saved. */
    public boolean remove(String namespace) {
        requireOwnerThread();
        if (!this.writable) return refuseWrite();
        var replacement = new LinkedHashMap<>(this.state.tables());
        if (replacement.remove(namespace) == null) return true;
        return persist(replacement);
    }

    /** Persists current editor changes using ordered, atomic configuration replacement. */
    public boolean saveTablesConfig() {
        requireOwnerThread();
        if (!this.writable) return refuseWrite();
        return persist(this.state.tables());
    }

    /** A menu belonging to an older reload must not save over the current repository. */
    public boolean saveTablesConfig(LootTable edited) {
        requireOwnerThread();
        if (edited == null || this.get(edited.getNamespace()) != edited) return false;
        return this.saveTablesConfig();
    }

    private boolean persist(Map<String, LootTable> replacement) {
        try {
            for (var entry : replacement.entrySet()) validate(entry.getKey(), entry.getValue());
            ConfigurationFile candidate = configuration();
            candidate.loadFromString(this.state.document());
            candidate.set("Tables", new LinkedHashMap<>(replacement));
            // ConfigurationFile serializes on this thread, orders writes per destination, and
            // atomically replaces the file. Never publish membership changes before that succeeds.
            String document = candidate.saveToString();
            State saved =
                    new State(
                            Collections.unmodifiableMap(new LinkedHashMap<>(replacement)),
                            document);
            ConfigurationFile.saveSnapshot(this.plugin, this.file, document);
            this.state = saved;
            return true;
        } catch (InvalidConfigurationException | RuntimeException failure) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to save loot tables to '{}'; keeping the current repository.",
                            this.file.getAbsolutePath(),
                            failure);
            return false;
        }
    }

    private List<String> expectedTables(String document) {
        var options = new org.yaml.snakeyaml.LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(Integer.MAX_VALUE);
        Object raw =
                new org.yaml.snakeyaml.Yaml(
                                new org.yaml.snakeyaml.constructor.SafeConstructor(options))
                        .load(document);
        if (raw == null && this.state == null) return List.of();
        if (!(raw instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("Loot-table document must be a YAML mapping");
        }
        if (root.isEmpty() && this.state == null) return List.of();
        if (!(root.get("Tables") instanceof Map<?, ?> tables)) {
            throw new IllegalArgumentException(
                    "Tables must be a YAML mapping; use Tables: {} for an empty repository");
        }
        var names = new java.util.ArrayList<String>();
        for (Object key : tables.keySet()) {
            if (!(key instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("Loot table keys must be nonblank names");
            }
            names.add(name);
        }
        // Bukkit may omit a key when its ConfigurationSerializable constructor throws. Retaining
        // the raw key set lets reload reject those failures instead of silently losing entries.
        return names;
    }

    private static void validate(String name, LootTable table) {
        if (!name.equals(table.getNamespace())) {
            throw new IllegalArgumentException(
                    "Loot table key does not match its namespace: " + name);
        }
        table.validate();
    }

    private ConfigurationFile configuration() {
        var configuration = new ConfigurationFile(this.plugin);
        // Namespaces are literal keys, including names containing dots.
        configuration.options().pathSeparator('\0');
        return configuration;
    }

    private boolean refuseWrite() {
        this.plugin
                .getSLF4JLogger()
                .error(
                        "Refusing to overwrite '{}' after a failed reload. Repair the file and"
                            + " reload it first.",
                        this.file.getAbsolutePath());
        return false;
    }

    private static void requireOwnerThread() {
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Loot-table loading and edits require the server thread");
        }
    }
}
