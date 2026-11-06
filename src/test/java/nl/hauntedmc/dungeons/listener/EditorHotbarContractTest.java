package nl.hauntedmc.dungeons.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.item.ItemUtils;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class EditorHotbarContractTest {
    @TempDir Path directory;

    @Test
    void
            managedHotbarsProtectPlayerSlotsAcrossChestViewsAndBackOrCloseRestoresTheCapturedInventory()
                    throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var gui = new HeadlessInventoryMenus()) {
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            var session = f.session(state);
            var menu = mock(PlayerHotbarMenu.class);
            var menuItem = PlayerState.stack(1);
            when(menu.getItem(0)).thenReturn(menuItem);
            var listener = new HotbarMenuListener(f.paper.plugin, f.paper.runtime.playerSessions());
            session.captureAndShowHotbar(menu);
            f.scheduler.jobs.getLast().run();
            assertSame(menu, session.getCurrentHotbar());
            var click = TestEvents.cancellable(InventoryClickEvent.class);
            when(click.getWhoClicked()).thenReturn(state.player);
            when(click.getSlot()).thenReturn(10);
            when(click.getHotbarButton()).thenReturn(0);
            listener.onClickHotbarItem(click);
            assertTrue(click.isCancelled());
            var chest = mock(Inventory.class);
            var view = mock(InventoryView.class);
            when(view.getInventory(0)).thenReturn(chest);
            when(view.convertSlot(0)).thenReturn(0);
            when(view.getInventory(54)).thenReturn(state.inventory);
            when(view.convertSlot(54)).thenReturn(0);
            for (int rawSlot : List.of(0, 54)) {
                var drag = TestEvents.cancellable(InventoryDragEvent.class);
                when(drag.getWhoClicked()).thenReturn(state.player);
                when(drag.getInventory()).thenReturn(chest);
                when(drag.getView()).thenReturn(view);
                when(drag.getRawSlots()).thenReturn(Set.of(rawSlot));
                when(drag.getInventorySlots()).thenReturn(Set.of(0));
                listener.onDragHotbarItem(drag);
                assertEquals(rawSlot == 54, drag.isCancelled());
            }
            var drop = TestEvents.cancellable(PlayerDropItemEvent.class);
            when(drop.getPlayer()).thenReturn(state.player);
            listener.onDropMenuItem(drop);
            assertTrue(drop.isCancelled());
            f.scheduler.jobs.getLast().run();
            assertNull(session.getCurrentHotbar());
            assertEquals(7, state.contents[0].getAmount());
            session.captureAndShowHotbar(menu);
            f.scheduler.jobs.getLast().run();
            var swap = TestEvents.cancellable(PlayerSwapHandItemsEvent.class);
            when(swap.getPlayer()).thenReturn(state.player);
            listener.onSwapHands(swap);
            assertTrue(swap.isCancelled());
            assertNull(session.getCurrentHotbar());
            assertNull(session.getSavedHotbar());
            assertEquals(7, state.contents[0].getAmount());
            verify(state.player, times(2))
                    .playSound(
                            any(Location.class),
                            eq("minecraft:item.lodestone_compass.lock"),
                            eq(1.0F),
                            eq(1.2F));
            try (var tools = mockStatic(ItemUtils.class)) {
                session.setEditMode(true);
                for (int rawSlot : List.of(0, 54)) {
                    var drag = TestEvents.cancellable(InventoryDragEvent.class);
                    when(drag.getWhoClicked()).thenReturn(state.player);
                    when(drag.getInventory()).thenReturn(chest);
                    when(drag.getView()).thenReturn(view);
                    when(drag.getRawSlots()).thenReturn(Set.of(rawSlot));
                    when(drag.getInventorySlots()).thenReturn(Set.of(0));
                    listener.onDragHotbarItem(drag);
                    assertEquals(rawSlot == 54, drag.isCancelled());
                }
            }
        }
    }
}
