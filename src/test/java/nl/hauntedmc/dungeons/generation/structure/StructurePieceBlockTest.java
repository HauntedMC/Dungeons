package nl.hauntedmc.dungeons.generation.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.block.data.BlockData;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.List;

class StructurePieceBlockTest {
    @Test
    void equalSnapshotsWorkInHashCollections() {
        // Only these BlockData methods are needed; no Paper server is required for this test.
        BlockData data =
                (BlockData)
                        Proxy.newProxyInstance(
                                BlockData.class.getClassLoader(),
                                new Class<?>[] {BlockData.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "getAsString" -> "minecraft:stone";
                                            case "equals" -> proxy == args[0];
                                            case "hashCode" -> 42;
                                            default ->
                                                    throw new UnsupportedOperationException(
                                                            method.getName());
                                        });
        StructurePieceBlock first = new StructurePieceBlock(new Vector3i(1, 2, 3), data, null);
        StructurePieceBlock second = new StructurePieceBlock(new Vector3i(1, 2, 3), data, null);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(1, new HashSet<>(List.of(first, second)).size());
        assertTrue(new HashSet<>(List.of(first)).contains(first.clone()));
        assertNotEquals(first, new StructurePieceBlock(new Vector3i(1, 2, 4), data, null));
        assertNotEquals(first, new StructurePieceBlock(new Vector3i(1, 2, 3), data, "{}"));
    }
}
