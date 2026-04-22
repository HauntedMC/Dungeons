package nl.hauntedmc.dungeons.command.config;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;

/**
 * Metadata description for a single configurable dungeon setting.
 *
 * <p>Entries describe where a value lives, how it should be parsed, when it applies, and what
 * suggestions the config command should offer while editing it.</p>
 */
public final class DungeonConfigEntry {
    private final String key;
    private final String description;
    private final DungeonConfigFile configFile;
    private final String path;
    private final DungeonConfigValueType valueType;
    private final Predicate<DungeonDefinition> supportPredicate;
    private final Function<DungeonDefinition, List<String>> suggestionsProvider;
    private final List<String> aliases;

    /**
     * Creates a new config entry definition.
     */
    public DungeonConfigEntry(String key, String description, DungeonConfigFile configFile, String path,
            DungeonConfigValueType valueType, Predicate<DungeonDefinition> supportPredicate,
            Function<DungeonDefinition, List<String>> suggestionsProvider, List<String> aliases) {
        this.key = key;
        this.description = description;
        this.configFile = configFile;
        this.path = path;
        this.valueType = valueType;
        this.supportPredicate = supportPredicate;
        this.suggestionsProvider = suggestionsProvider;
        this.aliases = List.copyOf(aliases);
    }

    /**
     * Returns whether this setting is available for the supplied dungeon type.
     */
    public boolean supports(DungeonDefinition dungeon) {
        return this.supportPredicate.test(dungeon);
    }

    /**
     * Returns command suggestions that are valid for the supplied dungeon.
     */
    public List<String> getSuggestions(DungeonDefinition dungeon) {
        return List.copyOf(this.suggestionsProvider.apply(dungeon));
    }

    /**
     * Returns the command-facing key used to address this setting.
     */
    public String getKey() {
        return this.key;
    }

    /**
     * Returns the human-readable description shown in config command output.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Returns the backing config file that stores this setting.
     */
    public DungeonConfigFile getConfigFile() {
        return this.configFile;
    }

    /**
     * Returns the underlying Bukkit configuration path for this setting.
     */
    public String getPath() {
        return this.path;
    }

    /**
     * Returns the command value type used for parsing and completion.
     */
    public DungeonConfigValueType getValueType() {
        return this.valueType;
    }

    /**
     * Returns alternate lookup keys that resolve to the same setting.
     */
    public List<String> getAliases() {
        return this.aliases;
    }
}
