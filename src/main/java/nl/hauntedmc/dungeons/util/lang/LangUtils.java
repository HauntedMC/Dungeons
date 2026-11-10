package nl.hauntedmc.dungeons.util.lang;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.integration.DungeonMessages;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.featureframework.toolkit.text.format.ComponentFormatter;
import nl.hauntedmc.theme.HauntedMcColor;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** HauntedMC localization boundary; legacy string accessors remain for Bukkit model/UI codecs. */
public final class LangUtils {
    private static final Component DEFAULT_PREFIX =
            Component.text("[Dungeons] ", HauntedMcColor.BRAND.textColor());
    private static volatile DungeonMessages messages;

    private LangUtils() {}

    public static void initialize() {
        var plugin = RuntimeContext.plugin();
        messages =
                new DungeonMessages(
                        plugin.getDataFolder().toPath(),
                        LangUtils.class.getClassLoader(),
                        FrameworkLogger.from(plugin.getSLF4JLogger()),
                        LangUtils::language);
    }

    private static Language language(Audience audience) {
        if (audience instanceof Player player) {
            var profiles = RuntimeContext.runtime().profiles();
            if (profiles != null) return profiles.language(player);
            var locale = player.locale();
            if (locale != null)
                return Language.fromCode(locale.getLanguage())
                        .filter(Language::isLocalizable)
                        .orElse(Language.EN);
        }
        return Language.EN;
    }

    public static Locale getLocale() {
        var current = messages;
        return current == null
                ? Locale.ENGLISH
                : Locale.forLanguageTag(current.template("general.java-locale", null));
    }

    public static void clear() {
        messages = null;
    }

    public static LangPlaceholder placeholder(String name, Object value) {
        return new LangPlaceholder(name, value);
    }

    private static Map<String, Object> values(LangPlaceholder... placeholders) {
        var result = new LinkedHashMap<String, Object>();
        if (placeholders == null) return result;
        for (var value : placeholders) if (value != null) result.put(value.name(), value.value());
        return result;
    }

    public static Component component(
            String key, Audience audience, boolean prefix, Map<String, ?> values) {
        var current = messages;
        return current == null
                ? Component.empty()
                : current.message(key, audience, prefix, values == null ? Map.of() : values);
    }

    public static Component component(
            String key, Audience audience, boolean prefix, LangPlaceholder... values) {
        return component(key, audience, prefix, values(values));
    }

    /**
     * Parse administrator-authored text; substitutions must go through the component message API.
     */
    public static Component trusted(String template) {
        var current = messages;
        if (current != null) return current.render(template, Map.of());
        return ComponentFormatter.deserialize(template == null ? "" : template)
                .features(ComponentFormatter.ALL_DEFAULTS())
                .toComponent();
    }

    public static Component notice(String text) {
        return notice(Component.text(text == null ? "" : text));
    }

    public static Component notice(Component text) {
        return notice(text, null);
    }

    private static Component notice(Component text, Audience audience) {
        Component body =
                (text == null ? Component.empty() : text)
                        .colorIfAbsent(HauntedMcColor.TEXT.textColor());
        var current = messages;
        return (current == null ? DEFAULT_PREFIX : current.prefix(audience)).append(body);
    }

    public static void sendRaw(CommandSender target, Component text) {
        target.sendMessage(notice(text, target));
    }

    public static void sendRaw(CommandSender target, String text) {
        target.sendMessage(notice(Component.text(text == null ? "" : text), target));
    }

    public static void sendMessage(CommandSender target, String key) {
        sendMessage(target, key, new LangPlaceholder[0]);
    }

    public static void sendMessage(CommandSender target, String key, LangPlaceholder... values) {
        Component message = component(key, target, true, values(values));
        if (!message.equals(Component.empty())) target.sendMessage(message);
    }

    /** Legacy string boundary for inventory title APIs; delivery uses components directly. */
    public static String getMessage(String key, boolean prefix) {
        return ComponentUtils.serialize(component(key, null, prefix));
    }

    public static void sendActionBar(Player target, String key) {
        sendActionBar(target, key, new LangPlaceholder[0]);
    }

    public static void sendActionBar(Player target, String key, LangPlaceholder... values) {
        target.sendActionBar(component(key, target, false, values(values)));
    }

    public static void sendMessageList(CommandSender target, String key) {
        sendMessageList(target, key, new LangPlaceholder[0]);
    }

    public static void sendMessageList(
            CommandSender target, String key, LangPlaceholder... values) {
        for (Component line : components(key, target, values)) target.sendMessage(line);
    }

    public static List<Component> components(
            String key, Audience audience, LangPlaceholder... values) {
        var current = messages;
        var replacements = values(values);
        return current == null
                ? List.of()
                : current.lines(key, audience).stream()
                        .map(line -> current.render(line, replacements))
                        .toList();
    }
}
