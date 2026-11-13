package nl.hauntedmc.dungeons.localization;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
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
import org.bukkit.entity.Player;

/**
 * Shared HauntedMC rendering. Templates are trusted; substitutions are inserted as components after
 * parsing.
 */
public final class DungeonMessages extends ComponentLocalization {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.-]+)}");
    private final Map<String, Component> staticMessages =
            new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Component> entry) {
                    return size() > 512;
                }
            };
    private final ConfigService files;
    private final Function<Audience, Language> languages;

    public DungeonMessages(
            Path directory,
            ClassLoader resources,
            FrameworkLogger logger,
            Function<Audience, Language> languages) {
        this(MessageResources.open(directory, resources, logger), resources, logger, languages);
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

    @Override
    public synchronized void reloadLocalization() {
        refreshFiles();
        super.reloadLocalization();
        staticMessages.clear();
    }

    @Override
    public synchronized void reloadLocalizationQuietly() {
        refreshFiles();
        super.reloadLocalizationQuietly();
        staticMessages.clear();
    }

    private void refreshFiles() {
        // Read operator edits before the framework merges defaults into its cached documents.
        // Otherwise a missing bundled key can cause a stale snapshot to overwrite those edits.
        files.reloadIfCached(MessageResources.DEFAULT_MESSAGES);
        for (Language language : Language.localizableValues()) {
            files.reloadIfCached(MessageResources.translation(language));
        }
    }

    public String template(String key, Audience audience) {
        return store().message(key, languageFor(audience));
    }

    public boolean contains(String key) {
        return files.view(MessageResources.DEFAULT_MESSAGES, true).contains(key);
    }

    public List<String> lines(String key, Audience audience) {
        Language language = languageFor(audience);
        if (language != null) {
            var translated =
                    files.view(MessageResources.translation(language), true)
                            .getList(key, String.class);
            if (translated != null) return List.copyOf(translated);
        }
        var lines = files.view(MessageResources.DEFAULT_MESSAGES, true).getList(key, String.class);
        return lines == null ? List.of() : List.copyOf(lines);
    }

    private Language languageFor(Audience audience) {
        return audience instanceof Player ? languages.apply(audience) : null;
    }

    public Component message(String key, Audience audience, boolean prefix, Map<String, ?> values) {
        Component body = render(template(key, audience), values);
        return prefix && !body.equals(Component.empty()) ? prefix(audience).append(body) : body;
    }

    public Component prefix(Audience audience) {
        return render(template("general.prefix", audience), Map.of()).append(Component.space());
    }

    public synchronized Component render(String template, Map<String, ?> values) {
        if (template == null || template.isEmpty()) return Component.empty();
        // Older bundled messages used Info, which is not part of the HauntedMC palette.
        // Keep existing server translations usable without rewriting operator edits.
        template =
                template.replace("<HauntedMC:Info>", "<HauntedMC:Text>")
                        .replace("</HauntedMC:Info>", "</HauntedMC:Text>");
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
