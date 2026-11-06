package nl.hauntedmc.dungeons.runtime.rewards;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class LootRepositoryContractTest {
    @TempDir Path directory;

    @Test
    void realLootRepositorySaveAndReloadKeepValidTablesWhenANeighborHasTheWrongType()
            throws Exception {
        String alias = "dungeons.reward.loot_table";
        var previous = ConfigurationSerialization.getClassByAlias(alias);
        ConfigurationSerialization.registerClass(LootTable.class, alias);
        try (var paper = new PaperBoundary();
                var menus = new HeadlessInventoryMenus()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var repository = new LootTableRepository(paper.plugin);
            repository.put(new LootTable("valid"));
            Path file = directory.resolve("loottables.yml");
            Files.writeString(file, Files.readString(file) + "  broken: not-a-loot-table\n");
            assertDoesNotThrow(repository::reload);
            assertNotNull(repository.get("valid"));
            assertEquals("valid", repository.get("valid").getNamespace());
            assertFalse(repository.contains("broken"));
            repository.remove("valid");
            repository.reload();
            assertTrue(repository.getTables().isEmpty());
        } finally {
            ConfigurationSerialization.unregisterClass(alias);
            if (previous != null) ConfigurationSerialization.registerClass(previous, alias);
        }
    }
}
