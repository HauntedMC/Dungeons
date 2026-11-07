package nl.hauntedmc.dungeons.config;

import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;

import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Project-specific YAML configuration wrapper.
 *
 * <p>Besides delegating to Bukkit's {@link YamlConfiguration}, this type serializes {@link
 * ConfigSerializableModel} values automatically, captures owner-thread snapshots, orders disk
 * operations globally per canonical destination, and restores persisted model graphs.
 */
public class ConfigurationFile extends YamlConfiguration {
    private final Plugin plugin;

    /**
     * Creates a configuration wrapper for the given plugin.
     *
     * @param plugin owning plugin used for scheduler access and logging
     */
    public ConfigurationFile(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Captures YAML on the server thread, then queues immutable text for ordered disk replacement.
     * Off-thread callers are marshalled to the server thread before any serialization occurs.
     */
    public CompletableFuture<Boolean> saveAsync(@NotNull File file) {
        return saveAsync(this.plugin, this, file);
    }

    private static final java.util.Set<SnapshotRequest> PENDING_CAPTURES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Uses the same snapshot protocol for ordinary Bukkit YAML configurations. */
    public static CompletableFuture<Boolean> saveAsync(
            Plugin plugin, YamlConfiguration configuration, File file) {
        return onOwnerThread(
                plugin,
                () -> {
                    try {
                        String snapshot = configuration.saveToString();
                        Path target = ConfigurationIoQueue.destination(file);
                        return ConfigurationIoQueue.writeAsync(target, snapshot, plugin)
                                .handle(
                                        (ignored, failure) -> {
                                            if (failure != null) logFailure(plugin, file, failure);
                                            return failure == null;
                                        });
                    } catch (RuntimeException | IOException failure) {
                        logFailure(plugin, file, failure);
                        return CompletableFuture.completedFuture(false);
                    }
                });
    }

    /**
     * Queues filesystem-only work in destination order and includes it in shutdown draining. The
     * action must use captured values only and must never call Bukkit or wait for the owner thread.
     * Caller cancellation cannot cancel an admitted operation.
     */
    public static <T> CompletableFuture<T> filesystemAsync(
            Plugin plugin, File destination, java.util.concurrent.Callable<T> action) {
        try {
            return ConfigurationIoQueue.filesystemAsync(
                    ConfigurationIoQueue.destination(destination), action, plugin);
        } catch (IOException | RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** Executes configuration/model capture on the owner thread, including shutdown draining. */
    public static CompletableFuture<Boolean> onOwnerThread(
            Plugin plugin, java.util.function.Supplier<CompletableFuture<Boolean>> capture) {
        var request = new SnapshotRequest(plugin, capture);
        try {
            if (plugin.isEnabled() && !Bukkit.isPrimaryThread()) {
                PENDING_CAPTURES.add(request);
                Bukkit.getScheduler().runTask(plugin, request::capture);
            } else {
                request.capture();
            }
        } catch (RuntimeException failure) {
            request.reject(failure);
        }
        return request.result;
    }

    /** Captures pending owner-thread requests and drains disk queues before shutdown completes. */
    public static void flushPendingSaves() {
        requireOwnerThread();
        for (SnapshotRequest pending : PENDING_CAPTURES.toArray(SnapshotRequest[]::new)) {
            pending.capture();
        }
        ConfigurationIoQueue.flush();
    }

    private static final class SnapshotRequest {
        final Plugin plugin;
        final java.util.function.Supplier<CompletableFuture<Boolean>> action;
        final CompletableFuture<Boolean> result = new CompletableFuture<>();
        final java.util.concurrent.atomic.AtomicBoolean claimed =
                new java.util.concurrent.atomic.AtomicBoolean();

        SnapshotRequest(
                Plugin plugin, java.util.function.Supplier<CompletableFuture<Boolean>> action) {
            this.plugin = plugin;
            this.action = action;
        }

        void capture() {
            if (!claimed.compareAndSet(false, true)) return;
            PENDING_CAPTURES.remove(this);
            try {
                requireOwnerThread();
                action.get()
                        .whenComplete(
                                (success, failure) -> {
                                    if (failure != null)
                                        plugin.getSLF4JLogger()
                                                .error(
                                                        "Owner-thread configuration capture"
                                                                + " failed.",
                                                        failure);
                                    result.complete(
                                            failure == null && Boolean.TRUE.equals(success));
                                });
            } catch (Throwable failure) {
                plugin.getSLF4JLogger()
                        .error("Owner-thread configuration capture failed.", failure);
                result.complete(false);
            }
        }

        void reject(RuntimeException failure) {
            if (!claimed.compareAndSet(false, true)) return;
            PENDING_CAPTURES.remove(this);
            plugin.getSLF4JLogger()
                    .error("Failed to schedule owner-thread configuration capture.", failure);
            result.complete(false);
        }
    }

    /**
     * Synchronously saves a captured snapshot after earlier operations for this destination.
     * Storage failures are reported to the caller rather than logged as apparent success.
     */
    @Override
    public void save(@NotNull File file) {
        try {
            requireOwnerThread();
            String snapshot = this.saveToString();
            ConfigurationIoQueue.writeNow(ConfigurationIoQueue.destination(file), snapshot);
        } catch (IOException failure) {
            logFailure(this.plugin, file, failure);
            throw new UncheckedIOException(failure);
        } catch (CompletionException failure) {
            logFailure(this.plugin, file, failure);
            if (failure.getCause() instanceof IOException io) throw new UncheckedIOException(io);
            throw failure;
        }
    }

    /** Reads after earlier queued writes, then applies YAML on the owning thread. */
    @Override
    public void load(@NotNull File file) {
        requireOwnerThread();
        try {
            loadSnapshot(this, file);
        } catch (InvalidConfigurationException | IOException | CompletionException failure) {
            this.plugin
                    .getSLF4JLogger()
                    .error("Failed to load configuration '{}'.", file.getAbsolutePath(), failure);
        }
    }

    /** Reads ordinary YAML through the same destination barrier before owner-thread parsing. */
    public static void loadSnapshot(YamlConfiguration configuration, File file)
            throws IOException, InvalidConfigurationException {
        requireOwnerThread();
        String snapshot;
        try {
            snapshot = ConfigurationIoQueue.readNow(ConfigurationIoQueue.destination(file));
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof IOException io) throw io;
            throw failure;
        }
        configuration.loadFromString(snapshot);
    }

    private static void requireOwnerThread() {
        // Standalone configurations (e.g. migration tools) have no Bukkit server. While a server
        // exists, mutable YAML and model serialization must stay on its primary thread, even at
        // disable.
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "Configuration snapshots require the server thread; use saveAsync");
        }
    }

