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
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

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

    @Test
    void bootstrapRegistersEveryManifestTypeWithItsSerializerEditorMetadataAndRuntimeRegistry()
            throws Exception {
        try (var aliases =
                        new nl.hauntedmc.dungeons.support.StaticMapSnapshot(
                                org.bukkit.configuration.serialization.ConfigurationSerialization
                                        .class,
                                "aliases");
                var layouts =
                        new nl.hauntedmc.dungeons.support.StaticMapSnapshot(
                                nl.hauntedmc.dungeons.registry.LayoutRegistry.class, "layouts");
                var dungeons =
                        new nl.hauntedmc.dungeons.support.StaticMapSnapshot(
                                nl.hauntedmc.dungeons.registry.DungeonTypeRegistry.class, "types");
                var f =
                        new nl.hauntedmc.dungeons.support.RunFixture(
                                directory, new org.bukkit.configuration.file.YamlConfiguration());
                var menus = new nl.hauntedmc.dungeons.support.HeadlessMenus();
                var gui = new nl.hauntedmc.dungeons.support.HeadlessInventoryMenus()) {
            nl.hauntedmc.dungeons.registry.LayoutRegistry.clear();
            nl.hauntedmc.dungeons.registry.DungeonTypeRegistry.clear();
            var runtime = f.paper.runtime;
            runtime.setFunctionRegistry(
                    new nl.hauntedmc.dungeons.registry.FunctionRegistry(
                            f.paper.plugin,
                            runtime.activeInstances(),
                            runtime.elementEventHandler()));
            runtime.setTriggerRegistry(
                    new nl.hauntedmc.dungeons.registry.TriggerRegistry(
                            f.paper.plugin,
                            runtime.activeInstances(),
                            runtime.elementEventHandler()));
            runtime.setConditionRegistry(
                    new nl.hauntedmc.dungeons.registry.ConditionRegistry(f.paper.plugin));
            var bootstrap = new RegistryBootstrap(runtime);
            bootstrap.registerConfigurationSerializers();
            bootstrap.registerRuntimeTypes();
            Set<Class<?>> registered = new java.util.HashSet<>();
            registered.addAll(runtime.functionRegistry().getRegisteredFunctions());
            registered.addAll(runtime.triggerRegistry().getRegisteredTriggers());
            registered.addAll(runtime.conditionRegistry().getRegisteredConditions());
            try (var input = getClass().getResourceAsStream("/contracts/mechanics.csv")) {
                assertNotNull(input);
                var entries =
                        new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                                .lines()
                                .skip(1)
                                .toList();
                assertEquals(entries.size(), registered.size());
                for (String row : entries) {
                    var columns = row.split(",", 2);
                    var type = Class.forName(columns[1]);
                    assertTrue(registered.contains(type), columns[1]);
                    assertEquals(
                            type,
                            org.bukkit.configuration.serialization.ConfigurationSerialization
                                    .getClassByAlias(columns[0]));
                    assertTrue(
                            runtime.functionRegistry().getFunctionButtons().containsKey(columns[0])
                                    || runtime.triggerRegistry()
                                            .getTriggerButtons()
                                            .containsKey(columns[0])
                                    || runtime.conditionRegistry()
                                            .getConditionButtons()
                                            .containsKey(columns[0]),
                            columns[1]);
                }
            }
            assertEquals(
                    Set.of("static", "open", "branching"),
                    nl.hauntedmc.dungeons.registry.DungeonTypeRegistry.getDungeonTypes().keySet());
            assertNotNull(
                    org.bukkit.configuration.serialization.ConfigurationSerialization
                            .getClassByAlias("dungeons.function_target_type"));
        }
    }
}
