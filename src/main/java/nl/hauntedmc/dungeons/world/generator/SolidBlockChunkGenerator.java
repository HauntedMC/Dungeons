package nl.hauntedmc.dungeons.world.generator;

import java.util.List;
import java.util.Random;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Simple chunk generator that fills generated chunks with one block type.
 */
public class SolidBlockChunkGenerator extends ChunkGenerator {
    protected final Material blockType;

    /**
     * Creates a solid-block generator, defaulting to stone for invalid materials.
     */
    public SolidBlockChunkGenerator(Material blockType) {
        if (!blockType.isBlock()) {
            this.blockType = Material.STONE;
        } else {
            this.blockType = blockType;
        }
    }

    /**
     * Disables default populators for this generated world.
     */
    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
        return List.of();
    }

    /**
     * Fills the full chunk area with the configured block type.
     */
    @Override
    public void generateNoise(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData chunkData) {
        chunkData.setRegion(
                0, worldInfo.getMinHeight(), 0, 15, worldInfo.getMaxHeight(), 15, this.blockType);
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
