package nl.hauntedmc.dungeons.repository;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Side-effect-free structural validation before content is allowed near the live catalog. */
public final class RepoValidation {
    public record Result(
            String type,
            boolean scripts,
            Set<String> bukkitAliases,
            Set<String> modelTypes,
            Set<String> blockData) {}

    private RepoValidation() {}

    public static Result validate(RepoContent.Snapshot snapshot, UUID dungeon, boolean publishing)
            throws IOException {
        var config = RepoContent.readYaml(snapshot.root().resolve("config.yml"));
        Object identity = value(config, "dungeon", "id");
        if (!(identity instanceof String id) || !dungeon.toString().equalsIgnoreCase(id))
            throw new IOException("Dungeon identity mismatch");
        String type = String.valueOf(value(config, "dungeon", "type")).toLowerCase(Locale.ROOT);
        if (!Set.of("static", "open", "branching").contains(type))
            throw new IOException("Unsupported dungeon type: " + type);
        if (publishing && !type.equals("branching") && value(config, "locations", "start") == null)
            throw new IOException("Set a start location before publication");
        var aliases = new HashSet<String>();
        var models = new HashSet<String>();
        var blockData = new HashSet<String>();
        var roomNames = new HashSet<String>();
        boolean startRoom = false;
        boolean scripts =
                value(config, "players", "join_commands") instanceof List<?> commands
                        && !commands.isEmpty();
        for (var item : snapshot.items()) {
            if (item.directory()) continue;
            if (item.path().equals("config.yml")
                    || item.path().equals("functions.yml")
                    || item.path().equals("gamerules.yml")
                    || item.path().startsWith("rooms/") && item.path().endsWith(".yml")) {
                var yaml = RepoContent.readYaml(snapshot.root().resolve(item.path()));
                scan(yaml, aliases, models);
                if (item.path().startsWith("rooms/") && item.path().endsWith(".yml")) {
                    String room = item.path().substring(6, item.path().length() - 4);
                    if (room.contains("/"))
                        throw new IOException("Room definitions must be directly inside rooms/");
                    roomNames.add(room);
                    if (!(value(yaml, "Bounds", "Corner1") instanceof Map<?, ?>)
                            || !(value(yaml, "Bounds", "Corner2") instanceof Map<?, ?>))
                        throw new IOException("Missing room bounds: " + room);
                    if (value(yaml, "Config", "SpawnPoint") != null) startRoom = true;
                    if (publishing
                            && !Files.isRegularFile(
                                    snapshot.root().resolve("rooms/" + room + ".struct")))
                        throw new IOException("Missing room schematic: " + room);
                }
            }
            if (item.path().startsWith("rooms/") && item.path().endsWith(".struct"))
                validateStructure(snapshot.root().resolve(item.path()), blockData);
        }
        if (publishing && type.equals("branching")) {
            if (roomNames.isEmpty() || !startRoom)
                throw new IOException(
                        "Branching publication requires rooms and at least one room spawn");
            validatePools(config, roomNames);
        }
        scripts |=
                aliases.contains("dungeons.function.command")
                        || aliases.contains("CommandFunction")
                        || models.contains("dungeons.function.command");
        return new Result(
                type, scripts, Set.copyOf(aliases), Set.copyOf(models), Set.copyOf(blockData));
    }

    private static void scan(Object value, Set<String> aliases, Set<String> models)
            throws IOException {
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of("==", "TYPE--")) {
                Object id = map.get(key);
                if (id != null) {
                    if (!(id instanceof String text) || text.isEmpty())
                        throw new IOException("Invalid serialized type");
                    (key.equals("==") ? aliases : models).add(text);
                }
            }
            for (Object child : map.values()) scan(child, aliases, models);
        } else if (value instanceof Collection<?> list)
            for (Object child : list) scan(child, aliases, models);
        else if (value instanceof Number number && !Double.isFinite(number.doubleValue()))
            throw new IOException("Non-finite content number");
    }

    private static void validatePools(Object value, Set<String> rooms) throws IOException {
        if (value instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (Set.of("room_pool", "end_room_pool").contains(entry.getKey())
                        && entry.getValue() instanceof Collection<?> pool)
                    for (Object room : pool)
                        if (!rooms.contains(String.valueOf(room)))
                            throw new IOException("Unknown room reference: " + room);
                validatePools(entry.getValue(), rooms);
            }
        }
    }

    private static void validateStructure(Path file, Set<String> blocks) throws IOException {
        try (var reader =
                new java.io.BufferedReader(
                        new java.io.InputStreamReader(
                                new java.util.zip.InflaterInputStream(Files.newInputStream(file)),
                                java.nio.charset.StandardCharsets.UTF_8))) {
            if (!"DUNGEONS-STRUCTURE-2".equals(token(reader, '\n', 64)))
                throw new IOException("Unsupported structure format: " + file.getFileName());
            String palette = token(reader, '\n', 1024 * 1024);
            String[] entries = palette.isEmpty() ? new String[0] : palette.split("\\|", -1);
            blocks.addAll(List.of(entries));
            if (blocks.size() > 65536) throw new IOException("Structure palette limit exceeded");
            long total = 0;
            while (true) {
                String entry = token(reader, '!', 1024 * 1024);
                total += entry.length() + 1;
                if (entry.isEmpty()) {
                    reader.mark(1);
                    if (reader.read() == -1) break;
                    reader.reset();
                    if (total > 256L * 1024 * 1024)
                        throw new IOException("Structure expansion exceeds 256 MiB");
                    continue;
                }
                if (total > 256L * 1024 * 1024)
                    throw new IOException("Structure expansion exceeds 256 MiB");
                String[] parts = entry.split("\\|", -1);
                if (parts.length != 3 || parts[0].split(",", -1).length != 3)
                    throw new IOException("Invalid structure block");
                try {
                    for (String coordinate : parts[0].split(",")) Integer.parseInt(coordinate);
                    int index = Integer.parseInt(parts[1]);
                    if (index < 0 || index >= entries.length)
                        throw new IOException("Invalid structure palette index");
                    if (!parts[2].equals("null")) java.util.Base64.getDecoder().decode(parts[2]);
                } catch (IllegalArgumentException invalid) {
                    throw new IOException("Invalid structure block", invalid);
                }
            }
        }
    }

    private static String token(java.io.Reader input, char separator, int maximum)
            throws IOException {
        var result = new StringBuilder();
        int character;
        while ((character = input.read()) != -1 && character != separator) {
            if (result.length() >= maximum || Thread.currentThread().isInterrupted())
                throw new IOException("Structure token limit exceeded or operation interrupted");
            result.append((char) character);
        }
        return result.toString();
    }

    private static Object value(Map<?, ?> root, String... path) {
        Object value = root;
        for (String part : path) value = value instanceof Map<?, ?> map ? map.get(part) : null;
        return value;
    }
}
