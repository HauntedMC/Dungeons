package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.ConnectorDoor;
import nl.hauntedmc.dungeons.generation.room.WhitelistEntry;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
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

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.EventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

class RegistryContractTest {
    @TempDir Path directory;

    @Test
    void dungeonAliasesAreIndependentOfTheServerLocale() throws Exception {
        var previousLocale = java.util.Locale.getDefault();
        try (var registry = new StaticMapSnapshot(DungeonTypeRegistry.class, "types")) {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            DungeonTypeRegistry.register(nl.hauntedmc.dungeons.content.dungeon.StaticDungeon.class,
                    "STATIC", "INSTANCE");
            assertTrue(DungeonTypeRegistry.getDungeonTypes().containsKey("static"));
            assertTrue(DungeonTypeRegistry.getDungeonTypes().containsKey("instance"));
        } finally {
            java.util.Locale.setDefault(previousLocale);
        }
    }

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
    void registeredInstanceRelayContinuesAfterFailureAndMutationAndFiltersListenerTypes()
            throws Exception {
        var rules = new YamlConfiguration();
        rules.set("rules.building.break_blocks", false);
        try (var aliases = new StaticMapSnapshot(ConfigurationSerialization.class, "aliases");
                var layouts = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
                var dungeons = new StaticMapSnapshot(DungeonTypeRegistry.class, "types");
                var f = new RunFixture(directory, rules);
                var menus = new HeadlessMenus();
                var gui = new HeadlessInventoryMenus()) {
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
            new RegistryBootstrap(runtime).registerRuntimeTypes();
            var executor = ArgumentCaptor.forClass(EventExecutor.class);
            verify(Bukkit.getPluginManager())
                    .registerEvent(
                            eq(BlockBreakEvent.class),
                            eq(runtime.instanceEventHandler()),
                            eq(EventPriority.NORMAL),
                            executor.capture(),
                            eq(f.paper.plugin));
            var otherWorld = mock(World.class);
            var healthy = new RunFixture.Run(f.dungeon, otherWorld);
            runtime.activeInstances().registerActiveInstance(f.run);
            runtime.activeInstances().registerActiveInstance(new EditableInstance(f.dungeon, null));
            runtime.activeInstances()
                    .registerActiveInstance(
                            new DungeonInstance(f.dungeon, null) {
                                @Override
                                public void onDispose() {}
                            });
            runtime.activeInstances().registerActiveInstance(healthy);
            var block = mock(Block.class);
            when(block.getWorld())
                    .thenAnswer(
                            c -> {
                                runtime.activeInstances().unregisterActiveInstance(f.run);
                                throw new IllegalStateException(
                                        "first listener failed after removal");
                            })
                    .thenReturn(otherWorld);
            var event = new BlockBreakEvent(block, mock(Player.class));
            executor.getValue().execute(runtime.instanceEventHandler(), event);
            assertTrue(event.isCancelled(), "The healthy run must still enforce its block rule");
            verify(block, times(2)).getWorld();
            assertFalse(runtime.activeInstances().getActiveInstances().contains(f.run));
            event.setCancelled(false);
            executor.getValue().execute(runtime.instanceEventHandler(), event);
            assertTrue(event.isCancelled());
            verify(block, times(3)).getWorld();
            executor.getValue()
                    .execute(
                            runtime.instanceEventHandler(),
                            new DungeonStartEvent(healthy, java.util.List.of()));
            verify(block, times(3)).getWorld();
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
