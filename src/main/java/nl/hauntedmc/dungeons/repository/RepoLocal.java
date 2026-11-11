package nl.hauntedmc.dungeons.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Durable working-copy identity. Local files never pretend to be the database authority. */
public record RepoLocal(UUID dungeonId, UUID releaseId, RepoVersion version, String contentHash) {
    public RepoLocal {
        Objects.requireNonNull(dungeonId);
        Objects.requireNonNull(version);
        if (contentHash == null
                || !contentHash.matches("[a-f0-9]{64}")
                || (releaseId == null) != version.equals(RepoVersion.DRAFT))
            throw new IllegalArgumentException("Invalid repository metadata");
    }

    public static RepoLocal read(Path folder) throws IOException {
        Path marker = folder.resolve(RepoContent.MARKER);
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return null;
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unsafe repository marker");
        try {
            var values = RepoContent.readYaml(marker);
            if (!Objects.equals(values.get("format"), 1))
                throw new IOException("Unsupported local repository format");
            String release = String.valueOf(values.getOrDefault("release", ""));
            return new RepoLocal(
                    UUID.fromString((String) values.get("dungeon")),
                    release.isEmpty() ? null : UUID.fromString(release),
                    RepoVersion.parse((String) values.get("version")),
                    (String) values.get("hash"));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid local repository metadata", invalid);
        }
    }

    public void write(Path folder) throws IOException {
        String yaml =
                "# Managed by /dungeon repo. Do not edit.\nformat: 1\ndungeon: '"
                        + dungeonId
                        + "'\nrelease: '"
                        + (releaseId == null ? "" : releaseId)
                        + "'\nversion: '"
                        + version
                        + "'\nhash: '"
                        + contentHash
                        + "'\n";
        RepoContent.atomicWrite(
                folder.resolve(RepoContent.MARKER), yaml.getBytes(StandardCharsets.UTF_8));
    }

    public static boolean valid(Path folder, UUID dungeon) {
        try {
            var local = read(folder);
            return local != null && local.dungeonId().equals(dungeon);
        } catch (IOException invalid) {
            return false;
        }
    }
}
