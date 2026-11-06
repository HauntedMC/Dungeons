package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.*;

import nl.hauntedmc.dungeons.content.condition.*;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.function.meta.*;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.model.element.*;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class MechanicGraphPersistenceContractTest {
    @TempDir Path directory;

    @Test
    void
            yamlGraphsRestoreNestedMechanicsAndTheirClonesExecuteWithIndependentParentsConditionsAndTimers()
                    throws Exception {
        try (var aliases = new StaticMapSnapshot(ConfigurationSerialization.class, "aliases");
                var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus()) {
            for (var type :
                    List.of(
                            CompositeFunction.class,
                            DelayedFunction.class,
                            RepeaterFunction.class,
                            SetStatusFunction.class,
                            RemoteTrigger.class,
                            StatusCondition.class,
                            ChanceCondition.class,
                            FunctionTargetType.class)) {
                TypeMetadataSupport.registerConfigurationSerializable(type);
            }
            var root = new CompositeFunction();
            root.setLocation(new Location(null, -4, 70, 8));
            root.setTrigger(
                    new RemoteTrigger(
                            Map.of(
                                    "triggerName",
                                    "puzzle",
                                    "awaitConditions",
                                    false,
                                    "conditions",
                                    List.of(
                                            new StatusCondition(Map.of("status", "armed")),
                                            new ChanceCondition(
                                                    Map.of("chance", 0.0, "inverted", true))))));
            root.addFunction(new SetStatusFunction(Map.of("status", "intermediate")));
            var repeatedStatus =
                    new RepeaterFunction(
                            Map.of(
                                    "interval",
                                    2,
                                    "maxRepeats",
                                    1,
                                    "function",
                                    new SetStatusFunction(Map.of("status", "done"))));
            root.addFunction(new DelayedFunction(Map.of("delay", 5, "function", repeatedStatus)));
            root.initialize();
            var saved = new ConfigurationFile(f.paper.plugin);
            saved.set("Functions", List.of(root));
            var file = directory.resolve("graph.yml").toFile();
            assertTrue(saved.saveAsync(file).join());
            var loaded = new ConfigurationFile(f.paper.plugin);
            loaded.load(file);
            var restored =
                    assertInstanceOf(
                            CompositeFunction.class, loaded.getList("Functions").getFirst());
            restored.initialize();
            var delayed =
                    assertInstanceOf(DelayedFunction.class, restored.getFunctions().getLast());
            assertEquals(5, delayed.serialize().get("delay"));
            var repeater = (RepeaterFunction) delayed.serialize().get("function");
            assertSame(delayed, repeater.getParentFunction());
            assertSame(
                    repeater,
                    ((DungeonFunction) repeater.serialize().get("function")).getParentFunction());
            var copy = restored.clone();
            assertNotNull(copy);
            assertSame(copy, copy.getFunctions().getFirst().getParentFunction());
            var copiedDelay = (DelayedFunction) copy.getFunctions().getLast();
            var copiedRepeater = (RepeaterFunction) copiedDelay.serialize().get("function");
            assertSame(copiedDelay, copiedRepeater.getParentFunction());
            assertSame(
                    copiedRepeater,
                    ((DungeonFunction) copiedRepeater.serialize().get("function"))
                            .getParentFunction());
            assertNotSame(
                    restored.getTrigger().getConditions().getFirst(),
                    copy.getTrigger().getConditions().getFirst());
            var otherRun = new RunFixture.Run(f.dungeon, f.world);
            restored.enable(f.run, new Location(f.world, -4, 70, 8));
            copy.enable(otherRun, new Location(f.world, 4, 70, 8));
            f.run.setStatus("armed");
            otherRun.setStatus("armed");
            restored.getTrigger().trigger();
            f.scheduler.immediate.getLast().run();
            assertEquals("intermediate", f.run.getStatus());
            assertEquals("armed", otherRun.getStatus());
            var pending = f.scheduler.jobs.getLast();
            assertEquals(5, pending.delay);
            copy.disable();
            assertFalse(pending.cancelled);
            pending.run();
            var repeating = f.scheduler.jobs.getLast();
            assertEquals(2, repeating.period);
            repeating.run();
            repeating.run();
            assertTrue(repeating.cancelled);
            assertEquals("done", f.run.getStatus());
            assertEquals("armed", otherRun.getStatus());
            restored.disable();
            assertTrue(f.run.getFunctionListeners(SetStatusFunction.class).isEmpty());
        }
    }
}
