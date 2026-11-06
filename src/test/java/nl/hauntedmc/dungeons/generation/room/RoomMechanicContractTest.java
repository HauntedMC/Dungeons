package nl.hauntedmc.dungeons.generation.room;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.function.CheckpointFunction;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.function.SpawnMobFunction;
import nl.hauntedmc.dungeons.content.function.TeleportFunction;
import nl.hauntedmc.dungeons.content.function.room.RoomDoorFunction;
import nl.hauntedmc.dungeons.content.trigger.DistanceTrigger;
import nl.hauntedmc.dungeons.content.trigger.room.RoomDoorChangeTrigger;
import nl.hauntedmc.dungeons.event.RoomDoorChangeEvent;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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

    @Test
    void doorChangeSignalsExecuteOnlyTheMatchingRoomDoorAndActionGraph() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = mockStatic(RoomMenus.class);
                var hotbars = new HeadlessMenus()) {
            var b = new BranchingRunFixture(f);
            var room = room(b, "hall", 0);
            var other = room(b, "other", 20);
            var trigger = new RoomDoorChangeTrigger(Map.of("doorName", "exit", "action", 1));
            var effect = new SetStatusFunction(Map.of("status", "door-opened"));
            effect.initializeFields();
            effect.setTrigger(trigger);
            effect.enable(b.run, new Location(f.world, 2, 71, 2));
            var manager = Bukkit.getPluginManager();
            doAnswer(
                            c -> {
                                if (c.getArgument(0) instanceof RoomDoorChangeEvent event)
                                    trigger.onDoorChange(event);
                                return null;
                            })
                    .when(manager)
                    .callEvent(any(Event.class));
            trigger.onDoorChange(
                    new RoomDoorChangeEvent(
                            b.run,
                            other,
                            other.getConnectors().getLast().getDoor(),
                            DoorAction.OPEN));
            trigger.onDoorChange(
                    new RoomDoorChangeEvent(
                            b.run,
                            room,
                            room.getConnectors().getFirst().getDoor(),
                            DoorAction.OPEN));
            trigger.onDoorChange(
                    new RoomDoorChangeEvent(
                            b.run,
                            room,
                            room.getConnectors().getLast().getDoor(),
                            DoorAction.CLOSE));
            assertTrue(f.scheduler.immediate.isEmpty());
            function(b, f.world, Map.of("doorName", "exit", "action", 1))
                    .runFunction(null, List.of());
            assertEquals(1, f.scheduler.immediate.size());
            assertNull(b.run.getStatus());
            f.scheduler.immediate.getFirst().run();
            assertEquals("door-opened", b.run.getStatus());
            assertTrue(b.run.getTriggerListeners(RoomDoorChangeTrigger.class).isEmpty());
        }
    }

    @Test
    void wholePartyRoomDetectorsWaitForEveryLivingMemberAndEmitAnIndividualSourceForEach()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = mockStatic(RoomMenus.class)) {
            var b = new BranchingRunFixture(f);
            room(b, "hall", 0);
            room(b, "other", 20);
            var first = new PlayerState();
            var second = new PlayerState();
            var left = f.session(first);
            var right = f.session(second);
            b.run.addPlayer(left);
            b.run.addPlayer(right);
            first.location = new Location(f.world, 2, 71, 2);
            second.location = new Location(f.world, 22, 71, 2);
            f.paper
                    .bukkit
                    .when(Bukkit::getOnlinePlayers)
                    .thenReturn(List.of(first.player, second.player));
            when(f.world.getNearbyEntities(
                            any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                    .thenReturn(List.of(first.player, second.player));
            var trigger =
                    new DistanceTrigger(
                            Map.of(
                                    "radius",
                                    100.0,
                                    "count",
                                    -1,
                                    "forEachPlayer",
                                    true,
                                    "limitToRoom",
                                    true));
            trigger.initializeFields();
            trigger.setLocation(new Location(f.world, 2, 71, 2));
            trigger.setAllowRetrigger(true);
            trigger.enable(null, b.run);
            var scan = f.scheduler.jobs.getLast();
            assertEquals(10, scan.period);
            scan.run();
            assertTrue(f.scheduler.immediate.isEmpty());
            second.location = new Location(f.world, 3, 71, 2);
            scan.run();
            assertEquals(2, f.scheduler.immediate.size());
            f.scheduler.immediate.forEach(CapturedScheduler.Job::run);
            var events = ArgumentCaptor.forClass(TriggerFireEvent.class);
            verify(Bukkit.getPluginManager(), times(2)).callEvent(events.capture());
            assertEquals(
                    Set.of(left, right),
                    events.getAllValues().stream()
                            .map(TriggerFireEvent::getPlayerSession)
                            .collect(Collectors.toSet()));
            trigger.disable();
            assertTrue(scan.cancelled);
        }
    }

    @Test
    void rotatedRoomMechanicsTranslatePositionsAndFacingsWithoutMutatingReusableTemplates()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = mockStatic(RoomMenus.class);
                var hotbars = new HeadlessMenus()) {
            var b = new BranchingRunFixture(f);
            var definition =
                    new BranchingRoomDefinition(
                            b.dungeon, "rotated", new BoundingBox(0, 0, 0, 8, 4, 6));
            var teleport =
                    new TeleportFunction(
                            Map.of("teleportTarget", new Location(null, 4, 1, 5, 90, 15)));
            var checkpoint = new CheckpointFunction(Map.of("yaw", 100.0));
            var spawner = new SpawnMobFunction(Map.of("yaw", 45.0));
            var functions = List.of(teleport, checkpoint, spawner);
            var locations =
                    List.of(
                            new Location(null, 2, 1, 3),
                            new Location(null, 1, 1, 2),
                            new Location(null, 3, 1, 1));
            for (int index = 0; index < functions.size(); index++) {
                var function = functions.get(index);
                function.initializeFields();
                function.setLocation(locations.get(index));
                definition.addFunction(function.getLocation(), function);
            }
            var rotated = new RotatedRoom(definition, 90);
            var first =
                    new InstanceRoom(
                            rotated, new SimpleLocation(100, 70, 200), new SimpleLocation(0, 0, 0));
            b.add(first);
            var second =
                    new InstanceRoom(
                            rotated, new SimpleLocation(200, 70, 200), new SimpleLocation(0, 0, 0));
            b.add(second);
            var left =
                    assertInstanceOf(
                            TeleportFunction.class,
                            b.run.getFunctions().get(new Location(f.world, 103, 71, 206)));
            var right =
                    assertInstanceOf(
                            TeleportFunction.class,
                            b.run.getFunctions().get(new Location(f.world, 203, 71, 206)));
            assertEquals(new Location(f.world, 105, 71, 204, 0, 15), left.getInstanceLoc());
            assertEquals(new Location(f.world, 205, 71, 204, 0, 15), right.getInstanceLoc());
            var placedCheckpoint =
                    assertInstanceOf(
                            CheckpointFunction.class,
                            b.run.getFunctions().get(new Location(f.world, 102, 71, 207)));
            var placedSpawner =
                    assertInstanceOf(
                            SpawnMobFunction.class,
                            b.run.getFunctions().get(new Location(f.world, 101, 71, 205)));
            assertEquals(10, placedCheckpoint.getYaw());
            assertEquals(-45, placedSpawner.getYaw());
            left.getInstanceLoc().setX(999);
            assertEquals(205, right.getInstanceLoc().getX());
            assertEquals(new Location(null, 4, 1, 5, 90, 15), teleport.getTeleportTarget());
            assertEquals(locations.getFirst(), teleport.getLocation());
            assertEquals(100, checkpoint.getYaw());
            assertEquals(45, spawner.getYaw());
        }
    }

    @Test
    void oneFailedRoomMechanicCannotAbandonHealthyMechanicsOrLeaveItsPartialRegistrationBehind()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = mockStatic(RoomMenus.class);
                var hotbars = new HeadlessMenus()) {
            var b = new BranchingRunFixture(f);
            var definition =
                    new BranchingRoomDefinition(
                            b.dungeon, "recovery", new BoundingBox(0, 0, 0, 4, 3, 4));
            var broken = new FailedEnable();
            broken.setLocation(new Location(null, 1, 1, 1));
            definition.addFunction(broken.getLocation(), broken);
            var healthy = new SetStatusFunction(Map.of("status", "healthy"));
            healthy.initializeFields();
            healthy.setLocation(new Location(null, 2, 1, 2));
            definition.addFunction(healthy.getLocation(), healthy);
            var room =
                    new InstanceRoom(
                            new RotatedRoom(definition, 0),
                            new SimpleLocation(0, 70, 0),
                            new SimpleLocation(0, 0, 0));
            assertDoesNotThrow(() -> b.add(room));
            assertTrue(b.run.getFunctionListeners(FailedEnable.class).isEmpty());
            assertEquals(1, b.run.getFunctions().size());
            b.run.getFunctions().values().iterator().next().runFunction(null, List.of());
            assertEquals("healthy", b.run.getStatus());
        }
    }

    static class FailedEnable extends SetStatusFunction {
        @Override
        public void onEnable() {
            throw new IllegalStateException("cannot enable room mechanic");
        }
    }
}
