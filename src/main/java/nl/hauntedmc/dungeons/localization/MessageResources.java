package nl.hauntedmc.dungeons.localization;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;
import nl.hauntedmc.featureframework.localization.LocalizationStore;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * One default resource, framework language overrides, and a one-time relocation of old installs.
 */
final class MessageResources {
    static final String DEFAULT_MESSAGES = LocalizationStore.LANGUAGE_DIRECTORY + "/messages.yml";
    private static final String PREVIOUS_DEFAULTS = "localization/previous-message-defaults.yml";

    private MessageResources() {}

    static ConfigService open(Path directory, ClassLoader resources, FrameworkLogger logger) {
        Path legacy = directory.resolve("messages.yml");
        if (Files.exists(legacy)) {
            if (!Files.isRegularFile(legacy))
                throw new IllegalStateException(
                        "Legacy dungeon messages path is not a file: " + legacy);
            try {
                Path destination = directory.resolve(DEFAULT_MESSAGES);
                if (Files.exists(destination)) {
                    // The canonical file wins. Preserve the old contents outside the active config.
                    destination =
                            directory.resolve(
                                    "backups/messages-legacy-" + UUID.randomUUID() + ".yml");
                }
                Files.createDirectories(destination.getParent());
                Files.move(legacy, destination);
                logger.info(
                        "Moved legacy messages.yml to '"
                                + directory.relativize(destination)
                                + "'.");
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot relocate legacy dungeon messages", failure);
            }
        }
        var files = new ConfigService(directory, logger, resources);
        updateBundledDefaults(files, resources, logger);
        return files;
    }

    private static void updateBundledDefaults(
            ConfigService files, ClassLoader resources, FrameworkLogger logger) {
        try {
            var previous = readBundled(resources, PREVIOUS_DEFAULTS);
            updateBundledDefaults(
                    files, resources, DEFAULT_MESSAGES, previous.getConfigurationSection("nl"));
            updateBundledDefaults(
                    files,
                    resources,
                    translation(Language.EN),
                    previous.getConfigurationSection("en"));
        } catch (IOException | InvalidConfigurationException failure) {
            logger.warn(
                    "Could not read dungeon message defaults for the terminology update.", failure);
        }
    }

    private static void updateBundledDefaults(
            ConfigService files, ClassLoader resources, String path, ConfigurationSection previous)
            throws IOException, InvalidConfigurationException {
        if (previous == null || !files.exists(path)) return;

        var installed = files.view(path, false);
        var current = readBundled(resources, path);
        var updates = new LinkedHashMap<String, Object>();
        // Match the full previous value, including formatting and any lists, to retain edits.
        for (String key : previous.getKeys(true)) {
            if (previous.isConfigurationSection(key)) continue;
            Object oldValue = previous.get(key);
            Object newValue = current.get(key);
            if (newValue != null
                    && !newValue.equals(oldValue)
                    && oldValue.equals(installed.get(key))) updates.put(key, newValue);
        }
        installed.putAll(updates);
    }

    private static YamlConfiguration readBundled(ClassLoader resources, String path)
            throws IOException, InvalidConfigurationException {
        var configuration = new YamlConfiguration();
        try (var stream = resources.getResourceAsStream(path)) {
            if (stream != null)
                configuration.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return configuration;
    }

    static String translation(Language language) {
        return LocalizationStore.LANGUAGE_DIRECTORY + "/" + language.getFileName();
    }
}
