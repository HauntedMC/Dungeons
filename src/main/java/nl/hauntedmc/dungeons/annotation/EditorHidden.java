package nl.hauntedmc.dungeons.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a persisted field as internal to editor UIs.
 *
 * <p>{@link nl.hauntedmc.dungeons.util.metadata.PersistedFieldSupport} still includes the field in
 * reflective persistence, but editor metadata queries omit it so the field does not appear in
 * generic editor menus.</p>
 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EditorHidden {}
