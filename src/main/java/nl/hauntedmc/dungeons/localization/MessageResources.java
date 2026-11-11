package nl.hauntedmc.dungeons.localization;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import nl.hauntedmc.featureframework.localization.LocalizationStore;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;

/**
 * One default resource, framework language overrides, and a one-time relocation of old installs.
 */
final class MessageResources {
    static final String DEFAULT_MESSAGES = LocalizationStore.LANGUAGE_DIRECTORY + "/messages.yml";

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
        return new ConfigService(directory, logger, resources);
    }

    static String translation(Language language) {
        return LocalizationStore.LANGUAGE_DIRECTORY + "/" + language.getFileName();
    }
}
