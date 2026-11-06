package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

class AccessKeyReservationContractTest {
    @TempDir Path directory;

    @Test
    void aReservationHasOneOwnerAndOfflineRefundIsDeliveredExactlyOnceOnReconnect() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            var key = item();
            var reserved = item();
            var refund = item();
            when(key.clone()).thenReturn(reserved);
            when(reserved.clone()).thenReturn(refund);
            var session = new DungeonPlayerSession(paper.plugin, player);
            assertTrue(session.reserveAccessKey("crypt", key));
            assertFalse(session.reserveAccessKey("other", key));
            assertFalse(session.refundReservedAccessKey("other"));
            assertTrue(session.hasReservedAccessKey("crypt"));
            assertTrue(session.refundReservedAccessKey("crypt"));
            assertFalse(session.refundReservedAccessKey("crypt"));
            session.flushPendingAccessKeyRefund();
            verifyNoInteractions(inventory);
            when(player.isOnline()).thenReturn(true);
            session.flushPendingAccessKeyRefund();
            session.flushPendingAccessKeyRefund();
            verify(inventory, times(1)).addItem(refund);
            assertFalse(session.hasReservedAccessKey());
        }
    }

    @Test
    void successfulConsumptionCannotLaterRefundTheKeyAndWrongDungeonCannotConsumeIt() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            var player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var inventory = mock(PlayerInventory.class);
            when(player.getInventory()).thenReturn(inventory);
            var key = item();
            var reserved = item();
            when(key.clone()).thenReturn(reserved);
            var session = new DungeonPlayerSession(paper.plugin, player);
            assertTrue(session.reserveAccessKey("crypt", key));
            assertFalse(session.completeReservedAccessKey("other", null));
            assertTrue(session.hasReservedAccessKey("crypt"));
            assertTrue(session.completeReservedAccessKey("crypt", null));
            assertFalse(session.completeReservedAccessKey("crypt", null));
            assertFalse(session.refundReservedAccessKey());
            when(player.isOnline()).thenReturn(true);
            session.flushPendingAccessKeyRefund();
            verifyNoInteractions(inventory);
        }
    }

    ItemStack item() {
        var item = mock(ItemStack.class);
        // Material registry behavior belongs to Paper; only its non-air result matters here.
        when(item.getType()).thenReturn(mock(Material.class));
        return item;
    }
}
