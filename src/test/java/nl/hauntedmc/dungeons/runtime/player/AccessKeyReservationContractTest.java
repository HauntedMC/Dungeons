package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class AccessKeyReservationContractTest {
    @TempDir Path directory;

    @Test
    void aReservationHasOneOwnerAndOfflineRefundIsDeliveredExactlyOnceOnReconnect() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var state = new nl.hauntedmc.dungeons.support.PlayerState();
            var key = nl.hauntedmc.dungeons.support.PlayerState.stack(1);
            var session = new DungeonPlayerSession(paper.plugin, state.player);
            assertTrue(session.reserveAccessKey("crypt", key));
            state.online = false;
            assertFalse(session.reserveAccessKey("other", key));
            assertFalse(session.refundReservedAccessKey("other"));
            assertTrue(session.hasReservedAccessKey("crypt"));
            assertTrue(session.refundReservedAccessKey("crypt"));
            assertFalse(session.refundReservedAccessKey("crypt"));
            session.flushPendingAccessKeyRefund();
            org.junit.jupiter.api.Assertions.assertNull(state.contents[0]);
            // The old session is deliberately abandoned: offline refunds must survive recreation.
            var reloaded = new DungeonPlayerSession(paper.plugin, state.player);
            state.online = true;
            reloaded.flushPendingAccessKeyRefund();
            reloaded.flushPendingAccessKeyRefund();
            org.junit.jupiter.api.Assertions.assertEquals(1, state.contents[0].getAmount());
            assertTrue(reloaded.getRewardItems().isEmpty());
            assertFalse(reloaded.hasReservedAccessKey());
            verify(state.player, times(2)).saveData();
        }
    }

    @Test
    void successfulConsumptionCannotLaterRefundTheKeyAndWrongDungeonCannotConsumeIt() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var state = new nl.hauntedmc.dungeons.support.PlayerState();
            var player = state.player;
            var inventory = state.inventory;
            var key = nl.hauntedmc.dungeons.support.PlayerState.stack(1);
            var session = new DungeonPlayerSession(paper.plugin, player);
            assertTrue(session.reserveAccessKey("crypt", key));
            assertFalse(session.completeReservedAccessKey("other", null));
            assertTrue(session.hasReservedAccessKey("crypt"));
            assertTrue(session.completeReservedAccessKey("crypt", null));
            assertFalse(session.completeReservedAccessKey("crypt", null));
            assertFalse(session.refundReservedAccessKey());
            when(player.isOnline()).thenReturn(true);
            session.flushPendingAccessKeyRefund();
            org.junit.jupiter.api.Assertions.assertNull(inventory.getContents()[0]);
        }
    }

}
