package nl.hauntedmc.dungeons.util.metadata;

import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.reflections.Reflections;

/**
 * Type metadata and auto-discovery helpers for plugin model registration.
 */
public final class TypeMetadataSupport {
    private static final ConcurrentMap<Class<?>, TypeMetadata> TYPE_CACHE = new ConcurrentHashMap<>();

    /** Utility class. */
    private TypeMetadataSupport() {}

    /** Returns cached type metadata for a class. */
    public static TypeMetadata metadata(Class<?> type) {
        return TYPE_CACHE.computeIfAbsent(type, TypeMetadataSupport::resolveMetadata);
    }

    /** Returns required stable type id from metadata annotations. */
    public static String requiredId(Class<?> type) {
        return metadata(type).id();
    }

    /** Returns whether a class is concrete and instantiable for registration. */
    public static boolean isConcreteType(Class<?> type) {
        int modifiers = type.getModifiers();
        return !type.isInterface() && !Modifier.isAbstract(modifiers) && !type.isAnonymousClass();
    }

    /** Discovers auto-register subtypes under one package path. */
    public static <T> List<Class<? extends T>> discoverAutoRegisteredTypes(
            String packagePath, Class<T> baseType) {
        Reflections reflections = new Reflections(packagePath);
        return reflections.getSubTypesOf(baseType).stream()
                .filter(TypeMetadataSupport::isConcreteType)
                .filter(type -> type.isAnnotationPresent(AutoRegister.class))
                .sorted(Comparator.comparing(TypeMetadataSupport::requiredId))
                .toList();
    }

    /** Discovers all concrete subtypes under one package path. */
    public static <T> List<Class<? extends T>> discoverConcreteSubtypes(
            String packagePath, Class<T> baseType) {
        Reflections reflections = new Reflections(packagePath);
        return reflections.getSubTypesOf(baseType).stream()
                .filter(TypeMetadataSupport::isConcreteType)
                .sorted(Comparator.comparing(Class::getName))
                .toList();
    }

    /** Registers a Bukkit-serializable class under its required type alias. */
    public static void registerConfigurationSerializable(
            Class<? extends ConfigurationSerializable> type) {
        TypeMetadata metadata = metadata(type);
        ConfigurationSerialization.unregisterClass(type);
        registerAlias(type, metadata.id());
    }

    /** Registers one alias while guarding against conflicting existing mappings. */
    private static void registerAlias(Class<? extends ConfigurationSerializable> type, String alias) {
        Class<? extends ConfigurationSerializable> existing =
                ConfigurationSerialization.getClassByAlias(alias);
        if (existing != null && !existing.equals(type)) {
            throw new IllegalStateException(
                    "Alias '" + alias + "' is already registered to '" + existing.getName() + "'.");
        }

        ConfigurationSerialization.registerClass(type, alias);
    }

    /** Resolves and validates type metadata annotations for a class. */
    private static TypeMetadata resolveMetadata(Class<?> type) {
        AutoRegister autoRegister = type.getAnnotation(AutoRegister.class);
        TypeKey typeKey = type.getAnnotation(TypeKey.class);

        String id = null;

        if (autoRegister != null) {
            id = autoRegister.id();
        }

        if (typeKey != null) {
            if (id != null && !Objects.equals(id, typeKey.id())) {
                throw new IllegalStateException("Type '" + type.getName() + "' declares conflicting ids.");
            }

            id = typeKey.id();
        }

        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "Type '" + type.getName() + "' is missing @AutoRegister or @TypeKey metadata.");
        }

        return new TypeMetadata(id);
    }

    /** Immutable type metadata descriptor. */
    public record TypeMetadata(String id) {}
}
