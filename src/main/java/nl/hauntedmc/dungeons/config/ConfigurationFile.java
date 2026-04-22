package nl.hauntedmc.dungeons.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Project-specific YAML configuration wrapper.
 *
 * <p>Besides delegating to Bukkit's {@link YamlConfiguration}, this type serializes
 * {@link ConfigSerializableModel} values automatically, synchronizes file access per target file,
 * and provides typed helpers for restoring persisted model graphs.
 */
public class ConfigurationFile extends YamlConfiguration {
    private final Plugin plugin;
    private final Map<File, Object> fileMonitors;

    /**
     * Creates a configuration wrapper for the given plugin.
     *
     * @param plugin owning plugin used for scheduler access and logging
     */
    public ConfigurationFile(Plugin plugin) {
        this.plugin = plugin;
        this.fileMonitors = new HashMap<>();
    }

    /**
     * Normalizes file keys so logically identical paths share the same monitor.
     *
     * @param file file supplied by the caller
     * @return absolute file reference used as the synchronization key
     */
    private File normalize(@NotNull File file) {
        return file.getAbsoluteFile();
    }

    /**
     * Returns the monitor object guarding reads and writes for a single configuration file.
     *
     * @param file file whose monitor should be returned
     * @return shared monitor object for the normalized file path
     */
    private Object getMonitor(@NotNull File file) {
        File normalized = this.normalize(file);
        synchronized (this.fileMonitors) {
            return this.fileMonitors.computeIfAbsent(normalized, ignored -> new Object());
        }
    }

    /**
     * Saves this configuration asynchronously when the plugin is running.
     *
     * <p>If the plugin is already disabled, the save runs inline because Bukkit's scheduler is no
     * longer available.
     *
     * @param file destination file
     * @return future completed with {@code true} on success, otherwise {@code false}
     */
    public CompletableFuture<Boolean> saveAsync(@NotNull File file) {
        File normalized = this.normalize(file);
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable task = () -> {
            synchronized (this.getMonitor(normalized)) {
                try {
                    super.save(normalized);
                    result.complete(true);
                } catch (IOException exception) {
                    this.plugin.getSLF4JLogger()
                            .error(
                                    "Failed to asynchronously save configuration '{}'.",
                                    normalized.getAbsolutePath(),
                                    exception);
                    result.complete(false);
                }
            }
        };

        if (!this.plugin.isEnabled()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, task);
        }

        return result;
    }

    /**
     * Saves this configuration to disk while serializing access per target file.
     *
     * @param file destination file
     */
    @Override
    public void save(@NotNull File file) {
        File normalized = this.normalize(file);
        synchronized (this.getMonitor(normalized)) {
            try {
                super.save(normalized);
            } catch (IOException exception) {
                this.plugin.getSLF4JLogger()
                        .error(
                                "Failed to save configuration '{}'.",
                                normalized.getAbsolutePath(),
                                exception);
            }
        }
    }

    /**
     * Loads this configuration from disk while serializing access per target file.
     *
     * @param file source file
     */
    public void load(@NotNull File file) {
        File normalized = this.normalize(file);
        synchronized (this.getMonitor(normalized)) {
            try {
                super.load(normalized);
            } catch (InvalidConfigurationException | IOException exception) {
                this.plugin.getSLF4JLogger()
                        .error(
                                "Failed to load configuration '{}'.",
                                normalized.getAbsolutePath(),
                                exception);
            }
        }
    }

    /**
     * Stores a value under the given path, serializing project models into YAML-friendly data.
     *
     * <p>Collections and maps are traversed so nested {@link ConfigSerializableModel} values also
     * keep their runtime type ids.
     *
     * @param path configuration path
     * @param obj value to store
     */
    @Override
    public void set(@NotNull String path, Object obj) {
        switch (obj) {
            case ConfigSerializableModel serializable ->
                    super.set(path, ConfigSerializableModel.serializeWithTypeId(serializable));
            case Collection<?> col -> {
                List<Object> contents = new ArrayList<>();
                for (Object entry : col) {
                    if (entry instanceof ConfigSerializableModel serializableEntry) {
                        contents.add(ConfigSerializableModel.serializeWithTypeId(serializableEntry));
                    } else {
                        contents.add(entry);
                    }
                }
                super.set(path, contents);
            }
            case Map<?, ?> rawMap -> {
                Map<Object, Object> contents = new HashMap<>();
                for (Map.Entry<?, ?> pair : rawMap.entrySet()) {
                    Object key = pair.getKey();
                    Object value = pair.getValue();
                    if (key instanceof ConfigSerializableModel serializableKey) {
                        key = ConfigSerializableModel.serializeWithTypeId(serializableKey);
                    }
                    if (value instanceof ConfigSerializableModel serializableValue) {
                        value = ConfigSerializableModel.serializeWithTypeId(serializableValue);
                    }
                    contents.put(key, value);
                }
                super.set(path, contents);
            }
            case null, default -> super.set(path, obj);
        }
    }

    /**
     * Restores a single serializable model from the configuration.
     *
     * @param type target model type
     * @param path configuration path
     * @param <T> target model type
     * @return restored model, or {@code null} when the node is absent or invalid
     */
    public <T extends ConfigSerializableModel> T get(Class<T> type, @NotNull String path) {
        return ConfigSerializableModel.deserialize(type, this.getConfigurationSection(path));
    }

    /**
     * Restores a list of serializable models from the configuration.
     *
     * <p>Each entry may declare its own concrete type id. Entries that cannot be deserialized are
     * skipped so one bad record does not discard the entire list.
     *
     * @param type declared element type
     * @param path configuration path
     * @param <T> target model type
     * @return restored list, possibly empty
     */
    public <T extends ConfigSerializableModel> List<T> getListOf(Class<T> type, @NotNull String path) {
        List<T> list = new ArrayList<>();
        List<?> origin = this.getList(path);
        if (origin == null) {
            return list;
        }

        Map<String, Class<? extends T>> subclasses = new HashMap<>();
        for (Class<? extends ConfigSerializableModel> clazz : ConfigSerializableModel.pluginSerializables) {
            if (type.isAssignableFrom(clazz)) {
                @SuppressWarnings("unchecked")
                Class<? extends T> typedClazz = (Class<? extends T>) clazz;
                subclasses.put(TypeMetadataSupport.requiredId(clazz), typedClazz);
            }
        }

        for (Object entry : origin) {
            if (entry instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = new HashMap<>();
                for (Map.Entry<?, ?> e : rawMap.entrySet()) {
                    if (e.getKey() instanceof String key) {
                        map.put(key, e.getValue());
                    }
                }

                // Older payloads may omit the discriminator when the list stored only one concrete
                // type, so fall back to the declared element type in that case.
                String entryTypeName = (String) map.getOrDefault("TYPE--", "");
                if (entryTypeName == null || entryTypeName.isEmpty()) {
                    entryTypeName = TypeMetadataSupport.requiredId(type);
                }

                Class<? extends T> subclass = subclasses.get(entryTypeName);
                if (subclass != null) {
                    T obj = ConfigSerializableModel.deserialize(subclass, map);
                    if (obj != null) {
                        list.add(obj);
                    }
                }
            }
        }

        return list;
    }
}
