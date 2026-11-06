package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.registry.ConditionRegistry;
import nl.hauntedmc.dungeons.registry.DungeonTypeRegistry;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.registry.LayoutRegistry;
import nl.hauntedmc.dungeons.registry.TriggerRegistry;
import nl.hauntedmc.dungeons.support.HeadlessInventoryMenus;
import nl.hauntedmc.dungeons.support.HeadlessMenus;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.RunFixture;
import nl.hauntedmc.dungeons.support.StaticMapSnapshot;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

class RegistryContractTest {
    @TempDir Path directory;

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
        try (var aliases = new StaticMapSnapshot(ConfigurationSerialization.class, "aliases");
                var layouts = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
                var dungeons = new StaticMapSnapshot(DungeonTypeRegistry.class, "types");
                var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus();
                var gui = new HeadlessInventoryMenus()) {
            LayoutRegistry.clear();
            DungeonTypeRegistry.clear();
            var runtime = f.paper.runtime;
            runtime.setFunctionRegistry(
                    new FunctionRegistry(
                            f.paper.plugin,
                            runtime.activeInstances(),
                            runtime.elementEventHandler()));
            runtime.setTriggerRegistry(
                    new TriggerRegistry(
                            f.paper.plugin,
                            runtime.activeInstances(),
                            runtime.elementEventHandler()));
            runtime.setConditionRegistry(new ConditionRegistry(f.paper.plugin));
            var bootstrap = new RegistryBootstrap(runtime);
            bootstrap.registerConfigurationSerializers();
            bootstrap.registerRuntimeTypes();
            Set<Class<?>> registered = new HashSet<>();
            registered.addAll(runtime.functionRegistry().getRegisteredFunctions());
            registered.addAll(runtime.triggerRegistry().getRegisteredTriggers());
            registered.addAll(runtime.conditionRegistry().getRegisteredConditions());
            try (var input = getClass().getResourceAsStream("/contracts/mechanics.csv")) {
                assertNotNull(input);
                var entries =
                        new String(input.readAllBytes(), StandardCharsets.UTF_8)
                                .lines()
                                .skip(1)
                                .toList();
                assertEquals(entries.size(), registered.size());
                for (String row : entries) {
                    var columns = row.split(",", 2);
                    var type = Class.forName(columns[1]);
                    assertTrue(registered.contains(type), columns[1]);
                    assertEquals(type, ConfigurationSerialization.getClassByAlias(columns[0]));
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
                    DungeonTypeRegistry.getDungeonTypes().keySet());
            assertNotNull(
                    ConfigurationSerialization.getClassByAlias("dungeons.function_target_type"));
        }
    }
}
