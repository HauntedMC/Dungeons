package nl.hauntedmc.dungeons.generation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
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
import java.util.stream.Collectors;

class BranchingExecutionContractTest {
    @Test
    @Timeout(10)
    void branchingGenerationKeepsTheTrunkAndAddsExactlyOneConnectedNonOverlappingSideRoom() {
        try (var paper = new PaperBoundary()) {
            var graph = new Graph(paper);
            assertEquals(Layout.GenerationResult.SUCCESS, graph.layout.generate());
            var rooms = graph.layout.getAllRooms();
            assertEquals(4, rooms.size());
            var byName =
                    rooms.stream()
                            .collect(
                                    Collectors.toMap(
                                            r -> r.getSource().getOrigin().getNamespace(), r -> r));
            assertEquals(graph.definitions.keySet(), byName.keySet());
            assertSame(byName.get("start"), graph.layout.getFirst());
            var middle = byName.get("middle");
            assertEquals(3, middle.getUsedConnectors().size());
            var branch = byName.get("side");
            assertNotNull(branch.getStartConnector());
            assertEquals(3, branch.getDepth());
            assertTrue(
                    middle.getUsedConnectors().stream()
                            .anyMatch(
                                    c -> {
                                        var adjacent = c.getLocation().clone();
                                        adjacent.shift(1);
                                        return adjacent.equals(
                                                branch.getStartConnector().getLocation());
                                    }));
            for (InstanceRoom first : rooms) {
                for (InstanceRoom second : rooms) {
                    if (first != second)
                        assertFalse(first.getBounds().overlaps(second.getBounds()));
                }
            }
        }
    }

    @Test
    @Timeout(10)
    void missingEligibleBranchAnchorsFailRequiredBranchesButAllowOptionalBranches() {
        for (boolean required : List.of(true, false)) {
            try (var paper = new PaperBoundary()) {
                var graph = new Graph(paper);
                graph.settings.set("generator.branching.branches.side.eligible_depth", "20+");
                graph.settings.set(
                        "generator.branching.branches.side.enforce_occurrences", required);
                assertEquals(
                        required
                                ? Layout.GenerationResult.BAD_LAYOUT
                                : Layout.GenerationResult.SUCCESS,
                        graph.layout.attempt());
                assertEquals(3, graph.layout.getAllRooms().size());
                assertTrue(
                        graph.layout.getAllRooms().stream()
                                .noneMatch(
                                        r ->
                                                r.getSource().getOrigin()
                                                        == graph.definitions.get("side")));
                if (required)
                    assertTrue(
                            graph.layout.failureReasons.containsKey(
                                    GenerationFailureReason.MISSING_REQUIRED_ROOM));
            }
        }
    }

    static final class Graph {
        final BranchingDungeon dungeon = mock(BranchingDungeon.class);
        final Map<String, BranchingRoomDefinition> definitions =
                Map.of(
                        "start",
                        mock(BranchingRoomDefinition.class),
                        "middle",
                        mock(BranchingRoomDefinition.class),
                        "end",
                        mock(BranchingRoomDefinition.class),
                        "side",
                        mock(BranchingRoomDefinition.class));
        final YamlConfiguration settings = new YamlConfiguration();
        final TestLayout layout;

        Graph(PaperBoundary paper) {
            when(dungeon.getRuntime()).thenReturn(paper.runtime);
            when(dungeon.getEnvironment()).thenReturn(paper.runtime.environment());
            when(dungeon.getWorldName()).thenReturn("crypt");
            when(dungeon.getUniqueRooms()).thenReturn(definitions);
            when(dungeon.getStartRooms()).thenReturn(Map.of("start", definitions.get("start")));
            definitions.forEach(
                    (name, room) -> {
                        when(dungeon.getRoom(name)).thenReturn(room);
                        when(room.getNamespace()).thenReturn(name);
                        when(room.getBounds()).thenReturn(new BoundingBox(0, 0, 0, 2, 2, 2));
                        when(room.getDepth()).thenReturn(new RangedNumber("0+"));
                        when(room.getOccurrences()).thenReturn(new RangedNumber("0-1"));
                        when(room.getWeight()).thenReturn(1.0);
                        when(room.canGenerate(any())).thenReturn(true);
                    });
            var startConnectors = List.of(exit(2, 1, SimpleLocation.Direction.EAST, "middle"));
            when(definitions.get("start").getConnectors()).thenReturn(startConnectors);
            var middleConnectors =
                    List.of(
                            entrance(0, 1, SimpleLocation.Direction.WEST),
                            exit(2, 1, SimpleLocation.Direction.EAST, "end"),
                            exit(1, 2, SimpleLocation.Direction.SOUTH, "side"));
            when(definitions.get("middle").getConnectors()).thenReturn(middleConnectors);
            when(definitions.get("end").getConnectors())
                    .thenReturn(List.of(entrance(0, 1, SimpleLocation.Direction.WEST)));
            when(definitions.get("side").getConnectors())
                    .thenReturn(List.of(entrance(1, 0, SimpleLocation.Direction.NORTH)));
            definitions
                    .values()
                    .forEach(
                            room -> {
                                var rotated = new RotatedRoom(room, 0);
                                when(room.getRandomOrientation()).thenReturn(rotated);
                                when(room.getOrientations()).thenReturn(List.of(rotated));
                            });
            settings.set("generator.seal_open_connectors_with", "AIR");
            settings.set("generator.branching.trunk.room_pool", List.of("start", "middle", "end"));
            settings.set("generator.branching.trunk.end_room_pool", List.of("end"));
            settings.set("generator.branching.trunk.room_target.min", 3);
            settings.set("generator.branching.trunk.room_target.max", 3);
            settings.set("generator.branching.trunk.straightness", 1.0);
            settings.set("generator.branching.branches.side.room_pool", List.of("side"));
            settings.set("generator.branching.branches.side.end_room_pool", List.of("side"));
            settings.set("generator.branching.branches.side.eligible_depth", "2");
            settings.set("generator.branching.branches.side.occurrences", "1");
            settings.set("generator.branching.branches.side.room_target.min", 1);
            settings.set("generator.branching.branches.side.room_target.max", 1);
            layout = new TestLayout(dungeon, settings);
        }

        Connector entrance(int x, int z, SimpleLocation.Direction direction) {
            return new Connector(new SimpleLocation(x, 1, z, direction));
        }

        Connector exit(int x, int z, SimpleLocation.Direction direction, String target) {
            var connector = entrance(x, z, direction);
            connector.setSuccessChance(1);
            connector.setRoomWhitelist(List.of(new WhitelistEntry(definitions.get(target))));
            return connector;
        }
    }

    static final class TestLayout extends BranchingLayout {
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
