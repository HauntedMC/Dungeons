/**
 * Live dungeon instance lifecycle, registries and instance-owned resources.
 *
 * <p>Base instances coordinate world loading, participants, saves and disposal. Concrete bundled
 * runs live in {@code play} and {@code edit}; their Bukkit listeners live in {@code listener}.
 * Instances receive their runtime from their owning dungeon rather than selecting a global runtime.
 */
package nl.hauntedmc.dungeons.runtime.instance;
