package nl.hauntedmc.dungeons.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as part of the metadata-driven persistence model.
 *
 * <p>{@link nl.hauntedmc.dungeons.util.metadata.PersistedFieldSupport} discovers these fields via
 * reflection, hydrates them from saved configuration values, and serializes them back using the
 * field name as the default persisted key.</p>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PersistedField {}
