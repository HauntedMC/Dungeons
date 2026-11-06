package nl.hauntedmc.dungeons.registry;

import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.condition.ChanceCondition;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;

public class RegistryResourceContractTest {
    @TempDir Path directory;

    @Test
    void discoveryPrototypesReleaseTheirEditorListenersAfterTheirMetadataHasBeenCollected()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus();
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
            var listeners = ArgumentCaptor.forClass(Listener.class);
            verify(Bukkit.getPluginManager(), times(4))
                    .registerEvents(listeners.capture(), eq(f.paper.plugin));
            for (var listener : listeners.getAllValues())
                handlers.verify(() -> HandlerList.unregisterAll(listener));
        }
    }

    @nl.hauntedmc.dungeons.annotation.AutoRegister(id = "test.broken-menu")
    public static class BrokenButton extends SetStatusFunction {
        public BrokenButton() {}

        @Override
        public nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton buildMenuButton() {
            throw new IllegalStateException("editor metadata failed");
        }
    }
}
