package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import nl.hauntedmc.dungeons.util.config.ConfigSyncUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DungeonIdentityTest {
    @TempDir Path directory;

    @Test
    void assignsOnceAndPreservesKeyIdentityAndCustomConfiguration() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("dungeon:\n  type: static\naccess:\n  keys:\n    items:\n      - KeyId: existing-key\n");
        var path = directory.resolve("config.yml");
        var plugin = mock(Plugin.class);
        UUID assigned = DungeonIdentity.initialize(plugin, config, path.toFile());
        var reloaded = new YamlConfiguration();
        reloaded.load(path.toFile());
        assertEquals(assigned, DungeonIdentity.initialize(plugin, reloaded, path.toFile()));
        assertEquals("existing-key", reloaded.getMapList("access.keys.items").getFirst().get("KeyId"));
        var defaults = new YamlConfiguration();
        defaults.set("dungeon.type", "static");
        defaults.set("dungeon.id", "");
        reloaded.set("statistics.custom_metrics.score.unit", "points");
        ConfigSyncUtils.syncMissingAndObsolete(defaults, reloaded,
                Set.of("dungeon.id", "statistics.custom_metrics"));
        assertEquals(assigned.toString(), reloaded.getString("dungeon.id"));
        assertEquals("points", reloaded.getString("statistics.custom_metrics.score.unit"));
    }

    @Test
    void eachBlankTemplateGetsAnIndependentId() {
        var plugin = mock(Plugin.class);
        var first = new YamlConfiguration();
        var second = new YamlConfiguration();
        first.set("dungeon.id", "");
        second.set("dungeon.id", "");
        assertNotEquals(DungeonIdentity.initialize(plugin, first, directory.resolve("first.yml").toFile()),
                DungeonIdentity.initialize(plugin, second, directory.resolve("second.yml").toFile()));
    }

    @Test
    void invalidIdentityIsNeverReplaced() throws Exception {
        var plugin = mock(Plugin.class);
        for (Object malformed : new Object[] {"invalid", "1-1-1-1-1", 123}) {
            var config = new YamlConfiguration();
            config.set("dungeon.id", malformed);
            var path = directory.resolve("invalid.yml");
            Files.writeString(path, "preserve-me");
            assertThrows(IllegalArgumentException.class,
                    () -> DungeonIdentity.initialize(plugin, config, path.toFile()));
            assertEquals("preserve-me", Files.readString(path));
        }
    }

    @Test
    void failedSaveDoesNotPublishAssignedIdentity() throws Exception {
        Path parentFile = directory.resolve("not-a-directory");
        Files.writeString(parentFile, "preserve-me");
        var config = new YamlConfiguration();
        assertThrows(RuntimeException.class, () -> DungeonIdentity.initialize(mock(Plugin.class), config,
                parentFile.resolve("config.yml").toFile()));
        assertNull(config.get("dungeon.id"));
        assertEquals("preserve-me", Files.readString(parentFile));
    }
}
