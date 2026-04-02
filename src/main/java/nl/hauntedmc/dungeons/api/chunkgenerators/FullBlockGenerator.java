package nl.hauntedmc.dungeons.api.chunkgenerators;

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

public class FullBlockGenerator extends ChunkGenerator {
   protected Material blockType;

   public FullBlockGenerator(Material blockType) {
      if (!blockType.isBlock()) {
         this.blockType = Material.STONE;
      } else {
         this.blockType = blockType;
      }
   }

   public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
      return List.of();
   }

   public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
      chunkData.setRegion(0, worldInfo.getMinHeight(), 0, 15, worldInfo.getMaxHeight(), 15, this.blockType);
   }

    @Nullable
   public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
      return new FullBlockProvider();
   }

   public boolean canSpawn(@NotNull World world, int x, int z) {
      return true;
   }

   private static class FullBlockProvider extends BiomeProvider {
      @NotNull
      public Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
         return Biome.THE_VOID;
      }

      @NotNull
      public List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
         return List.of(Biome.THE_VOID);
      }
   }
}
