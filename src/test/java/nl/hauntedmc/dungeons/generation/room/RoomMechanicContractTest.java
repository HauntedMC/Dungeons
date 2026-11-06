package nl.hauntedmc.dungeons.generation.room;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.function.room.RoomDoorFunction;
import nl.hauntedmc.dungeons.event.RoomDoorChangeEvent;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.*;

class RoomMechanicContractTest {
    @TempDir Path directory;

    static InstanceRoom room(BranchingRunFixture b, String name, int offset) {
        var definition =
                new BranchingRoomDefinition(b.dungeon, name, new BoundingBox(0, 0, 0, 4, 3, 4));
        var entry = new Connector(new SimpleLocation(0, 0, 2, SimpleLocation.Direction.WEST));
        entry.getDoor().setNamespace("entry");
        var exit = new Connector(new SimpleLocation(4, 0, 2, SimpleLocation.Direction.EAST));
        exit.getDoor().setNamespace("exit");
        definition.addConnector(entry);
        definition.addConnector(exit);
        var room =
                new InstanceRoom(
                        new RotatedRoom(definition, 0),
                        new SimpleLocation(offset, 70, 2, SimpleLocation.Direction.WEST),
                        entry.getLocation());
        b.add(room);
        room.getConnectors()
                .forEach(
                        c -> {
                            c.getDoor().setHasAdjacentRoom(true);
                            c.getDoor().setOpen(true);
                        });
        return room;
    }

    static RoomDoorFunction function(
            BranchingRunFixture b, World world, Map<String, Object> config) {
        var function = new RoomDoorFunction(config);
        function.initializeFields();
        function.setInstance(b.run);
        function.setLocation(new Location(world, 2, 71, 2));
        return function;
    }

    @Test
    void roomDoorControllersHonorNamedActionsAdjacentDoorsAndTheEntranceException()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = mockStatic(RoomMenus.class)) {
            var b = new BranchingRunFixture(f);
            var room = room(b, "hall", 0);
            var other = room(b, "other", 20);
            var entry = room.getConnectors().getFirst().getDoor();
            var exit = room.getConnectors().getLast().getDoor();
            function(b, f.world, Map.of("doorName", "all", "action", 2, "keepEntranceOpen", true))
                    .runFunction(null, List.of());
            assertTrue(entry.isOpen());
            assertFalse(exit.isOpen());
            assertTrue(other.getConnectors().stream().allMatch(c -> c.getDoor().isOpen()));
            function(b, f.world, Map.of("doorName", "entry", "action", 0))
                    .runFunction(null, List.of());
            assertFalse(entry.isOpen());
            function(b, f.world, Map.of("doorName", "exit", "action", 1))
                    .runFunction(null, List.of());
            assertTrue(exit.isOpen());
            var events = ArgumentCaptor.forClass(RoomDoorChangeEvent.class);
            verify(Bukkit.getPluginManager(), times(3)).callEvent(events.capture());
            assertEquals(
                    List.of(DoorAction.CLOSE, DoorAction.CLOSE, DoorAction.OPEN),
                    events.getAllValues().stream().map(RoomDoorChangeEvent::getAction).toList());
            assertTrue(
                    events.getAllValues().stream()
                            .allMatch(e -> e.getRoom() == room && e.getInstance() == b.run));
            function(b, f.world, Map.of("doorName", "missing", "action", 2))
                    .runFunction(null, List.of());
            assertTrue(exit.isOpen());
        }
    }
}
