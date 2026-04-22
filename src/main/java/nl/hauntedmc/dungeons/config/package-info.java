/**
 * Internal configuration and reflective serialization support.
 *
 * <p>The types in this package back the plugin's YAML persistence layer. They register concrete
 * model types, convert persisted maps back into object graphs, and wrap Bukkit's configuration API
 * with conventions used throughout the dungeon editor and runtime.
 *
 * <p>Most higher-level systems interact with this package indirectly through
 * {@link nl.hauntedmc.dungeons.config.ConfigurationFile} and model classes that implement
 * {@link nl.hauntedmc.dungeons.config.ConfigSerializableModel}.
 */
package nl.hauntedmc.dungeons.config;
