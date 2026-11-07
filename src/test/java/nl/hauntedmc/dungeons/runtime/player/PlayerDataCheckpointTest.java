package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;

import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.*;

class PlayerDataCheckpointTest {
    @TempDir Path directory;

    @Test
    void absentOrStaleNativePlayerSaveCannotBeAcknowledged() throws Exception {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var server = Bukkit.getServer();
            doReturn(directory).when(server).getLevelDirectory();
            paper.checkpoints
                    .when(() -> PlayerDataCheckpoint.verify(player.player, "transaction"))
                    .thenCallRealMethod();
            assertThrows(
                    UncheckedIOException.class,
                    () -> PlayerDataCheckpoint.verify(player.player, "transaction"));
            Path file = directory.resolve("playerdata/" + player.player.getUniqueId() + ".dat");
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
            var journal = new PlayerRecoveryJournal(path);
            journal.addRewards(PlayerState.stack(5));
            paper.checkpoints
                    .when(() -> PlayerDataCheckpoint.verify(eq(player.player), anyString()))
                    .thenThrow(
                            new UncheckedIOException(
                                    new java.io.IOException("native save missing")));
            assertThrows(
                    UncheckedIOException.class,
                    () -> journal.claim(player.player, journal.rewards().getFirst()));
            assertTrue(new PlayerRecoveryJournal(path).hasPendingTransaction());
            assertNull(
                    player.player
                            .getPersistentDataContainer()
                            .get(
                                    new org.bukkit.NamespacedKey("dungeons", "player_transaction"),
                                    org.bukkit.persistence.PersistentDataType.STRING));
            var reconnected = new PlayerState();
            new PlayerRecoveryJournal(path).recover(reconnected.player);
            assertEquals(5, reconnected.contents[0].getAmount());
        }
    }
}
