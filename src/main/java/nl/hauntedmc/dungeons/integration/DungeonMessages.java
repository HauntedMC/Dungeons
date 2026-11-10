package nl.hauntedmc.dungeons.integration;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;

import nl.hauntedmc.featureframework.localization.ComponentLocalization;
import nl.hauntedmc.featureframework.localization.LocalizationStore;
import nl.hauntedmc.featureframework.theme.ThemeRegistry;
import nl.hauntedmc.featureframework.toolkit.io.config.ConfigService;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.theme.HauntedMcColor;
import nl.hauntedmc.theme.featureframework.HauntedMcTheme;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Shared HauntedMC rendering. Templates are trusted; substitutions are inserted as components after
 * parsing.
 */
public final class DungeonMessages extends ComponentLocalization {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");
    private final Map<String, Component> staticMessages =
            Collections.synchronizedMap(
                    new LinkedHashMap<>(128, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, Component> entry) {
                            return size() > 512;
                        }
                    });
    private final ConfigService files;
    private final Function<Audience, Language> languages;

    public DungeonMessages(
            Path directory,
            ClassLoader resources,
            FrameworkLogger logger,
            Function<Audience, Language> languages) {
        this(new ConfigService(directory, logger, resources), resources, logger, languages);
    }

    private DungeonMessages(
            ConfigService files,
            ClassLoader resources,
            FrameworkLogger logger,
            Function<Audience, Language> languages) {
        super(
                new LocalizationStore(resources, files, logger),
                logger,
                Player.class,
                languages,
                (message, audience) -> message,
                true,
                ThemeRegistry.of(List.of(HauntedMcTheme.theme())));
        this.files = files;
        this.languages = languages;
    }

    public static void importLegacy(Path directory) {
        Path legacy = directory.resolve("messages.yml");
        Path destination = directory.resolve("lang/messages.yml");
        if (!Files.isRegularFile(legacy) || Files.exists(destination)) return;
        try {
            Files.createDirectories(destination.getParent());
            var existing = new YamlConfiguration();
            existing.load(legacy.toFile());
            try (var oldInput =
                            DungeonMessages.class
                                    .getClassLoader()
                                    .getResourceAsStream("messages.yml");
                    var newInput =
                            DungeonMessages.class
                                    .getClassLoader()
                                    .getResourceAsStream("lang/messages.yml")) {
                if (oldInput != null && newInput != null) {
                    var oldDefaults =
                            YamlConfiguration.loadConfiguration(
                                    new InputStreamReader(oldInput, StandardCharsets.UTF_8));
                    var newDefaults =
                            YamlConfiguration.loadConfiguration(
                                    new InputStreamReader(newInput, StandardCharsets.UTF_8));
                    for (String key : oldDefaults.getKeys(true)) {
                        if (!oldDefaults.isConfigurationSection(key)
                                && Objects.equals(existing.get(key), oldDefaults.get(key)))
                            existing.set(key, newDefaults.get(key));
                    }
                }
            }
            Path temporary = Files.createTempFile(destination.getParent(), "messages-", ".tmp");
            try {
                Files.writeString(temporary, existing.saveToString(), StandardCharsets.UTF_8);
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | InvalidConfigurationException failure) {
            throw new IllegalStateException("Cannot import customized dungeon messages", failure);
        }
    }

    public String template(String key, Audience audience) {
        return store().message(key, audience == null ? null : languages.apply(audience));
    }

    public boolean contains(String key) {
        return files.view("lang/messages.yml", true).get(key, Object.class) != null;
    }

    public List<String> lines(String key, Audience audience) {
        Language language = audience == null ? null : languages.apply(audience);
        if (language != null) {
            List<?> translated =
                    files.view("lang/" + language.getFileName(), true).get(key, List.class);
            if (translated != null) return translated.stream().map(String::valueOf).toList();
        }
        List<?> lines = files.view("lang/messages.yml", true).get(key, List.class);
        return lines == null ? List.of() : lines.stream().map(String::valueOf).toList();
    }

    public Component message(String key, Audience audience, boolean prefix, Map<String, ?> values) {
        Component body = render(template(key, audience), values);
        return prefix && !body.equals(Component.empty()) ? prefix(audience).append(body) : body;
    }

    public Component prefix(Audience audience) {
        return render(template("general.prefix", audience), Map.of()).append(Component.space());
    }

    public Component render(String template, Map<String, ?> values) {
        if (template == null || template.isEmpty()) return Component.empty();
        if (values == null) values = Map.of();
        // One-character sentinels survive gradients/rainbows, which split text into individual
        // components. Substitute all of them in one pass so inserted values are never reparsed.
        if (values.isEmpty()) {
            var cached = staticMessages.get(template);
            if (cached != null) return cached;
        }
        var matcher = PLACEHOLDER.matcher(template);
        var replacements = new LinkedHashMap<String, Component>();
        int sentinel = 0xe000;
        var text = new StringBuilder();
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            if (value == null) continue;
            while (sentinel <= 0xf8ff && template.indexOf((char) sentinel) >= 0) sentinel++;
            if (sentinel > 0xf8ff)
                throw new IllegalArgumentException("Too many message placeholders");
            String token = String.valueOf((char) sentinel++);
            replacements.put(
                    token,
                    value instanceof Component component
                            ? component
                            : Component.text(String.valueOf(value)));
            matcher.appendReplacement(text, token);
        }
        matcher.appendTail(text);
        Component result = getMessage("internal.render").with("text", text.toString()).build();
        if (!replacements.isEmpty())
            result =
                    result.replaceText(
                            TextReplacementConfig.builder()
                                    .match(
                                            Pattern.compile(
                                                    "["
                                                            + String.join("", replacements.keySet())
                                                            + "]"))
                                    .replacement(
                                            (match, builder) -> replacements.get(match.group()))
                                    .build());
        result = result.colorIfAbsent(HauntedMcColor.TEXT.textColor());
        if (values.isEmpty()) staticMessages.put(template, result);
        return result;
    }
}
