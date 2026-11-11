package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.support.TestStateStore;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerRecoveryJournalTest {
    @TempDir Path directory;

    @Test
    void reservationCrashBeforeNativeSaveRestoresExactlyOneKeyFromEscrow() {
        try (var paper = new PaperBoundary()) {
            var before = new PlayerState();
            var path = directory.resolve("escrow.yml");
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            doThrow(new IllegalStateException("native save failed")).when(before.player).saveData();
            assertThrows(
                    IllegalStateException.class,
                    () -> journal.reserveKey(before.player, "crypt", PlayerState.stack(1)));
            var reconnected = new PlayerState();
            reconnected.contents[0] = PlayerState.stack(1); // native data predates key removal
            var restarted = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            assertTrue(restarted.recover(reconnected.player));
            restarted.deliverRefunds(reconnected.player);
            assertEquals(1, reconnected.contents[0].getAmount());
            assertFalse(restarted.hasKeyReservation());
            assertTrue(restarted.refunds().isEmpty());
            new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                    .recover(reconnected.player);
            assertEquals(1, reconnected.contents[0].getAmount());
        }
    }

    @Test
    void reservedKeySurvivesSessionLossButSuccessfulConsumptionDoesNotRefundIt() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("escrow.yml");
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            journal.reserveKey(player.player, "crypt", PlayerState.stack(1));
            var restarted = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            assertTrue(restarted.hasKeyReservation());
            restarted.recover(player.player);
            restarted.deliverRefunds(player.player);
            assertEquals(1, player.contents[0].getAmount());
            player.contents[0] = null; // the next reservation removes the key again
            restarted.reserveKey(player.player, "crypt", PlayerState.stack(1));
            assertTrue(restarted.settleKey(false, null));
            var consumed = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            consumed.recover(player.player);
            consumed.deliverRefunds(player.player);
            assertNull(player.contents[0]);
            assertTrue(consumed.refunds().isEmpty());
        }
    }

    @Test
    void invalidCapturesDoNotLeaveARecoveryRecordThatBlocksTheNextAttempt() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("invalid.yml");
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            player.contents = new ItemStack[36];
            assertThrows(IllegalArgumentException.class, () -> journal.begin(player.player));
            assertFalse(journal.hasRecovery());
            assertFalse(Files.exists(path));
            player.contents = new ItemStack[43];
            journal.begin(player.player);
            assertTrue(
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .hasRecovery());
        }
    }

    @Test
    void unsupportedJournalAndInventoryFormatsAreRejected() throws Exception {
        try (var paper = new PaperBoundary()) {
            var file = directory.resolve("unsupported.yml");
            paper.state.journal(file, "version: 1\n");
            assertThrows(
                    IllegalStateException.class,
                    () -> new PlayerRecoveryJournal(paper.state, TestStateStore.player(file)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PlayerRecoverySnapshot.validateInventoryContents(new ItemStack[41]));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PlayerRecoverySnapshot.validateInventoryContents(new ItemStack[42]));
        }
    }

    @Test
    void modernPlayerInventoryCanEnterRecoverAndDeliverRefundsWithoutLosingExtraEquipment() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            player.contents = Arrays.copyOf(player.contents, 43);
            player.contents[41] = PlayerState.stack(2);
            player.contents[42] = PlayerState.stack(3);
            paper.bukkit
                    .when(() -> Bukkit.getWorld(player.originWorld.getUID()))
                    .thenReturn(player.originWorld);
            var path = directory.resolve("modern.yml");
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            journal.begin(player.player);
            assertEquals(43, journal.originalSnapshot(player.player).contents().length);
            player.inventory.clear();
            var restarted = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            assertTrue(restarted.recover(player.player));
            assertEquals(2, player.contents[41].getAmount());
            assertEquals(3, player.contents[42].getAmount());
            var refund = PlayerState.stack(7);
            restarted.addRefunds(refund);
            assertTrue(restarted.deliverRefund(player.player, refund));
            assertEquals(7, player.contents[0].getAmount());
            assertEquals(3, player.contents[42].getAmount());
            assertTrue(
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .refunds()
                            .isEmpty());
        }
    }

    @Test
    void deadOrOfflineClaimsLeaveRefundsDurableUntilThePlayerCanReceiveThem() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("recovery.yml");
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            var refund = PlayerState.stack(7);
            journal.addRefunds(refund);
            when(player.player.isOnline()).thenReturn(false);
            assertFalse(journal.deliverRefund(player.player, refund));
            when(player.player.isOnline()).thenReturn(true);
            when(player.player.isDead()).thenReturn(true);
            assertFalse(journal.deliverRefund(player.player, refund));
            verify(player.player, never()).saveData();
            assertNull(player.contents[0]);
            assertEquals(
                    7,
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .refunds()
                            .getFirst()
                            .getAmount());
            when(player.player.isDead()).thenReturn(false);
            assertTrue(journal.deliverRefund(player.player, refund));
            assertTrue(
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .refunds()
                            .isEmpty());
            assertEquals(7, player.contents[0].getAmount());
        }
    }

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
            new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                    .begin(player.player);
            player.inventory.clear();
            player.gameMode = GameMode.CREATIVE;
            player.exp = 0;
            player.level = 0;
            player.health = 1;
            player.food = 0;
            var restarted = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
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
            new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                    .recover(player.player);
            assertEquals(1, player.contents[0].getAmount());
        }
    }

    @Test
    void deniedWritesRejectEntryWithoutChangingMembershipOrInventory() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var player = new PlayerState();
            var session = f.session(player);
            player.contents[0] = PlayerState.stack(8);
            Path journal =
                    directory.resolve("players/" + player.player.getUniqueId() + ".recovery.yml");
            f.paper.state.failWrites = true;
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
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            journal.begin(player.player);
            when(player.player.teleport(
                            any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
                    .thenReturn(false);
            assertThrows(IllegalStateException.class, () -> journal.recover(player.player));
            assertTrue(
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .hasRecovery());
            verify(player.player, never()).saveData();
        }
    }

    @Test
    void missingWorldAndCorruptRecordsFailClosed() throws Exception {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("recovery.yml");
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            player.vanillaDead = true;
            assertThrows(IllegalStateException.class, () -> journal.begin(player.player));
            assertFalse(Files.exists(path));
            player.vanillaDead = false;
            journal.begin(player.player);
            assertThrows(NullPointerException.class, () -> journal.recover(player.player));
            assertTrue(
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .hasRecovery());
            paper.state.journal(path, "version: 1\nrecovery: broken\n");
            assertThrows(
                    IllegalStateException.class,
                    () -> new PlayerRecoveryJournal(paper.state, TestStateStore.player(path)));
            assertEquals(
                    "version: 1\nrecovery: broken\n",
                    paper.state.payload("player-recovery", TestStateStore.player(path).toString()));
        }
    }

    @Test
    void unclaimedRefundsAndPartialDeliverySurviveRestartAndStaleClicks() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("recovery.yml");
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            journal.addRefunds(PlayerState.stack(20));
            for (int slot = 0; slot < 36; slot++) player.contents[slot] = PlayerState.stack(64);
            player.contents[0].setAmount(60);
            var restarted = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            assertTrue(restarted.deliverRefund(player.player, restarted.refunds().getFirst()));
            assertEquals(64, player.contents[0].getAmount());
            assertEquals(
                    16,
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .refunds()
                            .getFirst()
                            .getAmount());
            assertFalse(restarted.deliverRefund(player.player, PlayerState.stack(20)));
            assertFalse(restarted.deliverRefund(player.player, restarted.refunds().getFirst()));
            player.contents[1] = null;
            assertTrue(restarted.deliverRefund(player.player, restarted.refunds().getFirst()));
            assertTrue(
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .refunds()
                            .isEmpty());
            assertEquals(16, player.contents[1].getAmount());
        }
    }

    @Test
    void crashBeforePlayerSaveReplaysDeliveryInsteadOfLosingRefund() {
        try (var paper = new PaperBoundary()) {
            var path = directory.resolve("recovery.yml");
            var player = new PlayerState();
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            journal.addRefunds(PlayerState.stack(5));
            doThrow(new IllegalStateException("save failed")).when(player.player).saveData();
            assertThrows(
                    IllegalStateException.class,
                    () -> journal.deliverRefund(player.player, journal.refunds().getFirst()));
            var reconnect = new PlayerState();
            new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                    .recover(reconnect.player);
            assertEquals(5, reconnect.contents[0].getAmount());
            assertTrue(
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .refunds()
                            .isEmpty());
            new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                    .recover(reconnect.player);
            assertEquals(5, reconnect.contents[0].getAmount());
        }
    }

    @Test
    void crashAfterPlayerSaveBeforeJournalAcknowledgementDoesNotReplayDeliveredItems()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var path = directory.resolve("recovery.yml");
            var player = new PlayerState();
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            journal.addRefunds(PlayerState.stack(5));
            byte[][] durableJournal = {null};
            doAnswer(
                            call -> {
                                paper.state.failWrites = true;
                                return null;
                            })
                    .when(player.player)
                    .saveData();
            assertThrows(
                    RuntimeException.class,
                    () -> journal.deliverRefund(player.player, journal.refunds().getFirst()));
            paper.state.failWrites = false;
            // Simulate saved marker loaded on reconnect. Subsequent gameplay must not be
            // overwritten.
            player.contents[0].setAmount(2);
            new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                    .recover(player.player);
            assertEquals(2, player.contents[0].getAmount());
            assertTrue(
                    new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                            .refunds()
                            .isEmpty());
            verify(player.player, times(1)).saveData();
        }
    }

    @Test
    void normalExitAcknowledgesCurrentConfiguredStateRatherThanReplayingPreEntryInventory() {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState();
            var path = directory.resolve("recovery.yml");
            var journal = new PlayerRecoveryJournal(paper.state, TestStateStore.player(path));
            journal.begin(player.player);
            player.contents[0] = PlayerState.stack(9);
            journal.acknowledgeDeparture(player.player);
            new PlayerRecoveryJournal(paper.state, TestStateStore.player(path))
                    .recover(player.player);
            assertEquals(9, player.contents[0].getAmount());
            assertFalse(journal.hasRecovery());
        }
    }
}
