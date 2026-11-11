package nl.hauntedmc.dungeons.runtime.instance;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FunctionStartupContractTest {
    @TempDir Path directory;

    @Test
    void failedEnableReleasesThePartialCloneAndDoesNotPreventOtherMechanicsFromStarting()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var _ = new HeadlessMenus()) {
            var bad = new FailingFunction();
            bad.setLocation(new Location(null, 1, 70, 0));
            var good = new SetStatusFunction(Map.of("status", "ready"));
            good.initializeFields();
            good.setLocation(new Location(null, 2, 70, 0));
            f.dungeon.getFunctions().put(bad.getLocation(), bad);
            f.dungeon.getFunctions().put(good.getLocation(), good);
            f.run.initializeFunctions();
            assertTrue(f.run.getFunctionListeners(FailingFunction.class).isEmpty());
            assertEquals(1, f.run.getFunctions().size());
            var clone = f.run.getFunctions().get(new Location(f.world, 2, 70, 0));
            assertNotSame(good, clone);
            assertSame(f.run, clone.getInstance());
            clone.runFunction(null, List.of());
            assertEquals("ready", f.run.getStatus());
            assertNull(good.getInstance());
            assertNull(good.getLocation().getWorld());
            f.run.onDispose();
            assertTrue(f.run.getFunctionListeners(SetStatusFunction.class).isEmpty());
        }
    }

    static class FailingFunction extends DungeonFunction {
        FailingFunction() {
            super("failed enable");
        }

        @Override
        public void onEnable() {
            throw new IllegalStateException("cannot initialize mechanic");
        }

        @Override
        public void runFunction(TriggerFireEvent event, List<DungeonPlayerSession> targets) {}

        @Override
        public MenuButton buildMenuButton() {
            return null;
        }

        @Override
        public void buildHotbarMenu() {}
    }
}
