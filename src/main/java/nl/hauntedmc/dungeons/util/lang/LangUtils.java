package nl.hauntedmc.dungeons.util.lang;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * Localization utility for loading and formatting messages from {@code messages.yml}.
 */
public final class LangUtils {
    private static final String MESSAGES_FILE_NAME = "messages.yml";
    private static final Pattern NAMED_PLACEHOLDER_PATTERN = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");
    private static FileConfiguration messagesYaml;
    private static FileConfiguration jarMessages;

    /** Utility class. */
    private LangUtils() {}

    /** Loads bundled and disk language files into memory. */
    public static void initialize() {
        messagesYaml = new YamlConfiguration();
        File messagesFile = new File(RuntimeContext.plugin().getDataFolder(), MESSAGES_FILE_NAME);
        jarMessages = new YamlConfiguration();

        try (InputStream jarMessagesStream =
                RuntimeContext.plugin().getResource(MESSAGES_FILE_NAME)) {
            if (jarMessagesStream != null) {
                try (InputStreamReader reader = new InputStreamReader(jarMessagesStream)) {
                    jarMessages = YamlConfiguration.loadConfiguration(reader);
                }
            } else {
                RuntimeContext.logger()
                        .warn("Bundled {} resource was not found in the plugin jar.", MESSAGES_FILE_NAME);
            }
        } catch (IOException exception) {
            RuntimeContext.logger()
                    .error("Failed to read bundled {} resource.", MESSAGES_FILE_NAME, exception);
        }

        try {
            if (!messagesFile.exists()) {
                RuntimeContext.plugin().saveResource(MESSAGES_FILE_NAME, false);
            }

            messagesYaml.load(messagesFile);
        } catch (InvalidConfigurationException | IOException exception) {
            RuntimeContext.logger()
                    .error("Failed to load messages file '{}'.", messagesFile.getAbsolutePath(), exception);
        }
    }

    /** Returns whether a language namespace exists in loaded messages. */
    public static boolean contains(String langNamespace) {
        return messagesYaml != null && messagesYaml.contains(langNamespace);
    }

    /** Creates a named placeholder helper value. */
    public static LangPlaceholder placeholder(String name, Object value) {
        return new LangPlaceholder(name, value);
    }

    /** Sends one localized message to a command sender with default prefix behavior. */
    public static void sendMessage(CommandSender target, String langNamespace) {
        String message = formatMessage(resolveString(langNamespace), true);
        if (!message.isEmpty()) {
            target.sendMessage(message);
        }
    }

    /** Sends one localized message with named placeholders. */
    public static void sendMessage(
            CommandSender target, String langNamespace, LangPlaceholder... placeholders) {
        String message = getMessage(langNamespace, true, placeholders);
        if (!message.isEmpty()) {
            target.sendMessage(message);
        }
    }

    /** Returns one localized message with default prefix behavior. */
    public static String getMessage(String langNamespace) {
        return getMessage(langNamespace, true);
    }

    /** Returns one localized message with configurable prefix behavior. */
    public static String getMessage(String langNamespace, boolean prefixed) {
        return formatMessage(resolveString(langNamespace), prefixed);
    }

    /** Returns one localized message with placeholder substitution. */
    public static String getMessage(String langNamespace, LangPlaceholder... placeholders) {
        return getMessage(langNamespace, true, placeholders);
    }

    /** Returns one localized message with placeholders and optional prefix. */
    public static String getMessage(
            String langNamespace, boolean prefixed, LangPlaceholder... placeholders) {
        return getMessage(langNamespace, prefixed, toPlaceholderMap(placeholders));
    }

    /** Returns one localized message with placeholder map substitution. */
    public static String getMessage(String langNamespace, Map<String, ?> placeholders) {
        return getMessage(langNamespace, true, placeholders);
    }

    /** Returns one localized message with placeholder map and prefix control. */
    public static String getMessage(
            String langNamespace, boolean prefixed, Map<String, ?> placeholders) {
        String message = resolveString(langNamespace);
        if (message == null) {
            return "";
        }

        message = applyNamedPlaceholders(message, placeholders);

        if (prefixed) {
            message = applyPrefix(message);
        }

        return ColorUtils.fullColor(message);
    }

    /** Sends an action-bar localized message to a player. */
    public static void sendActionBar(Player target, String langNamespace) {
        String message = formatMessage(resolveString(langNamespace), false);
        if (!message.isEmpty()) {
            target.sendActionBar(Component.text(message));
        }
    }

    /** Sends an action-bar localized message with placeholders. */
    public static void sendActionBar(
            Player target, String langNamespace, LangPlaceholder... placeholders) {
        String message = getMessage(langNamespace, false, placeholders);
        if (!message.isEmpty()) {
            target.sendActionBar(Component.text(message));
        }
    }

    /** Sends every localized line in a list namespace to a command sender. */
    public static void sendMessageList(CommandSender target, String langNamespace) {
        for (String line : getMessageList(langNamespace)) {
            target.sendMessage(line);
        }
    }

