package nl.hauntedmc.dungeons.gui.framework.window;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.TestEvents;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

class GuiWindowLifetimeTest {
    @Test
    void servicesOwnIndependentNamespacesAndCloseOnlyTheirOwnWindows() {
        try (var paper = new PaperBoundary();
                var other = new GuiService(paper.plugin)) {
            var service = paper.runtime.guiService();
            var first = service.createWindow("same", 9, "First lifecycle");
            var second = other.createWindow("same", 9, "Second lifecycle");
            assertSame(first, service.getWindow("same"));
            assertSame(second, other.getWindow("same"));
            service.close();
            service.close();
            assertNull(service.getWindow("same"));
            assertSame(second, other.getWindow("same"));
            assertThrows(
                    IllegalStateException.class, () -> service.createWindow("late", 9, "Closed"));
        }
    }

    @Test
    void revokedAccessBlocksOpenButtonsEditableSlotsShiftTransfersDragsAndCloseMutations()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState().player;
            var inventory = mock(Inventory.class);
            paper.bukkit
                    .when(() -> Bukkit.createInventory(eq(player), eq(9), any(Component.class)))
                    .thenReturn(inventory);
            var window = paper.runtime.guiService().createWindow("guarded", 9, "Guarded");
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
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState().player;
            var inventory = mock(Inventory.class);
            paper.bukkit
                    .when(() -> Bukkit.createInventory(eq(player), eq(9), any(Component.class)))
                    .thenReturn(inventory);
            var window = paper.runtime.guiService().createWindow("expiry", 9, "Expiry");
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
        try (var paper = new PaperBoundary()) {
            var player = new PlayerState().player;
            var first = mock(Inventory.class);
            var second = mock(Inventory.class);
            paper.bukkit
                    .when(() -> Bukkit.createInventory(eq(player), eq(9), any(Component.class)))
                    .thenReturn(first, second);
            var window = paper.runtime.guiService().createWindow("reentrant", 9, "Reentrant");
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
            assertNull(paper.runtime.guiService().getWindow("reentrant"));
        }
    }

    @Test
    void unregisteringAnOldWindowCannotRemoveItsReplacement() throws Exception {
        try (var paper = new PaperBoundary()) {
            var old = paper.runtime.guiService().createWindow("same", 9, "Old");
            var replacement = paper.runtime.guiService().createWindow("same", 9, "Replacement");
            old.unregister();
            assertSame(replacement, paper.runtime.guiService().getWindow("same"));
            replacement.unregister();
            assertNull(paper.runtime.guiService().getWindow("same"));
        }
    }
}
