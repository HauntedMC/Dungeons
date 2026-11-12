package nl.hauntedmc.dungeons.experience;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Immutable, optional gameplay definitions. No file means no added gameplay. */
public record ExperienceDefinition(
        Map<String, Objective> objectives,
        Map<String, Track> tracks,
        Map<String, Group> groups,
        Map<String, Encounter> encounters,
        String completion,
        String fingerprint,
        String legacyFingerprint) {
    public enum Source {
        SIGNAL,
        KILL,
        DESTROY,
        REACH,
        INTERACT,
        VARIABLE
    }

    public enum Scope {
        RUN,
        PLAYER
    }

    public record Point(double x, double y, double z, double radius) {}

    public record Objective(
            String id,
            String title,
            String description,
            Source source,
            Scope scope,
            String selector,
            int target,
            boolean capture,
            boolean optional,
            boolean hidden,
            String hint,
            Point point,
            boolean persistent) {}

    public record Stage(String id, String title, List<String> objectives) {}

    public record Track(
            String id, Scope scope, List<Stage> stages, boolean autoStart, boolean optional) {}

    public record Group(
            String id,
            String entity,
            int count,
            Point point,
            String boss,
            List<Double> phases,
            boolean existing) {}

    public record Encounter(
            String id,
            String title,
            List<List<String>> waves,
            int preparationSeconds,
            int timeoutSeconds,
            boolean autoStart) {}

    public ExperienceDefinition {
        objectives = immutable(objectives);
        tracks = immutable(tracks);
        groups = immutable(groups);
        encounters = immutable(encounters);
    }

    private static <T> Map<String, T> immutable(Map<String, T> input) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(input));
    }

    public static ExperienceDefinition empty() {
        return new ExperienceDefinition(Map.of(), Map.of(), Map.of(), Map.of(), "manual", "", "");
    }

    public boolean hasMechanics() {
        return !objectives.isEmpty() || !groups.isEmpty() || !encounters.isEmpty();
    }

    public static ExperienceDefinition load(Path folder) throws IOException {
        Path path = folder.resolve("experience.yml");
        if (!Files.exists(path)) return empty();
        var yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(path));
        } catch (InvalidConfigurationException exception) {
            throw new IOException("Invalid experience.yml", exception);
        }
        try {
            return parse(yaml);
        } catch (IllegalArgumentException exception) {
            throw new IOException("experience.yml: " + exception.getMessage(), exception);
        }
    }

    public static ExperienceDefinition parse(YamlConfiguration yaml) {
        if (!flag(yaml, "enabled", true)) return empty();
        require(integer(yaml, "version", 1) == 1, "Unsupported experience version");
        var goals = new LinkedHashMap<String, Objective>();
        for (var entry : sections(yaml, "objectives").entrySet()) {
            String id = entry.getKey();
            var node = entry.getValue();
            if (!flag(node, "enabled", true)) continue;
            var source =
                    Source.valueOf(node.getString("source", "signal").toUpperCase(Locale.ROOT));
            var scope = Scope.valueOf(node.getString("scope", "run").toUpperCase(Locale.ROOT));
            int target = integer(node, "target", 1);
            boolean capture = flag(node, "capture", false);
            require(target > 0, id + ": target must be positive");
            require(
                    !capture || source == Source.KILL || source == Source.DESTROY,
                    id + ": capture requires kill or destroy");
            String selector = node.getString("selector", id);
            require(!selector.isBlank(), id + ": missing selector");
            if ((source == Source.KILL || source == Source.DESTROY)
                    && !selector.startsWith("group:")) {
                String entity = selector.startsWith("type:") ? selector.substring(5) : selector;
                try {
                    org.bukkit.entity.EntityType.valueOf(entity.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException failure) {
                    throw new IllegalArgumentException(
                            id + ": unknown entity selector " + selector, failure);
                }
            }
            Point point = point(node.getConfigurationSection("location"));
            require(
                    source != Source.REACH || point != null || selector.startsWith("room:"),
                    id + ": reach needs a location");
            boolean persistent = flag(node, "persistent", false);
            require(
                    !persistent || scope == Scope.PLAYER && !capture && source != Source.VARIABLE,
                    id + ": persistence requires a personal, non-captured, non-variable objective");
            goals.put(
                    id,
                    new Objective(
                            id,
                            node.getString("title", id),
                            node.getString("description", ""),
                            source,
                            scope,
                            selector,
                            target,
                            capture,
                            flag(node, "optional", false),
                            flag(node, "hidden", false),
                            node.getString("hint", ""),
                            point,
                            persistent));
        }
        var tracks = new LinkedHashMap<String, Track>();
        Set<String> assigned = new LinkedHashSet<>();
        for (var entry : sections(yaml, "tracks").entrySet()) {
            var node = entry.getValue();
            if (!flag(node, "enabled", true)) continue;
            Scope scope = Scope.valueOf(node.getString("scope", "run").toUpperCase(Locale.ROOT));
            var stages = new ArrayList<Stage>();
            for (var stage : sections(node, "stages").entrySet()) {
                var ids = List.copyOf(stage.getValue().getStringList("objectives"));
                require(!ids.isEmpty(), stage.getKey() + ": stage needs objectives");
                require(
                        ids.stream()
                                .anyMatch(id -> goals.containsKey(id) && !goals.get(id).optional()),
                        stage.getKey() + ": stage needs a required objective");
                for (String id : ids) {
                    require(goals.containsKey(id), "Unknown objective " + id);
                    require(goals.get(id).scope() == scope, "Scope mismatch: " + id);
                    require(assigned.add(id), "Objective appears in multiple stages: " + id);
                }
                stages.add(
                        new Stage(
                                stage.getKey(),
                                stage.getValue().getString("title", stage.getKey()),
                                ids));
            }
            require(!stages.isEmpty(), entry.getKey() + ": track needs stages");
            tracks.put(
                    entry.getKey(),
                    new Track(
                            entry.getKey(),
                            scope,
                            List.copyOf(stages),
                            flag(node, "auto_start", true),
                            flag(node, "optional", false)));
        }
        var groups = new LinkedHashMap<String, Group>();
        for (var entry : sections(yaml, "groups").entrySet()) {
            var node = entry.getValue();
            if (!flag(node, "enabled", true)) continue;
            int count = integer(node, "count", 1);
            require(count > 0 && count <= 1000, "Group count must be 1–1000");
            Point point = point(node.getConfigurationSection("location"));
            require(point != null, entry.getKey() + ": group needs location");
            String entity = node.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT);
            try {
                var entityType = org.bukkit.entity.EntityType.valueOf(entity);
                require(
                        (flag(node, "existing", false) || entityType.isAlive()),
                        "Group must spawn a living entity");
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Invalid group entity: " + entity, exception);
            }
            require(
                    !node.contains("phases") || node.isList("phases"),
                    "Expected phase threshold list");
            var phases = node.getDoubleList("phases");
            require(
                    phases.stream()
                            .allMatch(value -> Double.isFinite(value) && value > 0 && value < 1),
                    "Phase thresholds must be between 0 and 1");
            groups.put(
                    entry.getKey(),
                    new Group(
                            entry.getKey(),
                            entity,
                            count,
                            point,
                            node.getString("boss", ""),
                            List.copyOf(phases),
                            flag(node, "existing", false)));
        }
        var encounters = new LinkedHashMap<String, Encounter>();
        for (var entry : sections(yaml, "encounters").entrySet()) {
            var node = entry.getValue();
            if (!flag(node, "enabled", true)) continue;
            var waves = new ArrayList<List<String>>();
            for (var wave : sections(node, "waves").entrySet()) {
                var ids = List.copyOf(wave.getValue().getStringList("groups"));
                require(
                        !ids.isEmpty() && groups.keySet().containsAll(ids),
                        "Wave has missing/unknown groups: " + wave.getKey());
                require(
                        ids.stream().noneMatch(id -> groups.get(id).existing()),
                        "Waves cannot spawn groups marked existing");
                waves.add(ids);
            }
            require(!waves.isEmpty(), entry.getKey() + ": encounter needs waves");
            int preparation = integer(node, "preparation_seconds", 3),
                    timeout = integer(node, "timeout_seconds", 0);
            require(preparation >= 0 && timeout >= 0, "Encounter timing cannot be negative");
            encounters.put(
                    entry.getKey(),
                    new Encounter(
                            entry.getKey(),
                            node.getString("title", entry.getKey()),
                            List.copyOf(waves),
                            preparation,
                            timeout,
                            flag(node, "auto_start", false)));
        }
        String completion = yaml.getString("completion", "manual");
        require(
                Set.of("manual", "objectives").contains(completion),
                "Completion must be manual or objectives");
        Set<String> nonGating = new java.util.HashSet<>();
        for (var track : tracks.values())
            if (track.optional())
                for (var stage : track.stages()) nonGating.addAll(stage.objectives());
        require(
                !completion.equals("objectives")
                        || goals.values().stream()
                                .anyMatch(
                                        goal -> !goal.optional() && !nonGating.contains(goal.id())),
                "Automatic completion needs required objectives");
        for (var goal : goals.values())
            if (goal.selector().startsWith("group:"))
                require(
                        groups.containsKey(goal.selector().substring(6)),
                        "Unknown group: " + goal.selector());
        return new ExperienceDefinition(
                goals,
                tracks,
                groups,
                encounters,
                completion,
                progressionFingerprint(goals, tracks, groups, encounters, completion),
                fingerprint(yaml.saveToString()));
    }

    /** Only parsed mechanics enter the versioned compatibility hash; labels never do. */
    private static String progressionFingerprint(
            Map<String, Objective> goals,
            Map<String, Track> tracks,
            Map<String, Group> groups,
            Map<String, Encounter> encounters,
            String completion) {
        var structure = new YamlConfiguration();
        structure.set("completion", completion);
        var objectives = new java.util.TreeMap<String, Object>();
        goals.forEach(
                (id, goal) ->
                        objectives.put(
                                id,
                                java.util.Arrays.asList(
                                        goal.source().name(),
                                        goal.scope().name(),
                                        goal.selector(),
                                        goal.target(),
                                        goal.capture(),
                                        goal.optional(),
                                        goal.hidden(),
                                        pointValues(goal.point()),
                                        goal.persistent())));
        structure.set("objectives", objectives);
        var paths = new java.util.TreeMap<String, Object>();
        tracks.forEach(
                (id, track) ->
                        paths.put(
                                id,
                                List.of(
                                        track.scope().name(),
                                        track.autoStart(),
                                        track.optional(),
                                        track.stages().stream()
                                                .map(
                                                        stage ->
                                                                List.of(
                                                                        stage.id(),
                                                                        stage.objectives()))
                                                .toList())));
        structure.set("tracks", paths);
        var mobs = new java.util.TreeMap<String, Object>();
        groups.forEach(
                (id, group) ->
                        mobs.put(
                                id,
                                List.of(
                                        group.entity(),
                                        group.count(),
                                        pointValues(group.point()),
                                        group.phases(),
                                        group.existing())));
        structure.set("groups", mobs);
        var fights = new java.util.TreeMap<String, Object>();
        encounters.forEach(
                (id, encounter) ->
                        fights.put(
                                id,
                                List.of(
                                        encounter.waves(),
                                        encounter.preparationSeconds(),
                                        encounter.timeoutSeconds(),
                                        encounter.autoStart())));
        structure.set("encounters", fights);
        return "progress-v2:" + fingerprint(structure.saveToString());
    }

    private static List<Double> pointValues(Point point) {
        return point == null ? List.of() : List.of(point.x(), point.y(), point.z(), point.radius());
    }

    /** Legacy hashes are accepted only against unchanged source; the next save upgrades them. */
    public boolean acceptsFingerprint(String saved) {
        return fingerprint.equals(saved) || legacyFingerprint.equals(saved);
    }

    private static Point point(ConfigurationSection node) {
        if (node == null) return null;
        double x = decimal(node, "x", null),
                y = decimal(node, "y", null),
                z = decimal(node, "z", null),
                radius = decimal(node, "radius", 3.0);
        require(
                Double.isFinite(x)
                        && Double.isFinite(y)
                        && Double.isFinite(z)
                        && Double.isFinite(radius)
                        && radius > 0,
                "Invalid location or radius");
        return new Point(x, y, z, radius);
    }

    private static Map<String, ConfigurationSection> sections(
            ConfigurationSection parent, String path) {
        var result = new LinkedHashMap<String, ConfigurationSection>();
        var section = parent.getConfigurationSection(path);
        require(!parent.contains(path) || section != null, "Expected a section: " + path);
        if (section != null)
            for (String id : section.getKeys(false)) {
                require(id.matches("[A-Za-z0-9_-]+"), "Invalid id: " + id);
                var child = section.getConfigurationSection(id);
                require(child != null, "Expected section: " + path + "." + id);
                result.put(id, child);
            }
        return result;
    }

    private static int integer(ConfigurationSection node, String path, int fallback) {
        Object value = node.get(path);
        if (value == null) return fallback;
        require(
                value instanceof Integer || value instanceof Long,
                "Expected integer: " + node.getCurrentPath() + "." + path);
        long result = ((Number) value).longValue();
        require(
                result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE,
                "Integer out of range: " + path);
        return (int) result;
    }

    private static boolean flag(ConfigurationSection node, String path, boolean fallback) {
        Object value = node.get(path);
        if (value == null) return fallback;
        require(
                value instanceof Boolean,
                "Expected boolean: " + node.getCurrentPath() + "." + path);
        return (Boolean) value;
    }

    private static double decimal(ConfigurationSection node, String path, Double fallback) {
        Object value = node.get(path);
        if (value == null && fallback != null) return fallback;
        require(value instanceof Number, "Expected number: " + node.getCurrentPath() + "." + path);
        return ((Number) value).doubleValue();
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private static String fingerprint(String input) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
