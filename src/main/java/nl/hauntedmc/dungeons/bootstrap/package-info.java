/**
 * Plugin bootstrap package.
 *
 * <p>This package contains the application composition root, startup stages, and lifecycle
 * operations that remain tightly coupled to bootstrap ordering. The intent is to keep enable and
 * disable wiring explicit without pushing long-lived runtime state back into the plugin entrypoint.
 *
 * <p>Key boundaries:
 *
 * <ul>
 *   <li>{@link nl.hauntedmc.dungeons.bootstrap.DungeonsApplication}: startup/shutdown
 *       orchestration.
 *   <li>{@code *Bootstrap}: concrete startup stages.
 *   <li>{@link nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator}: runtime reload and shutdown
 *       orchestration.
 * </ul>
 */
package nl.hauntedmc.dungeons.bootstrap;
