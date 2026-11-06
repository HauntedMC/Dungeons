package nl.hauntedmc.dungeons.model.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IssuedKeyLedgerContractTest {
    @TempDir Path directory;

    @Test
    void issuedKeysReserveOnceReleaseForRetryAndCannotBeReplayedAfterConsumptionOrReload() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var dungeon = fixture.dungeon;
            var first = dungeon.issueAccessKey(1);
            var second = dungeon.issueAccessKey(1);
            assertNotNull(first);
            assertNotNull(second);
            var duplicate = first.clone();
            String consumedId = ItemUtils.getDungeonAccessKeyInstanceId(first);
            String secondId = ItemUtils.getDungeonAccessKeyInstanceId(second);
            assertNotEquals(consumedId, secondId);
            assertNotNull(dungeon.isValidKey(first));
            assertTrue(dungeon.reserveIssuedAccessKey(first));
            assertFalse(dungeon.reserveIssuedAccessKey(duplicate));
            assertTrue(dungeon.isReservedAccessKey(duplicate));
            assertNull(dungeon.isValidKey(duplicate));
            assertTrue(dungeon.releaseReservedAccessKey(first));
            assertFalse(dungeon.releaseReservedAccessKey(first));
            assertNotNull(dungeon.isValidKey(duplicate));
            assertTrue(dungeon.reserveIssuedAccessKey(duplicate));
            assertTrue(dungeon.consumeAccessKey(first));
            assertFalse(dungeon.consumeAccessKey(duplicate));
            assertFalse(dungeon.isReservedAccessKey(first));
            var restored = fixture.reload();
            assertNull(restored.isValidKey(first));
            assertFalse(restored.reserveIssuedAccessKey(duplicate));
            assertNotNull(restored.isValidKey(second));
            var saved = YamlConfiguration.loadConfiguration(fixture.stateFile.toFile());
            assertEquals(List.of(consumedId), saved.getStringList("invalidated_instance_ids"));
            assertTrue(saved.getStringList("reserved_instance_ids").isEmpty());
            assertEquals(fixture.definition.getKeyId(), saved.getString("issued_instances." + secondId));
            assertNull(saved.get("issued_instances." + consumedId));
        }
    }

    @Test
    void reloadReleasesInterruptedReservationsWithoutRevivingConsumedKeys() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var interrupted = fixture.dungeon.issueAccessKey(1);
            var consumed = fixture.dungeon.issueAccessKey(1);
            assertTrue(fixture.dungeon.reserveIssuedAccessKey(interrupted));
            assertTrue(fixture.dungeon.consumeAccessKey(consumed));
            String interruptedId = ItemUtils.getDungeonAccessKeyInstanceId(interrupted);
            assertEquals(List.of(interruptedId), YamlConfiguration.loadConfiguration(fixture.stateFile.toFile()).getStringList("reserved_instance_ids"));
            var restored = fixture.reload();
            assertNotNull(restored.isValidKey(interrupted));
            assertFalse(restored.isReservedAccessKey(interrupted));
            assertNull(restored.isValidKey(consumed));
            assertTrue(YamlConfiguration.loadConfiguration(fixture.stateFile.toFile()).getStringList("reserved_instance_ids").isEmpty());
            assertTrue(restored.reserveIssuedAccessKey(interrupted));
            assertFalse(restored.reserveIssuedAccessKey(interrupted.clone()));
        }
    }

    @Test
    void reusableKeysRotateIdentityAndRemovingTheDefinitionRevokesEveryIssuedCopyDurably() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var original = fixture.dungeon.issueAccessKey(1);
            var other = fixture.dungeon.issueAccessKey(1);
            assertTrue(fixture.dungeon.reserveIssuedAccessKey(original));
            var replacement = fixture.dungeon.rotateAccessKey(original);
            assertNotNull(replacement);
            assertNotEquals(ItemUtils.getDungeonAccessKeyInstanceId(original), ItemUtils.getDungeonAccessKeyInstanceId(replacement));
            assertNull(fixture.dungeon.isValidKey(original.clone()));
            assertFalse(fixture.dungeon.isReservedAccessKey(original));
            assertNotNull(fixture.dungeon.isValidKey(replacement));
            var restored = fixture.reload();
            assertNotNull(restored.isValidKey(replacement));
            assertNull(restored.isValidKey(original));
            assertTrue(restored.reserveIssuedAccessKey(other));
            assertEquals(1, restored.removeAllAccessKeys());
            assertFalse(restored.isReservedAccessKey(other));
            assertNull(restored.isValidKey(other));
            assertNull(restored.isValidKey(replacement));
            var reinstalledDefinition = fixture.reload();
            assertNull(reinstalledDefinition.isValidKey(original));
            assertNull(reinstalledDefinition.isValidKey(replacement));
            assertNull(reinstalledDefinition.isValidKey(other));
            var saved = YamlConfiguration.loadConfiguration(fixture.stateFile.toFile());
            assertTrue(saved.getConfigurationSection("issued_instances").getKeys(false).isEmpty());
            assertTrue(saved.getStringList("reserved_instance_ids").isEmpty());
            assertEquals(3, saved.getStringList("invalidated_instance_ids").size());
        }
    }

    static final class Fixture implements AutoCloseable {
        final PaperBoundary paper = new PaperBoundary();
        final Path folder;
        final Path stateFile;
        final AccessKeyDefinition definition;
        final LedgerDungeon dungeon;
        Fixture(Path directory) throws Exception {
            folder = Files.createDirectories(directory.resolve("crypt"));
            stateFile = folder.resolve("access_keys.yml");
            when(paper.plugin.namespace()).thenReturn("dungeons");
            paper.bukkit.when(org.bukkit.Bukkit::getWorldContainer).thenReturn(directory.toFile());
            paper.config.set("access.keys.unique_validation_enabled", true);
            definition = new AccessKeyDefinition(item(new HashMap<>()), null, null, null);
            dungeon = reload();
        }
        LedgerDungeon reload() throws Exception {
            paper.config.set("access.keys.unique_validation_enabled", false);
            paper.runtime.setDefaultDungeonConfig(new YamlConfiguration());
            var loaded = new LedgerDungeon(paper, folder);
            loaded.define(definition);
            paper.config.set("access.keys.unique_validation_enabled", true);
            return loaded;
        }
        @Override public void close() { paper.close(); }
    }

    // ItemStack deserialization belongs to Paper. Supply definition metadata directly, then exercise
    // the real ledger load/save, validation, tags, and transitions through dungeon methods.
    static final class LedgerDungeon extends StaticDungeon {
        LedgerDungeon(PaperBoundary paper, Path folder) throws DungeonLoadException {
            super(paper.runtime, folder.toFile(), new YamlConfiguration());
        }
        void define(AccessKeyDefinition definition) { this.accessKeys = new ArrayList<>(List.of(definition)); }
    }

    static ItemStack item(Map<NamespacedKey, String> tags) {
        var item = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        var data = mock(PersistentDataContainer.class);
        when(item.getType()).thenReturn(Material.PAPER);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(item.clone()).thenAnswer(call -> item(new HashMap<>(tags)));
        when(data.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(call -> tags.get(call.getArgument(0)));
        doAnswer(call -> { tags.put(call.getArgument(0), call.getArgument(2)); return null; })
                .when(data).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
        doAnswer(call -> { tags.remove(call.getArgument(0)); return null; })
                .when(data).remove(any(NamespacedKey.class));
        return item;
    }
}
