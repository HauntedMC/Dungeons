package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import nl.hauntedmc.dungeons.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.mysql.MySQLContainer;

class RepoStoreIT {
    @TempDir Path directory;

    @Test
    void immutableReleasesDeduplicateRejectStalePublishAndSurviveLostCommitReplies()
            throws Exception {
        try (var mysql = new MySQLContainer("mysql:8.4")) {
            mysql.start();
            try (var c =
                    DriverManager.getConnection(
                            mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                DungeonSql.update(
                        c,
                        "CREATE TABLE player_entity(id BIGINT AUTO_INCREMENT PRIMARY KEY,uuid VARCHAR(36) NOT NULL UNIQUE)");
            }
            try (var first = new DatabaseTestHost(mysql, "repo-a", 1);
                    var second = new DatabaseTestHost(mysql, "repo-b", 1)) {
                var a = new RepoStore(first.database);
                var b = new RepoStore(second.database);
                UUID dungeon = UUID.randomUUID();
                var initial = snapshot("first");
                UUID one = stage(a, dungeon, null, initial);
                a.seal(one, initial.items());
                var published = a.publish(one, "Crypt", "patch");
                assertEquals(RepoVersion.FIRST, published.version());
                assertEquals(published, b.entry(dungeon).head());
                assertEquals(initial.items(), b.manifest(published));
                assertArrayEquals("first".getBytes(), RepoContent.decode(b.chunk(one, 0, 0)));
                var changed = snapshot("second");
                UUID two = stage(a, dungeon, one, changed);
                a.seal(two, changed.items());
                UUID stale = stage(b, dungeon, one, changed);
                b.seal(stale, changed.items());
                first.failAfterCommit.set(true);
                assertThrows(CompletionException.class, () -> a.publish(two, "Crypt", "minor"));
                var committed = b.published(two);
                assertNotNull(committed);
                assertEquals(RepoVersion.parse("1.1.0"), committed.version());
                assertEquals(committed, a.publish(two, "Crypt", "minor"));
                assertThrows(CompletionException.class, () -> b.publish(stale, "Crypt", "patch"));
                assertEquals(2, a.history(dungeon, 1).size());
                assertEquals(published, a.find(dungeon, RepoVersion.FIRST));
                try (var c =
                        DriverManager.getConnection(
                                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                    assertEquals(
                            2,
                            ((Number)
                                            DungeonSql.rows(
                                                            c,
                                                            "SELECT COUNT(*) AS n FROM dungeon_repo_chunk")
                                                    .getFirst()
                                                    .get("n"))
                                    .intValue());
                    DungeonSql.update(
                            c,
                            "UPDATE dungeon_repo_chunk SET payload=? WHERE chunk_hash=?",
                            new byte[] {0},
                            DungeonSchema.checksum("first".getBytes()));
                }
                assertThrows(
                        java.io.IOException.class, () -> RepoContent.decode(a.chunk(one, 0, 0)));
                first.database.heartbeat().get(5, TimeUnit.SECONDS);
                second.database.heartbeat().get(5, TimeUnit.SECONDS);
                OrmTestSupport.initialize(mysql, "validate");
            }
        }
    }

    private RepoContent.Snapshot snapshot(String text) throws Exception {
        Path root = Files.createDirectories(directory.resolve(UUID.randomUUID().toString()));
        Files.writeString(root.resolve("map"), text);
        var item =
                new RepoContent.Item("map", text.length(), DungeonSchema.checksum(text.getBytes()));
        return new RepoContent.Snapshot(
                root, List.of(item), RepoContent.manifestHash(List.of(item)), text.length());
    }

    private UUID stage(RepoStore store, UUID dungeon, UUID parent, RepoContent.Snapshot snapshot)
            throws Exception {
        UUID op = UUID.randomUUID();
        store.begin(op, dungeon, parent, snapshot, "26.1", "actor", "test");
        store.file(op, 0, snapshot.items().getFirst());
        store.chunk(
                op, 0, 0, RepoContent.encode(Files.readAllBytes(snapshot.root().resolve("map"))));
        return op;
    }
}
