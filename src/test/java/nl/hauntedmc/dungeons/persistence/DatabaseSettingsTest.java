package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import nl.hauntedmc.dungeons.util.config.ConfigSyncUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DatabaseSettingsTest {
    @Test void defaultsAreAddedToExistingConfigsWithoutReplacingTheConnection() throws Exception {
        try (var input = new InputStreamReader(getClass().getResourceAsStream("/config.yml"))) {
            var defaults = YamlConfiguration.loadConfiguration(input);
            var existing = new YamlConfiguration();
            existing.set("database.connection-id", "survival_rw");
            existing.set("database.enabled", true);
            assertTrue(ConfigSyncUtils.syncMissingAndObsolete(defaults, existing));
            var settings = DatabaseSettings.read(existing);
            assertTrue(settings.enabled());
            assertEquals("survival_rw", settings.connectionId());
            assertEquals(10, settings.shutdownTimeoutSeconds());
            assertTrue(existing.contains("runtime.server-id"));
        }
    }

    @Test void policyStaysWithDataProviderAndInvalidConsumerSettingsFailEarly() {
        var config = new YamlConfiguration();
        assertFalse(DatabaseSettings.read(config).enabled());
        assertEquals("player_data_rw", DatabaseSettings.read(config).connectionId());
        config.set("database.orm.schema-mode", "update");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.read(config))
                .getMessage().contains("DataProvider"));
        config.set("database.orm.schema-mode", null);
        for (Object value : java.util.List.of("true", 1)) {
            config.set("database.enabled", value);
            assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.read(config));
        }
        config.set("database.enabled", true);
        for (Object value : java.util.List.of(0, 61, "10")) {
            config.set("database.shutdown-timeout-seconds", value);
            assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.read(config));
        }
        config.set("database.shutdown-timeout-seconds", 10);
        for (Object value : java.util.List.of(" ", 42)) {
            config.set("database.connection-id", value);
            assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.read(config));
        }
    }
}
