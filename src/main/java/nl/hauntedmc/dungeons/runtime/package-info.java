/**
 * Long-lived runtime package.
 *
 * <p>This package contains the mutable runtime state assembled during bootstrap and the small
 * helper types that expose plugin-specific infrastructure to the rest of the application.
 *
 * <p>Key boundaries:
 *
 * <ul>
 *   <li>{@code DungeonsRuntime}: owned application runtime graph.
 *   <li>{@code PluginEnvironment}: thin wrapper around plugin-specific APIs.
 *   <li>{@code RuntimeContext}: bridge for static access sites.
 * </ul>
 */
package nl.hauntedmc.dungeons.runtime;
