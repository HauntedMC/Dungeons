package nl.hauntedmc.dungeons.persistence;

import org.bukkit.configuration.ConfigurationSection;

/** Startup-only consumer settings. DataProvider owns credentials, pools and ORM schema policy. */
public record DatabaseSettings(boolean enabled, String connectionId, int shutdownTimeoutSeconds) {
    public static DatabaseSettings read(ConfigurationSection config) {
        if (config.contains("database.orm.schema-mode") || config.contains("database.schema-mode"))
            throw new IllegalArgumentException("Configure orm.schema_mode in DataProvider; remove the ignored Dungeons schema-mode override");
        if (config.contains("database.enabled") && !config.isBoolean("database.enabled"))
            throw new IllegalArgumentException("database.enabled must be a boolean");
        if (config.contains("database.connection-id") && !config.isString("database.connection-id"))
            throw new IllegalArgumentException("database.connection-id must be a named DataProvider connection");
        if (config.contains("runtime.server-id") && !config.isString("runtime.server-id"))
            throw new IllegalArgumentException("runtime.server-id must be a string");
        String connection = config.getString("database.connection-id", "player_data_rw").trim();
        if (connection.isEmpty()) throw new IllegalArgumentException("database.connection-id must not be blank");
        if (config.contains("database.shutdown-timeout-seconds") && !config.isInt("database.shutdown-timeout-seconds"))
            throw new IllegalArgumentException("database.shutdown-timeout-seconds must be an integer");
        int timeout = config.getInt("database.shutdown-timeout-seconds", 10);
        if (timeout < 1 || timeout > 60)
            throw new IllegalArgumentException("database.shutdown-timeout-seconds must be between 1 and 60");
        return new DatabaseSettings(config.getBoolean("database.enabled", false), connection, timeout);
    }
}
