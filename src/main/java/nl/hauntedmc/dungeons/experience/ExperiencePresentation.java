package nl.hauntedmc.dungeons.experience;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.localization.LangUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Validated, immutable presentation snapshot; omitted local values inherit without being saved. */
public record ExperiencePresentation(
        Map<String, Boolean> flags, Map<String, String> text, Optional<List<String>> rows) {
    public static final List<String> WIDGETS =
            List.of(
                    "state",
                    "difficulty",
                    "stage",
                    "objective",
                    "remaining",
                    "elapsed",
                    "lives",
                    "party",
                    "respawn",
                    "help",
                    "leave");
    private static final List<String> DEFAULT_ROWS =
            List.of(
                    "state",
                    "difficulty",
                    "",
                    "stage",
                    "objective",
                    "remaining",
                    "elapsed",
                    "lives",
                    "party",
                    "respawn",
                    "",
                    "help",
                    "leave");

    public ExperiencePresentation {
        flags = Map.copyOf(flags);
        text = Map.copyOf(text);
        rows = rows.map(List::copyOf);
    }

    public static ExperiencePresentation defaults() {
        return resolve(new YamlConfiguration(), new YamlConfiguration());
    }

    public static ExperiencePresentation resolve(
            ConfigurationSection local, ConfigurationSection global) {
        for (String prefix :
                List.of(
                        "experience",
                        "experience.channels",
                        "experience.widgets",
                        "experience.sidebar",
                        "experience.notifications")) {
            if (local.contains(prefix, true) && !local.isConfigurationSection(prefix))
                throw new IllegalArgumentException("Expected a section: " + prefix);
            String inherited = prefix.replace("experience", "experience.defaults");
            if (global.contains(inherited, true) && !global.isConfigurationSection(inherited))
                throw new IllegalArgumentException("Expected a section: " + inherited);
        }
        var flags = new LinkedHashMap<String, Boolean>();
        var text = new LinkedHashMap<String, String>();
        for (String key :
                List.of(
                        "enabled",
                        "confirm_exit",
                        "channels.sidebar",
                        "channels.actionbar",
                        "channels.bossbar",
                        "channels.titles")) {
            Object value = raw(local, global, key);
            if (value != null && !(value instanceof Boolean))
                throw new IllegalArgumentException("Expected boolean: experience." + key);
            flags.put(key, value == null || (Boolean) value);
        }
        String profile = string(local, global, "profile", "balanced");
        if (!Set.of("balanced", "minimal", "immersive").contains(profile))
            throw new IllegalArgumentException("Unknown experience profile: " + profile);
        text.put("profile", profile);
        for (String widget : WIDGETS) {
            String key = "widgets." + widget, visibility = string(local, global, key, "auto");
            if (!Set.of("auto", "shown", "hidden").contains(visibility))
                throw new IllegalArgumentException("Unknown widget visibility: " + visibility);
            text.put(key, visibility);
        }
        for (String key : List.of("description", "sidebar.title"))
            text.put(key, string(local, global, key, ""));
        for (String path :
                List.of("experience.notifications", "experience.defaults.notifications")) {
            var section =
                    (path.startsWith("experience.defaults") ? global : local)
                            .getConfigurationSection(path);
            if (section == null) continue;
            for (String key : section.getKeys(true))
                if (!section.isConfigurationSection(key)) {
                    if (!key.matches("[A-Za-z0-9_-]+\\.(enabled|chat|actionbar|title|sound)"))
                        throw new IllegalArgumentException("Unknown notification setting: " + key);
                    Object value = raw(local, global, "notifications." + key);
                    if (!(value instanceof Boolean))
                        throw new IllegalArgumentException("Expected notification boolean: " + key);
                    flags.put("notifications." + key, (Boolean) value);
                }
        }
        Object rows = raw(local, global, "sidebar.rows");
        if (rows != null
                && (!(rows instanceof List<?> list)
                        || list.size() > 15
                        || list.stream().anyMatch(row -> !(row instanceof String))))
            throw new IllegalArgumentException("Sidebar rows must contain at most 15 strings");
        Optional<List<String>> layout =
                rows instanceof List<?> list
                        ? Optional.of(list.stream().map(String.class::cast).toList())
                        : Optional.empty();
        return new ExperiencePresentation(flags, text, layout);
    }

    /** Resolve a saved player preference against the dungeon/global policy. */
    public String profile(String preference) {
        return Set.of("balanced", "minimal", "immersive").contains(preference)
                ? preference
                : text.get("profile");
    }

    /** One layout path for default and custom rows; missing or hidden widgets leave no gaps. */
    public List<Component> layout(
            Map<String, ? extends Component> widgets, Map<String, ?> substitutions) {
        var result = new java.util.ArrayList<Component>();
        boolean separator = false;
        for (String row : rows.orElse(DEFAULT_ROWS)) {
            if (row.isBlank()) {
                separator = !result.isEmpty();
                continue;
            }
            Component value;
            if (WIDGETS.contains(row)) {
                if ("hidden".equals(text.get("widgets." + row))) continue;
                value = widgets.get(row);
                if (value == null) continue;
            } else value = LangUtils.trusted(row, substitutions);
            if (separator) result.add(Component.empty());
            separator = false;
            result.add(value);
        }
        return List.copyOf(result.subList(0, Math.min(15, result.size())));
    }

    private static Object raw(ConfigurationSection local, ConfigurationSection global, String key) {
        String path = "experience." + key;
        return local.contains(path, true)
                ? local.get(path)
                : global.get("experience.defaults." + key);
    }

    private static String string(
            ConfigurationSection local, ConfigurationSection global, String key, String fallback) {
        Object value = raw(local, global, key);
        if (value == null) return fallback;
        if (!(value instanceof String))
            throw new IllegalArgumentException("Expected text: experience." + key);
        return (String) value;
    }
}
