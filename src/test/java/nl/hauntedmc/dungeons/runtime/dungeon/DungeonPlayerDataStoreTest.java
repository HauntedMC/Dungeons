package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;

class DungeonPlayerDataStoreTest {
    @TempDir Path directory;

    @Test
    void malformedRecordsRemainUnchangedAndCannotBeReplacedByAnEmptySave() throws Exception {
        for (String document :
                List.of(
                        "Finished: [invalid",
                        "Finished: true\nRewardsInfo: broken\n",
                        "AccessCooldown: broken\n",
                        "Finished: broken\n")) {
            try (var paper = new PaperBoundary()) {
                var player = new PlayerState().player;
                Path file =
                        Files.createDirectories(directory.resolve("players"))
                                .resolve(player.getUniqueId() + ".yml");
                Files.writeString(file, document);
                var store = new DungeonPlayerDataStore(paper.plugin, directory.toFile());
                assertThrows(
                        IllegalStateException.class,
                        () -> store.setCompleted(player.getUniqueId(), false));
                assertThrows(IllegalStateException.class, () -> store.save(player));
                assertEquals(document, Files.readString(file));
                if (document.contains("Finished: true"))
                    assertTrue(store.isCompleted(player.getUniqueId()));
                Files.delete(file);
            }
        }
    }

    @Test
    void atomicFailurePreservesPreviousDocumentAndRetryPersistsUnknownFields() throws Exception {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState().player;
            Path file =
                    Files.createDirectories(directory.resolve("players"))
                            .resolve(player.getUniqueId() + ".yml");
            Files.writeString(file, "Finished: false\nExtensionField: retained\n");
            var store = new DungeonPlayerDataStore(paper.plugin, directory.toFile());
            store.setCompleted(player.getUniqueId(), true);
            Path backup = directory.resolve("previous.yml");
            Files.move(file, backup);
            Files.createDirectory(file);
            Files.writeString(file.resolve("blocker"), "block");
            assertThrows(UncheckedIOException.class, () -> store.save(player));
            assertFalse(
                    YamlConfiguration.loadConfiguration(backup.toFile()).getBoolean("Finished"));
            assertTrue(store.isCompleted(player.getUniqueId()));
            Files.delete(file.resolve("blocker"));
            Files.delete(file);
            Files.move(backup, file);
            store.save(player);
            var saved = YamlConfiguration.loadConfiguration(file.toFile());
            assertTrue(saved.getBoolean("Finished"));
            assertEquals("retained", saved.getString("ExtensionField"));
        }
    }

    @Test
    void rebuildingCooldownIndexesIsIdempotentAndMismatchedPlayerIdsAreProtected()
            throws Exception {
        try (var aliases =
                        new nl.hauntedmc.dungeons.support.StaticMapSnapshot(
                                org.bukkit.configuration.serialization.ConfigurationSerialization
                                        .class,
                                "aliases");
                var paper = new PaperBoundary()) {
            org.bukkit.configuration.serialization.ConfigurationSerialization.registerClass(
                    nl.hauntedmc.dungeons.content.reward.PlayerLootData.class);
            var player = new PlayerState().player;
            var data = new YamlConfiguration();
            data.set(
                    "RewardsInfo", new nl.hauntedmc.dungeons.content.reward.PlayerLootData(player));
            Path folder = Files.createDirectories(directory.resolve("players"));
            Path file = folder.resolve(player.getUniqueId() + ".yml");
            Files.writeString(file, data.saveToString());
            var store = new DungeonPlayerDataStore(paper.plugin, directory.toFile());
            store.loadCooldowns(player.getUniqueId());
            store.loadCooldowns(player.getUniqueId());
            assertEquals(1, store.getLootCooldowns().size());
            var another = new PlayerState().player;
            Path mismatched = folder.resolve(another.getUniqueId() + ".yml");
            Files.writeString(mismatched, data.saveToString());
            var reloaded = new DungeonPlayerDataStore(paper.plugin, directory.toFile());
            assertNull(reloaded.getRewards(another.getUniqueId()));
            assertThrows(IllegalStateException.class, () -> reloaded.save(another));
            assertEquals(data.saveToString(), Files.readString(mismatched));
        }
    }

    @Test
    void accessDatesAreDetachedAndWorkerMutationsAreRejected() throws Exception {
        try (var paper = new PaperBoundary()) {
            var id = new PlayerState().player.getUniqueId();
            var store = new DungeonPlayerDataStore(paper.plugin, directory.toFile());
            var date = new Date(System.currentTimeMillis() + 60000);
            long expected = date.getTime();
            store.setAccessCooldown(id, date);
            date.setTime(0);
            store.getAccessCooldown(id).setTime(0);
            assertEquals(expected, store.getAccessCooldown(id).getTime());
            paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> store.setCompleted(id, true));
            assertThrows(IllegalStateException.class, () -> store.setAccessCooldown(id, date));
            paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertFalse(store.isCompleted(id));
            assertEquals(expected, store.getAccessCooldown(id).getTime());
        }
    }
}
