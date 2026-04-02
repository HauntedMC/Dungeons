package nl.hauntedmc.dungeons.api.chunkgenerators;

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

public class VoidGenerator extends ChunkGenerator {
   public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) {
      return List.of();
   }

    @Nullable
   public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) {
      return new VoidBiomeProvider();
   }

   public boolean canSpawn(@NotNull World world, int x, int z) {
      return true;
   }

   public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
      return new Location(world, 0.0, 100.0, 0.0);
   }

   private static class VoidBiomeProvider extends BiomeProvider {
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
