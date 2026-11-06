package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.model.element.DungeonElement;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;

import org.bukkit.Location;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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
                        "nl.hauntedmc.dungeons.content.function", DungeonFunction.class));
        types.addAll(
                TypeMetadataSupport.discoverAutoRegisteredTypes(
                        "nl.hauntedmc.dungeons.content.trigger", DungeonTrigger.class));
        types.addAll(
                TypeMetadataSupport.discoverAutoRegisteredTypes(
                        "nl.hauntedmc.dungeons.content.condition", TriggerCondition.class));
        assertEquals(
                expected.keySet(), types.stream().map(Class::getName).collect(Collectors.toSet()));
        Set<String> ids = new HashSet<>();
        for (var type : types) {
            assertEquals(expected.get(type.getName()), TypeMetadataSupport.requiredId(type));
            assertTrue(ids.add(TypeMetadataSupport.requiredId(type)));
        }
    }

    @Test
    void everyPersistedMechanicAliasRestoresItsConcreteTypeAndConfiguredBaseFields()
            throws Exception {
        var aliases = new LinkedHashMap<String, Class<? extends ConfigurationSerializable>>();
        try (var paper = new PaperBoundary();
                var menus = new HeadlessMenus();
                var gui = new HeadlessInventoryMenus();
                var items = mockStatic(ItemUtils.class)) {
            when(paper.plugin.namespace()).thenReturn("dungeons");
            for (var entry : manifest().entrySet()) {
                var type = Class.forName(entry.getKey()).asSubclass(DungeonElement.class);
                aliases.put(
                        entry.getValue(),
                        ConfigurationSerialization.getClassByAlias(entry.getValue()));
                ConfigurationSerialization.registerClass(type, entry.getValue());
                var data = new LinkedHashMap<String, Object>();
                data.put("==", entry.getValue());
                data.put("location", new Location(null, -17, 80, 31));
                data.put("allowRetrigger", true);
                data.put("inverted", true);
                var restored = ConfigurationSerialization.deserializeObject(data);
                assertEquals(type, restored.getClass(), entry.getKey());
                var element = (DungeonElement) restored;
                element.initializeFields();
                var serialized = new LinkedHashMap<>(element.serialize());
                if (element instanceof DungeonFunction function) {
                    assertEquals(
                            new Location(null, -17, 80, 31),
                            function.getLocation(),
                            entry.getKey());
                }
                if (element instanceof DungeonTrigger trigger)
                    assertTrue(trigger.isAllowRetrigger(), entry.getKey());
                if (element instanceof TriggerCondition condition)
                    assertTrue(condition.isInverted(), entry.getKey());
                serialized.put("==", entry.getValue());
                var copy =
                        (DungeonElement) ConfigurationSerialization.deserializeObject(serialized);
                copy.initializeFields();
                assertEquals(element.serialize(), copy.serialize(), entry.getKey());
            }
        } finally {
            aliases.forEach(
                    (alias, type) -> {
                        ConfigurationSerialization.unregisterClass(alias);
                        if (type != null) ConfigurationSerialization.registerClass(type, alias);
                    });
        }
    }
}
