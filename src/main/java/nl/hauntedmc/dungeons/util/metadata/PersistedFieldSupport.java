package nl.hauntedmc.dungeons.util.metadata;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import nl.hauntedmc.dungeons.annotation.EditorHidden;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

/**
 * Reflection helper for persisted-field discovery, serialization, and hydration.
 */
public final class PersistedFieldSupport {
    public static final Object SKIP = new Object();

    private static final ConcurrentMap<Class<?>, List<FieldMetadata>> FIELD_CACHE =
            new ConcurrentHashMap<>();

    /** Utility class. */
    private PersistedFieldSupport() {}

    /** Returns cached persisted-field metadata for a type. */
    public static List<FieldMetadata> persistedFields(Class<?> type) {
        return FIELD_CACHE.computeIfAbsent(type, PersistedFieldSupport::loadFields);
    }

    /** Returns persisted fields that are visible in editor UIs. */
    public static List<FieldMetadata> editorVisibleFields(Class<?> type) {
        return persistedFields(type).stream().filter(metadata -> !metadata.editorHidden()).toList();
    }

    /** Populates persisted fields on a target object using decoder-provided coercion. */
    public static void populate(Object target, Map<String, Object> values, FieldValueDecoder decoder)
            throws IllegalAccessException {
        for (FieldMetadata metadata : persistedFields(target.getClass())) {
            Object rawValue = resolveConfiguredValue(values, metadata);
            if (rawValue == null) {
                continue;
            }

            Object decoded = decoder.decode(metadata, rawValue);
            if (decoded != SKIP) {
                metadata.field().set(target, decoded);
            }
        }
    }

    /** Serializes persisted fields from a source object using encoder-provided conversion. */
    public static Map<String, Object> serialize(Object source, FieldValueEncoder encoder)
            throws IllegalAccessException {
        Map<String, Object> values = new LinkedHashMap<>();

        for (FieldMetadata metadata : persistedFields(source.getClass())) {
            Object fieldValue = metadata.field().get(source);
            Object encoded = encoder.encode(metadata, fieldValue);
            if (encoded != SKIP) {
                values.put(metadata.name(), encoded);
            }
        }

        return values;
    }

    /** Resolves a raw configured value for one field metadata entry. */
    public static Object resolveConfiguredValue(Map<String, Object> values, FieldMetadata metadata) {
        return values.get(metadata.name());
    }

    /** Attempts common scalar coercions for persisted field assignment. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object coerceSimpleValue(FieldMetadata metadata, Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        Class<?> fieldType = metadata.field().getType();
        Class<?> boxedFieldType =
                fieldType.isPrimitive() ? ClassUtils.primitiveToWrapper(fieldType) : fieldType;
        if (boxedFieldType.isInstance(rawValue)) {
            return rawValue;
        }

        if (fieldType.isEnum() && rawValue instanceof String enumName) {
            try {
                return Enum.valueOf((Class<Enum>) fieldType, enumName);
            } catch (IllegalArgumentException ignored) {
                return SKIP;
            }
        }

        if (boxedFieldType == String.class) {
            return String.valueOf(rawValue);
        }

        if (rawValue instanceof Number number) {
            if (boxedFieldType == Integer.class) {
                return number.intValue();
            }
            if (boxedFieldType == Long.class) {
                return number.longValue();
            }
            if (boxedFieldType == Double.class) {
                return number.doubleValue();
            }
            if (boxedFieldType == Float.class) {
                return number.floatValue();
            }
            if (boxedFieldType == Short.class) {
                return number.shortValue();
            }
            if (boxedFieldType == Byte.class) {
                return number.byteValue();
            }
        }

        return rawValue;
    }

    /** Discovers persisted-field metadata by annotation scan and caches it. */
    private static List<FieldMetadata> loadFields(Class<?> type) {
        List<FieldMetadata> metadata = new ArrayList<>();

        for (Field field : FieldUtils.getFieldsListWithAnnotation(type, PersistedField.class)) {
            field.setAccessible(true);
            metadata.add(new FieldMetadata(field, field.isAnnotationPresent(EditorHidden.class)));
        }

        return List.copyOf(new LinkedHashSet<>(metadata));
    }

    /** Immutable metadata descriptor for one persisted field. */
    public record FieldMetadata(Field field, boolean editorHidden) {
        /** Returns the persisted field key name. */
        public String name() {
            return this.field.getName();
        }
    }

    /** Decoder contract used while hydrating persisted field values. */
    @FunctionalInterface
    public interface FieldValueDecoder {
        /** Decodes one raw config value into assignment-ready field content. */
        Object decode(FieldMetadata metadata, Object rawValue);
    }

    /** Encoder contract used while serializing persisted field values. */
    @FunctionalInterface
    public interface FieldValueEncoder {
        /** Encodes one field value into config-serializable form. */
        Object encode(FieldMetadata metadata, Object fieldValue);
    }
}
