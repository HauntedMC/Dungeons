package nl.hauntedmc.dungeons.registry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.content.condition.ChanceCondition;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

public class RegistryResourceContractTest {
    @TempDir Path directory;

    @Test
    void discoveryPrototypesDoNotAcquireEditorListeners() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var _ = new HeadlessMenus();
                var handlers = mockStatic(HandlerList.class)) {
            var runtime = f.paper.runtime;
            new FunctionRegistry(
                            f.paper.plugin,
                            runtime.activeInstances(),
                            runtime.elementEventHandler())
                    .register(SetStatusFunction.class);
            new TriggerRegistry(
                            f.paper.plugin,
                            runtime.activeInstances(),
                            runtime.elementEventHandler())
                    .register(RemoteTrigger.class);
            new ConditionRegistry(f.paper.plugin).register(ChanceCondition.class);
            new FunctionRegistry(
                            f.paper.plugin,
                            runtime.activeInstances(),
                            runtime.elementEventHandler())
                    .register(BrokenButton.class);
            verify(Bukkit.getPluginManager(), never()).registerEvents(any(), any());
            handlers.verifyNoInteractions();
        }
    }

    @Test
    void editorListenersAreAcquiredOnceAndClonesOwnSeparateRegistrations() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var _ = new HeadlessMenus();
                var handlers = mockStatic(HandlerList.class)) {
            var function = new SetStatusFunction();
            function.initialize();
            function.initializeMenu();
            var clone = function.clone();
            assertNotNull(clone);
            var listeners = ArgumentCaptor.forClass(Listener.class);
            verify(Bukkit.getPluginManager(), times(2))
                    .registerEvents(listeners.capture(), eq(f.paper.plugin));
            assertNotSame(listeners.getAllValues().get(0), listeners.getAllValues().get(1));
            function.releaseEditorListener();
            function.releaseEditorListener();
            clone.releaseEditorListener();
            for (var listener : listeners.getAllValues())
                handlers.verify(() -> HandlerList.unregisterAll(listener), times(1));
        }
    }

    @AutoRegister(id = "test.broken-menu")
    public static class BrokenButton extends SetStatusFunction {
        public BrokenButton() {}

        @Override
        public MenuButton buildMenuButton() {
            throw new IllegalStateException("editor metadata failed");
        }
    }
}
