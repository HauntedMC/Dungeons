package nl.hauntedmc.dungeons.world.generator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.world.BlockNbtUtils;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.generator.WorldInfo;
import org.bukkit.util.BoundingBox;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import java.util.*;

class ChunkAssemblyContractTest {
    @Test
    void negativeChunkBoundariesKeepBlocksInTheCorrectChunkAndApplyTileDataAfterPlacement() {
        try (var paper = new PaperBoundary();
                var nbt = mockStatic(BlockNbtUtils.class)) {
            var scheduler = new CapturedScheduler(paper);
            var room = mock(InstanceRoom.class);
            when(room.getBounds()).thenReturn(new BoundingBox(-17, 70, -1, 1, 71, 1));
            var fill = mock(Material.class);
            when(fill.isBlock()).thenReturn(true);
            var data = mock(BlockData.class);
            var tile = mock(StructurePieceBlock.class);
            when(tile.getBlockData()).thenReturn(data);
            when(tile.getBlockNbt()).thenReturn("{Text:'a;b'}");
            var plain = mock(StructurePieceBlock.class);
            when(plain.getBlockData()).thenReturn(data);
            var placements =
                    Map.of(
                            new Vector3i(-17, 70, -1),
                            tile,
                            new Vector3i(-16, 70, 0),
                            plain,
                            new Vector3i(0, 71, 0),
                            plain);
            var generator =
                    new DungeonChunkGenerator(paper.plugin, fill, List.of(room), placements);
            var worldInfo = mock(WorldInfo.class);
            when(worldInfo.getName()).thenReturn("generated");
            var corner = mock(ChunkData.class);
            var middle = mock(ChunkData.class);
            var positive = mock(ChunkData.class);
            generator.generateNoise(worldInfo, new Random(0), -2, -1, corner);
            verify(corner).setBlock(15, 70, 15, data);
            assertEquals(2, generator.getBlocks().size());
            generator.generateNoise(worldInfo, new Random(0), -1, 0, middle);
            generator.generateNoise(worldInfo, new Random(0), 0, 0, positive);
            verify(middle).setBlock(0, 70, 0, data);
            verify(positive).setBlock(0, 71, 0, data);
            assertTrue(generator.getBlocks().isEmpty());
            assertEquals(3, placements.size());
            for (var chunk : List.of(corner, middle, positive)) {
                for (var call : mockingDetails(chunk).getInvocations()) {
                    if (call.getMethod().getName().equals("setBlock")) {
                        int x = call.getArgument(0);
                        int z = call.getArgument(2);
                        assertTrue(x >= 0 && x < 16 && z >= 0 && z < 16);
                    }
                }
            }
            nbt.verifyNoInteractions();
            assertEquals(1, scheduler.jobs.size());
            assertEquals(1, scheduler.jobs.getFirst().delay);
            var world = mock(World.class);
            var block = mock(Block.class);
            paper.bukkit.when(() -> Bukkit.getWorld("generated")).thenReturn(world);
            when(world.getBlockAt(-17, 70, -1)).thenReturn(block);
            scheduler.jobs.getFirst().run();
            nbt.verify(() -> BlockNbtUtils.applyTileSnbt(block, "{Text:'a;b'}"));
        }
    }
}
