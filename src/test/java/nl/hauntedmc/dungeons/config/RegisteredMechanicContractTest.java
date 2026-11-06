package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.*;

import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class RegisteredMechanicContractTest {
    static Map<String, String> manifest() throws IOException {
        try (var input =
                RegisteredMechanicContractTest.class.getResourceAsStream(
                        "/contracts/mechanics.csv")) {
            assertNotNull(input);
            var rows =
                    new String(input.readAllBytes(), StandardCharsets.UTF_8)
                            .lines()
                            .skip(1)
                            .toList();
            Map<String, String> result = new LinkedHashMap<>();
            for (String row : rows) {
                var columns = row.split(",", 2);
                assertNull(result.put(columns[1], columns[0]));
            }
            return result;
        }
    }

    @Test
    void
            registeredMechanicsKeepTheirPersistedTypeIdsAndEveryDiscoveredTypeHasAnExplicitManifestEntry()
                    throws Exception {
        var expected = manifest();
        var types = new ArrayList<Class<?>>();
        types.addAll(
                TypeMetadataSupport.discoverAutoRegisteredTypes(
                        "nl.hauntedmc.dungeons.content.function",
                        nl.hauntedmc.dungeons.model.element.DungeonFunction.class));
        types.addAll(
                TypeMetadataSupport.discoverAutoRegisteredTypes(
                        "nl.hauntedmc.dungeons.content.trigger",
                        nl.hauntedmc.dungeons.model.element.DungeonTrigger.class));
        types.addAll(
                TypeMetadataSupport.discoverAutoRegisteredTypes(
                        "nl.hauntedmc.dungeons.content.condition",
                        nl.hauntedmc.dungeons.model.element.TriggerCondition.class));
        assertEquals(
                expected.keySet(),
                types.stream().map(Class::getName).collect(java.util.stream.Collectors.toSet()));
        Set<String> ids = new HashSet<>();
        for (var type : types) {
            assertEquals(expected.get(type.getName()), TypeMetadataSupport.requiredId(type));
            assertTrue(ids.add(TypeMetadataSupport.requiredId(type)));
        }
    }
}
