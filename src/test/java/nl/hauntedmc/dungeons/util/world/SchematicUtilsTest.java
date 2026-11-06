package nl.hauntedmc.dungeons.util.world;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.DeflaterOutputStream;
import org.junit.jupiter.api.Test;

class SchematicUtilsTest {
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
