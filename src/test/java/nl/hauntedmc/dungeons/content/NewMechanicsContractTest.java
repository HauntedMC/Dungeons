package nl.hauntedmc.dungeons.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.content.condition.*;
import nl.hauntedmc.dungeons.content.function.HealthFunction;
import nl.hauntedmc.dungeons.content.trigger.*;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NewMechanicsContractTest {
    @TempDir Path directory;

    private static ItemStack stack(Material material, int count) {
        var item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenReturn(count);
        return item;
    }

    @Test
    void timerUsesTicksRepeatsAndReleasesItsClock() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var function = mock(DungeonFunction.class);
            var timer =
                    new TimerTrigger(Map.of("delay", 7, "interval", 12, "allowRetrigger", true));
            timer.initializeFields();
            timer.enable(function, f.run);
            var clock = f.scheduler.jobs.getFirst();
            assertEquals(7, clock.delay);
            assertEquals(12, clock.period);
            clock.run();
            f.scheduler.immediate.getFirst().run();
            clock.run();
            f.scheduler.immediate.getLast().run();
            verify(function, times(2)).execute(any());
            timer.disable();
            assertTrue(clock.cancelled);
        }
    }

    @Test
    void oneShotTimerDisablesAfterSuccessfulDispatchAndClampsInvalidTimings() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var function = mock(DungeonFunction.class);
            var timer = new TimerTrigger(Map.of("delay", -3, "interval", 0));
            timer.initializeFields();
            timer.enable(function, f.run);
            var clock = f.scheduler.jobs.getFirst();
            assertEquals(0, clock.delay);
            assertEquals(1, clock.period);
            clock.run();
            f.scheduler.immediate.getFirst().run();
            assertTrue(clock.cancelled);
            clock.run();
            verify(function).execute(any());
        }
    }

    @Test
    void variableWatcherObservesEdgesIncludingRemovalWithoutFiringOnInitialState()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            f.run.getInstanceVariables().set("score", 1);
            var function = mock(DungeonFunction.class);
            var watcher = new VariableChangeTrigger(Map.of("allowRetrigger", true));
            watcher.initializeFields();
            watcher.enable(function, f.run);
            var clock = f.scheduler.jobs.getFirst();
            clock.run();
            assertTrue(f.scheduler.immediate.isEmpty());
            f.run.getInstanceVariables().set("score", 2);
            clock.run();
            f.scheduler.immediate.getLast().run();
            clock.run();
            assertEquals(1, f.scheduler.immediate.size());
            f.run.getInstanceVariables().set("score", null);
            clock.run();
            f.scheduler.immediate.getLast().run();
            verify(function, times(2)).execute(any());
            watcher.disable();
            assertTrue(clock.cancelled);
        }
    }

    @Test
    void clonedTimerAndWatcherNeverCancelTheOriginalTasks() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus()) {
            for (DungeonTrigger original :
                    List.of(new TimerTrigger(), new VariableChangeTrigger())) {
                original.enable(null, f.run);
                var clock = f.scheduler.jobs.getLast();
                var clone = original.clone();
                clone.disable();
                assertFalse(clock.cancelled);
                original.disable();
                assertTrue(clock.cancelled);
            }
        }
    }

    @Test
    void healthConditionUsesInclusiveBoundsAndRejectsMissingSourceAndInvalidRanges()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var source = mock(nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession.class);
            var player = mock(org.bukkit.entity.Player.class);
            when(source.getPlayer()).thenReturn(player);
            when(source.getInstance()).thenReturn(f.run);
            var event = new TriggerFireEvent(source, new RemoteTrigger());
            var condition = new PlayerHealthCondition(Map.of("minimum", 5, "maximum", 10));
            condition.initializeFields();
            condition.setInstance(f.run);
            for (double health : new double[] {4, 5, 7, 10, 11}) {
                when(player.getHealth()).thenReturn(health);
                assertEquals(health >= 5 && health <= 10, condition.check(event));
            }
            assertFalse(condition.check(null));
            assertFalse(condition.check(new TriggerFireEvent(f.run, new RemoteTrigger())));
            when(source.isObserving()).thenReturn(true);
            assertFalse(condition.check(event));
            for (Map<String, Object> config :
                    List.of(
                            Map.<String, Object>of("minimum", Double.NaN),
                            Map.<String, Object>of("minimum", 30, "maximum", 20))) {
                var invalid = new PlayerHealthCondition(config);
                invalid.initializeFields();
                invalid.setInstance(f.run);
                assertFalse(invalid.check(event));
            }
        }
    }

    @Test
    void inventoryCountsStorageStacksWithoutConsumingItems() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var source = mock(nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession.class);
            var player = mock(org.bukkit.entity.Player.class);
            var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
            when(source.getPlayer()).thenReturn(player);
            when(source.getInstance()).thenReturn(f.run);
            when(player.getInventory()).thenReturn(inventory);
            ItemStack[] contents = {
                stack(Material.TRIPWIRE_HOOK, 2),
                null,
                stack(Material.STONE, 10),
                stack(Material.TRIPWIRE_HOOK, 3)
            };
            when(inventory.getStorageContents()).thenReturn(contents);
            var event = new TriggerFireEvent(source, new RemoteTrigger());
            for (int required : new int[] {0, 5, 6, -1}) {
                var condition = new InventoryCondition(Map.of("count", required));
                condition.initializeFields();
                condition.setInstance(f.run);
                assertEquals(required >= 0 && required <= 5, condition.check(event));
                assertFalse(condition.check(null));
            }
            verify(inventory, never()).setStorageContents(any());
        }
    }

    @Test
    void healthFunctionSetsAddsClampsAndSkipsDeadForeignAndObservingPlayers() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var source = mock(nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession.class);
            var player = mock(org.bukkit.entity.Player.class);
            when(source.getPlayer()).thenReturn(player);
            when(source.getInstance()).thenReturn(f.run);
            f.paper
                    .entities
                    .when(() -> nl.hauntedmc.dungeons.util.entity.EntityUtils.getMaxHealth(player))
                    .thenReturn(40.0);
            when(player.getHealth()).thenReturn(10.0);
            for (double amount : new double[] {-30, 5, 100, Double.NaN}) {
                var function = new HealthFunction(Map.of("amount", amount, "relative", true));
                function.initializeFields();
                function.setInstance(f.run);
                clearInvocations(player);
                function.runFunction(null, List.of(source));
                if (Double.isNaN(amount)) verify(player, never()).setHealth(anyDouble());
                else verify(player).setHealth(Math.clamp(10 + amount, 0, 40));
            }
            var setter = new HealthFunction(Map.of("amount", 7));
            setter.initializeFields();
            setter.setInstance(f.run);
            setter.runFunction(null, List.of(source));
            verify(player).setHealth(7);
            clearInvocations(player);
            when(player.isDead()).thenReturn(true);
            setter.runFunction(null, List.of(source));
            when(player.isDead()).thenReturn(false);
            when(source.isObserving()).thenReturn(true);
            setter.runFunction(null, List.of(source));
            when(source.isObserving()).thenReturn(false);
            when(source.getInstance()).thenReturn(null);
            setter.runFunction(null, List.of(source));
            verify(player, never()).setHealth(anyDouble());
        }
    }

    @Test
    void conditionalGroupEvaluatesLiveStateAndMalformedComparisonsFailClosed() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var child = mock(DungeonFunction.class);
            var group =
                    new nl.hauntedmc.dungeons.content.function.meta.ConditionalFunction(
                            Map.of("comparison", "<score> >= 5"));
            group.initializeFields();
            group.setInstance(f.run);
            group.addFunction(child);
            var event = new TriggerFireEvent(f.run, new RemoteTrigger());
            var target = mock(nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession.class);
            when(target.getInstance()).thenReturn(f.run);
            var targets = List.of(target);
            f.run.getInstanceVariables().set("score", 4);
            group.runFunction(event, targets);
            verify(child, never()).runFunction(any(), any());
            f.run.getInstanceVariables().set("score", 5);
            group.runFunction(event, targets);
            verify(child).runFunction(eq(event), eq(targets));
            f.run.getInstanceVariables().set("score", "invalid");
            assertDoesNotThrow(() -> group.runFunction(event, targets));
            verify(child, times(1)).runFunction(eq(event), eq(targets));
        }
    }

    @Test
    void blockConditionUsesOffsetsAndNeverQueriesBlocksOutsideLoadedChunksOrWorldHeight()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var condition = new BlockCondition(Map.of("offsetX", 17, "offsetY", -1, "offsetZ", -1));
            condition.initializeFields();
            condition.setInstance(f.run);
            var origin = new org.bukkit.Location(f.world, 0, 70, 0);
            condition.setLocation(origin);
            when(f.world.getMinHeight()).thenReturn(-64);
            when(f.world.getMaxHeight()).thenReturn(320);
            assertFalse(condition.check(null));
            verify(f.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
            when(f.world.isChunkLoaded(1, -1)).thenReturn(true);
            var block = mock(org.bukkit.block.Block.class);
            when(f.world.getBlockAt(17, 69, -1)).thenReturn(block);
            when(block.getType()).thenReturn(Material.REDSTONE_BLOCK);
            assertTrue(condition.check(null));
            assertEquals(70, origin.getBlockY());
            when(block.getType()).thenReturn(Material.STONE);
            assertFalse(condition.check(null));
            condition.setLocation(new org.bukkit.Location(f.world, 0, -64, 0));
            assertFalse(condition.check(null));
            condition.setLocation(null);
            assertFalse(condition.check(null));
        }
    }
}
