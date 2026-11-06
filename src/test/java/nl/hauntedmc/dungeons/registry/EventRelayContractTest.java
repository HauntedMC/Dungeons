package nl.hauntedmc.dungeons.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.*;
import org.bukkit.plugin.EventExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.*;

public class EventRelayContractTest {
    @TempDir Path directory;

    @Test
    void reflectiveFunctionRelayHonorsRegistrationAndSnapshotsWhileIsolatingHandlerFailures()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus()) {
            var runtime = f.paper.runtime;
            var relay = runtime.elementEventHandler();
            var registry = new FunctionRegistry(f.paper.plugin, runtime.activeInstances(), relay);
            registry.register(ObservedFunction.class);
            var executor = ArgumentCaptor.forClass(EventExecutor.class);
            verify(Bukkit.getPluginManager())
                    .registerEvent(
                            eq(DungeonStartEvent.class),
                            eq(relay),
                            eq(EventPriority.HIGH),
                            executor.capture(),
                            eq(f.paper.plugin));
            var other = new RunFixture.Run(f.dungeon, f.world);
            runtime.activeInstances().registerActiveInstance(f.run);
            runtime.activeInstances().registerActiveInstance(other);
            List<String> calls = new ArrayList<>();
            var bad = new ObservedFunction();
            bad.name = "bad";
            bad.calls = calls;
            bad.fail = true;
            bad.enable(f.run, null);
            var remover = new ObservedFunction();
            remover.name = "remove";
            remover.calls = calls;
            remover.enable(f.run, null);
            var healthy = new ObservedFunction();
            healthy.name = "healthy";
            healthy.calls = calls;
            healthy.enable(f.run, null);
            var next = new ObservedFunction();
            next.name = "other";
            next.calls = calls;
            next.enable(other, null);
            remover.action =
                    () -> {
                        runtime.activeInstances().unregisterActiveInstance(f.run);
                        remover.unregister();
                        healthy.unregister();
                    };
            executor.getValue().execute(relay, new DungeonStartEvent(f.run, List.of()));
            assertEquals(List.of("bad", "remove", "healthy", "other"), calls);
            executor.getValue().execute(relay, new DungeonStartEvent(other, List.of()));
            assertEquals(List.of("bad", "remove", "healthy", "other", "other"), calls);
            next.disable();
            assertTrue(other.getFunctionListeners(ObservedFunction.class).isEmpty());
        }
    }

    @AutoRegister(id = "test.observed-function")
    public static class ObservedFunction extends SetStatusFunction {
        String name = "prototype";
        List<String> calls = new ArrayList<>();
        boolean fail;
        Runnable action = () -> {};

        public ObservedFunction() {}

        @EventHandler(priority = EventPriority.HIGH)
        public void onStart(DungeonStartEvent event) {
            calls.add(name);
            if (fail) throw new IllegalStateException("handler failed");
            action.run();
        }
    }
}
