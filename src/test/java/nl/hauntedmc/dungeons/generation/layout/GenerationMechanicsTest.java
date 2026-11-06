package nl.hauntedmc.dungeons.generation.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

class GenerationMechanicsTest {
    @Test
    void placementRejectsExistingAndQueuedOverlapButAllowsAdjacentRoomsWithoutMutatingBounds() {
        var layout = new TestLayout(mock(BranchingDungeon.class));
        layout.roomAreas.add(new BoundingBox(0, 0, 0, 3, 3, 3).expand(0, 0, 0, 1, 1, 1));
        layout.queuedRoomAreas.add(new BoundingBox(10, 0, 0, 13, 3, 3).expand(0, 0, 0, 1, 1, 1));
        assertFalse(layout.canRoomGenerate(room(new BoundingBox(3, 0, 0, 6, 3, 3))));
        assertFalse(layout.canRoomGenerate(room(new BoundingBox(12, 0, 0, 15, 3, 3))));
        BoundingBox adjacent = new BoundingBox(4, 0, 0, 7, 3, 3);
        assertTrue(layout.canRoomGenerate(room(adjacent)));
        assertEquals(new BoundingBox(4, 0, 0, 7, 3, 3), adjacent);
        assertTrue(layout.canRoomGenerate(room(new BoundingBox(-5, 0, 0, -2, 3, 3))));
    }

    InstanceRoom room(BoundingBox bounds) {
        var room = mock(InstanceRoom.class);
        when(room.getBounds()).thenReturn(bounds);
        return room;
    }

    static class TestLayout extends ConnectorExpansionLayout {
        TestLayout(BranchingDungeon dungeon) { super(dungeon, settings()); resetGenerationState(); }
        static YamlConfiguration settings() {
            var settings = new YamlConfiguration();
            settings.set("generator.seal_open_connectors_with", "AIR");
            return settings;
        }
        @Override public void initializeConnectorEditMenu() {}
    }
}
