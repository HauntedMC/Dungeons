package nl.hauntedmc.dungeons.gui.framework.window;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.gui.menu.EditorMenus;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.item.ItemUtils;

import org.bukkit.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class EditorAccessContractTest {
    @TempDir Path directory;

    @Test
    void editorWindowsRecheckMaintenancePermissionAndOwnershipAtEveryCallback() throws Exception {
        try (var f = new StaffFixture(directory);
                var windows = new StaticMapSnapshot(GuiWindowRegistry.class, "windows")) {
            var player = f.player("Builder");
            var session = mock(DungeonPlayerSession.class);
            var sessions = mock(PlayerSessionRegistry.class);
            when(sessions.get(player.player)).thenReturn(session);
            when(sessions.snapshot()).thenReturn(List.of(session));
            f.runtime.setPlayerSessions(sessions);
            var editor = mock(EditableInstance.class);
            when(editor.getRuntime()).thenReturn(f.runtime);
            when(editor.getDungeon()).thenReturn(f.run.dungeon);
            when(session.getInstance()).thenReturn(editor);
            when(session.isEditMode()).thenReturn(true);
            var window = EditorMenus.editorWindow("test_editor", 9, "Editor");
            var inventory = mock(Inventory.class);
            f.run.paper
                    .bukkit
                    .when(
                            () ->
                                    Bukkit.createInventory(
                                            eq(player.player), eq(9), any(Component.class)))
                    .thenReturn(inventory);
            window.open(player.player);
            assertNull(window.getInventoryFor(player.player));
            f.run.dungeon.setEditSession(editor);
            window.open(player.player);
            assertNull(window.getInventoryFor(player.player));
            f.ops.maintenance(f.actor, "crypt", true, "Editing");
            window.open(player.player);
            assertNotNull(window.getInventoryFor(player.player));
            var event = TestEvents.cancellable(InventoryOpenEvent.class);
            when(event.getPlayer()).thenReturn(player.player);
            when(event.getInventory()).thenReturn(inventory);
            window.onOpen(event);
            assertFalse(event.isCancelled());
            when(player.player.isPermissionSet("dungeons.staff.edit")).thenReturn(true);
            when(player.player.hasPermission("dungeons.staff.edit")).thenReturn(false);
            window.onOpen(event);
            assertTrue(event.isCancelled());
            when(player.player.hasPermission("dungeons.staff.edit")).thenReturn(true);
            f.run.dungeon.setEditSession(null);
            var stale = TestEvents.cancellable(InventoryOpenEvent.class);
            when(stale.getPlayer()).thenReturn(player.player);
            when(stale.getInventory()).thenReturn(inventory);
            window.onOpen(stale);
            assertTrue(stale.isCancelled());
        }
    }

    @Test
    void roomWhitelistActionsCannotRunAfterThePlayerMovesToAnotherEditor() throws Exception {
        var placeholder = RoomMenus.class.getDeclaredField("openSlotItem");
        placeholder.setAccessible(true);
        Object previous = placeholder.get(null);
        placeholder.set(null, null);
        try (var f = new StaffFixture(directory);
                var windows = new StaticMapSnapshot(GuiWindowRegistry.class, "windows")) {
            var player = f.player("Builder");
            try (var rendering = new StaffGuiBoundary(f, player);
                    var items = mockStatic(ItemUtils.class)) {
                var blocked = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                items.when(ItemUtils::getBlockedMenuItem).thenReturn(blocked);
                var dungeon = mock(BranchingDungeon.class);
                when(dungeon.getWorldName()).thenReturn("crypt");
                when(dungeon.getRuntime()).thenReturn(f.runtime);
                var room = mock(BranchingRoomDefinition.class);
                when(room.getNamespace()).thenReturn("hall");
                when(room.getRoomWhitelist()).thenReturn(new ArrayList<>());
                var session = mock(DungeonPlayerSession.class);
                var sessions = mock(PlayerSessionRegistry.class);
                when(sessions.get(player.player)).thenReturn(session);
                when(sessions.snapshot()).thenReturn(List.of(session));
                f.runtime.setPlayerSessions(sessions);
                var editor = mock(EditableInstance.class);
                when(editor.getRuntime()).thenReturn(f.runtime);
                when(editor.getDungeon()).thenReturn(dungeon);
                when(dungeon.getEditSession()).thenReturn(editor);
                when(session.isEditMode()).thenReturn(true);
                when(session.getInstance()).thenReturn(editor);
                f.ops.maintenance(f.actor, "crypt", true, "Editing");
                RoomMenus.initializeRoomWhitelistMenu(dungeon, room);
                var window = GuiWindowRegistry.getWindow("whitelist_crypt_hall");
                assertNotNull(window);
                var inventory = mock(Inventory.class);
                f.run.paper
                        .bukkit
                        .when(
                                () ->
                                        Bukkit.createInventory(
                                                eq(player.player), eq(54), any(Component.class)))
                        .thenReturn(inventory);
                window.open(player.player);
                var open = TestEvents.cancellable(InventoryOpenEvent.class);
                when(open.getPlayer()).thenReturn(player.player);
                when(open.getInventory()).thenReturn(inventory);
                window.onOpen(open);
                assertFalse(open.isCancelled());
                var click = TestEvents.cancellable(InventoryClickEvent.class);
                when(click.getWhoClicked()).thenReturn(player.player);
                when(click.getInventory()).thenReturn(inventory);
                when(click.getClickedInventory()).thenReturn(inventory);
                when(click.getRawSlot()).thenReturn(3);
                window.onButtonClick(click);
                verify(session).setAwaitingRoomName(true);
                var replacement = mock(EditableInstance.class);
                when(replacement.getDungeon()).thenReturn(f.run.dungeon);
                when(session.getInstance()).thenReturn(replacement);
                window.onButtonClick(click);
                verify(session, times(1)).setAwaitingRoomName(true);
                assertTrue(click.isCancelled());
            }
        } finally {
            placeholder.set(null, previous);
        }
    }
}
