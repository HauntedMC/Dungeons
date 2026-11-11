package nl.hauntedmc.dungeons.repository;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import nl.hauntedmc.dataprovider.database.relational.TransactionCallback;
import nl.hauntedmc.dungeons.persistence.*;

/** Short fenced JDBC batches. Call only from repository workers, never Bukkit/provider workers. */
public final class RepoStore {
    public static final int FORMAT = 1;

    public record Release(
            UUID id,
            UUID dungeon,
            UUID parent,
            RepoVersion version,
            String hash,
            int format,
            String minecraft,
            String actor,
            String server,
            String message,
            Instant created,
            int files,
            long bytes) {}

    public record Entry(String name, Release head) {}

    private final DungeonDatabase database;
    private final Runnable checkpoint;

    public RepoStore(DungeonDatabase database) {
        this(database, () -> {});
    }

    public RepoStore(DungeonDatabase database, Runnable checkpoint) {
        this.database = database;
        this.checkpoint = checkpoint;
    }

    private <T> T tx(TransactionCallback<T> action) {
        checkpoint.run();
        return database.transaction(action).join();
    }

    private static List<Map<String, Object>> rows(Connection c, String sql, Object... args)
            throws SQLException {
        try (var statement = c.prepareStatement(sql)) {
            statement.setQueryTimeout(3);
            for (int n = 0; n < args.length; n++)
                statement.setObject(n + 1, args[n] instanceof UUID id ? id.toString() : args[n]);
            try (var result = statement.executeQuery()) {
                var list = new ArrayList<Map<String, Object>>();
                var meta = result.getMetaData();
                while (result.next()) {
                    var row = new HashMap<String, Object>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        Object value = result.getObject(i);
                        if (value instanceof Timestamp stamp) value = stamp.toInstant();
                        if (value instanceof java.time.LocalDateTime date)
                            value = date.toInstant(java.time.ZoneOffset.UTC);
                        row.put(meta.getColumnLabel(i), value);
                    }
                    list.add(row);
                }
                return list;
            }
        }
    }

    private static int update(Connection c, String sql, Object... args) throws SQLException {
        try (var statement = c.prepareStatement(sql)) {
            statement.setQueryTimeout(3);
            for (int n = 0; n < args.length; n++)
                statement.setObject(n + 1, args[n] instanceof UUID id ? id.toString() : args[n]);
            return statement.executeUpdate();
        }
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString((String) value);
    }

    private static int number(Map<String, Object> row, String name) {
        return ((Number) row.get(name)).intValue();
    }

    private static Release release(Map<String, Object> row) {
        return new Release(
                uuid(row.get("release_id")),
                uuid(row.get("dungeon_id")),
                uuid(row.get("parent_id")),
                row.get("major") == null
                        ? null
                        : new RepoVersion(
                                number(row, "major"), number(row, "minor"), number(row, "patch")),
                (String) row.get("content_hash"),
                number(row, "format_version"),
                (String) row.get("minecraft_version"),
                (String) row.get("actor"),
                (String) row.get("server_id"),
                (String) row.get("message"),
                (Instant) row.get("created_at"),
                number(row, "file_count"),
                ((Number) row.get("total_bytes")).longValue());
    }

    public List<Entry> catalog() {
        var result = new ArrayList<Entry>();
        String after = "";
        while (true) {
            String cursor = after;
            var page =
                    tx(
                            c ->
                                    rows(
                                            c,
                                            "SELECT e.repo_name,r.* FROM dungeon_repo_entry e JOIN dungeon_repo_release r ON r.release_id=e.head_id WHERE e.repo_name>? ORDER BY e.repo_name LIMIT 128",
                                            cursor));
            for (var row : page) result.add(new Entry((String) row.get("repo_name"), release(row)));
            if (page.size() < 128) return List.copyOf(result);
            after = (String) page.getLast().get("repo_name");
        }
    }

    public Entry entry(UUID dungeon) {
        return tx(
                c -> {
                    var found =
                            rows(
                                    c,
                                    "SELECT e.repo_name,r.* FROM dungeon_repo_entry e JOIN dungeon_repo_release r ON r.release_id=e.head_id WHERE e.dungeon_id=?",
                                    dungeon);
                    return found.isEmpty()
                            ? null
                            : new Entry(
                                    (String) found.getFirst().get("repo_name"),
                                    release(found.getFirst()));
                });
    }

    public Release find(UUID dungeon, RepoVersion version) {
        return tx(
                c -> {
                    var found =
                            rows(
                                    c,
                                    "SELECT * FROM dungeon_repo_release WHERE dungeon_id=? AND state='PUBLISHED' AND major=? AND minor=? AND patch=?",
                                    dungeon,
                                    version.major(),
                                    version.minor(),
                                    version.patch());
                    if (found.isEmpty())
                        throw new IllegalArgumentException(
                                "Published version not found: " + version);
                    return release(found.getFirst());
                });
    }

    public List<Release> history(UUID dungeon, int page) {
        if (page < 1 || page > 100000) throw new IllegalArgumentException("Invalid page");
        return tx(
                c ->
                        rows(
                                        c,
                                        "SELECT * FROM dungeon_repo_release WHERE dungeon_id=? AND state='PUBLISHED' ORDER BY major DESC,minor DESC,patch DESC LIMIT 20 OFFSET ?",
                                        dungeon,
                                        (page - 1) * 20)
                                .stream()
                                .map(RepoStore::release)
                                .toList());
    }

    public Release published(UUID id) {
        return database.transaction(
                        c -> {
                            var found =
                                    rows(
                                            c,
                                            "SELECT * FROM dungeon_repo_release WHERE release_id=? AND state='PUBLISHED'",
                                            id);
                            return found.isEmpty() ? null : release(found.getFirst());
                        })
                .join();
    }

    public void begin(
            UUID operation,
            UUID dungeon,
            UUID parent,
            RepoContent.Snapshot snapshot,
            String minecraft,
            String actor,
            String message) {
        if (actor.length() > 128 || message.length() > 1024)
            throw new IllegalArgumentException("Publication metadata too long");
        tx(
                c -> {
                    update(
                            c,
                            "INSERT IGNORE INTO dungeon_definition(dungeon_id) VALUES(?)",
                            dungeon);
                    update(
                            c,
                            "INSERT INTO dungeon_repo_release(release_id,dungeon_id,parent_id,state,content_hash,format_version,minecraft_version,actor,server_id,boot_id,message,created_at,expires_at,file_count,total_bytes) VALUES(?,?,?,'UPLOADING',?,?,?,?,?,?,?,UTC_TIMESTAMP(3),TIMESTAMPADD(HOUR,24,UTC_TIMESTAMP(3)),?,?)",
                            operation,
                            dungeon,
                            parent,
                            snapshot.hash(),
                            FORMAT,
                            minecraft,
                            actor,
                            database.serverId(),
                            database.bootId(),
                            message,
                            snapshot.items().size(),
                            snapshot.bytes());
                    return null;
                });
    }

    private void uploading(Connection c, UUID operation) throws SQLException {
        if (update(
                        c,
                        "UPDATE dungeon_repo_release SET expires_at=TIMESTAMPADD(HOUR,24,UTC_TIMESTAMP(3)) WHERE release_id=? AND state='UPLOADING' AND boot_id=? AND server_id=? AND expires_at>UTC_TIMESTAMP(3)",
                        operation,
                        database.bootId(),
                        database.serverId())
                != 1) throw new SQLException("Upload ownership expired");
    }

    public void file(UUID operation, int ordinal, RepoContent.Item item) {
        tx(
                c -> {
                    uploading(c, operation);
                    update(
                            c,
                            "INSERT INTO dungeon_repo_file(release_id,file_no,path,path_hash,file_hash,file_bytes,chunk_count) VALUES(?,?,?,?,?,?,?)",
                            operation,
                            ordinal,
                            item.path(),
                            DungeonSchema.checksum(
                                    item.path().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            item.hash(),
                            item.size(),
                            item.directory()
                                    ? 0
                                    : (int)
                                            ((item.size() + RepoContent.CHUNK_BYTES - 1)
                                                    / RepoContent.CHUNK_BYTES));
                    return null;
                });
    }

    public boolean referenceIfPresent(
            UUID operation, int file, int ordinal, String hash, int bytes) {
        return tx(
                c -> {
                    uploading(c, operation);
                    var existing =
                            rows(
                                    c,
                                    "SELECT raw_bytes FROM dungeon_repo_chunk WHERE chunk_hash=? FOR UPDATE",
                                    hash);
                    if (existing.isEmpty()) return false;
                    if (number(existing.getFirst(), "raw_bytes") != bytes)
                        throw new SQLException("Chunk hash collision");
                    update(
                            c,
                            "INSERT INTO dungeon_repo_file_chunk(release_id,file_no,chunk_no,chunk_hash) VALUES(?,?,?,?)",
                            operation,
                            file,
                            ordinal,
                            hash);
                    return true;
                });
    }

    public void chunk(UUID operation, int file, int ordinal, RepoContent.Chunk chunk) {
        tx(
                c -> {
                    uploading(c, operation);
                    update(
                            c,
                            "INSERT INTO dungeon_repo_chunk(chunk_hash,codec,raw_bytes,payload,created_at) VALUES(?,?,?,?,UTC_TIMESTAMP(3)) ON DUPLICATE KEY UPDATE chunk_hash=VALUES(chunk_hash)",
                            chunk.hash(),
                            chunk.codec(),
                            chunk.rawBytes(),
                            chunk.payload());
                    var existing =
                            rows(
                                    c,
                                    "SELECT raw_bytes FROM dungeon_repo_chunk WHERE chunk_hash=?",
                                    chunk.hash());
                    if (number(existing.getFirst(), "raw_bytes") != chunk.rawBytes())
                        throw new SQLException("Chunk hash collision");
                    update(
                            c,
                            "INSERT INTO dungeon_repo_file_chunk(release_id,file_no,chunk_no,chunk_hash) VALUES(?,?,?,?)",
                            operation,
                            file,
                            ordinal,
                            chunk.hash());
                    return null;
                });
    }

    public void reference(UUID operation, UUID source, int file, int after) {
        tx(
                c -> {
                    uploading(c, operation);
                    update(
                            c,
                            "INSERT INTO dungeon_repo_file_chunk(release_id,file_no,chunk_no,chunk_hash) SELECT ?,file_no,chunk_no,chunk_hash FROM dungeon_repo_file_chunk WHERE release_id=? AND file_no=? AND chunk_no>=? AND chunk_no<?",
                            operation,
                            source,
                            file,
                            after,
                            after + 128);
                    return null;
                });
    }

    public List<RepoContent.Item> manifest(Release release) {
        if (release.format() != FORMAT)
            throw new IllegalArgumentException("Unsupported repository format");
        var result = new ArrayList<RepoContent.Item>();
        int after = -1;
        while (true) {
            int cursor = after;
            var page =
                    tx(
                            c ->
                                    rows(
                                            c,
                                            "SELECT file_no,path,file_hash,file_bytes FROM dungeon_repo_file WHERE release_id=? AND file_no>? ORDER BY file_no LIMIT 128",
                                            release.id(),
                                            cursor));
            for (var row : page) {
                if (number(row, "file_no") != result.size())
                    throw new IllegalArgumentException("Non-contiguous manifest");
                result.add(
                        new RepoContent.Item(
                                (String) row.get("path"),
                                ((Number) row.get("file_bytes")).longValue(),
                                (String) row.get("file_hash")));
                if (result.size() > release.files())
                    throw new IllegalArgumentException("Manifest count mismatch");
            }
            if (page.size() < 128) break;
            after = number(page.getLast(), "file_no");
        }
        long bytes =
                result.stream()
                        .filter(i -> !i.directory())
                        .mapToLong(RepoContent.Item::size)
                        .reduce(0, Math::addExact);
        if (result.size() != release.files()
                || bytes != release.bytes()
                || !RepoContent.manifestHash(result).equals(release.hash()))
            throw new IllegalArgumentException("Corrupt release manifest");
        return List.copyOf(result);
    }

    public RepoContent.Chunk chunk(UUID release, int file, int ordinal) {
        return tx(
                c -> {
                    var found =
                            rows(
                                    c,
                                    "SELECT c.* FROM dungeon_repo_file_chunk f JOIN dungeon_repo_chunk c ON c.chunk_hash=f.chunk_hash WHERE f.release_id=? AND f.file_no=? AND f.chunk_no=?",
                                    release,
                                    file,
                                    ordinal);
                    if (found.size() != 1) throw new SQLException("Missing repository chunk");
                    var row = found.getFirst();
                    return new RepoContent.Chunk(
                            (String) row.get("chunk_hash"),
                            (String) row.get("codec"),
                            number(row, "raw_bytes"),
                            (byte[]) row.get("payload"));
                });
    }

    public void seal(UUID operation, List<RepoContent.Item> items) {
        for (int n = 0; n < items.size(); n++) {
            int file = n;
            RepoContent.Item item = items.get(n);
            tx(
                    c -> {
                        uploading(c, operation);
                        var counts =
                                rows(
                                                c,
                                                "SELECT COUNT(*) AS n,MIN(chunk_no) AS lo,MAX(chunk_no) AS hi FROM dungeon_repo_file_chunk WHERE release_id=? AND file_no=?",
                                                operation,
                                                file)
                                        .getFirst();
                        long expected =
                                item.directory()
                                        ? 0
                                        : (item.size() + RepoContent.CHUNK_BYTES - 1)
                                                / RepoContent.CHUNK_BYTES;
                        if (((Number) counts.get("n")).longValue() != expected
                                || expected > 0
                                        && (number(counts, "lo") != 0
                                                || number(counts, "hi") != expected - 1))
                            throw new SQLException("Incomplete file upload");
                        return null;
                    });
        }
        tx(
                c -> {
                    uploading(c, operation);
                    update(
                            c,
                            "UPDATE dungeon_repo_release SET state='SEALED' WHERE release_id=?",
                            operation);
                    return null;
                });
    }

    public Release publish(UUID operation, String name, String bump) {
        RepoContent.safeName(name);
        return tx(
                c -> {
                    var staged =
                            rows(
                                    c,
                                    "SELECT * FROM dungeon_repo_release WHERE release_id=? FOR UPDATE",
                                    operation);
                    if (staged.size() != 1) throw new SQLException("Upload not found");
                    var row = staged.getFirst();
                    if (row.get("state").equals("PUBLISHED")) return release(row);
                    if (!row.get("state").equals("SEALED")
                            || !row.get("boot_id").equals(database.bootId().toString()))
                        throw new SQLException("Upload is not sealed or owned");
                    UUID dungeon = uuid(row.get("dungeon_id"));
                    var heads =
                            rows(
                                    c,
                                    "SELECT e.head_id,r.major,r.minor,r.patch,r.content_hash FROM dungeon_repo_entry e JOIN dungeon_repo_release r ON r.release_id=e.head_id WHERE e.dungeon_id=? FOR UPDATE",
                                    dungeon);
                    UUID head = heads.isEmpty() ? null : uuid(heads.getFirst().get("head_id"));
                    if (!Objects.equals(head, uuid(row.get("parent_id"))))
                        throw new IllegalArgumentException(
                                "Repository advanced; fetch/pull latest and reapply your changes");
                    if (!heads.isEmpty()
                            && heads.getFirst().get("content_hash").equals(row.get("content_hash")))
                        throw new IllegalArgumentException("No content changes to publish");
                    RepoVersion version =
                            heads.isEmpty()
                                    ? RepoVersion.FIRST
                                    : new RepoVersion(
                                                    number(heads.getFirst(), "major"),
                                                    number(heads.getFirst(), "minor"),
                                                    number(heads.getFirst(), "patch"))
                                            .bump(bump);
                    update(
                            c,
                            "UPDATE dungeon_repo_release SET state='PUBLISHED',major=?,minor=?,patch=?,created_at=UTC_TIMESTAMP(3) WHERE release_id=?",
                            version.major(),
                            version.minor(),
                            version.patch(),
                            operation);
                    if (head == null)
                        update(
                                c,
                                "INSERT INTO dungeon_repo_entry(dungeon_id,repo_name,head_id) VALUES(?,?,?)",
                                dungeon,
                                name.toLowerCase(Locale.ROOT),
                                operation);
                    else if (update(
                                    c,
                                    "UPDATE dungeon_repo_entry SET head_id=? WHERE dungeon_id=? AND head_id=?",
                                    operation,
                                    dungeon,
                                    head)
                            != 1) throw new SQLException("Repository head changed");
                    return release(
                            rows(
                                            c,
                                            "SELECT * FROM dungeon_repo_release WHERE release_id=?",
                                            operation)
                                    .getFirst());
                });
    }

    /**
     * Work is deliberately bounded per invocation; live and published references are never pruned.
     */
    public void cleanup(BooleanSupplier stopping) {
        var expired =
                tx(
                        c ->
                                rows(
                                        c,
                                        "SELECT release_id FROM dungeon_repo_release WHERE state IN ('UPLOADING','SEALED','ABANDONED') AND expires_at<=UTC_TIMESTAMP(3) LIMIT 8"));
        for (var row : expired) {
            if (stopping.getAsBoolean()) return;
            UUID id = uuid(row.get("release_id"));
            boolean claimed =
                    tx(
                            c ->
                                    update(
                                                    c,
                                                    "UPDATE dungeon_repo_release SET state='ABANDONED' WHERE release_id=? AND state IN ('UPLOADING','SEALED','ABANDONED') AND expires_at<=UTC_TIMESTAMP(3)",
                                                    id)
                                            == 1);
            if (!claimed) continue;
            int removed =
                    tx(
                            c ->
                                    update(
                                            c,
                                            "DELETE FROM dungeon_repo_file_chunk WHERE release_id=? LIMIT 128",
                                            id));
            if (removed > 0) continue;
            removed =
                    tx(
                            c ->
                                    update(
                                            c,
                                            "DELETE FROM dungeon_repo_file WHERE release_id=? LIMIT 128",
                                            id));
            if (removed == 0)
                tx(
                        c ->
                                update(
                                        c,
                                        "DELETE FROM dungeon_repo_release WHERE release_id=? AND state='ABANDONED'",
                                        id));
        }
        var candidates =
                tx(
                        c ->
                                rows(
                                        c,
                                        "SELECT c.chunk_hash FROM dungeon_repo_chunk c WHERE c.created_at<TIMESTAMPADD(HOUR,-24,UTC_TIMESTAMP(3)) AND NOT EXISTS(SELECT 1 FROM dungeon_repo_file_chunk f WHERE f.chunk_hash=c.chunk_hash) LIMIT 32"));
        for (var row : candidates) {
            if (stopping.getAsBoolean()) return;
            tx(
                    c -> {
                        rows(
                                c,
                                "SELECT chunk_hash FROM dungeon_repo_chunk WHERE chunk_hash=? FOR UPDATE",
                                row.get("chunk_hash"));
                        update(
                                c,
                                "DELETE FROM dungeon_repo_chunk WHERE chunk_hash=? AND NOT EXISTS(SELECT 1 FROM dungeon_repo_file_chunk f WHERE f.chunk_hash=?)",
                                row.get("chunk_hash"),
                                row.get("chunk_hash"));
                        return null;
                    });
        }
    }
}
