package nl.hauntedmc.dungeons.gui.loot;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.content.reward.LootTableItem;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.support.HeadlessInventoryMenus;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LootTableEditorTest {
    @Test
    void itemTransitionsKeepTheLeaseWhileSwitchingTablesReleasesThePreviousTable() {
        try (var f = new Fixture()) {
            var second = new LootTable("second");
            when(f.repository.get("second")).thenReturn(second);
            assertTrue(f.editor.open(f.table, f.player));
            doAnswer(
                            call -> {
                                f.editor.closed(f.table, f.player);
                                return null;
                            })
                    .when(f.gui)
                    .openGui(f.player, LootTableEditor.windowName(f.table, true));
            f.editor.openItem(f.table, new LootTableItem(PlayerState.stack(1)), f.player);
            assertSame(f.player, f.editor.getEditor(f.table));
            var competitor = new PlayerState().player;
            assertFalse(f.editor.open(f.table, competitor));
            doAnswer(
                            call -> {
                                f.editor.closed(f.table, f.player);
                                return null;
                            })
                    .when(f.gui)
                    .openGui(f.player, LootTableEditor.windowName(second, false));
            assertTrue(f.editor.open(second, f.player));
            assertNull(f.editor.getEditor(f.table));
            assertSame(f.player, f.editor.getEditor(second));
            verify(f.repository, times(2)).saveTablesConfig(f.table);
        }
    }

    @Test
    void replacementRetiresVisibleWindowsAndStaleCallbacksCannotSaveNewModels() {
        try (var f = new Fixture()) {
            assertTrue(f.editor.open(f.table, f.player));
            var window = f.menus.windows().getFirst();
            when(window.getInventoryFor(f.player)).thenReturn(mock(GuiInventory.class));
            var replacement = new LootTable("first");
            when(f.repository.get("first")).thenReturn(replacement);
            f.replaced.run();
            verify(f.player).closeInventory();
            verify(window).unregister();
            assertTrue(f.editor.open(replacement, f.player));
            f.editor.closed(f.table, f.player);
            verify(f.repository, never()).saveTablesConfig(f.table);
            assertFalse(f.editor.canEdit(f.table, f.player));
            assertTrue(
                    f.editor.canEdit(replacement, f.player),
                    "A delayed close from the old generation cannot release the new lease");
        }
    }

    @Test
    void invalidReloadDisablesEditingAndQuitCheckpointsEvenWithoutACloseEvent() {
        try (var f = new Fixture()) {
            assertTrue(f.editor.open(f.table, f.player));
            assertTrue(f.editor.canEdit(f.table, f.player));
            when(f.repository.isWritable()).thenReturn(false);
            assertFalse(f.editor.canEdit(f.table, f.player));
            assertFalse(f.editor.open(f.table, f.player));
            when(f.repository.isWritable()).thenReturn(true);
            var quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(f.player);
            f.editor.onQuit(quit);
            verify(f.repository).saveTablesConfig(f.table);
            assertNull(f.editor.getEditor(f.table));
        }
    }

    @Test
    void openFailureReleasesLeaseAndShutdownRetriesFailedCloseSavesAndUnregisters() {
        try (var f = new Fixture()) {
            doThrow(new IllegalStateException("inventory unavailable"))
                    .when(f.gui)
                    .openGui(f.player, LootTableEditor.windowName(f.table, false));
            assertThrows(IllegalStateException.class, () -> f.editor.open(f.table, f.player));
            assertNull(f.editor.getEditor(f.table));
            doNothing().when(f.gui).openGui(f.player, LootTableEditor.windowName(f.table, false));
            assertTrue(f.editor.open(f.table, f.player));
            when(f.repository.saveTablesConfig(f.table)).thenReturn(false);
            f.editor.closed(f.table, f.player);
            f.editor.close();
            f.editor.close();
            verify(f.repository).saveTablesConfig();
            verify(f.menus.windows().getFirst()).unregister();
            verify(f.unsubscribe).run();
            assertFalse(f.editor.open(f.table, f.player));
        }
    }

    @Test
    void tableNamesCannotCollideWithItemWindowNames() {
        var first = new LootTable("first");
        var second = new LootTable("first_item");
        assertNotEquals(
                LootTableEditor.windowName(first, true), LootTableEditor.windowName(second, false));
        assertNotEquals(
                LootTableEditor.windowName(first, true), LootTableEditor.windowName(first, false));
    }

    static final class Fixture implements AutoCloseable {
        final PaperBoundary paper = new PaperBoundary();
        final HeadlessInventoryMenus menus = new HeadlessInventoryMenus();
        final LootTableRepository repository = mock(LootTableRepository.class);
        final GuiService gui = mock(GuiService.class);
        final Runnable unsubscribe = mock(Runnable.class);
        final LootTable table = new LootTable("first");
        final Player player = new PlayerState().player;
        final LootTableEditor editor;
        final Runnable replaced;

        Fixture() {
            when(player.hasPermission("dungeons.staff.loot")).thenReturn(true);
            when(repository.get("first")).thenReturn(table);
            when(repository.isWritable()).thenReturn(true);
            when(repository.saveTablesConfig(table)).thenReturn(true);
            when(repository.onTableReplacement(any())).thenReturn(unsubscribe);
            when(player.isOnline()).thenReturn(true);
            paper.bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            editor = new LootTableEditor(repository, gui);
            var listener = ArgumentCaptor.forClass(Runnable.class);
            verify(repository).onTableReplacement(listener.capture());
            replaced = listener.getValue();
        }

        public void close() {
            editor.close();
            menus.close();
            paper.close();
        }
    }
}
