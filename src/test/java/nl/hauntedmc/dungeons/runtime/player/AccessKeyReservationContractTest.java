package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.UUID;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.RunFixture;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccessKeyReservationContractTest {
    @TempDir Path directory;

    @Test
    void aReservationHasOneOwnerAndOfflineRefundIsDeliveredExactlyOnceOnReconnect() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var state = new PlayerState();
            var key = PlayerState.stack(1);
            var session = new DungeonPlayerSession(paper.runtime, state.player);
            assertTrue(session.reserveAccessKey("crypt", key));
            state.online = false;
            assertFalse(session.reserveAccessKey("other", key));
            assertFalse(session.refundReservedAccessKey("other"));
            assertTrue(session.hasReservedAccessKey("crypt"));
            assertTrue(session.refundReservedAccessKey("crypt"));
            assertFalse(session.refundReservedAccessKey("crypt"));
            session.flushPendingAccessKeyRefund();
            Assertions.assertNull(state.contents[0]);
            // The old session is deliberately abandoned: offline refunds must survive recreation.
            var reloaded = new DungeonPlayerSession(paper.runtime, state.player);
            state.online = true;
            reloaded.flushPendingAccessKeyRefund();
            reloaded.flushPendingAccessKeyRefund();
            Assertions.assertEquals(1, state.contents[0].getAmount());
            assertFalse(reloaded.hasReservedAccessKey());
            verify(state.player, times(2)).saveData();
        }
    }

    @Test
    void successfulConsumptionCannotLaterRefundTheKeyAndWrongDungeonCannotConsumeIt() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var state = new PlayerState();
            var player = state.player;
            var inventory = state.inventory;
            var key = PlayerState.stack(1);
            var session = new DungeonPlayerSession(paper.runtime, player);
            assertTrue(session.reserveAccessKey("crypt", key));
            assertFalse(session.completeReservedAccessKey("other", null));
            assertTrue(session.hasReservedAccessKey("crypt"));
            assertTrue(session.completeReservedAccessKey("crypt", null));
            assertFalse(session.completeReservedAccessKey("crypt", null));
            assertFalse(session.refundReservedAccessKey());
            when(player.isOnline()).thenReturn(true);
            session.flushPendingAccessKeyRefund();
            Assertions.assertNull(inventory.getContents()[0]);
        }
    }

    @Test
    void staffIssuedKeysDeliverImmediatelyAndReplayedOperationsDoNotDuplicateThem()
            throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            fixture.session(state);
            UUID operation = UUID.randomUUID();
            assertTrue(
                    StaffPlayerRecords.deliverKey(
                            fixture.paper.runtime,
                            state.player.getUniqueId(),
                            operation,
                            PlayerState.stack(2)));
            Assertions.assertEquals(2, state.contents[0].getAmount());
            assertFalse(
                    StaffPlayerRecords.deliverKey(
                            fixture.paper.runtime,
                            state.player.getUniqueId(),
                            operation,
                            PlayerState.stack(2)));
            Assertions.assertEquals(2, state.contents[0].getAmount());
        }
    }
}
