package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DungeonPlayerDataStoreTest {
    @Test void malformedDatabaseRecordsCannotBeReplacedByEmptySaves() {
        for (String payload : List.of("Finished: broken\n", "AccessCooldown: broken\n", "RewardsInfo: broken\n")) {
            try (var paper = new PaperBoundary()) {
                var player = new PlayerState().player;
                UUID dungeon = UUID.randomUUID();
                String scope = "dungeon-progress/" + dungeon;
                paper.state.seed(scope, player.getUniqueId().toString(), player.getUniqueId(), payload);
                var store = new DungeonPlayerDataStore(paper.plugin, dungeon);
                assertThrows(IllegalStateException.class, () -> store.save(player));
                assertEquals(payload, paper.state.payload(scope, player.getUniqueId().toString()));
            }
        }
    }
    @Test void databaseFailurePreservesDurableDocumentAndRetryRetainsUnknownFields() throws Exception {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState().player;
            UUID dungeon = UUID.randomUUID();
            String scope = "dungeon-progress/" + dungeon;
            paper.state.seed(scope, player.getUniqueId().toString(), player.getUniqueId(), "Finished: false\nExtensionField: retained\n");
            var store = new DungeonPlayerDataStore(paper.plugin, dungeon);
            paper.state.failWrites = true;
            assertThrows(RuntimeException.class, () -> store.setCompleted(player.getUniqueId(), true));
            assertFalse(store.isCompleted(player.getUniqueId()));
            assertTrue(paper.state.payload(scope, player.getUniqueId().toString()).contains("Finished: false"));
            paper.state.failWrites = false;
            store.setCompleted(player.getUniqueId(), true);
            var saved = new YamlConfiguration();
            saved.loadFromString(paper.state.payload(scope, player.getUniqueId().toString()));
            assertTrue(saved.getBoolean("Finished"));
            assertEquals("retained", saved.getString("ExtensionField"));
        }
    }
    @Test void cooldownIndexesAreIdempotentAndMismatchedOwnersAreProtected() throws Exception {
        try (var aliases = new StaticMapSnapshot(org.bukkit.configuration.serialization.ConfigurationSerialization.class, "aliases");
                var paper = new PaperBoundary()) {
            org.bukkit.configuration.serialization.ConfigurationSerialization.registerClass(nl.hauntedmc.dungeons.content.reward.PlayerLootData.class);
            var player = new PlayerState().player;
            var data = new YamlConfiguration();
            data.set("RewardsInfo", new nl.hauntedmc.dungeons.content.reward.PlayerLootData(player));
            UUID dungeon = UUID.randomUUID();
            String scope = "dungeon-progress/" + dungeon;
            paper.state.seed(scope, player.getUniqueId().toString(), player.getUniqueId(), data.saveToString());
            var store = new DungeonPlayerDataStore(paper.plugin, dungeon);
            store.loadCooldowns(player.getUniqueId()); store.loadCooldowns(player.getUniqueId());
            assertEquals(1, store.getLootCooldowns().size());
            var other = new PlayerState().player;
            paper.state.seed(scope, other.getUniqueId().toString(), other.getUniqueId(), data.saveToString());
            var reload = new DungeonPlayerDataStore(paper.plugin, dungeon);
            assertNull(reload.getRewards(other.getUniqueId()));
            assertThrows(IllegalStateException.class, () -> reload.save(other));
        }
    }
    @Test void accessDatesAreDetachedAndWorkerMutationsAreRejected() throws Exception {
        try (var paper = new PaperBoundary()) {
            var id = UUID.randomUUID();
            var store = new DungeonPlayerDataStore(paper.plugin, UUID.randomUUID());
            var date = new Date(System.currentTimeMillis() + 60000);
            long expected = date.getTime();
            store.setAccessCooldown(id, date); date.setTime(0); store.getAccessCooldown(id).setTime(0);
            assertEquals(expected, store.getAccessCooldown(id).getTime());
            paper.bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> store.setCompleted(id, true));
        }
    }
}
