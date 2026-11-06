package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DungeonGraphPersistenceTest {
    @TempDir Path directory;

    @Test
    void diskRoundTripRestoresConnectorGraphAndEditsDoNotMutateTheOriginal() {
        var previous = new LinkedHashSet<>(ConfigSerializableModel.pluginSerializables);
        try (var paper = new PaperBoundary()) {
            registerModels();
            Connector original = connector();
            var stored = new ConfigurationFile(paper.plugin);
            stored.set("Connectors", List.of(original));
            var file = directory.resolve("room.yml").toFile();
            assertTrue(stored.saveAsync(file).join());
            var loaded = new ConfigurationFile(paper.plugin);
            loaded.load(file);
            List<Connector> restored = loaded.getListOf(Connector.class, "Connectors");

            assertEquals(1, restored.size());
            Connector copy = restored.getFirst();
            assertEquals(original.getLocation(), copy.getLocation());
            assertEquals(0.75, copy.getSuccessChance());
            assertEquals("exit", copy.getDoor().getNamespace());
            assertFalse(copy.getDoor().isStartOpen());
            assertTrue(copy.getDoor().isDisableSound());
            assertEquals(original.getDoor().getLocations(), copy.getDoor().getLocations());
            copy.getLocation().setX(999);
            copy.getDoor().getLocations().clear();
            assertEquals(-4, original.getLocation().getX());
            assertEquals(1, original.getDoor().getLocations().size());
        } finally {
            ConfigSerializableModel.pluginSerializables.clear();
            ConfigSerializableModel.pluginSerializables.addAll(previous);
        }
    }

    @Test
    void malformedAndUnknownTypeRecordsCannotDiscardValidNeighbors() {
        var previous = new LinkedHashSet<>(ConfigSerializableModel.pluginSerializables);
        try (var paper = new PaperBoundary()) {
            registerModels();
            var valid = ConfigSerializableModel.serializeWithTypeId(connector());
            var invalidType = new java.util.HashMap<String, Object>(valid);
            invalidType.put("TYPE--", 42);
            var unknownType = new java.util.HashMap<String, Object>(valid);
            unknownType.put("TYPE--", "not.installed.type");
            var stored = new ConfigurationFile(paper.plugin);
            stored.set("Connectors", List.of(valid, invalidType, unknownType, "bad record", valid));
            var file = directory.resolve("mixed.yml").toFile();
            assertTrue(stored.saveAsync(file).join());
            var loaded = new ConfigurationFile(paper.plugin);
            loaded.load(file);
            assertEquals(2, loaded.getListOf(Connector.class, "Connectors").size());
        } finally {
            ConfigSerializableModel.pluginSerializables.clear();
            ConfigSerializableModel.pluginSerializables.addAll(previous);
        }
    }

    static void registerModels() {
        ConfigSerializableModel.pluginSerializables.addAll(Set.of(Connector.class, ConnectorDoor.class, SimpleLocation.class, WhitelistEntry.class));
    }

    static Connector connector() {
        Connector connector = new Connector(new SimpleLocation(-4, 70, 9, SimpleLocation.Direction.WEST));
        connector.setSuccessChance(0.75);
        connector.getDoor().setNamespace("exit");
        connector.getDoor().setStartOpen(false);
        connector.getDoor().setDisableSound(true);
        connector.getDoor().addLocation(new SimpleLocation(-4, 71, 9));
        return connector;
    }
}
