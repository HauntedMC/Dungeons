package nl.hauntedmc.dungeons.repository;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Restart cache for discovery only. Every mutation resolves its authority from MySQL. */
final class RepoCatalog {
    record Cached(long timestamp, List<RepoStore.Entry> entries) {}

    static void write(Path file, long timestamp, List<RepoStore.Entry> catalog) throws IOException {
        var entries = new ArrayList<Map<String, Object>>();
        for (var entry : catalog) {
            var release = entry.head();
            var row = new LinkedHashMap<String, Object>();
            row.put("name", entry.name());
            row.put("release", release.id().toString());
            row.put("dungeon", release.dungeon().toString());
            row.put("parent", release.parent() == null ? "" : release.parent().toString());
            row.put("version", release.version().toString());
            row.put("hash", release.hash());
            row.put("format", release.format());
            row.put("minecraft", release.minecraft());
            row.put("actor", release.actor());
            row.put("server", release.server());
            row.put("message", release.message());
            row.put("created", release.created().toString());
            row.put("files", release.files());
            row.put("bytes", release.bytes());
            entries.add(row);
        }
        RepoContent.writeYaml(file, Map.of("format", 1, "fetched", timestamp, "entries", entries));
    }

    static Cached read(Path file) throws IOException {
        if (!Files.exists(file)) return new Cached(0, List.of());
        if (Files.isSymbolicLink(file)) throw new IOException("Unsafe repository cache");
        try {
            var document = RepoContent.readYaml(file);
            if (!Objects.equals(document.get("format"), 1)
                    || !(document.get("entries") instanceof List<?> values))
                throw new IOException("Unsupported repository cache");
            var entries = new ArrayList<RepoStore.Entry>();
            for (Object value : values) {
                var row = (Map<?, ?>) value;
                String parent = (String) row.get("parent");
                String name = (String) row.get("name");
                RepoContent.safeName(name);
                String hash = (String) row.get("hash");
                if (!hash.matches("[a-f0-9]{64}"))
                    throw new IOException("Invalid cached content hash");
                entries.add(
                        new RepoStore.Entry(
                                name,
                                new RepoStore.Release(
                                        UUID.fromString((String) row.get("release")),
                                        UUID.fromString((String) row.get("dungeon")),
                                        parent.isEmpty() ? null : UUID.fromString(parent),
                                        RepoVersion.parse((String) row.get("version")),
                                        hash,
                                        ((Number) row.get("format")).intValue(),
                                        (String) row.get("minecraft"),
                                        (String) row.get("actor"),
                                        (String) row.get("server"),
                                        (String) row.get("message"),
                                        Instant.parse((String) row.get("created")),
                                        ((Number) row.get("files")).intValue(),
                                        ((Number) row.get("bytes")).longValue())));
            }
            return new Cached(((Number) document.get("fetched")).longValue(), List.copyOf(entries));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid repository cache", invalid);
        }
    }
}
