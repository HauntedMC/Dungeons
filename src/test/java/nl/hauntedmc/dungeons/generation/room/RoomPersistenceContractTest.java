package nl.hauntedmc.dungeons.generation.room;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RoomPersistenceContractTest {
    @TempDir Path directory;

    @Test
    void roomSnapshotsMarshalBeforeReadingModelsAndRemainOrderedWhileEditingContinues()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var _ = mockStatic(RoomMenus.class);
                var _ = new HeadlessMenus()) {
            var branch = new BranchingRunFixture(f);
            var room =
                    new BranchingRoomDefinition(
                            branch.dungeon, "ordered", new BoundingBox(0, 0, 0, 2, 2, 2));
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            room.setOccurrencesString("2-4");
            f.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            var first = room.saveRoomAsync();
            assertTrue(f.scheduler.asynchronous.isEmpty());
            f.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            f.scheduler.immediate.getFirst().run();
            var file = f.dungeon.getFolder().toPath().resolve("rooms/ordered.yml");
            var observed = new ArrayList<String>();
            first.thenRun(
                    () ->
                            observed.add(
                                    YamlConfiguration.loadConfiguration(file.toFile())
                                            .getString("Config.Occurrences")));
            room.setOccurrencesString("5-7");
            var second = room.saveRoomAsync();
            second.thenRun(
                    () ->
                            observed.add(
                                    YamlConfiguration.loadConfiguration(file.toFile())
                                            .getString("Config.Occurrences")));
            room.setOccurrencesString("9");
            assertEquals(1, f.scheduler.asynchronous.size());
            f.scheduler.asynchronous.getFirst().run();
            assertTrue(first.join());
            assertTrue(second.join());
            assertEquals(List.of("2-4", "5-7"), observed);
            assertEquals("9", room.getOccurrencesString());
        }
    }

    @Test
    void
            savedRoomMetadataConnectorsAndFunctionsReloadWhileMalformedAndOutOfBoundsFunctionsAreSkipped()
                    throws Exception {
        var models = new LinkedHashSet<>(ConfigSerializableModel.pluginSerializables);
        String alias = "dungeons.function.set_status";
        var previous = ConfigurationSerialization.getClassByAlias(alias);
        String targetAlias = "dungeons.function_target_type";
        var previousTarget = ConfigurationSerialization.getClassByAlias(targetAlias);
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var _ = mockStatic(RoomMenus.class);
                var _ = new HeadlessMenus()) {
            ConfigSerializableModel.register("nl.hauntedmc.dungeons");
            ConfigurationSerialization.registerClass(SetStatusFunction.class, alias);
            ConfigurationSerialization.registerClass(FunctionTargetType.class, targetAlias);
            var b = new BranchingRunFixture(f);
            var bounds = new BoundingBox(-2, 0, -3, 6, 4, 7);
            var room = new BranchingRoomDefinition(b.dungeon, "hall", bounds);
            room.setOccurrencesString("2-4");
            room.setDepthString("3+");
            room.setWeight(2.5);
            room.setSpawn(new Location(f.world, 1.5, 1, 2.5, 90, 15));
            var connector = room.addConnector(new SimpleLocation(-2, 1, 2));
            assertNotNull(connector);
            connector.getDoor().setNamespace("entry");
            connector.getDoor().setStartOpen(false);
            connector.getDoor().setDisableSound(true);
            connector.getDoor().addLocation(new SimpleLocation(-2, 2, 2));
            var valid = new SetStatusFunction(Map.of("status", "saved"));
            valid.initializeFields();
            valid.setLocation(new Location(null, 1, 1, 1));
            room.addFunction(valid.getLocation(), valid);
            assertTrue(room.saveFunctionsAsync().join());
            var file = f.dungeon.getFolder().toPath().resolve("rooms/hall.yml").toFile();
            var yaml = YamlConfiguration.loadConfiguration(file);
            var outside = new SetStatusFunction();
            outside.setLocation(new Location(null, 100, 1, 1));
            yaml.set("Functions", List.of(valid, "malformed", new SetStatusFunction(), outside));
            yaml.save(file);
            var restored = new BranchingRoomDefinition(b.dungeon, file);
            assertEquals(bounds, restored.getBounds());
            assertEquals("2-4", restored.getOccurrencesString());
            assertEquals("3+", restored.getDepthString());
            assertEquals(2.5, restored.getWeight());
            assertEquals(new Location(null, 1.5, 1, 2.5, 90, 15), restored.getSpawn());
            assertEquals(4, restored.getOrientations().size());
            assertEquals(1, restored.getConnectors().size());
            var copy = restored.getConnectors().getFirst();
            assertEquals(SimpleLocation.Direction.WEST, copy.getLocation().getDirection());
            assertEquals("entry", copy.getDoor().getNamespace());
            assertFalse(copy.getDoor().isStartOpen());
            assertTrue(copy.getDoor().isDisableSound());
            assertEquals(1, copy.getDoor().getLocations().size());
            assertEquals(
                    Set.of(new Location(null, 1, 1, 1)),
                    restored.getFunctionsMapRelative().keySet());
            var effect = restored.getFunctionsMapRelative().values().iterator().next();
            effect.setInstance(f.run);
            effect.runFunction(null, List.of());
            assertEquals("saved", f.run.getStatus());
        } finally {
            ConfigSerializableModel.pluginSerializables.clear();
            ConfigSerializableModel.pluginSerializables.addAll(models);
            ConfigurationSerialization.unregisterClass(alias);
            if (previous != null) ConfigurationSerialization.registerClass(previous, alias);
            ConfigurationSerialization.unregisterClass(targetAlias);
            if (previousTarget != null)
                ConfigurationSerialization.registerClass(previousTarget, targetAlias);
        }
    }
}
