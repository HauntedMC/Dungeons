package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

class RegistryContractTest {
    @Test
    void startupDiscoversRoomGraphModelsOutsideThePluginEntryPointPackage() {
        var previous = new LinkedHashSet<>(ConfigSerializableModel.pluginSerializables);
        try (var paper = new PaperBoundary()) {
            ConfigSerializableModel.pluginSerializables.clear();
            new RegistryBootstrap(paper.runtime).registerElementSerialization();
            assertTrue(
                    ConfigSerializableModel.pluginSerializables.containsAll(
                            Set.of(
                                    Connector.class,
                                    ConnectorDoor.class,
                                    WhitelistEntry.class,
                                    SimpleLocation.class)));
            var original = new Connector(new SimpleLocation(-8, 72, 15));
            var copy =
                    ConfigSerializableModel.deserialize(
                            Connector.class, ConfigSerializableModel.serializeWithTypeId(original));
            assertNotNull(copy);
            assertEquals(original.getLocation(), copy.getLocation());
        } finally {
            ConfigSerializableModel.pluginSerializables.clear();
            ConfigSerializableModel.pluginSerializables.addAll(previous);
        }
    }
}
