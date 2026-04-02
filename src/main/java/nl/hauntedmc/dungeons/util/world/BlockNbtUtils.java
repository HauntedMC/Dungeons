package nl.hauntedmc.dungeons.util.world;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import java.util.Set;
import nl.hauntedmc.dungeons.Dungeons;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.jetbrains.annotations.Nullable;

public final class BlockNbtUtils {
   private static final Set<String> VOLATILE_TILE_KEYS = Set.of("x", "y", "z", "id", "keepPacked");

   private BlockNbtUtils() {
   }

   @Nullable
   public static String readTileSnbt(Block block) {
      if (block == null) {
         return null;
      }

      BlockState state = block.getState();
      if (!(state instanceof TileState tileState)) {
         return null;
      }

      try {
         String raw = NBT.get(tileState, readable -> {
            return readable.toString();
         });
         return normalizeSnbt(raw);
      } catch (Exception exception) {
         logError(exception);
         return null;
      }
   }

   public static void applyTileSnbt(Block block, String snbt) {
      if (block == null || snbt == null || snbt.isBlank()) {
         return;
      }

      BlockState state = block.getState();
      if (!(state instanceof TileState tileState)) {
         return;
      }

      try {
         ReadWriteNBT parsed = NBT.parseNBT(snbt);
         stripVolatileTileKeys(parsed);
         if (parsed.getKeys().isEmpty()) {
            return;
         }

         NBT.modify(tileState, writable -> {
            writable.mergeCompound(parsed);
         });
      } catch (Exception exception) {
         logError(exception);
      }
   }

   @Nullable
   public static String normalizeSnbt(String snbt) {
      if (snbt == null || snbt.isBlank()) {
         return null;
      }

      try {
         ReadWriteNBT parsed = NBT.parseNBT(snbt);
         stripVolatileTileKeys(parsed);
         return parsed.getKeys().isEmpty() ? null : parsed.toString();
      } catch (Exception exception) {
         logError(exception);
         return null;
      }
   }

   private static void stripVolatileTileKeys(ReadWriteNBT nbt) {
      for (String key : VOLATILE_TILE_KEYS) {
         nbt.removeKey(key);
      }
   }

   private static void logError(Exception exception) {
      Dungeons.inst().getLogger().severe(Dungeons.logPrefix + exception.getMessage());
   }
}
