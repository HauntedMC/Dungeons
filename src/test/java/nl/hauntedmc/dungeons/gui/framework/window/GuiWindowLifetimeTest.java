package nl.hauntedmc.dungeons.gui.framework.window;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.StaticMapSnapshot;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class GuiWindowLifetimeTest {
    @Test
    void closeCallbacksCanInspectTheViewButFailureStillReleasesThePlayer() throws Exception {
        try (var windows = new StaticMapSnapshot(GuiWindowRegistry.class, "windows");
                var paper = new PaperBoundary()) {
            var player = new PlayerState().player;
            var inventory = mock(Inventory.class);
            paper.bukkit
                    .when(() -> Bukkit.createInventory(eq(player), eq(9), any(Component.class)))
                    .thenReturn(inventory);
            var window = new GuiWindow("expiry", 9, "Expiry");
            window.open(player);
            var close = mock(InventoryCloseEvent.class);
            when(close.getPlayer()).thenReturn(player);
            when(close.getInventory()).thenReturn(inventory);
            window.addCloseAction(
                    "failure",
                    event -> {
                        assertSame(inventory, window.getInventoryFor(player).inventory());
                        throw new IllegalStateException("save failed");
                    });
            assertThrows(IllegalStateException.class, () -> window.onClose(close));
            assertNull(window.getInventoryFor(player));
        }
    }

    @Test
    void staleCloseCannotRemoveAReentrantlyReopenedWindowAndQuitReleasesItsView() throws Exception {
        try (var windows = new StaticMapSnapshot(GuiWindowRegistry.class, "windows");
                var paper = new PaperBoundary()) {
            var player = new PlayerState().player;
            var first = mock(Inventory.class);
            var second = mock(Inventory.class);
            paper.bukkit
                    .when(() -> Bukkit.createInventory(eq(player), eq(9), any(Component.class)))
                    .thenReturn(first, second);
            var window = new GuiWindow("reentrant", 9, "Reentrant");
            window.open(player);
            var calls = new AtomicInteger();
            window.addCloseAction(
                    "reopen",
                    event -> {
                        calls.incrementAndGet();
                        window.open(player);
                    });
            var close = mock(InventoryCloseEvent.class);
            when(close.getPlayer()).thenReturn(player);
            when(close.getInventory()).thenReturn(first);
            window.onClose(close);
            assertSame(second, window.getInventoryFor(player).inventory());
            window.onClose(close);
            assertEquals(1, calls.get());
            var quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(player);
            window.onPlayerQuit(quit);
            assertNull(window.getInventoryFor(player));
            window.open(player);
            window.unregister();
            assertNull(window.getInventoryFor(player));
            assertNull(GuiWindowRegistry.getWindow("reentrant"));
        }
    }

    @Test
    void unregisteringAnOldWindowCannotRemoveItsReplacement() throws Exception {
        try (var windows = new StaticMapSnapshot(GuiWindowRegistry.class, "windows");
                var paper = new PaperBoundary()) {
            var old = new GuiWindow("same", 9, "Old");
            var replacement = new GuiWindow("same", 9, "Replacement");
            old.unregister();
            assertSame(replacement, GuiWindowRegistry.getWindow("same"));
            replacement.unregister();
            assertNull(GuiWindowRegistry.getWindow("same"));
        }
    }
}