    private static void logFailure(Plugin plugin, File file, Throwable failure) {
        Throwable cause =
                failure instanceof CompletionException && failure.getCause() != null
                        ? failure.getCause()
                        : failure;
        plugin.getSLF4JLogger()
                .error("Failed to save configuration '{}'.", file.getAbsolutePath(), cause);
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
                        contents.add(
                                ConfigSerializableModel.serializeWithTypeId(serializableEntry));
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
    public <T extends ConfigSerializableModel> List<T> getListOf(
            Class<T> type, @NotNull String path) {
        List<T> list = new ArrayList<>();
        List<?> origin = this.getList(path);
        if (origin == null) {
            return list;
        }

        Map<String, Class<? extends T>> subclasses = new HashMap<>();
        for (Class<? extends ConfigSerializableModel> clazz :
                ConfigSerializableModel.pluginSerializables) {
            if (type.isAssignableFrom(clazz)) {
                Class<? extends T> typedClazz = clazz.asSubclass(type);
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
                Object rawTypeName = map.get("TYPE--");
                if (rawTypeName != null && !(rawTypeName instanceof String)) {
                    this.plugin
                            .getSLF4JLogger()
                            .warn(
                                    "Skipping invalid type id '{}' in configuration list '{}'.",
                                    rawTypeName,
                                    path);
                    continue;
                }
                String entryTypeName = rawTypeName instanceof String name ? name : "";
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
