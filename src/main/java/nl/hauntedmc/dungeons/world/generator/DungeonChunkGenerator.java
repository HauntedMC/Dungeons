package nl.hauntedmc.dungeons.world.generator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.util.world.BlockNbtUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2i;
import org.joml.Vector3i;

/**
 * Chunk generator that emits precomputed branching room blocks and borders.
 */
public class DungeonChunkGenerator extends SolidBlockChunkGenerator {
    private final DungeonsPlugin plugin;
    private final ConcurrentMap<Vector3i, StructurePieceBlock> blocks;
    private final ConcurrentMap<Vector2i, List<Vector3i>> outlines;
    private final ConcurrentMap<Vector2i, List<Vector3i>> chunkBlocks;
    private final AtomicLong nextNbtDelay = new AtomicLong(1L);

    /**
     * Creates a generator with pre-baked room block placements and room-outline coordinates.
     */
    public DungeonChunkGenerator(
            DungeonsPlugin plugin,
            Material blockType,
            Collection<InstanceRoom> rooms,
            Map<Vector3i, StructurePieceBlock> blocks) {
        super(blockType);
        this.plugin = plugin;
        this.blocks = new ConcurrentHashMap<>(blocks);
        Map<Vector2i, List<Vector3i>> outlinesByChunk = new HashMap<>();
        Map<Vector2i, List<Vector3i>> roomBlocksByChunk = new HashMap<>();

        for (InstanceRoom room : rooms) {
            BoundingBox newBox = room.getBounds().clone();
            BoundingBox trimmed = newBox.clone().expand(1.0, 1.0, 1.0);

            for (double x = trimmed.getMinX(); x <= trimmed.getMaxX(); x++) {
                for (double y = trimmed.getMinY(); y <= trimmed.getMaxY(); y++) {
                    for (double z = trimmed.getMinZ(); z <= trimmed.getMaxZ(); z++) {
                        boolean lessThanMax =
                                x < trimmed.getMaxX() && y < trimmed.getMaxY() && z < trimmed.getMaxZ();
                        boolean greaterThanMin =
                                x > trimmed.getMinX() && y > trimmed.getMinY() && z > trimmed.getMinZ();
                        List<Vector3i> chunkBlocks;
                        if (lessThanMax && greaterThanMin) {
                            chunkBlocks =
                                    roomBlocksByChunk.computeIfAbsent(
                                            new Vector2i((int) x >> 4, (int) z >> 4), k -> new ArrayList<>());
                        } else {
                            chunkBlocks =
                                    outlinesByChunk.computeIfAbsent(
                                            new Vector2i((int) x >> 4, (int) z >> 4), k -> new ArrayList<>());
                        }

                        chunkBlocks.add(new Vector3i((int) x, (int) y, (int) z));
                    }
                }
            }
        }

        this.outlines = freezeChunkMap(outlinesByChunk);
        this.chunkBlocks = freezeChunkMap(roomBlocksByChunk);
    }

    /**
     * Places precomputed outline and room blocks for the requested chunk.
     */
    @Override
    public void generateNoise(
            @NotNull WorldInfo worldInfo,
            @NotNull Random random,
            int chunkX,
            int chunkZ,
            @NotNull ChunkData chunkData) {
        this.populateRoomOutlines(chunkX, chunkZ, chunkData);
        this.populateRooms(chunkX, chunkZ, worldInfo, chunkData);
    }

    /** Writes precomputed room outline blocks for this chunk. */
    private void populateRoomOutlines(int chunkX, int chunkZ, ChunkData data) {
        int xOffset = chunkX << 4;
        int zOffset = chunkZ << 4;
        Vector2i chunkVec = new Vector2i(chunkX, chunkZ);
        List<Vector3i> blockLocs = this.outlines.remove(chunkVec);
        if (blockLocs != null) {
            for (Vector3i target : blockLocs) {
                data.setBlock(target.x - xOffset, target.y, target.z - zOffset, this.blockType);
            }
        }
    }

    /** Writes precomputed room blocks and schedules deferred tile-NBT application. */
    private void populateRooms(int chunkX, int chunkZ, WorldInfo worldInfo, ChunkData chunkData) {
        int xOffset = chunkX << 4;
        int zOffset = chunkZ << 4;
        Vector2i chunkVec = new Vector2i(chunkX, chunkZ);
        List<Vector3i> blockLocs = this.chunkBlocks.remove(chunkVec);
        if (blockLocs != null) {
            for (Vector3i bTarget : blockLocs) {
                double x = bTarget.x - xOffset;
                double y = bTarget.y;
                double z = bTarget.z - zOffset;
                StructurePieceBlock holder = this.blocks.remove(bTarget);
                if (holder == null) {
                    chunkData.setBlock((int) x, (int) y, (int) z, Material.AIR);
                } else {
                    BlockData data = holder.getBlockData();
                    chunkData.setBlock((int) x, (int) y, (int) z, data);

                    String weTag = holder.getBlockNbt();
                    if (weTag != null) {
                        this.scheduleTileNbtApply(worldInfo.getName(), bTarget.x, bTarget.y, bTarget.z, weTag);
                    }
                }
            }
        }
    }

    /** Returns pending block placements that have not yet been consumed by generation. */
    public Map<Vector3i, StructurePieceBlock> getBlocks() {
        return this.blocks;
    }

    /** Freezes chunk-to-block lists into immutable lists inside a concurrent map. */
    private static ConcurrentMap<Vector2i, List<Vector3i>> freezeChunkMap(
            Map<Vector2i, List<Vector3i>> source) {
        ConcurrentMap<Vector2i, List<Vector3i>> frozen = new ConcurrentHashMap<>();
        for (Map.Entry<Vector2i, List<Vector3i>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return frozen;
    }

    /** Schedules one delayed task to apply tile SNBT once the block exists in the world. */
    private void scheduleTileNbtApply(String worldName, int x, int y, int z, String snbt) {
        long delay = this.nextNbtDelay.getAndIncrement();
        Bukkit.getScheduler()
                .runTaskLater(
                        this.plugin,
                        () -> {
                            World world = Bukkit.getWorld(worldName);
                            if (world == null) {
                                return;
                            }

                            BlockNbtUtils.applyTileSnbt(world.getBlockAt(x, y, z), snbt);
                        },
                        delay);
    }
}
