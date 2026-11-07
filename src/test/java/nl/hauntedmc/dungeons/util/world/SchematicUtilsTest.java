package nl.hauntedmc.dungeons.util.world;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.generation.structure.StructurePiece;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.DeflaterOutputStream;

class SchematicUtilsTest {
    @TempDir Path directory;

    @Test
    void createsParentsAndReplacesExistingSchematicsWithoutLeavingTemporaryFiles()
            throws Exception {
        Path target = directory.resolve("rooms/room.struct");
        SchematicUtils.saveStructurePiece(target.toFile(), new StructurePiece());
        Files.writeString(target, "previous version");
        SchematicUtils.saveStructurePiece(target.toFile(), new StructurePiece());
        assertEquals(
                "DUNGEONS-STRUCTURE-2\n\n", SchematicUtils.decompress(Files.readAllBytes(target)));
        try (var files = Files.list(target.getParent())) {
            assertEquals(java.util.List.of(target), files.toList());
        }
    }

    @Test
    void invalidParentPropagatesFailure() throws Exception {
        Path parent = Files.writeString(directory.resolve("file"), "not a directory");
        assertThrows(
                IOException.class,
                () ->
                        SchematicUtils.saveStructurePiece(
                                parent.resolve("room.struct").toFile(), new StructurePiece()));
        assertEquals("not a directory", Files.readString(parent));
    }

    @Test
    void replacementFailurePreservesDestinationAndCleansTemporaryFile() throws Exception {
        Path target = Files.createDirectory(directory.resolve("room.struct"));
        Files.writeString(target.resolve("keep"), "previous content");
        assertThrows(
                IOException.class,
                () -> SchematicUtils.saveStructurePiece(target.toFile(), new StructurePiece()));
        assertEquals("previous content", Files.readString(target.resolve("keep")));
        try (var files = Files.list(directory)) {
            assertEquals(java.util.List.of(target), files.toList());
        }
    }

    @Test
    void flushFailurePreservesOldBytesAndCleansTemporaryFile() throws Exception {
        Path target = Files.writeString(directory.resolve("room.struct"), "previous content");
        FileChannel channel = mock(FileChannel.class);
        when(channel.write(any(java.nio.ByteBuffer.class)))
                .thenAnswer(
                        call -> {
                            java.nio.ByteBuffer buffer = call.getArgument(0);
                            int size = buffer.remaining();
                            buffer.position(buffer.limit());
                            return size;
                        });
        doThrow(new IOException("disk full")).when(channel).force(true);
        try (var channels = mockStatic(FileChannel.class)) {
            channels.when(() -> FileChannel.open(any(Path.class), eq(StandardOpenOption.WRITE)))
                    .thenReturn(channel);
            assertThrows(
                    IOException.class,
                    () -> SchematicUtils.saveStructurePiece(target.toFile(), new StructurePiece()));
        }
        assertEquals("previous content", Files.readString(target));
        verify(channel).close();
        try (var files = Files.list(directory)) {
            assertEquals(java.util.List.of(target), files.toList());
        }
    }

    @Test
    void compressionPreservesLegacyFormatAndUtf8() throws Exception {
        for (String text :
                new String[] {"", "minecraft:stone\n0,0,0|0|null", "é👻\n".repeat(10000)}) {
            ByteArrayOutputStream legacy = new ByteArrayOutputStream();
            DeflaterOutputStream stream = new DeflaterOutputStream(legacy);
            stream.write(text.getBytes(StandardCharsets.UTF_8));
            stream.close();

            assertArrayEquals(legacy.toByteArray(), SchematicUtils.compress(text));
            assertEquals(text, SchematicUtils.decompress(legacy.toByteArray()));
        }
    }
}