    /** Sends every localized list line with placeholder substitution. */
    public static void sendMessageList(
            CommandSender target, String langNamespace, LangPlaceholder... placeholders) {
        for (String line : getMessageList(langNamespace, placeholders)) {
            target.sendMessage(line);
        }
    }

    /** Returns a localized message list with no placeholder substitution. */
    public static List<String> getMessageList(String langNamespace) {
        return getMessageList(langNamespace, Map.of());
    }

    /** Returns a localized message list with placeholder substitution. */
    public static List<String> getMessageList(String langNamespace, LangPlaceholder... placeholders) {
        return getMessageList(langNamespace, toPlaceholderMap(placeholders));
    }

    /** Returns a localized message list with placeholder-map substitution. */
    public static List<String> getMessageList(String langNamespace, Map<String, ?> placeholders) {
        List<String> messages = new ArrayList<>();
        List<String> source =
                messagesYaml == null ? List.of() : messagesYaml.getStringList(langNamespace);

        for (String message : source) {
            messages.add(ColorUtils.fullColor(applyNamedPlaceholders(message, placeholders)));
        }

        return messages;
    }

    /** Returns configured Java locale for date/time rendering. */
    public static Locale getLocale() {
        Locale locale = Locale.forLanguageTag(resolveString("general.java-locale", "nl-NL"));
        if (locale.getLanguage().isBlank()) {
            locale = Locale.of("nl", "NL");
        }

        return locale;
    }

    /** Synchronizes missing message keys from bundled resources into disk messages file. */
    public static void saveMissingValues() {
        if (messagesYaml == null || jarMessages == null) {
            RuntimeContext.logger()
                    .warn("Message resources were not initialized before saveMissingValues() was called.");
            return;
        }

        syncSection(jarMessages, messagesYaml);

        File langFile = new File(RuntimeContext.plugin().getDataFolder(), MESSAGES_FILE_NAME);
        try {
            messagesYaml.save(langFile);
        } catch (IOException exception) {
            RuntimeContext.logger()
                    .error("Failed to save messages file '{}'.", langFile.getAbsolutePath(), exception);
        }
    }

    /** Resolves one string value from loaded messages with optional fallback. */
    private static String resolveString(String langNamespace) {
        return resolveString(langNamespace, null);
    }

    /** Resolves one string value from disk messages, then bundled messages, then fallback. */
    private static String resolveString(String langNamespace, String fallback) {
        if (messagesYaml == null) {
            return fallback;
        }

        String message = messagesYaml.getString(langNamespace);
        if (message != null) {
            return message;
        }

        if (jarMessages != null) {
            return jarMessages.getString(langNamespace, fallback);
        }

        return fallback;
    }

    /** Converts placeholder records into a string substitution map. */
    private static Map<String, String> toPlaceholderMap(LangPlaceholder... placeholders) {
        Map<String, String> values = new LinkedHashMap<>();
        for (LangPlaceholder placeholder : placeholders) {
            if (placeholder == null) {
                continue;
            }

            values.put(placeholder.name(), String.valueOf(placeholder.value()));
        }
        return values;
    }

    /** Replaces `{name}` tokens in a message with provided placeholder values. */
    private static String applyNamedPlaceholders(String message, Map<String, ?> placeholders) {
        if (message == null || message.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return message == null ? "" : message;
        }

        Matcher matcher = NAMED_PLACEHOLDER_PATTERN.matcher(message);
        StringBuilder formatted = new StringBuilder();
        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            Object value = placeholders.get(placeholderName);
            String replacement = value == null ? matcher.group(0) : String.valueOf(value);
            matcher.appendReplacement(formatted, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(formatted);
        return formatted.toString();
    }

    /** Prefixes a message with configured `general.prefix` when available. */
    private static String applyPrefix(String message) {
        String prefix = resolveString("general.prefix", "[Dungeons]");
        if (prefix == null || prefix.isEmpty()) {
            return message;
        }

        return prefix + " " + message;
    }

    /** Applies optional prefix and color formatting to one message string. */
    private static String formatMessage(String message, boolean prefixed) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        if (prefixed) {
            message = applyPrefix(message);
        }

        return ColorUtils.fullColor(message);
    }

    /** Synchronizes one configuration section recursively from source into target. */
    private static void syncSection(ConfigurationSection source, ConfigurationSection target) {
        if (source == null || target == null) {
            return;
        }

        for (String key : new ArrayList<>(target.getKeys(false))) {
            if (!source.contains(key)) {
                target.set(key, null);
            }
        }

        for (String key : source.getKeys(false)) {
            Object sourceValue = source.get(key);
            if (sourceValue instanceof ConfigurationSection sourceSection) {
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    if (target.contains(key)) {
                        target.set(key, null);
                    }
                    targetSection = target.createSection(key);
                }

                syncSection(sourceSection, targetSection);
            } else {
                if (!target.contains(key) || target.isConfigurationSection(key)) {
                    target.set(key, copyValue(sourceValue));
                }
            }
        }
    }

    /** Creates shallow copies for mutable list/map config values. */
    private static Object copyValue(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }

        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>(map);
        }

        return value;
    }
}
