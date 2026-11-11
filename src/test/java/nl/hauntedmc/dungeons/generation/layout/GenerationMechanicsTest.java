package nl.hauntedmc.dungeons.generation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.room.RotatedRoom;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.util.math.RangedNumber;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
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

    @Test
    void candidateRoomsMustPassConnectorFiltersDepthAndOccurrenceLimitsTogether() {
        var dungeon = mock(BranchingDungeon.class);
        var parent = mock(BranchingRoomDefinition.class);
        var allowed = definition(dungeon, parent, "allowed", "2-4", "0-3");
        var tooDeep = definition(dungeon, parent, "deep", "3-5", "0+");
        var maxed = definition(dungeon, parent, "maxed", "0+", "0-1");
        var blocked = definition(dungeon, parent, "blocked", "0+", "0+");
        var connector = new Connector(new SimpleLocation(0, 0, 0));
        connector.setRoomWhitelist(
                List.of(
                        new WhitelistEntry(allowed),
                        new WhitelistEntry(tooDeep),
                        new WhitelistEntry(maxed),
                        new WhitelistEntry(blocked)));
        connector.setRoomBlacklist(List.of(new WhitelistEntry(blocked)));
        var rotated = mock(RotatedRoom.class);
        when(rotated.getOrigin()).thenReturn(parent);
        var from = mock(InstanceRoom.class);
        when(from.getSource()).thenReturn(rotated);
        when(from.getDepth()).thenReturn(1);
        var layout = new TestLayout(dungeon);
        layout.queuedCountsByRoom.put(maxed, 1);
        assertEquals(
                List.of(allowed), layout.filterRooms(connector, from).values().stream().toList());
        layout.queuedCountsByRoom.put(allowed, 3);
        assertTrue(layout.filterRooms(connector, from).isEmpty());
    }

    BranchingRoomDefinition definition(
            BranchingDungeon dungeon,
            BranchingRoomDefinition parent,
            String name,
            String depth,
            String occurrences) {
        var definition = mock(BranchingRoomDefinition.class);
        when(definition.getNamespace()).thenReturn(name);
        when(definition.getWeight()).thenReturn(1.0);
        when(definition.getDepth()).thenReturn(new RangedNumber(depth));
        when(definition.getOccurrences()).thenReturn(new RangedNumber(occurrences));
        when(definition.canGenerate(parent)).thenReturn(true);
        when(definition.getConnectors())
                .thenReturn(List.of(new Connector(new SimpleLocation(0, 0, 0))));
        when(dungeon.getRoom(name)).thenReturn(definition);
        return definition;
    }

    InstanceRoom room(BoundingBox bounds) {
        var room = mock(InstanceRoom.class);
        when(room.getBounds()).thenReturn(bounds);
        return room;
    }

    static class TestLayout extends ConnectorExpansionLayout {
        TestLayout(BranchingDungeon dungeon) {
            super(dungeon, settings());
            resetGenerationState();
        }

        static YamlConfiguration settings() {
            var settings = new YamlConfiguration();
            settings.set("generator.seal_open_connectors_with", "AIR");
            return settings;
        }

        @Override
        public void initializeConnectorEditMenu() {}
    }
}
