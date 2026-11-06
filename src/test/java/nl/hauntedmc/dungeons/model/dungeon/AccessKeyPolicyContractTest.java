package nl.hauntedmc.dungeons.model.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;

import org.bukkit.*;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.Test;

import java.util.*;

class AccessKeyPolicyContractTest {

    @Test
    void
            serializedKeyDefinitionsKeepAuditAndCopyIsolationWhileGenericAndMetadataPoliciesRemainDistinct()
                    throws Exception {
        try (var aliases = new StaticMapSnapshot(ConfigurationSerialization.class, "aliases");
                var paper = new PaperBoundary()) {
            when(paper.plugin.namespace()).thenReturn("dungeons");
            TypeMetadataSupport.registerConfigurationSerializable(AccessKeyDefinition.class);
            var marker = new NamespacedKey("custom", "mark");
            for (boolean secured : List.of(false, true)) {
                paper.config.set("access.keys.unique_validation_enabled", false);
                paper.config.set("access.keys.metadata_validation_enabled", secured);
                var template = TaggedItems.stack(Material.PAPER, 2, Map.of(marker, "configured"));
                var timestamp = new Date(1700000000000L);
                var author = UUID.randomUUID();
                var key = new AccessKeyDefinition(template, timestamp, "Operator", author);
                var map = new HashMap<>(key.serialize());
                map.put("==", "dungeons.access.key_definition");
                var copy =
                        assertInstanceOf(
                                AccessKeyDefinition.class,
                                ConfigurationSerialization.deserializeObject(map));
                assertEquals(key.getKeyId(), copy.getKeyId());
                assertEquals("Operator", copy.getAddedByName());
                assertEquals(author.toString(), copy.getAddedByUniqueId());
                timestamp.setTime(0);
                var exposed = copy.getAddedAt();
                exposed.setTime(0);
                assertEquals(1700000000000L, copy.getAddedAt().getTime());
                var issued = copy.createItemCopy();
                assertTrue(copy.matches(issued));
                issued.setAmount(1);
                assertEquals(2, copy.getItem().getAmount());
                var ordinary = TaggedItems.stack(Material.PAPER, 1, Map.of(marker, "configured"));
                assertEquals(!secured, copy.matches(ordinary));
                ItemUtils.tagDungeonAccessKey(ordinary, "wrong-definition");
                assertEquals(!secured, copy.matches(ordinary));
                var wrong = TaggedItems.stack(Material.PAPER, 2, Map.of(marker, "different"));
                assertFalse(copy.matches(wrong));
                assertEquals(
                        secured ? copy.getKeyId() : null, ItemUtils.getDungeonAccessKeyId(issued));
                assertNull(
                        AccessKeyDefinition.fromConfigValue(Map.of("KeyId", 42, "Item", template)));
                assertNull(
                        AccessKeyDefinition.fromConfigValue(
                                Map.of("KeyId", copy.getKeyId(), "Item", "not an item")));
            }
        }
    }
}
