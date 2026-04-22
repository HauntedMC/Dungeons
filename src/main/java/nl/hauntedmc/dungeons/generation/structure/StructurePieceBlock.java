package nl.hauntedmc.dungeons.generation.structure;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import nl.hauntedmc.dungeons.util.world.BlockNbtUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captured block state within a structure piece.
 *
 * <p>The block stores relative coordinates, block data, and optional tile-entity SNBT so room
 * schematics can be reapplied later with fidelity.
 */
public class StructurePieceBlock implements Cloneable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructurePieceBlock.class);
    private final int x;
    private final int y;
    private final int z;
    private BlockData blockData;
    @Nullable private String blockNbt;
    private static final Map<String, BlockData> dataCache = new HashMap<>();

    /**
     * Creates a captured block snapshot at the given absolute location.
     */
    public StructurePieceBlock(Location loc, BlockData data, String nbt) {
        this(new Vector3i(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), data, nbt);
    }

    /**
     * Creates a captured block snapshot using relative coordinates.
     */
    public StructurePieceBlock(Vector3i pos, BlockData data, @Nullable String nbt) {
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.blockData = checkCache(data);
        this.blockNbt = nbt;
    }

    /**
     * Replaces the captured block state for this snapshot.
     */
    public void setBlockData(BlockData data) {
        this.blockData = checkCache(data);
    }

    /**
     * Returns the relative position represented by this snapshot.
     */
    public Vector3i getPos() {
        return new Vector3i(this.x, this.y, this.z);
    }

    /**
     * Places this block state and tile data at the target world location.
     */
    public void placeAt(Location loc) {
        World world = loc.getWorld();
        world.setBlockData(loc, this.blockData);
        BlockNbtUtils.applyTileSnbt(world.getBlockAt(loc), this.blockNbt);
    }

    /**
     * Captures a block in the world as a structure block snapshot.
     */
    public static StructurePieceBlock from(Block block) {
        String compound = BlockNbtUtils.readTileSnbt(block);
        return new StructurePieceBlock(block.getLocation(), block.getBlockData().clone(), compound);
    }

    @Override
        public String toString() {
        return this.blockData.getAsString(true);
    }

    @Override
        public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            StructurePieceBlock compare = (StructurePieceBlock) obj;
            if (this.x != compare.x) {
                return false;
            } else if (this.y != compare.y) {
                return false;
            } else if (this.z != compare.z) {
                return false;
            } else {
                return this.blockData.equals(compare.blockData)
                        && Objects.equals(this.blockNbt, compare.blockNbt);
            }
        }
    }

    @Override
    public StructurePieceBlock clone() {
        try {
            StructurePieceBlock clone = (StructurePieceBlock) super.clone();
            clone.blockData = this.blockData;
            clone.blockNbt = this.blockNbt;
            return clone;
        } catch (CloneNotSupportedException exception) {
            LOGGER.error(
                    "Failed to clone StructurePieceBlock at ({}, {}, {}). Returning original instance.",
                    this.x,
                    this.y,
                    this.z,
                    exception);
            return this;
        }
    }

        private static BlockData checkCache(BlockData data) {
        String str = data.getAsString(true);
        // BlockData creation is relatively expensive and many structure blocks share identical
        // states, so a small string-keyed cache keeps memory use lower during captures.
        return dataCache.computeIfAbsent(str, k -> data);
    }

    /**
     * Creates an explicit AIR snapshot for the given position.
     */
    public static StructurePieceBlock air(Vector3i pos) {
        return new StructurePieceBlock(pos, Bukkit.createBlockData(Material.AIR), null);
    }

    /**
     * Returns the relative X coordinate.
     */
    public int getX() {
        return this.x;
    }

    /**
     * Returns the relative Y coordinate.
     */
    public int getY() {
        return this.y;
    }

    /**
     * Returns the relative Z coordinate.
     */
    public int getZ() {
        return this.z;
    }

    /**
     * Returns the captured block data.
     */
    public BlockData getBlockData() {
        return this.blockData;
    }

    /**
     * Returns captured SNBT payload for tile entities, if present.
     */
    @Nullable
    public String getBlockNbt() {
        return this.blockNbt;
    }
}
