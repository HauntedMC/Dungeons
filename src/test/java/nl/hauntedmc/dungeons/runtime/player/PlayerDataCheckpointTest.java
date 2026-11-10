package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.TestStateStore;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;

class PlayerDataCheckpointTest {
    @TempDir Path directory;

    @Test
    void unsupportedStorageRejectsEntryBeforeWritingRecoveryOrReplacingInventory() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            player.contents[0] = PlayerState.stack(4);
            var file = directory.resolve("recovery.yml");
            var journal = new PlayerRecoveryJournal(TestStateStore.player(file));
            paper.checkpoints.when(PlayerDataCheckpoint::requireStorage)
                    .thenThrow(new UncheckedIOException(new IOException("unsupported storage adapter")));
            assertThrows(UncheckedIOException.class, () -> journal.begin(player.player));
            assertFalse(Files.exists(file));
            assertFalse(journal.hasRecovery());
            assertEquals(4, player.contents[0].getAmount());
        }
    }

    @Test
    void absentOrStaleNativePlayerSaveCannotBeAcknowledged() throws Exception {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            Path nativeDirectory = directory.resolve("players/data");
            paper.checkpoints.when(PlayerDataCheckpoint::playerDataDirectory).thenReturn(nativeDirectory);
            paper.checkpoints
                    .when(() -> PlayerDataCheckpoint.verify(player.player, "transaction"))
                    .thenCallRealMethod();
            Path legacy = directory.resolve("playerdata/" + player.player.getUniqueId() + ".dat");
            Files.createDirectories(legacy.getParent());
            Files.writeString(legacy, "stale legacy player data");
            paper.checkpoints.when(() -> PlayerDataCheckpoint.readMarker(legacy)).thenReturn("transaction");
            assertThrows(
                    UncheckedIOException.class,
                    () -> PlayerDataCheckpoint.verify(player.player, "transaction"));
            Path file = nativeDirectory.resolve(player.player.getUniqueId() + ".dat");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "native file boundary");
            paper.checkpoints
                    .when(() -> PlayerDataCheckpoint.readMarker(file))
                    .thenReturn("older transaction");
            assertThrows(
                    UncheckedIOException.class,
                    () -> PlayerDataCheckpoint.verify(player.player, "transaction"));
            paper.checkpoints
                    .when(() -> PlayerDataCheckpoint.readMarker(file))
                    .thenReturn("transaction");
            assertDoesNotThrow(() -> PlayerDataCheckpoint.verify(player.player, "transaction"));
            assertEquals("native file boundary", Files.readString(file));
        }
    }

    @Test
    void silentlyFailedNativeSaveRetainsDeliveryAndRollsBackInMemoryMarker() {
        try (var paper = new PaperBoundary()) {
            var path = directory.resolve("recovery.yml");
            var player = new PlayerState();
            var journal = new PlayerRecoveryJournal(TestStateStore.player(path));
            journal.addRefunds(PlayerState.stack(5));
            paper.checkpoints
                    .when(() -> PlayerDataCheckpoint.verify(eq(player.player), anyString()))
                    .thenThrow(
                            new UncheckedIOException(
                                    new IOException("native save missing")));
            assertThrows(
                    UncheckedIOException.class,
                    () -> journal.deliverRefund(player.player, journal.refunds().getFirst()));
            assertTrue(new PlayerRecoveryJournal(TestStateStore.player(path)).hasPendingTransaction());
            assertNull(
                    player.player
                            .getPersistentDataContainer()
                            .get(
                                    new NamespacedKey("dungeons", "player_transaction"),
                                    PersistentDataType.STRING));
            var reconnected = new PlayerState();
            new PlayerRecoveryJournal(TestStateStore.player(path)).recover(reconnected.player);
            assertEquals(5, reconnected.contents[0].getAmount());
        }
    }
}
