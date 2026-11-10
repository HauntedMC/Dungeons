package nl.hauntedmc.dungeons.model.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.persistence.StateDocument;
import nl.hauntedmc.dungeons.persistence.StateStore;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonAccessKeyService;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonIdentity;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.util.item.ItemUtils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class IssuedKeyLedgerContractTest {
    @TempDir Path directory;

    @Test
    void failedKeyDefinitionSaveReportsFailureAndRestoresThePreviousConfiguration() throws Exception {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.namespace()).thenReturn("dungeons");
            paper.config.set("access.keys.unique_validation_enabled", false);
            paper.config.set("access.keys.metadata_validation_enabled", true);
            var dungeon = paper.dungeon(directory.resolve("key-save"), new YamlConfiguration());
            var configFile = dungeon.getFolder().toPath().resolve("config.yml");
            String durable = Files.readString(configFile);
            Files.delete(configFile);
            Files.createDirectory(configFile);
            Files.writeString(configFile.resolve("obstacle"), "keep");
            try {
                Assertions.assertThrows(UncheckedIOException.class,
                        () -> dungeon.addAccessKey(item(new HashMap<>()), null));
                assertTrue(dungeon.getAccessKeys().isEmpty());
                assertTrue(dungeon.getValidKeys().isEmpty());
                assertNull(dungeon.getConfig().get("access.keys.items"));
                assertEquals("keep", Files.readString(configFile.resolve("obstacle")));
            } finally {
                Files.delete(configFile.resolve("obstacle"));
                Files.delete(configFile);
                Files.writeString(configFile, durable);
            }
            var added = dungeon.addAccessKey(item(new HashMap<>()), null);
            assertEquals(1, dungeon.getAccessKeys().size());
            assertTrue(Files.readString(configFile).contains(added.getKeyId()));
        }
    }

    @Test
    void metadataOnlyKeysSurviveIdAssignmentWithoutNewTagsOrAReadableUniqueLedger() throws Exception {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.namespace()).thenReturn("dungeons");
            paper.config.set("access.keys.unique_validation_enabled", false);
            paper.config.set("access.keys.metadata_validation_enabled", true);
            var definition = new AccessKeyDefinition(Map.of("KeyId", "existing-definition", "Item", item(new HashMap<>())));
            var original = item(new HashMap<>(Map.of(new NamespacedKey("dungeons", "dungeon_access_key_id"), "existing-definition")));
            Files.writeString(directory.resolve("access_keys.yml"), "broken: [unused");
            var config = new YamlConfiguration();
            var id = DungeonIdentity.initialize(
                    paper.plugin, config, directory.resolve("config.yml").toFile());
            var service = new DungeonAccessKeyService(
                    paper.runtime, directory.toFile(), id, () -> List.of(definition));
            assertNotNull(service.isValidKey(original));
            ItemUtils.tagDungeonAccessKeyInstance(original, UUID.randomUUID().toString());
            assertNotNull(service.isValidKey(original));
            assertEquals("existing-definition", definition.getKeyId());
            var restored = new YamlConfiguration();
            restored.load(directory.resolve("config.yml").toFile());
            assertEquals(id, DungeonIdentity.initialize(
                    paper.plugin, restored, directory.resolve("config.yml").toFile()));
            assertEquals("broken: [unused", Files.readString(directory.resolve("access_keys.yml")));
        }
    }

    @Test
    void failedLedgerReplacementCannotReportConsumptionOrChangeTheWorkingLedger() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var key = fixture.dungeon.issueAccessKey(1);
            String document = fixture.paper.state.payload("key-ledger", fixture.dungeon.getDungeonId().toString());
            fixture.paper.state.failWrites = true;
            Assertions.assertThrows(IllegalStateException.class,
                    () -> fixture.dungeon.consumeAccessKey(key));
            assertNotNull(fixture.dungeon.isValidKey(key));
            fixture.paper.state.failWrites = false;
            assertNotNull(fixture.reload().isValidKey(key));
            assertTrue(fixture.dungeon.consumeAccessKey(key));
            assertNull(fixture.reload().isValidKey(key));
        }
    }

    @Test
    void issuedKeysReserveOnceReleaseForRetryAndCannotBeReplayedAfterConsumptionOrReload()
            throws Exception {
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
            var saved = fixture.saved();
            assertEquals(List.of(consumedId), saved.getStringList("invalidated_instance_ids"));
            assertTrue(saved.getStringList("reserved_instance_ids").isEmpty());
            assertEquals(
                    fixture.definition.getKeyId(), saved.getString("issued_instances." + secondId));
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
            assertEquals(
                    List.of(interruptedId),
                    fixture.saved()
                            .getStringList("reserved_instance_ids"));
            var restored = fixture.reload();
            assertNotNull(restored.isValidKey(interrupted));
            assertFalse(restored.isReservedAccessKey(interrupted));
            assertNull(restored.isValidKey(consumed));
            assertTrue(
                    fixture.saved()
                            .getStringList("reserved_instance_ids")
                            .isEmpty());
            assertTrue(restored.reserveIssuedAccessKey(interrupted));
            assertFalse(restored.reserveIssuedAccessKey(interrupted.clone()));
        }
    }

    @Test
    void reusableKeysRotateIdentityAndRemovingTheDefinitionRevokesEveryIssuedCopyDurably()
            throws Exception {
        try (var fixture = new Fixture(directory)) {
            var original = fixture.dungeon.issueAccessKey(1);
            var other = fixture.dungeon.issueAccessKey(1);
            assertTrue(fixture.dungeon.reserveIssuedAccessKey(original));
            var replacement = fixture.dungeon.rotateAccessKey(original);
            assertNotNull(replacement);
            assertNotEquals(
                    ItemUtils.getDungeonAccessKeyInstanceId(original),
                    ItemUtils.getDungeonAccessKeyInstanceId(replacement));
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
            var saved = fixture.saved();
            assertTrue(saved.getConfigurationSection("issued_instances").getKeys(false).isEmpty());
            assertTrue(saved.getStringList("reserved_instance_ids").isEmpty());
            assertEquals(3, saved.getStringList("invalidated_instance_ids").size());
        }
    }

    @Test
    void malformedLedgerCannotBeOverwrittenByIssuingAKeyAndCanRecoverAfterOperatorRepair()
            throws Exception {
        try (var fixture = new Fixture(directory)) {
            String broken = "issued_instances: [unfinished";
            fixture.paper.state.seed("key-ledger", fixture.dungeon.getDungeonId().toString(), null, broken);
            assertNull(fixture.dungeon.issueAccessKey(1));
            assertEquals(broken, fixture.paper.state.payload("key-ledger", fixture.dungeon.getDungeonId().toString()));
            fixture.paper.state.seed("key-ledger", fixture.dungeon.getDungeonId().toString(), null,
                    "issued_instances: {}\nreserved_instance_ids: []\ninvalidated_instance_ids: []\n");
            var issued = fixture.dungeon.issueAccessKey(1);
            assertNotNull(issued);
            assertNotNull(fixture.dungeon.isValidKey(issued));
            assertNotNull(fixture.reload().isValidKey(issued));
        }
    }

    @Test
    void unknownOrMismatchedTagsCannotReserveConsumeOrRotateAGenuineIssuedKey() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var genuine = fixture.dungeon.issueAccessKey(1);
            var unknownInstance = genuine.clone();
            ItemUtils.tagDungeonAccessKeyInstance(unknownInstance, UUID.randomUUID().toString());
            var wrongDefinition = genuine.clone();
            ItemUtils.tagDungeonAccessKey(wrongDefinition, UUID.randomUUID().toString());
            for (var invalid : List.of(unknownInstance, wrongDefinition, item(new HashMap<>()))) {
                assertNull(fixture.dungeon.isValidKey(invalid));
                assertFalse(fixture.dungeon.reserveIssuedAccessKey(invalid));
                assertFalse(fixture.dungeon.consumeAccessKey(invalid));
                assertNull(fixture.dungeon.rotateAccessKey(invalid));
            }
            assertNull(fixture.dungeon.isValidKey(null));
            assertFalse(fixture.dungeon.reserveIssuedAccessKey(null));
            assertFalse(fixture.dungeon.consumeAccessKey(null));
            assertNull(fixture.dungeon.rotateAccessKey(null));
            assertNotNull(fixture.dungeon.isValidKey(genuine));
            assertNotNull(fixture.reload().isValidKey(genuine));
        }
    }

    @Test
    void jointSettlementDoesNotRotateTheWorkingLedgerWhenTheRecoveryWriteFails() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var service = new DungeonAccessKeyService(
                    fixture.paper.runtime, fixture.folder.toFile(), fixture.dungeon.getDungeonId(), () -> List.of(fixture.definition));
            var key = service.issueAccessKey(fixture.definition);
            assertTrue(service.reserveIssuedAccessKey(key));
            var journalKey = new StateStore.Key("player-recovery-test", "atomic", null);
            var journal = fixture.paper.state.document(journalKey);
            var settlement = service.prepareSettlement(key, false, false);
            assertTrue(settlement.valid());
            assertTrue(service.isReservedAccessKey(key));
            fixture.paper.state.failedWrites.add(journalKey);
            Assertions.assertThrows(RuntimeException.class, () ->
                    StateDocument.commitTogether(settlement.write(), journal.prepare("replacement")));
            assertTrue(service.isReservedAccessKey(key));
            assertFalse(fixture.paper.state.payload("key-ledger", fixture.dungeon.getDungeonId().toString())
                    .contains(ItemUtils.getDungeonAccessKeyInstanceId(settlement.replacement())));
            assertEquals(0, journal.revision());
            fixture.paper.state.failedWrites.clear();
            StateDocument.commitTogether(settlement.write(), journal.prepare("replacement"));
            settlement.publish().run();
            assertFalse(service.isReservedAccessKey(key));
            assertNull(service.isValidKey(key));
            assertNotNull(service.isValidKey(settlement.replacement()));
            assertEquals(1, journal.revision());
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
            paper.bukkit.when(Bukkit::getWorldContainer).thenReturn(directory.toFile());
            paper.config.set("access.keys.unique_validation_enabled", true);
            definition = new AccessKeyDefinition(item(new HashMap<>()), null, null, null);
            dungeon = reload();
        }

        YamlConfiguration saved() { return paper.state.yaml("key-ledger", dungeon.getDungeonId().toString()); }
        LedgerDungeon reload() throws Exception {
            paper.config.set("access.keys.unique_validation_enabled", false);
            paper.runtime.setDefaultDungeonConfig(new YamlConfiguration());
            var loaded = new LedgerDungeon(paper, folder);
            loaded.define(definition);
            paper.config.set("access.keys.unique_validation_enabled", true);
            return loaded;
        }

        @Override
        public void close() {
            paper.close();
        }
    }

    // ItemStack deserialization belongs to Paper. Supply definition metadata directly, then
    // exercise
    // the real ledger load/save, validation, tags, and transitions through dungeon methods.
    static final class LedgerDungeon extends StaticDungeon {
        LedgerDungeon(PaperBoundary paper, Path folder) throws DungeonLoadException {
            super(paper.runtime, folder.toFile(), new YamlConfiguration());
        }

        void define(AccessKeyDefinition definition) {
            this.accessKeys = new ArrayList<>(List.of(definition));
        }
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
        doAnswer(
                        call -> {
                            tags.put(call.getArgument(0), call.getArgument(2));
                            return null;
                        })
                .when(data)
                .set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
        doAnswer(
                        call -> {
                            tags.remove(call.getArgument(0));
                            return null;
                        })
                .when(data)
                .remove(any(NamespacedKey.class));
        return item;
    }
}
