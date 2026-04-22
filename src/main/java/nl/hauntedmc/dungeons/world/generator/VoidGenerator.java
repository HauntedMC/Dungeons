package nl.hauntedmc.dungeons.world.generator;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal void-world chunk generator.
 */
public class VoidGenerator extends ChunkGenerator {
    /**
     * Disables default populators for this generated world.
     */
    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return List.of();
    }

    /**
     * Uses a single-biome provider so generated chunks stay in THE_VOID.
     */
    @Override
    @Nullable
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
        return new VoidBiomeProvider();
    }

    /**
     * Allows spawning at any location in this generated world.
     */
    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) {
        return true;
    }

    /**
     * Sets a fixed spawn point above the void.
     */
    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.0, 100.0, 0.0);
    }

    /** Biome provider that forces all generated chunks to THE_VOID. */
    private static class VoidBiomeProvider extends BiomeProvider {
        /**
         * Returns THE_VOID for every queried coordinate.
         */
        @Override
        @NotNull
        public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        /**
         * Declares the full biome set used by this provider.
         */
        @Override
        @NotNull
        public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
            return List.of(Biome.THE_VOID);
        }
    }
}
