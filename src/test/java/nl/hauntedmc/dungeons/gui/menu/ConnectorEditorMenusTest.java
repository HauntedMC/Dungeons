package nl.hauntedmc.dungeons.gui.menu;

import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.support.HeadlessInventoryMenus;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class ConnectorEditorMenusTest {
    private HeadlessInventoryMenus menus;

    @BeforeEach
    void setUp() {
        this.menus = new HeadlessInventoryMenus();
    }

    @AfterEach
    void tearDown() {
        this.menus.close();
    }

    @Test
    void doorNavigationUsesTheCurrentMenuAfterReinitialization() {
        var runtime = mock(DungeonsRuntime.class);
        var sessions = mock(PlayerSessionRegistry.class);
        var session = mock(DungeonPlayerSession.class);
        var player = mock(Player.class);
        var event = mock(PlayerInteractEvent.class);
        var connector = mock(Connector.class);
        var door = mock(ConnectorDoor.class);
        when(runtime.playerSessions()).thenReturn(sessions);
        when(sessions.get(player)).thenReturn(session);
        when(event.getPlayer()).thenReturn(player);
        when(session.getActiveRoom()).thenReturn(mock(BranchingRoomDefinition.class));
        when(session.getActiveConnector()).thenReturn(connector);
        when(connector.getDoor()).thenReturn(door);
        var current = new AtomicReference<>(mock(PlayerHotbarMenu.class));
        var menu = ConnectorEditorMenus.connectorMenu(runtime, current::get);
        var replacement = mock(PlayerHotbarMenu.class);
        current.set(replacement);

        try (var messages = mockStatic(LangUtils.class)) {
            menu.getMenuItems().get(2).onSelect(event);
        }

        verify(session).showHotbar(replacement, true);
        verify(session).setActiveDoor(door);
        menu.getMenuItems().get(1).onSelect(event);
        verify(session).setActiveConnector(null);
        verify(session).setConfirmRoomAction(false);
        verify(session).restorePreviousHotbar(true);
    }

    @Test
    void doorTargetRangeIsReadAtInteractionTime() {
        var runtime = mock(DungeonsRuntime.class);
        var player = mock(Player.class);
        var event = mock(PlayerInteractEvent.class);
        when(event.getPlayer()).thenReturn(player);
        var range = new AtomicInteger(5);
        var menu = ConnectorEditorMenus.doorMenu(runtime, range::get);
        menu.getMenuItems().get(3).onSelect(event);
        range.set(12);
        menu.getMenuItems().get(3).onSelect(event);
        verify(player).getTargetBlockExact(5);
        verify(player).getTargetBlockExact(12);
    }
}
