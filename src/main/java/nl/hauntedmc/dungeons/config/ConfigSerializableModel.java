package nl.hauntedmc.dungeons.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import nl.hauntedmc.dungeons.util.metadata.PersistedFieldSupport;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.plugin.Plugin;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contract for plugin models that can be serialized into YAML-friendly maps and restored later.
 *
 * <p>Implementations are typically simple data holders whose persisted state is declared with the
 * reflective metadata utilities in this project. The helpers on this interface take care of
 * registering concrete implementations, restoring nested serializable values, and preserving
 * runtime subtype information when a field is declared as an abstract base type.
 */
public interface ConfigSerializableModel {
    /**
     * Registry of concrete serializable types discovered from the plugin package.
     *
     * <p>Deserialization consults this set when a persisted payload declares a concrete type id.
     */
    Set<Class<? extends ConfigSerializableModel>> pluginSerializables = new LinkedHashSet<>();

    /** Shared logger for serialization and deserialization diagnostics. */
    Logger LOGGER = LoggerFactory.getLogger(ConfigSerializableModel.class);

    /**
     * Registers all serializable model types that live under the plugin's base package.
     *
     * @param plugin plugin whose package should be scanned for serializable subtypes
     */
    static void register(Plugin plugin) {
        register(plugin.getClass().getPackage().getName());
    }

    /**
     * Registers every concrete {@link ConfigSerializableModel} implementation in the given package.
     *
     * @param packagePath root package to scan with Reflections
     */
    static void register(String packagePath) {
        Reflections reflections = new Reflections(packagePath);
        Set<Class<? extends ConfigSerializableModel>> subtypes =
                reflections.getSubTypesOf(ConfigSerializableModel.class);
        for (Class<? extends ConfigSerializableModel> subtype : subtypes) {
            if (TypeMetadataSupport.isConcreteType(subtype)) {
                pluginSerializables.add(subtype);
            }
        }
    }

    /**
     * Populates this instance from raw persisted values.
     *
     * <p>Field assignment is delegated to {@link PersistedFieldSupport}, which handles reflective
     * access and persisted-name mapping. This hook adds the project-specific conversion logic for
     * nested serializable models, collections, and maps before values are assigned.
     *
     * @param values raw persisted values keyed by serialized field name
     */
    default void initializeFields(Map<String, Object> values) {
        try {
            PersistedFieldSupport.populate(
                    this,
                    values,
                    (metadata, rawValue) ->
                            deserializeFieldValue(metadata.field(), metadata, rawValue, this.getClass()));
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            LOGGER.error(
                    "Failed to initialize serialized fields on '{}'.",
                    this.getClass().getSimpleName(),
                    exception);
        }
    }

    /**
     * Applies any normalization that depends on all persisted fields already being loaded.
     *
     * <p>Implementations can use this to rebuild derived state, restore defaults for omitted data,
     * or migrate legacy payloads before the object is accepted by callers.
     */
    default void postDeserialize() {}

    /**
     * Validates the restored object before it is returned to callers.
     *
     * @return {@code true} when the restored instance is safe to keep, otherwise {@code false}
     */
    default boolean isDeserializedValid() {
        return true;
    }

    /**
     * Copies a raw map into a string-keyed representation expected by the serializer.
     *
     * @param input raw persisted map
     * @return map containing only string keys
     */
    static Map<String, Object> castMap(Map<?, ?> input) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Serializes this instance into a YAML-friendly map.
     *
     * <p>Only fields declared as persisted metadata participate. Nested serializable values are
     * recursively converted into maps and collections.
     *
     * @return serialized state for this object
     */
    default Map<String, Object> serialize() {
        try {
            return PersistedFieldSupport.serialize(
                    this, (metadata, fieldValue) -> serializeFieldValue(metadata.field(), fieldValue));
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            LOGGER.error("Failed to serialize '{}'.", this.getClass().getSimpleName(), exception);
            return new HashMap<>();
        }
    }

    /**
     * Serializes a model and embeds its concrete type id.
     *
     * <p>This is required whenever the declared field type is broader than the concrete runtime
     * type, otherwise deserialization would not know which subtype to instantiate later.
     *
     * @param serializable model to serialize
     * @return serialized state with a {@code TYPE--} discriminator
     */
    static Map<String, Object> serializeWithTypeId(ConfigSerializableModel serializable) {
        Map<String, Object> serialized = new HashMap<>(serializable.serialize());
        serialized.put("TYPE--", TypeMetadataSupport.requiredId(serializable.getClass()));
        return serialized;
    }

