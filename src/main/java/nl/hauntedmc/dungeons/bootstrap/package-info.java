/**
 * Plugin bootstrap package.
 *
 * <p>This package contains the application composition root, startup stages, and lifecycle
 * operations that remain tightly coupled to bootstrap ordering. The intent is to keep enable and
 * disable wiring explicit without pushing long-lived runtime state back into the plugin
 * entrypoint.</p>
 *
 * <p>Key boundaries:</p>
 * <ul>
 *   <li>{@link nl.hauntedmc.dungeons.bootstrap.DungeonsApplication}: startup/shutdown
 *       orchestration.</li>
 *   <li>{@code *Bootstrap}: concrete startup stages.</li>
 *   <li>{@link nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator}: runtime reload and shutdown
 *       orchestration.</li>
 * </ul>
 */
package nl.hauntedmc.dungeons.bootstrap;
