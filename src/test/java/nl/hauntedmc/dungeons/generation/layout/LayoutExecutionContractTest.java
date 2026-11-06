package nl.hauntedmc.dungeons.generation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.RotatedRoom;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.util.math.RangedNumber;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

class LayoutExecutionContractTest {
    @Test
    @Timeout(10)
    void fullGenerationPlacesAConnectedGraphAndSubsequentRunsStartWithFreshState() {
        try (var paper = new PaperBoundary()) {
            var graph = new Graph(paper);
            assertEquals(Layout.GenerationResult.SUCCESS, graph.layout.generate());
            assertEquals(2, graph.layout.getAllRooms().size());
            var start = graph.layout.getFirst();
            var end =
                    graph.layout.getAllRooms().stream()
                            .filter(r -> r != start)
                            .findFirst()
                            .orElseThrow();
            assertSame(graph.start, start.getSource().getOrigin());
            assertSame(graph.end, end.getSource().getOrigin());
            assertNotNull(end.getStartConnector());
            assertEquals(1, start.getUsedConnectors().size());
            assertFalse(start.getBounds().overlaps(end.getBounds()));
            Set<UUID> previous =
                    graph.layout.getAllRooms().stream()
                            .map(r -> r.getUuid())
                            .collect(Collectors.toSet());
            assertEquals(Layout.GenerationResult.SUCCESS, graph.layout.generate());
            assertEquals(2, graph.layout.getAllRooms().size());
            assertTrue(
                    graph.layout.getAllRooms().stream()
                            .noneMatch(r -> previous.contains(r.getUuid())));
        }
    }

    @Test
    @Timeout(10)
    void incompatibleRequiredRoomsFailInsteadOfReportingAPartialGraphAsSuccessful() {
        try (var paper = new PaperBoundary()) {
            var graph = new Graph(paper);
            when(graph.end.getConnectors())
                    .thenReturn(
                            List.of(
                                    new Connector(
                                            new SimpleLocation(
                                                    0, 1, 1, SimpleLocation.Direction.EAST))));
            var incompatible = new RotatedRoom(graph.end, 0);
            when(graph.end.getOrientations()).thenReturn(List.of(incompatible));
            assertEquals(Layout.GenerationResult.BAD_LAYOUT, graph.layout.attempt());
            assertEquals(1, graph.layout.getAllRooms().size());
            assertTrue(
                    graph.layout.failureReasons.containsKey(
                            GenerationFailureReason.NO_COMPATIBLE_CONNECTOR));
            assertTrue(
                    graph.layout.failureReasons.containsKey(
                            GenerationFailureReason.MISSING_REQUIRED_ROOM));
        }
    }

    static class Graph {
        final BranchingDungeon dungeon = mock(BranchingDungeon.class);
        final BranchingRoomDefinition start = mock(BranchingRoomDefinition.class);
        final BranchingRoomDefinition end = mock(BranchingRoomDefinition.class);
        final TestLayout layout;

        Graph(PaperBoundary paper) {
            when(dungeon.getRuntime()).thenReturn(paper.runtime);
            when(dungeon.getEnvironment()).thenReturn(paper.runtime.environment());
            when(dungeon.getWorldName()).thenReturn("crypt");
            when(dungeon.getUniqueRooms()).thenReturn(Map.of("start", start, "end", end));
            when(dungeon.getStartRooms()).thenReturn(Map.of("start", start));
            when(dungeon.getRoom("end")).thenReturn(end);
            describe(start, "start", "0+");
            describe(end, "end", "2");
            var exit = new Connector(new SimpleLocation(2, 1, 1, SimpleLocation.Direction.EAST));
            exit.setSuccessChance(1);
            exit.setRoomWhitelist(List.of(new WhitelistEntry(end)));
            when(start.getConnectors()).thenReturn(List.of(exit));
            when(end.getConnectors())
                    .thenReturn(
                            List.of(
                                    new Connector(
                                            new SimpleLocation(
                                                    0, 1, 1, SimpleLocation.Direction.WEST))));
            when(end.canGenerate(start)).thenReturn(true);
            RotatedRoom first = new RotatedRoom(start, 0);
            RotatedRoom last = new RotatedRoom(end, 0);
            when(start.getRandomOrientation()).thenReturn(first);
            when(end.getOrientations()).thenReturn(List.of(last));
            var settings = new YamlConfiguration();
            settings.set("generator.seal_open_connectors_with", "AIR");
            settings.set("generator.room_target.min", 2);
            settings.set("generator.room_target.max", 2);
            layout = new TestLayout(dungeon, settings);
        }

        void describe(BranchingRoomDefinition room, String name, String depth) {
            when(room.getNamespace()).thenReturn(name);
            when(room.getBounds()).thenReturn(new BoundingBox(0, 0, 0, 2, 2, 2));
            when(room.getDepth()).thenReturn(new RangedNumber(depth));
            when(room.getOccurrences()).thenReturn(new RangedNumber("1"));
            when(room.getWeight()).thenReturn(1.0);
        }
    }

    static class TestLayout extends ConnectorExpansionLayout {
        TestLayout(BranchingDungeon dungeon, YamlConfiguration settings) {
            super(dungeon, settings);
        }

        @Override
        public void initializeConnectorEditMenu() {}

        GenerationResult attempt() {
            return tryLayout();
        }
    }
}
