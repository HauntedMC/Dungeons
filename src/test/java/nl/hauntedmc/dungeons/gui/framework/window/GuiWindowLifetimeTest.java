package nl.hauntedmc.dungeons.gui.framework.window;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.StaticMapSnapshot;
import nl.hauntedmc.dungeons.support.TestEvents;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class GuiWindowLifetimeTest {
    @Test
    void revokedAccessBlocksOpenButtonsEditableSlotsShiftTransfersDragsAndCloseMutations()
            throws Exception {
        try (var windows = new StaticMapSnapshot(GuiWindowRegistry.class, "windows");
                var paper = new PaperBoundary()) {
            var player = new PlayerState().player;
            var inventory = mock(Inventory.class);
            paper.bukkit
                    .when(() -> Bukkit.createInventory(eq(player), eq(9), any(Component.class)))
                    .thenReturn(inventory);
            var window = new GuiWindow("guarded", 9, "Guarded");
            window.setCancelClick(false);
            var allowed = new AtomicBoolean(false);
            window.setAccessCheck(p -> allowed.get());
            window.open(player);
            assertNull(window.getInventoryFor(player));
            allowed.set(true);
            var button = mock(Button.class);
            when(button.clone()).thenReturn(button);
            window.addButton(0, button);
            window.open(player);
            var opened = TestEvents.cancellable(InventoryOpenEvent.class);
            when(opened.getPlayer()).thenReturn(player);
            when(opened.getInventory()).thenReturn(inventory);
            var openActions = new AtomicInteger();
            var closeActions = new AtomicInteger();
            window.addOpenAction("open", event -> openActions.incrementAndGet());
            window.addCloseAction("save", event -> closeActions.incrementAndGet());
            window.onOpen(opened);
            assertEquals(1, openActions.get());
            var editable = TestEvents.cancellable(InventoryClickEvent.class);
            when(editable.getWhoClicked()).thenReturn(player);
            when(editable.getInventory()).thenReturn(inventory);
            when(editable.getClickedInventory()).thenReturn(inventory);
            when(editable.getRawSlot()).thenReturn(4);
            window.onClick(editable);
            assertFalse(editable.isCancelled());
            allowed.set(false);
            window.onOpen(opened);
            assertTrue(opened.isCancelled());
            assertEquals(1, openActions.get());
            window.onButtonClick(editable);
            assertTrue(editable.isCancelled());
            var shift = TestEvents.cancellable(InventoryClickEvent.class);
            when(shift.getWhoClicked()).thenReturn(player);
            when(shift.getInventory()).thenReturn(inventory);
            var playerInventory = player.getInventory();
            when(shift.getClickedInventory()).thenReturn(playerInventory);
            when(shift.getRawSlot()).thenReturn(12);
            when(shift.getClick()).thenReturn(ClickType.SHIFT_LEFT);
            window.onClick(shift);
            assertTrue(shift.isCancelled());
            var click = TestEvents.cancellable(InventoryClickEvent.class);
            when(click.getWhoClicked()).thenReturn(player);
            when(click.getInventory()).thenReturn(inventory);
            when(click.getClickedInventory()).thenReturn(inventory);
            when(click.getRawSlot()).thenReturn(0);
            window.onButtonClick(click);
            assertTrue(click.isCancelled());
            verify(button, never()).click(any());
            var drag = TestEvents.cancellable(InventoryDragEvent.class);
            when(drag.getWhoClicked()).thenReturn(player);
            when(drag.getInventory()).thenReturn(inventory);
            when(drag.getRawSlots()).thenReturn(Set.of(4, 5));
            window.onDrag(drag);
            assertTrue(drag.isCancelled());
            var closed = mock(InventoryCloseEvent.class);
            when(closed.getPlayer()).thenReturn(player);
            when(closed.getInventory()).thenReturn(inventory);
            window.onClose(closed);
            assertEquals(0, closeActions.get());
            assertNull(window.getInventoryFor(player));
        }
    }

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