    /**
     * Deserializes a serializable model from a raw persisted map.
     *
     * <p>If the payload declares a concrete type id, the requested base type is replaced with the
     * discovered subtype before instantiation. After fields are populated, the instance gets a
     * chance to normalize itself and to reject invalid payloads.
     *
     * @param serializable declared target type
     * @param config raw persisted values
     * @param <T> target model type
     * @return restored model, or {@code null} when the payload is absent or invalid
     */
    @SuppressWarnings("unchecked")
    static <T extends ConfigSerializableModel> T deserialize(
            Class<?> serializable, @Nullable Map<String, Object> config) {
        if (config == null) {
            return null;
        }

        Map<String, Class<? extends T>> subclasses = new HashMap<>();
        for (Class<? extends ConfigSerializableModel> clazz : pluginSerializables) {
            if (serializable.isAssignableFrom(clazz)) {
                subclasses.put(TypeMetadataSupport.requiredId(clazz), (Class<? extends T>) clazz);
            }
        }

        String entryTypeName = (String) config.getOrDefault("TYPE--", "");
        if (!entryTypeName.isEmpty()) {
            Class<? extends T> subtype = subclasses.get(entryTypeName);
            if (subtype != null) {
                serializable = subtype;
            }
        }

        T instance = instantiate((Class<? extends T>) serializable);
        if (instance != null) {
            instance.initializeFields(config);
            try {
                instance.postDeserialize();
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "Failed to normalize serialized type '{}'.", serializable.getName(), exception);
                return null;
            }

            if (!instance.isDeserializedValid()) {
                LOGGER.warn("Skipping invalid serialized type '{}'.", serializable.getName());
                return null;
            }
        }

