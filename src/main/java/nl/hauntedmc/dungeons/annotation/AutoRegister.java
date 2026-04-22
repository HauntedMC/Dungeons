package nl.hauntedmc.dungeons.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a concrete runtime type as eligible for package scanning during bootstrap.
 *
 * <p>The bootstrap layer uses this annotation when discovering functions, triggers, and
 * conditions. The declared {@linkplain #id() id} becomes the stable metadata key used by the
 * registry layer and should match {@link TypeKey#id()} when both annotations appear on the same
 * type.</p>
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoRegister {
    /**
     * Returns the stable identifier used when the type is discovered and registered.
     */
    String id();
}
