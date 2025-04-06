package net.playavalon.mythicdungeons.api.chunkgenerators;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VoidGenerator extends ChunkGenerator {
   public List<BlockPopulator> getDefaultPopulators(World world) {
      return List.of();
   }

   public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
   }

   public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
   }

   public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
   }

   public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
   }

   @Nullable
   public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
      return new VoidGenerator.VoidBiomeProvider();
   }

   public boolean canSpawn(World world, int x, int z) {
      return true;
   }

   public Location getFixedSpawnLocation(World world, Random random) {
      return new Location(world, 0.0, 100.0, 0.0);
   }

   private class VoidBiomeProvider extends BiomeProvider {
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