        return instance;
    }

    /**
     * Deserializes a serializable model from a Bukkit configuration section.
     *
     * <p>Bukkit exposes nested YAML nodes as {@link MemorySection} instances, so those sections are
     * flattened into plain maps before the reflective deserializer runs.
     *
     * @param serializable declared target type
     * @param config configuration section containing the serialized payload
     * @param <T> target model type
     * @return restored model, or {@code null} when the payload is absent or invalid
     */
    static <T extends ConfigSerializableModel> T deserialize(
            Class<T> serializable, @Nullable ConfigurationSection config) {
        if (config == null) {
            LOGGER.warn(
                    "Attempted to deserialize '{}' from a null configuration section.",
                    serializable.getName());
            return null;
        }

        Map<String, Object> configMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : config.getValues(false).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof MemorySection section) {
                value = section.getValues(false);
            }
            configMap.put(entry.getKey(), value);
        }
        return deserialize(serializable, configMap);
    }

    /**
     * Converts a raw persisted field value into the type expected by the target field.
     *
     * @param field target field
     * @param metadata persisted field metadata
     * @param rawValue raw value read from YAML
     * @param ownerType declaring model type
     * @return converted value, or {@link PersistedFieldSupport#SKIP} when the value is unusable
     */
    private static Object deserializeFieldValue(
            Field field,
            PersistedFieldSupport.FieldMetadata metadata,
            Object rawValue,
            Class<?> ownerType) {
        // Primitive coercion handles numerics, booleans, enums, and other simple conversions first.
        Object coerced = PersistedFieldSupport.coerceSimpleValue(metadata, rawValue);
        if (coerced == PersistedFieldSupport.SKIP) {
            LOGGER.warn(
                    "Ignoring invalid value '{}' for field '{}' on '{}'.",
                    rawValue,
                    metadata.name(),
                    ownerType.getSimpleName());
            return PersistedFieldSupport.SKIP;
        }
        if (coerced != rawValue) {
            return coerced;
        }

        if (Map.class.isAssignableFrom(field.getType()) && rawValue instanceof Map<?, ?> rawMap) {
            return deserializeMapField(field, rawMap, metadata.name(), ownerType);
        }
        if (ConfigSerializableModel.class.isAssignableFrom(field.getType())
                && rawValue instanceof Map<?, ?> map) {
            return deserialize(field.getType(), castMap(map));
        }
        if (List.class.isAssignableFrom(field.getType()) && rawValue instanceof List<?> baseList) {
            return deserializeListField(field, baseList, metadata.name(), ownerType);
        }

        return rawValue;
    }

    /**
     * Restores a persisted map field, deserializing serializable keys and values when needed.
     *
     * @param field target field
     * @param rawMap raw persisted map
     * @param fieldName serialized field name used in log output
     * @param ownerType declaring model type used in log output
     * @return restored map value, original map when generics are unavailable, or {@code SKIP} on
     *         fatal conversion errors
     */
    private static Object deserializeMapField(
            Field field, Map<?, ?> rawMap, String fieldName, Class<?> ownerType) {
        Type keyType = resolveTypeArgument(field, 0, fieldName, ownerType);
        Type valueType = resolveTypeArgument(field, 1, fieldName, ownerType);
        if (keyType == null || valueType == null) {
            return rawMap;
        }

        try {
            Class<?> keyClass = Class.forName(keyType.getTypeName());
            Class<?> valueClass = Class.forName(valueType.getTypeName());

            boolean keySerializable = ConfigSerializableModel.class.isAssignableFrom(keyClass);
            boolean valueSerializable = ConfigSerializableModel.class.isAssignableFrom(valueClass);
            Map<Object, Object> converted = new HashMap<>();

            for (Map.Entry<?, ?> pair : rawMap.entrySet()) {
                // Map entries may themselves be serializable models, so each side is converted
                // independently to preserve mixed key/value payloads.
                Object key = pair.getKey();
                if (keySerializable && key instanceof Map<?, ?> map) {
                    key = deserialize(keyClass, castMap(map));
                    if (key == null) {
                        continue;
                    }
                }

                Object value = pair.getValue();
                if (valueSerializable && value instanceof Map<?, ?> map) {
                    value = deserialize(valueClass, castMap(map));
                    if (value == null) {
                        continue;
                    }
                }

                converted.put(key, value);
            }

            return converted;
        } catch (ClassNotFoundException exception) {
            LOGGER.error(
                    "Failed to resolve map types while loading field '{}' on '{}'.",
                    fieldName,
                    ownerType.getSimpleName(),
                    exception);
            return PersistedFieldSupport.SKIP;
        }
    }

    /**
     * Restores a persisted list field, deserializing serializable entries when needed.
     *
     * @param field target field
     * @param baseList raw persisted list
     * @param fieldName serialized field name used in log output
     * @param ownerType declaring model type used in log output
     * @return restored list value, original list when generics are unavailable, or {@code SKIP} on
     *         fatal conversion errors
     */
    private static Object deserializeListField(
            Field field, List<?> baseList, String fieldName, Class<?> ownerType) {
        Type itemType = resolveTypeArgument(field, 0, fieldName, ownerType);
        if (itemType == null) {
            return baseList;
        }

        try {
            Class<?> itemClass = Class.forName(itemType.getTypeName());
            if (!ConfigSerializableModel.class.isAssignableFrom(itemClass)) {
                return baseList;
            }

            List<Object> converted = new ArrayList<>();
            for (Object entry : baseList) {
                if (entry instanceof Map<?, ?> map) {
                    // Invalid child entries are dropped so one bad element does not poison the
                    // entire collection payload.
                    Object deserialized = deserialize(itemClass, castMap(map));
                    if (deserialized != null) {
                        converted.add(deserialized);
                    }
                } else {
                    converted.add(entry);
                }
            }
            return converted;
        } catch (ClassNotFoundException exception) {
            LOGGER.error(
                    "Failed to resolve list item type while loading field '{}' on '{}'.",
                    fieldName,
                    ownerType.getSimpleName(),
                    exception);
            return PersistedFieldSupport.SKIP;
        }
    }

    /**
     * Serializes a field value into data that Bukkit's YAML implementation can persist directly.
     *
     * @param field declaring field
     * @param fieldValue current runtime value
     * @return YAML-friendly value
     */
    private static Object serializeFieldValue(Field field, Object fieldValue) {
        return switch (fieldValue) {
            case null -> null;
            case Enum<?> enumValue when field.getType().isEnum() -> enumValue.name();
            case Map<?, ?> baseMap -> serializeMapField(field, baseMap);
            case Collection<?> collection -> serializeCollectionField(field, collection);
            case ConfigSerializableModel serializable ->
                    serializeNestedSerializable(field.getType(), serializable);
            default -> fieldValue;
        };
    }

    /**
     * Serializes a map field while preserving subtype information for serializable entries.
     *
     * @param field declaring field
     * @param baseMap runtime map value
     * @return YAML-friendly map representation
     */
    private static Object serializeMapField(Field field, Map<?, ?> baseMap) {
        Class<?> keyClass = resolveGenericClass(field, 0);
        Class<?> valueClass = resolveGenericClass(field, 1);
        Map<Object, Object> serialized = new HashMap<>();

        for (Map.Entry<?, ?> pair : baseMap.entrySet()) {
            Object key = pair.getKey();
            Object value = pair.getValue();
            if (key instanceof ConfigSerializableModel serializableKey) {
                key = serializeNestedSerializable(keyClass, serializableKey);
            }
            if (value instanceof ConfigSerializableModel serializableValue) {
                value = serializeNestedSerializable(valueClass, serializableValue);
            }
            serialized.put(key, value);
        }

        return serialized;
    }

    /**
     * Serializes a collection field while preserving subtype information for serializable entries.
     *
     * @param field declaring field
     * @param collection runtime collection value
     * @return YAML-friendly list representation
     */
    private static Object serializeCollectionField(Field field, Collection<?> collection) {
        Class<?> itemClass = resolveGenericClass(field, 0);
        List<Object> serialized = new ArrayList<>();

        for (Object item : collection) {
            if (item instanceof ConfigSerializableModel serializable) {
                serialized.add(serializeNestedSerializable(itemClass, serializable));
            } else {
                serialized.add(item);
            }
        }

        return serialized;
    }

    /**
     * Serializes a nested serializable value, adding a type discriminator when required.
     *
     * @param declaredType declared field type, if known
     * @param serializable runtime value
     * @return serialized payload for the nested value
     */
    private static Object serializeNestedSerializable(
            @Nullable Class<?> declaredType, ConfigSerializableModel serializable) {
        // Matching declared and runtime types do not need an explicit discriminator; broader
        // declarations do, otherwise deserialization would fall back to the abstract/base type.
        if (!serializable.getClass().equals(declaredType)) {
            return serializeWithTypeId(serializable);
        }

        return serializable.serialize();
    }

    /**
     * Resolves one generic type argument from a parameterized field declaration.
     *
     * @param field field whose generic signature should be inspected
     * @param index generic argument index
     * @param fieldName serialized field name used in log output
     * @param ownerType declaring model type used in log output
     * @return resolved generic type, or {@code null} when the field is not parameterized
     */
    private static @Nullable Type resolveTypeArgument(
            Field field, int index, String fieldName, Class<?> ownerType) {
        if (!(field.getGenericType() instanceof ParameterizedType parameterizedType)) {
            LOGGER.warn(
                    "Field '{}' on '{}' is missing generic type information.",
                    fieldName,
                    ownerType.getSimpleName());
            return null;
        }

        return parameterizedType.getActualTypeArguments()[index];
    }

    /**
     * Resolves one generic type argument into a concrete class when possible.
     *
     * @param field field whose generic signature should be inspected
     * @param index generic argument index
     * @return resolved class, or {@code null} when the type cannot be loaded
     */
    private static @Nullable Class<?> resolveGenericClass(Field field, int index) {
        if (!(field.getGenericType() instanceof ParameterizedType parameterizedType)) {
            return null;
        }

        Type type = parameterizedType.getActualTypeArguments()[index];
        try {
            return Class.forName(type.getTypeName());
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    /**
     * Instantiates a serializable type using its no-argument constructor.
     *
     * @param serializable target type to instantiate
     * @param <T> model type
     * @return new instance, or {@code null} when instantiation fails
     */
    private static <T extends ConfigSerializableModel> @Nullable T instantiate(
            Class<? extends T> serializable) {
        try {
            Constructor<? extends T> constructor = serializable.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException exception) {
            LOGGER.error(
                    "Failed to deserialize '{}' because it does not declare a no-arg constructor.",
                    serializable.getName(),
                    exception);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            LOGGER.error(
                    "Failed to instantiate serialized type '{}'.", serializable.getName(), exception);
        }

        return null;
    }
}
