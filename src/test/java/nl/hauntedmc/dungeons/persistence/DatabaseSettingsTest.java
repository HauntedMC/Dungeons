package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStreamReader;
import java.util.List;
import nl.hauntedmc.dungeons.config.ConfigSyncUtils;
import nl.hauntedmc.dungeons.config.PluginConfigView;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class DatabaseSettingsTest {
    @Test
    void defaultsAreAddedToExistingConfigsWithoutReplacingTheConnection() throws Exception {
        try (var input = new InputStreamReader(getClass().getResourceAsStream("/config.yml"))) {
            var defaults = YamlConfiguration.loadConfiguration(input);
            var existing = new YamlConfiguration();
            existing.set("database.connection-id", "survival_rw");
            existing.set("database.enabled", false);
            assertTrue(ConfigSyncUtils.syncMissingAndObsolete(defaults, existing));
            var settings = DatabaseSettings.read(existing);
            assertFalse(existing.contains("database.enabled"));
            assertEquals(30, settings.startupTimeoutSeconds());
            assertEquals("survival_rw", settings.connectionId());
            assertEquals(10, settings.shutdownTimeoutSeconds());
            assertTrue(existing.contains("runtime.server-id"));
        }
    }

    @Test
    void inheritedBukkitDefaultsAreMaterializedBeforeSavingPartialDatabaseConfiguration()
            throws Exception {
        try (var input = new InputStreamReader(getClass().getResourceAsStream("/config.yml"))) {
            var defaults = YamlConfiguration.loadConfiguration(input);
            var existing = new YamlConfiguration();
            existing.loadFromString(
                    "database:\n  connection-id: custom_rw\nruntime:\n  server-id: survival\n");
            existing.setDefaults(defaults);
            ConfigSyncUtils.syncMissingAndObsolete(defaults, existing);
            boolean metadataEnabled =
                    PluginConfigView.isAccessKeyMetadataValidationEnabled(existing);
            assertTrue(metadataEnabled);
            var restarted = new YamlConfiguration();
            restarted.loadFromString(existing.saveToString());
            assertTrue(restarted.getBoolean("access.keys.metadata_validation_enabled"));
            assertEquals(1000, restarted.getInt("database.operation-timeout-ms"));
            assertEquals("custom_rw", restarted.getString("database.connection-id"));
        }
    }

    @Test
    void policyStaysWithDataProviderAndInvalidConsumerSettingsFailEarly() {
        var config = new YamlConfiguration();
        assertEquals(1000, DatabaseSettings.read(config).operationTimeoutMillis());
        assertEquals("player_data_rw", DatabaseSettings.read(config).connectionId());
        config.set("database.orm.schema-mode", "update");
        assertTrue(
                assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.read(config))
                        .getMessage()
                        .contains("DataProvider"));
        config.set("database.orm.schema-mode", null);
        for (Object value : List.of(0, 61, "10")) {
            config.set("database.shutdown-timeout-seconds", value);
            assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.read(config));
        }
        config.set("database.shutdown-timeout-seconds", 10);
        for (String key :
                List.of("database.startup-timeout-seconds", "database.operation-timeout-ms")) {
            for (Object value : List.of(0, 100000, "10")) {
                config.set(key, value);
                assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.read(config));
            }
            config.set(key, null);
        }
        for (Object value : List.of(" ", 42)) {
            config.set("database.connection-id", value);
            assertThrows(IllegalArgumentException.class, () -> DatabaseSettings.read(config));
        }
    }
}
