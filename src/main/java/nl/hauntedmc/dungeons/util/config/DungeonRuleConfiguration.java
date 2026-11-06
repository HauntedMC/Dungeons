package nl.hauntedmc.dungeons.util.config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Dungeon YAML with per-setting global rule fallbacks that are never written to the map file. */
public final class DungeonRuleConfiguration extends YamlConfiguration {
    private final Supplier<FileConfiguration> globalConfig;

    /** Creates a dungeon config backed by the main plugin configuration's global_rules. */
    public DungeonRuleConfiguration(FileConfiguration globalConfig) {
        this(() -> globalConfig);
    }

    /** Follows the current plugin config even when a reload replaces its configuration object. */
    public DungeonRuleConfiguration(Supplier<FileConfiguration> globalConfig) {
        this.globalConfig = globalConfig;
    }

    @Override
    public @Nullable Object get(@NotNull String path, @Nullable Object def) {
        Object value = super.get(path, null);
        if (value != null) {
            return value;
        }
        if (path.startsWith("rules.")) {
            Object globalValue = this.globalConfig.get().get("global_rules." + path.substring("rules.".length()));
            // Never expose global sections: creating a local override must create local sections.
            if (globalValue != null && !(globalValue instanceof ConfigurationSection)) {
                return globalValue instanceof List<?> list ? new ArrayList<>(list) : globalValue;
            }
        }
        return def;
    }
}
