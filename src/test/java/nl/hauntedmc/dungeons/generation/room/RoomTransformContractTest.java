package nl.hauntedmc.dungeons.generation.room;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

class RoomTransformContractTest {
    @Test
    void cloningAndRotatingConnectorGraphsNeverMutatesTheirTemplateCoordinatesOrFilters() {
        var source = new Connector(new SimpleLocation(2, 1, 3, SimpleLocation.Direction.NORTH));
        source.getDoor().addLocation(new SimpleLocation(2, 1, 3));
        var neighbor = mock(BranchingRoomDefinition.class);
        when(neighbor.getNamespace()).thenReturn("hall");
        when(neighbor.getWeight()).thenReturn(1.0);
        source.setRoomWhitelist(List.of(new WhitelistEntry(neighbor)));
        Connector clone = source.clone();
        clone.getLocation().setX(999);
        clone.getDoor().getLocations().getFirst().setX(999);
        clone.getRoomWhitelist().getFirst().setWeight(9);
        assertEquals(2, source.getLocation().getX());
        assertEquals(2, source.getDoor().getLocations().getFirst().getX());
        assertEquals(1, source.getRoomWhitelist().getFirst().getWeight());

        var room = mock(BranchingRoomDefinition.class);
        when(room.getBounds()).thenReturn(new BoundingBox(0, 0, 0, 8, 4, 6));
        when(room.getConnectors()).thenReturn(List.of(source));
        RotatedRoom rotated = new RotatedRoom(room, 90);
        assertEquals(6, rotated.getBounds().getWidthX());
        assertEquals(8, rotated.getBounds().getWidthZ());
        var target = rotated.getConnectors().getFirst();
        assertEquals(new SimpleLocation(3, 1, 6, SimpleLocation.Direction.WEST), target.getLocation());
        target.getDoor().getLocations().getFirst().setX(100);
        assertEquals(2, source.getDoor().getLocations().getFirst().getX());
        assertEquals(SimpleLocation.Direction.NORTH, source.getLocation().getDirection());
    }
}
