package nl.hauntedmc.dungeons.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoContentTest {
    @TempDir Path directory;
    private final RepoContent.Limits limits = new RepoContent.Limits(32L * 1024 * 1024, 100);

    private Path content() throws Exception {
        Path source = Files.createDirectories(directory.resolve("source"));
        Files.writeString(
                source.resolve("config.yml"),
                "dungeon:\n  id: '"
                        + UUID.randomUUID()
                        + "'\n  type: static\n  enabled: true\nlocations:\n  exit:\n    world: survival\n    x: 12\n  start:\n    x: 2\nplayers:\n  join_commands: []\n");
        return source;
    }

    @Test
    void versionsUseStrictArithmeticAndNeverWrap() {
        assertEquals("1.2.4", RepoVersion.parse("1.2.3").bump("patch").toString());
        assertEquals("1.3.0", RepoVersion.parse("1.2.3").bump("minor").toString());
        assertEquals("2.0.0", RepoVersion.parse("1.2.3").bump("major").toString());
        for (String invalid : List.of("1.2", "01.2.3", "-1.2.3", "1.2.3.4", "2147483648.0.0"))
            assertThrows(IllegalArgumentException.class, () -> RepoVersion.parse(invalid));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RepoVersion(Integer.MAX_VALUE, 0, 0).bump("major"));
    }

    @Test
    void localSettingsAreExcludedAndAllAuthoredFilesRoundTrip() throws Exception {
        Path source = content();
        Files.createDirectories(source.resolve("rooms/empty"));
        Files.createDirectories(source.resolve("region"));
        Files.write(source.resolve("region/r.0.0.mca"), new byte[] {1, 3, 7});
        Files.write(source.resolve("empty"), new byte[0]);
        Files.createDirectories(source.resolve("playerdata"));
        Files.writeString(source.resolve("playerdata/private"), "secret");
        Files.writeString(source.resolve("uid.dat"), "local");
        var one = RepoContent.snapshot(source, directory.resolve("one"), limits);
        String config = Files.readString(source.resolve("config.yml"));
        Files.writeString(
                source.resolve("config.yml"),
                config.replace("enabled: true", "enabled: false")
                        .replace("survival", "another_world")
                        .replace("x: 12", "x: 999"));
        var two = RepoContent.snapshot(source, directory.resolve("two"), limits);
        assertEquals(one.hash(), two.hash());
        assertEquals(one.items(), two.items());
        assertFalse(Files.exists(one.root().resolve("playerdata")));
        assertFalse(Files.exists(one.root().resolve("uid.dat")));
        assertTrue(Files.isDirectory(one.root().resolve("rooms/empty")));
        assertArrayEquals(
                new byte[] {1, 3, 7}, Files.readAllBytes(one.root().resolve("region/r.0.0.mca")));
        Files.writeString(source.resolve("config.yml"), config.replace("x: 2", "x: 3"));
        assertNotEquals(
                one.hash(),
                RepoContent.snapshot(source, directory.resolve("three"), limits).hash());
    }

    @Test
    void chunksAreBoundedVerifiedAndCompressedOnlyWhenUseful() throws Exception {
        byte[] compressible = new byte[RepoContent.CHUNK_BYTES];
        var gzip = RepoContent.encode(compressible);
        assertEquals("gzip", gzip.codec());
        assertArrayEquals(compressible, RepoContent.decode(gzip));
        byte[] random = new byte[RepoContent.CHUNK_BYTES];
        new Random(123).nextBytes(random);
        var raw = RepoContent.encode(random);
        assertEquals("raw", raw.codec());
        assertArrayEquals(random, RepoContent.decode(raw));
        assertThrows(
                java.io.IOException.class,
                () ->
                        RepoContent.decode(
                                new RepoContent.Chunk(
                                        "0".repeat(64),
                                        raw.codec(),
                                        raw.rawBytes(),
                                        raw.payload())));
        assertThrows(
                java.io.IOException.class,
                () ->
                        RepoContent.decode(
                                new RepoContent.Chunk(gzip.hash(), "gzip", 2, gzip.payload())));
        assertThrows(
                java.io.IOException.class,
                () -> RepoContent.encode(new byte[RepoContent.CHUNK_BYTES + 1]));
    }

    @Test
    void hostilePathsLinksDuplicatesAndLimitsAreRejected() throws Exception {
        for (String path :
                List.of("../config.yml", "/root", "a/../b", "a\\b", "a//b", "a\nb", "C:/x"))
            assertThrows(IllegalArgumentException.class, () -> RepoContent.safePath(path));
        Path source = content();
        Files.createSymbolicLink(source.resolve("linked"), source.resolve("config.yml"));
        assertThrows(
                java.io.IOException.class,
                () -> RepoContent.snapshot(source, directory.resolve("links"), limits));
        Files.delete(source.resolve("linked"));
        Files.writeString(source.resolve("A"), "a");
        Files.writeString(source.resolve("a"), "a");
        assertThrows(
                java.io.IOException.class,
                () -> RepoContent.snapshot(source, directory.resolve("case"), limits));
        Files.delete(source.resolve("A"));
        Files.delete(source.resolve("a"));
        assertThrows(
                java.io.IOException.class,
                () ->
                        RepoContent.snapshot(
                                source, directory.resolve("size"), new RepoContent.Limits(1, 100)));
        Files.writeString(source.resolve("extra"), "x");
        assertThrows(
                java.io.IOException.class,
                () ->
                        RepoContent.snapshot(
                                source,
                                directory.resolve("count"),
                                new RepoContent.Limits(10000, 1)));
    }

    @Test
    void metadataDetectsCorruptionAndIdentityMismatch() throws Exception {
        Path source = content();
        UUID id = UUID.randomUUID();
        assertNull(RepoLocal.read(source));
        var local = new RepoLocal(id, null, RepoVersion.DRAFT, "a".repeat(64));
        local.write(source);
        assertEquals(local, RepoLocal.read(source));
        assertTrue(RepoLocal.valid(source, id));
        assertFalse(RepoLocal.valid(source, UUID.randomUUID()));
        Files.writeString(source.resolve(RepoContent.MARKER), "format: 9\n");
        assertFalse(RepoLocal.valid(source, id));
        assertThrows(java.io.IOException.class, () -> RepoLocal.read(source));
    }

    @Test
    void configProjectionIsStableAndKeepsAuthoredComments() throws Exception {
        byte[] input =
                ("# authored\ndungeon:\n  # LOCAL — NOT SYNCED\n  enabled: true\n  # name\n  display_name: Hello\nlocations:\n  exit: null\n  start: null\n")
                        .getBytes(StandardCharsets.UTF_8);
        byte[] projected = RepoContent.portableConfig(input);
        assertArrayEquals(projected, RepoContent.portableConfig(projected));
        assertTrue(new String(projected, StandardCharsets.UTF_8).contains("# authored"));
        assertFalse(new String(projected, StandardCharsets.UTF_8).contains("enabled:"));
    }
}
