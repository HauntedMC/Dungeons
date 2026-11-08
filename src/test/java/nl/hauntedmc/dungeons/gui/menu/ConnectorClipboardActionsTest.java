package nl.hauntedmc.dungeons.gui.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ConnectorClipboardActionsTest {
    @Test
    void copyKeepsTheSourceAndClipboardWhileMovingConsumesTheClipboard() {
        for (boolean copying : new boolean[] {true, false}) {
            var player = mock(Player.class);
            var session = mock(DungeonPlayerSession.class);
            var room = mock(BranchingRoomDefinition.class);
            var sourceLocation = new SimpleLocation(2, 1, 3, SimpleLocation.Direction.NORTH);
            var source = new Connector(sourceLocation);
            source.getDoor().addLocation(sourceLocation.clone());
            var target = new SimpleLocation(9, 1, 8, SimpleLocation.Direction.EAST);
            when(session.isConnectorCopying()).thenReturn(copying);
            when(session.isConnectorCutting()).thenReturn(!copying);
            when(session.getCopiedConnector()).thenReturn(source);
            when(room.applyDirectionAtEdge(target)).thenReturn(true);
            try (var messages = mockStatic(LangUtils.class)) {
                ConnectorClipboardActions.paste(player, session, room, target);
            }
            var selected = ArgumentCaptor.forClass(Connector.class);
            verify(session).setActiveConnector(selected.capture());
            var result = selected.getValue();
            assertEquals(target, result.getLocation());
            var doorBlock = result.getDoor().getLocations().getFirst();
            assertEquals(target.getX(), doorBlock.getX());
            assertEquals(target.getZ(), doorBlock.getZ());
            if (copying) {
                assertNotSame(source, result);
                assertEquals(2, source.getLocation().getX());
                assertEquals(2, source.getDoor().getLocations().getFirst().getX());
                verify(room).addConnector(result);
                verify(session, never()).clearConnectorClipboard();
            } else {
                assertSame(source, result);
                verify(session).clearConnectorClipboard();
                verify(room, never()).addConnector(any(Connector.class));
            }
        }
    }

    @Test
    void rejectedPlacementRetainsTheClipboardAndDoesNotMutateTheSource() {
        var session = mock(DungeonPlayerSession.class);
        var room = mock(BranchingRoomDefinition.class);
        var source = new Connector(new SimpleLocation(2, 1, 3));
        var target = new SimpleLocation(8, 1, 8);
        when(session.isConnectorCutting()).thenReturn(true);
        when(session.getCopiedConnector()).thenReturn(source);
        try (var messages = mockStatic(LangUtils.class)) {
            ConnectorClipboardActions.paste(mock(Player.class), session, room, target);
        }
        assertEquals(2, source.getLocation().getX());
        verify(session, never()).clearConnectorClipboard();
        verify(session, never()).setActiveConnector(any());
        verify(room, never()).addConnector(any(Connector.class));
    }
}
