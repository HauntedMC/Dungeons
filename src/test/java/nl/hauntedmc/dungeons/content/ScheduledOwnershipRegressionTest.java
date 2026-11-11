package nl.hauntedmc.dungeons.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.content.function.variable.InstanceVariableFunction;
import nl.hauntedmc.dungeons.content.trigger.DungeonStartTrigger;
import nl.hauntedmc.dungeons.content.variable.VariableEditMode;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class ScheduledOwnershipRegressionTest {
    @TempDir Path directory;

    @Test
    void disablingVariableFunctionsCancelsTheirDeferredWritesAndClonesOwnNoOriginalTasks()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus()) {
            var function =
                    new InstanceVariableFunction(Map.of("varName", "score", "varValue", "5"));
            function.initializeFields();
            function.setInstance(f.run);
            function.runFunction(null, List.of());
            var pending = f.scheduler.jobs.getFirst();
            function.clone().disable();
            assertFalse(pending.cancelled);
            function.disable();
            assertTrue(pending.cancelled);
            pending.run();
            assertNull(f.run.getInstanceVariables().getString("score"));
        }
    }

    @Test
    void deferredVariableWritesCaptureTheirOriginalConfiguration() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var config = new HashMap<String, Object>(Map.of("varName", "score", "varValue", "5"));
            var function = new InstanceVariableFunction(config);
            function.initializeFields();
            function.setInstance(f.run);
            function.runFunction(null, List.of());
            config.put("varValue", "99");
            function.initializeFields();
            f.scheduler.jobs.getFirst().run();
            assertEquals("5", f.run.getInstanceVariables().getString("score"));
        }
    }

    @Test
    void missingNamesAndNonFiniteArithmeticNeverScheduleMutations() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            for (String value : List.of("NaN", "Infinity", "-Infinity", "text")) {
                var function =
                        new InstanceVariableFunction(
                                Map.of(
                                        "varName",
                                        "score",
                                        "varValue",
                                        value,
                                        "mode",
                                        VariableEditMode.ADD));
                function.initializeFields();
                function.setInstance(f.run);
                function.runFunction(null, List.of());
            }
            var missing = new InstanceVariableFunction();
            missing.setInstance(f.run);
            missing.runFunction(null, List.of());
            assertTrue(f.scheduler.jobs.isEmpty());
        }
    }

    @Test
    void startCallbacksSelectARemainingPlayerWhenTheOriginalFirstPlayerLeft() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var first = mock(DungeonPlayerSession.class);
            var second = mock(DungeonPlayerSession.class);
            when(first.getInstance()).thenReturn(f.run);
            when(second.getInstance()).thenReturn(f.run);
            var function = mock(DungeonFunction.class);
            var trigger = new DungeonStartTrigger();
            trigger.enable(function, f.run);
            trigger.onDungeonStart(new DungeonStartEvent(f.run, List.of(first, second)));
            when(first.getInstance()).thenReturn(null);
            f.scheduler.jobs.getFirst().run();
            f.scheduler.immediate.getFirst().run();
            var event = ArgumentCaptor.forClass(TriggerFireEvent.class);
            verify(function).execute(event.capture());
            assertSame(second, event.getValue().getPlayerSession());
        }
    }

    @Test
    void startCallbacksAreCancelledOnDisableAndNotSharedByClones() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus()) {
            var source = mock(DungeonPlayerSession.class);
            when(source.getInstance()).thenReturn(f.run);
            var trigger = new DungeonStartTrigger();
            trigger.enable(null, f.run);
            trigger.onDungeonStart(new DungeonStartEvent(f.run, List.of(source)));
            var pending = f.scheduler.jobs.getFirst();
            trigger.clone().disable();
            assertFalse(pending.cancelled);
            trigger.disable();
            assertTrue(pending.cancelled);
            pending.run();
            assertTrue(f.scheduler.immediate.isEmpty());
        }
    }
}
