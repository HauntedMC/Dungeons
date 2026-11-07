package nl.hauntedmc.dungeons.runtime.rewards;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.content.reward.LootTableItem;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class LootRepositoryContractTest {
    @TempDir Path directory;

    @Test
    void malformedLootItemsAreRejectedBeforeCreatingAnyEditorWindows() throws Exception {
        try (var f = new Fixture(directory, null)) {
            var item = PlayerState.stack(2);
            assertThrows(
                    IllegalArgumentException.class, () -> new LootTableItem(Map.of("weight", 1)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LootTableItem(Map.of("item", item, "weight", -1)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new LootTableItem(Map.of("item", item, "minItems", 2, "maxItems", 1)));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new LootTable(
                                    Map.of(
                                            "namespace",
                                            "bad",
                                            "lootItems",
                                            Map.of(8, new LootTableItem(item)))));
            assertEquals(0, f.menus.windows().size());
        }
    }

    @Test
    void malformedReloadKeepsWorkingModelsAndBlocksEveryWriteUntilAValidReload() throws Exception {
        try (var f = new Fixture(directory, null)) {
            var working = new LootTable("working");
            assertTrue(f.repository.put(working));
            String valid = Files.readString(f.file);
            String broken = "Tables: [unterminated\n";
            Files.writeString(f.file, broken);
            assertFalse(f.repository.reload());
            assertSame(working, f.repository.get("working"));
            assertFalse(f.repository.isWritable());
            assertFalse(f.repository.put(new LootTable("new")));
            assertFalse(f.repository.remove("working"));
            assertFalse(f.repository.saveTablesConfig());
            assertFalse(f.repository.saveTablesConfig(working));
            assertSame(working, f.repository.get("working"));
            assertFalse(f.repository.contains("new"));
            assertEquals(broken, Files.readString(f.file));
            Files.writeString(f.file, valid);
            assertTrue(f.repository.reload());
            assertTrue(f.repository.isWritable());
            assertTrue(f.repository.put(new LootTable("new")));
            assertTrue(f.repository.reload());
            assertTrue(f.repository.contains("working"));
            assertTrue(f.repository.contains("new"));
        }
    }

    @Test
    void invalidNeighborsSchemaAndModelDataRejectTheWholeReplacementInsteadOfDroppingEntries()
            throws Exception {
        try (var f = new Fixture(directory, null)) {
            var working = new LootTable("valid");
            assertTrue(f.repository.put(working));
            String good = Files.readString(f.file);
            for (String broken :
                    List.of(
                            good + "  broken: not-a-loot-table\n",
                            good + "  valid: duplicate-key\n",
                            "Tables: scalar\n",
                            "Tables: []\n",
                            "Other: only\n",
                            "",
                            good.replace("namespace: valid", "namespace: mismatch"),
                            good.replace("minItems: 1", "minItems: -1"),
                            good.replace("lootItems: {}", "lootItems: invalid"),
                            good.replace("dungeons.reward.loot_table", "unknown.loot_table"))) {
                Files.writeString(f.file, broken);
                assertFalse(f.repository.reload(), broken);
                assertSame(working, f.repository.get("valid"));
                assertFalse(f.repository.saveTablesConfig());
                assertEquals(broken, Files.readString(f.file));
            }
        }
    }

    @Test
    void failedInitialLoadCannotOverwriteTheOriginalDocumentAndCanRecoverLater() throws Exception {
        try (var f = new Fixture(directory, "Tables: [broken\n")) {
            assertTrue(f.repository.getTables().isEmpty());
            assertFalse(f.repository.isWritable());
            assertFalse(f.repository.put(new LootTable("new")));
            assertFalse(f.repository.remove("anything"));
            assertFalse(f.repository.saveTablesConfig());
            assertEquals("Tables: [broken\n", Files.readString(f.file));
            Files.writeString(f.file, "Tables: {}\n");
            assertTrue(f.repository.reload());
            assertTrue(f.repository.put(new LootTable("recovered")));
            assertTrue(f.repository.reload());
            assertTrue(f.repository.contains("recovered"));
        }
    }

    @Test
    void missingReloadDoesNotRecreateTheFileOrDiscardTheWorkingTables() throws Exception {
        try (var f = new Fixture(directory, null)) {
            assertFalse(Files.exists(f.file), "Loading a new installation need not write a file");
            var table = new LootTable("keep");
            assertTrue(f.repository.put(table));
            String good = Files.readString(f.file);
            Files.delete(f.file);
            assertFalse(f.repository.reload());
            assertSame(table, f.repository.get("keep"));
            assertFalse(f.repository.saveTablesConfig());
            assertFalse(Files.exists(f.file));
            Files.writeString(f.file, good);
            assertTrue(f.repository.reload());
        }
    }

    @Test
    void explicitEmptyReplacementIsValidAndCollectionSnapshotsCannotMutateMembership()
            throws Exception {
        try (var f = new Fixture(directory, "")) {
            assertTrue(f.repository.isWritable());
            var table = new LootTable("first");
            assertTrue(f.repository.put(table));
            var snapshot = f.repository.getTables();
            assertThrows(UnsupportedOperationException.class, snapshot::clear);
            Files.writeString(f.file, "Tables: {}\nMetadata: retained\n");
            assertTrue(f.repository.reload());
            assertTrue(f.repository.getTables().isEmpty());
            assertEquals(List.of(table), snapshot);
            assertTrue(f.repository.put(new LootTable("second")));
            assertTrue(Files.readString(f.file).contains("Metadata: retained"));
            assertTrue(f.repository.remove("second"));
            assertTrue(f.repository.reload());
            assertTrue(f.repository.getTables().isEmpty());
        }
    }

    @Test
    void rejectedAtomicReplacementKeepsThePreviousFileAndRepositoryMembership() throws Exception {
        try (var f = new Fixture(directory, null)) {
            var previous = new LootTable("previous");
            assertTrue(f.repository.put(previous));
            String good = Files.readString(f.file);
            try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                files.when(
                                () ->
                                        Files.move(
                                                any(Path.class),
                                                eq(f.file),
                                                eq(StandardCopyOption.ATOMIC_MOVE),
                                                eq(StandardCopyOption.REPLACE_EXISTING)))
                        .thenThrow(new IOException("disk failure"));
                assertFalse(f.repository.put(new LootTable("replacement")));
                assertFalse(f.repository.remove("previous"));
                assertFalse(f.repository.saveTablesConfig());
                assertEquals(good, Files.readString(f.file));
                assertSame(previous, f.repository.get("previous"));
                assertFalse(f.repository.contains("replacement"));
            }
            assertTrue(f.repository.put(new LootTable("retry")));
        }
    }

    @Test
    void serializationHappensOnceBeforePublicationAndFailureCannotPublishANewTable()
            throws Exception {
        try (var f = new Fixture(directory, null)) {
            var table = new CountingTable("once", false);
            assertTrue(f.repository.put(table));
            assertEquals(1, table.count.get());
            String good = Files.readString(f.file);
            assertTrue(f.repository.reload());
            var working = f.repository.get("once");
            var broken = new CountingTable("broken", true);
            assertFalse(f.repository.put(broken));
            assertEquals(1, broken.count.get());
            assertFalse(f.repository.contains("broken"));
            assertSame(working, f.repository.get("once"));
            assertEquals(good, Files.readString(f.file));
        }
    }

    @Test
    void replacementNotificationsFollowCommitAndObserverFailureCannotFalsifySuccess()
            throws Exception {
        try (var f = new Fixture(directory, null)) {
            var accepted = new java.util.concurrent.atomic.AtomicInteger();
            Runnable unsubscribe =
                    f.repository.onTableReplacement(
                            () -> {
                                assertNotNull(f.repository.get("first"));
                                assertDoesNotThrow(
                                        () ->
                                                assertTrue(
                                                        Files.readString(f.file)
                                                                .contains("first")));
                                throw new IllegalStateException("broken observer");
                            });
            f.repository.onTableReplacement(accepted::incrementAndGet);
            assertTrue(f.repository.put(new LootTable("first")));
            assertEquals(1, accepted.get());
            assertTrue(f.repository.saveTablesConfig());
            assertEquals(1, accepted.get(), "Saving unchanged models does not invalidate menus");
            Files.writeString(f.file, "Tables: [broken");
            assertFalse(f.repository.reload());
            assertEquals(1, accepted.get(), "Failed reloads retain the current editor generation");
            unsubscribe.run();
            Files.writeString(f.file, "Tables: {}\n");
            assertTrue(f.repository.reload());
            assertEquals(2, accepted.get());
        }
    }

    @org.bukkit.configuration.serialization.SerializableAs("dungeons.reward.loot_table")
    static final class CountingTable extends LootTable {
        final AtomicInteger count = new AtomicInteger();
        final boolean fail;

        CountingTable(String name, boolean fail) {
            super(name);
            this.fail = fail;
        }

        @Override
        public Map<String, Object> serialize() {
            count.incrementAndGet();
            if (fail) throw new IllegalStateException("broken serializer");
            return super.serialize();
        }
    }

    @Test
    void dottedNamespacesRoundTripLiterallyAndStaleMenusCannotSaveAnotherGeneration()
            throws Exception {
        try (var f = new Fixture(directory, null)) {
            var old = new LootTable("boss.cache");
            assertTrue(f.repository.put(old));
            assertEquals(0, f.menus.windows().size());
            var editor =
                    new nl.hauntedmc.dungeons.gui.loot.LootTableEditor(
                            f.repository,
                            org.mockito.Mockito.mock(
                                    nl.hauntedmc.dungeons.gui.framework.GuiService.class));
            editor.initialize(old);
            assertEquals(1, f.menus.windows().size());
            assertTrue(f.repository.reload());
            var current = f.repository.get("boss.cache");
            assertNotNull(current);
            assertNotSame(old, current);
            assertEquals(
                    1, f.menus.windows().size(), "Reloading must not construct new GUI windows");
            org.mockito.Mockito.verify(f.menus.windows().getFirst()).unregister();
            String good = Files.readString(f.file);
            assertFalse(f.repository.saveTablesConfig(old));
            assertEquals(good, Files.readString(f.file));
            assertTrue(f.repository.saveTablesConfig(current));
        }
    }

    @Test
    void loadingAndMutationRequireTheOwnerThreadWithoutChangingStateOnRejection() throws Exception {
        try (var f = new Fixture(directory, null)) {
            var old = new LootTable("keep");
            assertTrue(f.repository.put(old));
            String good = Files.readString(f.file);
            f.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, f.repository::reload);
            assertThrows(IllegalStateException.class, () -> f.repository.remove("keep"));
            assertSame(old, f.repository.get("keep"));
            assertEquals(good, Files.readString(f.file));
            assertTrue(f.repository.isWritable());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final PaperBoundary paper = new PaperBoundary();
        final HeadlessInventoryMenus menus = new HeadlessInventoryMenus();
        final Path file;
        final LootTableRepository repository;
        final Class<?> previousTable =
                ConfigurationSerialization.getClassByAlias("dungeons.reward.loot_table");
        final Class<?> previousItem =
                ConfigurationSerialization.getClassByAlias("dungeons.reward.loot_table_item");

        Fixture(Path directory, String initial) throws Exception {
            Files.createDirectories(directory);
            file = directory.resolve("loottables.yml");
            if (initial != null) Files.writeString(file, initial);
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            ConfigurationSerialization.registerClass(LootTable.class, "dungeons.reward.loot_table");
            ConfigurationSerialization.registerClass(
                    LootTableItem.class, "dungeons.reward.loot_table_item");
            repository = new LootTableRepository(paper.plugin);
        }

        @Override
        public void close() {
            ConfigurationSerialization.unregisterClass("dungeons.reward.loot_table");
            ConfigurationSerialization.unregisterClass("dungeons.reward.loot_table_item");
            if (previousTable != null)
                ConfigurationSerialization.registerClass(
                        previousTable.asSubclass(
                                org.bukkit.configuration.serialization.ConfigurationSerializable
                                        .class),
                        "dungeons.reward.loot_table");
            if (previousItem != null)
                ConfigurationSerialization.registerClass(
                        previousItem.asSubclass(
                                org.bukkit.configuration.serialization.ConfigurationSerializable
                                        .class),
                        "dungeons.reward.loot_table_item");
            menus.close();
            paper.close();
        }
    }
}
