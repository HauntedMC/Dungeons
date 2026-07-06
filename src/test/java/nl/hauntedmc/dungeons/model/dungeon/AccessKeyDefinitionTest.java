package nl.hauntedmc.dungeons.model.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class AccessKeyDefinitionTest {

    @Test
    void fromConfigValue_rejectsLegacyRawItemStackEntries() {
        ItemStack keyItem = new ItemStack(Material.TRIPWIRE_HOOK, 2);

        AccessKeyDefinition accessKey = AccessKeyDefinition.fromConfigValue(keyItem);

        assertNull(accessKey);
    }

    @Test
    void fromConfigValue_rejectsSerializedEntriesWithoutStableKeyId() {
        Map<String, Object> serialized = new HashMap<>();
        serialized.put("Item", new ItemStack(Material.TRIPWIRE_HOOK, 1));

        AccessKeyDefinition accessKey = AccessKeyDefinition.fromConfigValue(serialized);

        assertNull(accessKey);
    }

    @Test
    void serializeRoundTrip_preservesAuditMetadataAndItem() {
        Date addedAt = new Date(1_725_000_000_000L);
        UUID addedBy = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        AccessKeyDefinition original = new AccessKeyDefinition(
                new ItemStack(Material.TRIPWIRE_HOOK, 3),
                addedAt,
                "Remy",
                addedBy);

        AccessKeyDefinition restored = AccessKeyDefinition.fromConfigValue(original.serialize());

        assertNotNull(restored);
        assertEquals(original.getKeyId(), restored.getKeyId());
        assertEquals(addedAt, restored.getAddedAt());
        assertEquals("Remy", restored.getAddedByName());
        assertEquals(addedBy.toString(), restored.getAddedByUniqueId());
        assertEquals(Material.TRIPWIRE_HOOK, restored.getItem().getType());
        assertEquals(3, restored.getItem().getAmount());
        assertFalse(restored.matches(new ItemStack(Material.TRIPWIRE_HOOK, 3)));
    }
}
