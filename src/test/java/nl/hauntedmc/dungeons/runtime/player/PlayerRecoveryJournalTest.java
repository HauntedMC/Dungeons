package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;

import org.bukkit.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class PlayerRecoveryJournalTest {
    @TempDir Path directory;

    @Test
    void completeSnapshotSurvivesNewSessionAndRestoresOnce() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            paper.bukkit
                    .when(() -> Bukkit.getWorld(player.originWorld.getUID()))
                    .thenReturn(player.originWorld);
            paper.entities.when(() -> EntityUtils.getMaxHealth(player.player)).thenReturn(20.0);
            player.contents[0] = PlayerState.stack(7);
            player.contents[40] = PlayerState.stack(3);
            player.armor[0] = PlayerState.stack(2);
            var path = directory.resolve("recovery.yml");
            new PlayerRecoveryJournal(path).begin(player.player);
            player.inventory.clear();
            player.gameMode = GameMode.CREATIVE;
            player.exp = 0;
            player.level = 0;
            player.health = 1;
            player.food = 0;
            var restarted = new PlayerRecoveryJournal(path);
            assertTrue(restarted.recover(player.player));
            assertEquals(7, player.contents[0].getAmount());
            assertEquals(3, player.contents[40].getAmount());
            assertEquals(2, player.armor[0].getAmount());
            assertEquals(GameMode.SURVIVAL, player.gameMode);
            assertEquals(.4f, player.exp);
            assertEquals(17, player.level);
            assertEquals(13, player.health);
            assertEquals(9, player.food);
            verify(player.player).saveData();
            player.contents[0].setAmount(1);
            new PlayerRecoveryJournal(path).recover(player.player);
            assertEquals(1, player.contents[0].getAmount());
        }
    }

    @Test
    void deniedWritesRejectEntryWithoutChangingMembershipOrInventory() throws Exception {
        try (var f =
                new RunFixture(directory, new org.bukkit.configuration.file.YamlConfiguration())) {
            var player = new PlayerState();
            var session = f.session(player);
            player.contents[0] = PlayerState.stack(8);
            Path journal =
                    directory.resolve("players/" + player.player.getUniqueId() + ".recovery.yml");
            Files.createDirectory(journal);
            Files.writeString(journal.resolve("keep"), "keep");
            f.run.addPlayer(session);
            assertNull(session.getInstance());
            assertFalse(f.run.getPlayers().contains(session));
            assertEquals(8, player.contents[0].getAmount());
            verify(player.player, never()).setGameMode(any());
            verify(player.inventory, never()).clear();
        }
    }

    @Test
    void teleportFailureRetainsRecordAndCanBeRetried() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("recovery.yml");
            paper.bukkit
                    .when(() -> Bukkit.getWorld(player.originWorld.getUID()))
                    .thenReturn(player.originWorld);
            var journal = new PlayerRecoveryJournal(path);
            journal.begin(player.player);
            when(player.player.teleport(
                            any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
                    .thenReturn(false);
            assertThrows(IllegalStateException.class, () -> journal.recover(player.player));
            assertTrue(new PlayerRecoveryJournal(path).hasRecovery());
            verify(player.player, never()).saveData();
        }
    }

    @Test
    void missingWorldAndCorruptRecordsFailClosed() throws Exception {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("recovery.yml");
            var journal = new PlayerRecoveryJournal(path);
            player.vanillaDead = true;
            assertThrows(IllegalStateException.class, () -> journal.begin(player.player));
            assertFalse(Files.exists(path));
            player.vanillaDead = false;
            journal.begin(player.player);
            assertThrows(NullPointerException.class, () -> journal.recover(player.player));
            assertTrue(new PlayerRecoveryJournal(path).hasRecovery());
            Files.writeString(path, "version: 1\nrecovery: broken\n");
            assertThrows(IllegalStateException.class, () -> new PlayerRecoveryJournal(path));
            assertEquals("version: 1\nrecovery: broken\n", Files.readString(path));
        }
    }

    @Test
    void unclaimedRewardsAndPartialDeliverySurviveRestartAndStaleClicks() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("recovery.yml");
            var journal = new PlayerRecoveryJournal(path);
            journal.addRewards(PlayerState.stack(20));
            for (int slot = 0; slot < 36; slot++) player.contents[slot] = PlayerState.stack(64);
            player.contents[0].setAmount(60);
            var restarted = new PlayerRecoveryJournal(path);
            assertTrue(restarted.claim(player.player, restarted.rewards().getFirst()));
            assertEquals(64, player.contents[0].getAmount());
            assertEquals(16, new PlayerRecoveryJournal(path).rewards().getFirst().getAmount());
            assertFalse(restarted.claim(player.player, PlayerState.stack(20)));
            assertFalse(restarted.claim(player.player, restarted.rewards().getFirst()));
            player.contents[1] = null;
            assertTrue(restarted.claim(player.player, restarted.rewards().getFirst()));
            assertTrue(new PlayerRecoveryJournal(path).rewards().isEmpty());
            assertEquals(16, player.contents[1].getAmount());
        }
    }

    @Test
    void crashBeforePlayerSaveReplaysDeliveryInsteadOfLosingReward() {
        try (var paper = new PaperBoundary()) {
            var path = directory.resolve("recovery.yml");
            var player = new PlayerState();
            var journal = new PlayerRecoveryJournal(path);
            journal.addRewards(PlayerState.stack(5));
            doThrow(new IllegalStateException("save failed")).when(player.player).saveData();
            assertThrows(
                    IllegalStateException.class,
                    () -> journal.claim(player.player, journal.rewards().getFirst()));
            var reconnect = new PlayerState();
            new PlayerRecoveryJournal(path).recover(reconnect.player);
            assertEquals(5, reconnect.contents[0].getAmount());
            assertTrue(new PlayerRecoveryJournal(path).rewards().isEmpty());
            new PlayerRecoveryJournal(path).recover(reconnect.player);
            assertEquals(5, reconnect.contents[0].getAmount());
        }
    }

    @Test
    void crashAfterPlayerSaveBeforeJournalAcknowledgementDoesNotReplayDeliveredItems()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var path = directory.resolve("recovery.yml");
            var player = new PlayerState();
            var journal = new PlayerRecoveryJournal(path);
            journal.addRewards(PlayerState.stack(5));
            byte[][] durableJournal = {null};
            doAnswer(
                            call -> {
                                durableJournal[0] = Files.readAllBytes(path);
                                Files.delete(path);
                                Files.createDirectory(path);
                                Files.writeString(path.resolve("obstacle"), "keep");
                                return null;
                            })
                    .when(player.player)
                    .saveData();
            assertThrows(
                    java.io.UncheckedIOException.class,
                    () -> journal.claim(player.player, journal.rewards().getFirst()));
            Files.delete(path.resolve("obstacle"));
            Files.delete(path);
            Files.write(path, durableJournal[0]);
            // Simulate saved marker loaded on reconnect. Subsequent gameplay must not be
            // overwritten.
            player.contents[0].setAmount(2);
            new PlayerRecoveryJournal(path).recover(player.player);
            assertEquals(2, player.contents[0].getAmount());
            assertTrue(new PlayerRecoveryJournal(path).rewards().isEmpty());
            verify(player.player, times(1)).saveData();
        }
    }

    @Test
    void normalExitAcknowledgesCurrentConfiguredStateRatherThanReplayingPreEntryInventory() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("recovery.yml");
            var journal = new PlayerRecoveryJournal(path);
            journal.begin(player.player);
            player.contents[0] = PlayerState.stack(9);
            journal.acknowledgeDeparture(player.player);
            new PlayerRecoveryJournal(path).recover(player.player);
            assertEquals(9, player.contents[0].getAmount());
            assertFalse(journal.hasRecovery());
        }
    }
}
