package nl.hauntedmc.dungeons.repository;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import nl.hauntedmc.dungeons.persistence.DungeonSchema;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.*;
import org.yaml.snakeyaml.representer.Representer;

/** Filesystem-only portable content codec. Never constructs Bukkit models or worlds. */
public final class RepoContent {
    public static final int CHUNK_BYTES = 1024 * 1024;
    public static final String MARKER = ".dungeon-repo.yml";
    public static final Set<String> EXCLUDED =
            Set.of(
                    MARKER,
                    "players",
                    "playerdata",
                    "stats",
                    "advancements",
                    "access_keys.yml",
                    "session.lock",
                    "uid.dat",
                    ".dungeons-editor-recovery");

    public record Limits(long bytes, int files) {
        public Limits {
            if (bytes < 1 || files < 1 || files > 1_000_000)
                throw new IllegalArgumentException("Invalid repository limits");
        }
    }

    public record Item(String path, long size, String hash) {
        public Item {
            safePath(path);
            if (size < -1 || hash == null || !hash.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Invalid manifest entry");
        }

        public boolean directory() {
            return size == -1;
        }
    }

    public record Snapshot(Path root, List<Item> items, String hash, long bytes) {
        public Snapshot {
            items = List.copyOf(items);
        }
    }

    public record Chunk(String hash, String codec, int rawBytes, byte[] payload) {}

    private RepoContent() {}

    public static boolean excluded(Path relative) {
        for (Path part : relative) {
            String name = part.toString();
            if (EXCLUDED.contains(name)
                    || name.startsWith(".repo-")
                    || name.startsWith(".schematic-")
                    || name.startsWith(".configuration-")
                    || name.startsWith(".pending-")
                    || name.startsWith(".dungeons-")) return true;
        }
        return false;
    }

    public static void safeName(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException(
                    "Invalid dungeon name (1–128 letters, digits, _ or -)");
    }

    public static void safePath(String value) {
        if (value == null
                || value.isEmpty()
                || value.getBytes(StandardCharsets.UTF_8).length > 1024
                || value.startsWith("/")
                || value.contains("\\")
                || value.contains(":"))
            throw new IllegalArgumentException("Unsafe repository path");
        for (String part : value.split("/", -1))
            if (part.isEmpty()
                    || part.equals(".")
                    || part.equals("..")
                    || part.chars().anyMatch(c -> c < 32 || c == 127))
                throw new IllegalArgumentException("Unsafe repository path");
    }

    /** Hashes the portable working tree without allocating a second map on disk. */
    public static Snapshot inspect(Path source, Limits limits) throws IOException {
        return scan(source, null, limits);
    }

    public static Snapshot snapshot(Path source, Path destination, Limits limits)
            throws IOException {
        Objects.requireNonNull(destination);
        return scan(source, destination, limits);
    }

