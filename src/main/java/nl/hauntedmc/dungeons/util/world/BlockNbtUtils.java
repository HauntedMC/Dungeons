package nl.hauntedmc.dungeons.util.world;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import java.util.Set;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.jetbrains.annotations.Nullable;

/**
 * Tile-entity SNBT helpers with normalization of volatile positional keys.
 */
public final class BlockNbtUtils {
    private static final Set<String> VOLATILE_TILE_KEYS = Set.of("x", "y", "z", "id", "keepPacked");

    /** Utility class. */
    private BlockNbtUtils() {}

    /** Reads tile-state SNBT from a block, or null when unavailable. */
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
            String raw = NBT.get(tileState, ReadableNBT::toString);
            return normalizeSnbt(raw);
        } catch (Exception exception) {
            logError("read tile SNBT from", block, exception);
            return null;
        }
    }

    /** Applies normalized SNBT to a tile-state block. */
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

            NBT.modify(
                    tileState,
                    writable -> {
                        writable.mergeCompound(parsed);
                    });
        } catch (Exception exception) {
            logError("apply tile SNBT to", block, exception);
        }
    }

    /** Removes volatile keys from SNBT and returns canonicalized text, or null when empty. */
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
            RuntimeContext.logger().error("Failed to normalize tile SNBT '{}'.", snbt, exception);
            return null;
        }
    }

    /** Removes transient tile keys that should not be persisted across worlds. */
    private static void stripVolatileTileKeys(ReadWriteNBT nbt) {
        for (String key : VOLATILE_TILE_KEYS) {
            nbt.removeKey(key);
        }
    }

    /** Logs tile SNBT read/apply failures with block coordinates. */
    private static void logError(String action, Block block, Exception exception) {
        RuntimeContext.plugin()
                .getSLF4JLogger()
                .error(
                        "Failed to {} block {} at {}:{}:{},{}.",
                        action,
                        block.getType(),
                        block.getWorld().getName(),
                        block.getX(),
                        block.getY(),
                        block.getZ(),
                        exception);
    }
}
