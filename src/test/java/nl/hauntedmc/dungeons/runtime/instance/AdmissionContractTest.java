package nl.hauntedmc.dungeons.runtime.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AdmissionContractTest {
    @TempDir Path directory;

    @Test
    void realDungeonAdmissionHonorsIndependentGlobalTeamAndPerDungeonLimits() throws Exception {
        try (var paper = new PaperBoundary()) {
            var settings = new YamlConfiguration();
            settings.set("team.max_size", 2);
            settings.set("runs.max_active", 1);
            var dungeon = paper.dungeon(directory.resolve("crypt"), settings);
            assertTrue(dungeon.canAcceptStartNow(2, "", 0, 1));
            assertFalse(dungeon.canAcceptStartNow(3, "", 0, 1));
            assertFalse(dungeon.canAcceptStartNow(1, "", 1, 1));
            assertTrue(dungeon.canAcceptStartNow(1, "", 999, 0));
            dungeon.getInstances().add(new StaticInstance(dungeon, null));
            assertFalse(dungeon.canAcceptStartNow(1, "", 0, 100));
            dungeon.getConfig().set("runs.max_active", 0);
            assertTrue(dungeon.canAcceptStartNow(1, "", 0, 100));
            dungeon.getConfig().set("dungeon.enabled", false);
            dungeon.refreshRuntimeSettings();
            assertFalse(dungeon.canAcceptStartNow(1, "", 0, 0));
        }
    }
}
