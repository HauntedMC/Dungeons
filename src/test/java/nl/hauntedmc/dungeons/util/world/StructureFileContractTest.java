package nl.hauntedmc.dungeons.util.world;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.generation.structure.StructurePiece;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StructureFileContractTest {
    @TempDir Path directory;

    @Test
    void structureFilesRoundTripCoordinatesAndPaletteIncludingNegativePositions() {
        try (var paper = new PaperBoundary()) {
            BlockData stone = data(paper, "minecraft:stone");
            BlockData air = data(paper, "minecraft:air");
            StructurePiece source = new StructurePiece();
            source.add(new StructurePieceBlock(new Vector3i(-17, -64, 30), stone, null));
            source.add(new StructurePieceBlock(new Vector3i(16, 320, -1), air, null));
            source.add(new StructurePieceBlock(new Vector3i(0, 0, 0), stone, null));
            var file = directory.resolve("room.schem").toFile();
            SchematicUtils.saveStructurePiece(file, source);
            assertEquals(snapshots(source), snapshots(SchematicUtils.loadStructurePiece(file)));
        }
    }

    @Test
    void anEmptyCapturedStructureRemainsAValidEmptyStructureAfterReload() {
        try (var paper = new PaperBoundary()) {
            var file = directory.resolve("empty.schem").toFile();
            SchematicUtils.saveStructurePiece(file, new StructurePiece());
            assertEquals(0, SchematicUtils.loadStructurePiece(file).size());
        }
    }

    @Test
    void existingUnversionedStructureFilesRemainReadable() throws Exception {
        try (var paper = new PaperBoundary()) {
            data(paper, "minecraft:stone");
            var file = directory.resolve("legacy.schem");
            java.nio.file.Files.write(file, SchematicUtils.compress("minecraft:stone\n-3,5,8|0|null"));
            assertEquals(List.of("-3,5,8|minecraft:stone|null"), snapshots(SchematicUtils.loadStructurePiece(file.toFile())));
        }
    }

    BlockData data(PaperBoundary paper, String state) {
        BlockData data = mock(BlockData.class);
        when(data.getAsString(true)).thenReturn(state);
        paper.bukkit.when(() -> Bukkit.createBlockData(state)).thenReturn(data);
        return data;
    }

    List<String> snapshots(StructurePiece piece) {
        List<String> values = new ArrayList<>();
        for (StructurePieceBlock block : piece) {
            values.add(block.getX() + "," + block.getY() + "," + block.getZ() + "|" + block.getBlockData().getAsString(true) + "|" + block.getBlockNbt());
        }
        return values;
    }
}
