package nl.hauntedmc.dungeons.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the stable metadata identifier for a serializable or registry-backed type.
 *
 * <p>The runtime metadata layer uses this id for configuration-serialization aliases and for any
 * reflective type lookup that needs a durable, config-safe key. When combined with
 * {@link AutoRegister}, both ids must resolve to the same value.</p>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TypeKey {
    /**
     * Returns the stable identifier associated with the annotated type.
     */
    String id();
}
