package nl.hauntedmc.dungeons.util.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/** Moves legacy generation files into the normal dungeon configuration before defaults are synced. */
public final class GeneratorConfigMigration {
    private GeneratorConfigMigration() {}

    /** Loads the unified default template without writing fallback values before migration succeeds. */
    public static YamlConfiguration loadTemplate(
            File legacyFile, File targetFile, YamlConfiguration bundledDefaults)
            throws IOException, InvalidConfigurationException {
        boolean newTemplate = !targetFile.exists();
        YamlConfiguration target = new YamlConfiguration();
        if (newTemplate) {
            target.loadFromString(bundledDefaults.saveToString());
            if (legacyFile.exists()) {
                target.set("generator", null);
            }
        } else {
            target.load(targetFile);
        }
        migrate(legacyFile, targetFile, target);
        boolean changed = ConfigSyncUtils.syncMissingAndObsolete(bundledDefaults, target);
        if (newTemplate || changed) {
            target.save(targetFile);
        }
        return target;
    }

    /**
     * Imports missing generator settings, saves the unified config, then archives the old file.
     * Explicit values already present in the unified config take priority.
     */
    public static boolean migrate(File legacyFile, File targetFile, FileConfiguration target)
            throws IOException, InvalidConfigurationException {
        if (!legacyFile.exists()) {
            return false;
        }
        YamlConfiguration legacy = new YamlConfiguration();
        legacy.load(legacyFile);
        ConfigurationSection generator = requireSection(legacy, "generator");
        ConfigurationSection branching = requireSection(legacy, "branching");
        requireSection(target, "generator");
        requireSection(target, "generator.branching");
        copyMissing(generator, target, "generator");
        copyMissing(branching, target, "generator.branching");
        // Keep the original intact if writing the unified configuration fails.
        target.save(targetFile);
        File archive = new File(legacyFile.getPath() + ".migrated");
        for (int suffix = 1; archive.exists(); suffix++) {
            archive = new File(legacyFile.getPath() + ".migrated." + suffix);
        }
        Files.move(legacyFile.toPath(), archive.toPath());
        return true;
    }

    private static ConfigurationSection requireSection(FileConfiguration config, String path)
            throws InvalidConfigurationException {
        if (config.contains(path) && !config.isConfigurationSection(path)) {
            throw new InvalidConfigurationException(
                    "Expected a configuration section at '" + path + "'.");
        }
        return config.getConfigurationSection(path);
    }

    private static void copyMissing(
            ConfigurationSection source, FileConfiguration target, String prefix) {
        if (source == null) {
            return;
        }
        for (String key : source.getKeys(false)) {
            String path = prefix + "." + key;
            Object value = source.get(key);
            if (value instanceof ConfigurationSection section) {
                if (target.contains(path) && !target.isConfigurationSection(path)) {
                    continue;
                }
                if (!target.isConfigurationSection(path)) {
                    target.createSection(path);
                }
                copyMissing(section, target, path);
            } else if (!target.contains(path)) {
                target.set(path, value instanceof List<?> list ? new ArrayList<>(list) : value);
            }
        }
    }
}