    private static Snapshot scan(Path source, Path destination, Limits limits) throws IOException {
        if (Files.isSymbolicLink(source) || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Invalid dungeon directory");
        if (destination != null) Files.createDirectories(destination);
        var items = new ArrayList<Item>();
        var names = new HashSet<String>();
        long total = 0;
        var paths = new ArrayList<Path>();
        Files.walkFileTree(
                source,
                new SimpleFileVisitor<>() {
                    private void add(Path path) throws IOException {
                        if (paths.size() >= limits.files())
                            throw new IOException("Repository file limit exceeded");
                        paths.add(path);
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
                            throws IOException {
                        if (dir.equals(source)) return FileVisitResult.CONTINUE;
                        if (excluded(source.relativize(dir))) return FileVisitResult.SKIP_SUBTREE;
                        add(dir);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                            throws IOException {
                        if (!excluded(source.relativize(file))) add(file);
                        return FileVisitResult.CONTINUE;
                    }
                });
        {
            for (Path file : paths.stream().sorted().toList()) {
                if (Thread.currentThread().isInterrupted())
                    throw new IOException("Repository operation interrupted");
                if (file.equals(source)) continue;
                Path relative = source.relativize(file);
                if (excluded(relative)) continue;
                if (Files.isSymbolicLink(file))
                    throw new IOException("Symbolic links are not repository content");
                String path = relative.toString().replace(File.separatorChar, '/');
                safePath(path);
                if (!names.add(path.toLowerCase(Locale.ROOT)))
                    throw new IOException("Case-colliding content paths: " + path);
                if (items.size() >= limits.files())
                    throw new IOException("Repository file limit exceeded");
                Path target = destination == null ? file : destination.resolve(relative);
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                    if (destination != null) Files.createDirectories(target);
                    items.add(new Item(path, -1, DungeonSchema.checksum(new byte[0])));
                } else if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    long size = Files.size(file);
                    if (size > limits.bytes() - total)
                        throw new IOException("Repository byte limit exceeded");
                    if (destination != null) Files.createDirectories(target.getParent());
                    String hash;
                    if (path.equals("config.yml")) {
                        if (size > 4L * CHUNK_BYTES)
                            throw new IOException("Dungeon configuration exceeds 4 MiB");
                        byte[] portable = portableConfig(Files.readAllBytes(file));
                        if (destination != null) Files.write(target, portable);
                        size = portable.length;
                        hash = DungeonSchema.checksum(portable);
                    } else {
                        if (destination != null)
                            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                        size = Files.size(target);
                        hash = hashFile(target);
                    }
                    total = Math.addExact(total, size);
                    if (total > limits.bytes())
                        throw new IOException("Repository byte limit exceeded");
                    items.add(new Item(path, size, hash));
                } else throw new IOException("Special files are not repository content: " + path);
            }
        }
        return new Snapshot(
                destination == null ? source : destination, items, manifestHash(items), total);
    }

    public static String manifestHash(List<Item> items) {
        var digest = digest();
        String previous = null;
        var folded = new HashSet<String>();
        for (Item item : items.stream().sorted(Comparator.comparing(Item::path)).toList()) {
            if (item.path().equals(previous)
                    || !folded.add(item.path().toLowerCase(Locale.ROOT))
                    || excluded(Path.of(item.path())))
                throw new IllegalArgumentException("Invalid or duplicate manifest path");
            previous = item.path();
            byte[] name = item.path().getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(4).putInt(name.length).array());
            digest.update(name);
            digest.update(ByteBuffer.allocate(8).putLong(item.size()).array());
            digest.update(HexFormat.of().parseHex(item.hash()));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String hashFile(Path path) throws IOException {
        var digest = digest();
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[CHUNK_BYTES];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static Chunk encode(byte[] raw) throws IOException {
        if (raw.length > CHUNK_BYTES) throw new IOException("Chunk exceeds limit");
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) {
            gzip.write(raw);
        }
        byte[] compressed = output.toByteArray();
        boolean useGzip = compressed.length < raw.length;
        return new Chunk(
                DungeonSchema.checksum(raw),
                useGzip ? "gzip" : "raw",
                raw.length,
                useGzip ? compressed : raw);
    }

    public static byte[] decode(Chunk chunk) throws IOException {
        if (chunk.rawBytes() < 0
                || chunk.rawBytes() > CHUNK_BYTES
                || chunk.payload().length > CHUNK_BYTES)
            throw new IOException("Invalid chunk length");
        InputStream source = new ByteArrayInputStream(chunk.payload());
        if (chunk.codec().equals("gzip")) source = new GZIPInputStream(source);
        else if (!chunk.codec().equals("raw")) throw new IOException("Unknown chunk codec");
        try (var input = source) {
            byte[] raw = input.readNBytes(chunk.rawBytes() + 1);
            if (raw.length != chunk.rawBytes() || !DungeonSchema.checksum(raw).equals(chunk.hash()))
                throw new IOException("Corrupt repository chunk");
            return raw;
        }
    }

    private static Yaml yaml() {
        var loader = new LoaderOptions();
        loader.setAllowDuplicateKeys(false);
        loader.setMaxAliasesForCollections(0);
        loader.setCodePointLimit(4 * CHUNK_BYTES);
        loader.setNestingDepthLimit(64);
        loader.setProcessComments(true);
        var dumper = new DumperOptions();
        dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumper.setProcessComments(true);
        return new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
    }

    public static Map<String, Object> readYaml(Path file) throws IOException {
        if (Files.size(file) > 4L * CHUNK_BYTES)
            throw new IOException("YAML exceeds 4 MiB: " + file.getFileName());
        try {
            Object value = yaml().load(Files.readString(file));
            if (!(value instanceof Map<?, ?> map))
                throw new IOException("Expected YAML mapping: " + file.getFileName());
            var result = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key))
                    throw new IOException("Expected YAML string key");
                result.put(key, entry.getValue());
            }
            return result;
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid YAML: " + file.getFileName(), invalid);
        }
    }

    static void writeYaml(Path file, Map<String, Object> values) throws IOException {
        atomicWrite(file, yaml().dump(values).getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] portableConfig(byte[] input) throws IOException {
        try {
            var codec = yaml();
            Node node = codec.compose(new StringReader(new String(input, StandardCharsets.UTF_8)));
            if (!(node instanceof MappingNode root))
                throw new IOException("Invalid dungeon configuration");
            remove(root, "dungeon", "enabled");
            remove(root, "locations", "exit");
            var output = new StringWriter();
            codec.serialize(root, output);
            return output.toString().getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid dungeon configuration", invalid);
        }
    }

    private static void remove(MappingNode root, String section, String key) {
        for (var tuple : root.getValue())
            if (tuple.getKeyNode() instanceof ScalarNode scalar
                    && scalar.getValue().equals(section)
                    && tuple.getValueNode() instanceof MappingNode child)
                child.getValue()
                        .removeIf(
                                entry ->
                                        entry.getKeyNode() instanceof ScalarNode name
                                                && name.getValue().equals(key));
    }

    public static void atomicWrite(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".repo-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(
                    temporary,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            syncDirectory(file.getParent());
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static void syncDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var walk = Files.walk(root)) {
            for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
    }
}
