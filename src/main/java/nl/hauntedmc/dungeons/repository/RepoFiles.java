package nl.hauntedmc.dungeons.repository;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Same-filesystem swaps and fsynced journals, recovered before any dungeon can preload. */
public final class RepoFiles {
    private final Path pluginRoot;
    private final Path root;
    private final Path dungeons;

    public RepoFiles(Path pluginRoot, Path dungeons) throws IOException {
        this.pluginRoot = pluginRoot.toAbsolutePath().normalize();
        this.root = this.pluginRoot.resolve("repository");
        this.dungeons = dungeons.toAbsolutePath().normalize();
        Files.createDirectories(root.resolve("journals"));
        Files.createDirectories(root.resolve("staging"));
        managed(root);
        managed(this.dungeons);
    }

    public Path root() {
        return root;
    }

    public Path live(String name) throws IOException {
        RepoContent.safeName(name);
        return managed(dungeons.resolve(name));
    }

    public Path stage(UUID id) throws IOException {
        Path path = managed(root.resolve("staging").resolve(id.toString()));
        Files.createDirectories(path);
        return path;
    }

    public Path managed(Path path) throws IOException {
        path = path.toAbsolutePath().normalize();
        if (!path.startsWith(pluginRoot) || path.equals(pluginRoot))
            throw new IOException("Path outside plugin storage");
        Path current = pluginRoot;
        for (Path part : pluginRoot.relativize(path)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current))
                throw new IOException("Symbolic links in managed storage are unsupported");
        }
        return path;
    }

    private Path journal(UUID id) {
        return root.resolve("journals").resolve(id + ".yml");
    }

    public void publication(UUID id, String name) throws IOException {
        RepoContent.safeName(name);
        write(id, "kind: publish\nname: '" + name + "'\n");
    }

    private void write(UUID id, String text) throws IOException {
        RepoContent.atomicWrite(journal(id), text.getBytes(StandardCharsets.UTF_8));
    }

    public void complete(UUID id) throws IOException {
        Files.deleteIfExists(journal(id));
        RepoContent.syncDirectory(root.resolve("journals"));
    }

    public Path install(UUID id, String name, Path staging) throws IOException {
        Path live = live(name);
        staging = managed(staging);
        Path backup = managed(pluginRoot.resolve("backups").resolve(name).resolve("repo-" + id));
        Files.createDirectories(backup.getParent());
        if (!Files.getFileStore(staging).equals(Files.getFileStore(live.getParent()))
                || !Files.getFileStore(staging).equals(Files.getFileStore(backup.getParent())))
            throw new IOException(
                    "Repository staging, dungeons and backups must share a filesystem");
        syncTree(staging);
        boolean existed = Files.exists(live, LinkOption.NOFOLLOW_LINKS);
        write(
                id,
                "kind: install\nname: '"
                        + name
                        + "'\nprevious: "
                        + existed
                        + "\ncommitted: false\n");
        if (existed) {
            move(live, backup);
            write(
                    id,
                    "kind: install\nname: '"
                            + name
                            + "'\nprevious: true\ncommitted: false\nphase: moved\n");
        }
        move(staging, live);
        return backup;
    }

    public void commit(UUID id, String name, boolean previous) throws IOException {
        write(
                id,
                "kind: install\nname: '"
                        + name
                        + "'\nprevious: "
                        + previous
                        + "\ncommitted: true\n");
        complete(id);
    }

    public void rollback(UUID id) throws IOException {
        if (!Files.exists(journal(id))) return;
        var values = RepoContent.readYaml(journal(id));
        recoverInstall(id, values);
    }

    private void recoverInstall(UUID id, Map<String, Object> values) throws IOException {
        String name = (String) values.get("name");
        Path live = live(name);
        Path backup = managed(pluginRoot.resolve("backups").resolve(name).resolve("repo-" + id));
        if (!Boolean.TRUE.equals(values.get("committed"))) {
            if (Files.exists(backup)) {
                if (Files.exists(live))
                    move(live, root.resolve("staging").resolve("failed-" + UUID.randomUUID()));
                move(backup, live);
            } else if (Boolean.TRUE.equals(values.get("previous"))
                    && (!Files.exists(live) || Objects.equals(values.get("phase"), "moved"))) {
                throw new IOException("Recovery safety backup is missing for " + name);
            } else if (!Boolean.TRUE.equals(values.get("previous")) && Files.exists(live)) {
                move(live, root.resolve("staging").resolve("failed-" + UUID.randomUUID()));
            }
        }
        complete(id);
    }

    public void recover(RepoStore database) throws IOException {
        try (var files = Files.list(root.resolve("journals"))) {
            for (Path file : files.sorted().toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Unsafe recovery journal");
                String filename = file.getFileName().toString();
                UUID id = UUID.fromString(filename.substring(0, filename.length() - 4));
                var values = RepoContent.readYaml(file);
                if (Objects.equals(values.get("kind"), "install")) recoverInstall(id, values);
                else if (Objects.equals(values.get("kind"), "publish")) {
                    var release = database.published(id);
                    if (release != null) {
                        Path live = live((String) values.get("name"));
                        var config = RepoContent.readYaml(live.resolve("config.yml"));
                        if (!(config.get("dungeon") instanceof Map<?, ?> dungeon)
                                || !release.dungeon().toString().equals(dungeon.get("id")))
                            throw new IOException(
                                    "Cannot recover publication: local identity changed");
                        new RepoLocal(
                                        release.dungeon(),
                                        release.id(),
                                        release.version(),
                                        release.hash())
                                .write(live);
                    }
                    complete(id);
                    RepoContent.deleteTree(root.resolve("staging").resolve(id.toString()));
                } else throw new IOException("Unknown recovery journal kind");
            }
        }
        try (var staging = Files.list(root.resolve("staging"))) {
            for (Path path : staging.toList()) {
                try {
                    UUID.fromString(path.getFileName().toString());
                } catch (IllegalArgumentException quarantine) {
                    continue;
                }
                RepoContent.deleteTree(managed(path));
            }
        }
    }

    public void prune(String name) throws IOException {
        // Never prune while another journal could refer to a recovery backup.
        try (var journals = Files.list(root.resolve("journals"))) {
            if (journals.findAny().isPresent()) return;
        }
        Path parent = managed(pluginRoot.resolve("backups").resolve(name));
        try (var files = Files.list(parent)) {
            var backups =
                    files.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                            .sorted(Comparator.comparingLong(RepoFiles::modified).reversed())
                            .toList();
            for (int i = 10; i < backups.size(); i++)
                RepoContent.deleteTree(managed(backups.get(i)));
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException failure) {
            return Long.MAX_VALUE;
        }
    }

    private static void move(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        RepoContent.syncDirectory(from.getParent());
        RepoContent.syncDirectory(to.getParent());
    }

    private static void syncTree(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            for (Path file : walk.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("Unsafe staging path");
                if (Files.isRegularFile(file))
                    try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                        channel.force(true);
                    }
                else RepoContent.syncDirectory(file);
            }
        }
    }
}
