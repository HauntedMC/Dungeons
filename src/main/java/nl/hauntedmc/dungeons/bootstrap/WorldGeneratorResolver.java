package nl.hauntedmc.dungeons.bootstrap;

import nl.hauntedmc.dungeons.runtime.PluginEnvironment;
import nl.hauntedmc.dungeons.world.generator.SolidBlockChunkGenerator;
import nl.hauntedmc.dungeons.world.generator.VoidGenerator;
import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the plugin-specific string world generator identifiers used by Bukkit's plugin hook.
 */
final class WorldGeneratorResolver {
    private final PluginEnvironment environment;

    /**
     * Creates the resolver used by the Bukkit world-generator callback.
     */
    WorldGeneratorResolver(PluginEnvironment environment) {
        this.environment = environment;
    }

    /**
     * Returns the generator for the provided id or {@code null} when Bukkit should use its default
     * world generation behavior.
     */
    @Nullable ChunkGenerator resolve(@NotNull String worldName, @Nullable String id) {
        if (id == null) {
            return null;
        }

        if (id.toLowerCase().startsWith("block") && id.contains(".")) {
            String[] split = id.split("\\.", 2);
            Material material = Material.STONE;

            try {
                material = Material.valueOf(split[1]);
            } catch (IllegalArgumentException exception) {
                this.environment
                        .logger()
                        .warn(
                                "Invalid block generator material '{}' for world '{}'. Falling back to STONE.",
                                split[1],
                                worldName,
                                exception);
            }

            return new SolidBlockChunkGenerator(material);
        }

        if ("void".equalsIgnoreCase(id)) {
            return new VoidGenerator();
        }

        return null;
    }
}
