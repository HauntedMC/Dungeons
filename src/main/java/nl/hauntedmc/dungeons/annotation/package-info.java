/**
 * Internal metadata annotations used by the runtime metadata layer.
 *
 * <p>These annotations drive four related behaviors:
 *
 * <ul>
 *   <li>{@link nl.hauntedmc.dungeons.annotation.AutoRegister}: bootstrap-time type discovery.
 *   <li>{@link nl.hauntedmc.dungeons.annotation.TypeKey}: stable serialization and registry ids.
 *   <li>{@link nl.hauntedmc.dungeons.annotation.PersistedField}: reflective field persistence.
 *   <li>{@link nl.hauntedmc.dungeons.annotation.EditorHidden}: editor-only visibility control.
 * </ul>
 *
 * <p>They are part of the plugin's internal metadata contract rather than a standalone public API.
 */
package nl.hauntedmc.dungeons.annotation;
