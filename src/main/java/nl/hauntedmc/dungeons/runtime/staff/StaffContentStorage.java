package nl.hauntedmc.dungeons.runtime.staff;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.repository.RepoContent;
import nl.hauntedmc.dungeons.repository.RepoFiles;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;

/** Filesystem work runs after drain. Backups never contain progression or key ledgers. */
public final class StaffContentStorage {
    private static final Set<String> RUNTIME_FILES =
            Set.of(
                    "players",
                    "playerdata",
                    "stats",
                    "advancements",
                    "access_keys.yml",
                    "session.lock",
                    "uid.dat",
                    ".dungeons-editor-recovery");
    private final DungeonsRuntime runtime;
    private final File root;

    public StaffContentStorage(DungeonsRuntime runtime) {
        this.runtime = runtime;
        this.root = runtime.environment().plugin().getDataFolder();
    }

    public List<String> archives() {
        return directories(managed(new File(root, "archives")));
    }

    public List<String> backups(String dungeon) {
        validName(dungeon);
        return directories(managed(new File(root, "backups/" + dungeon)));
    }

    public String archiveRevision(String id) {
        File archive = child(new File(root, "archives"), id);
        if (!archive.isDirectory()) throw new IllegalArgumentException("Archive not found");
        return archive.getName() + "/" + archive.lastModified();
    }

    public String archivedDungeonName(String id) {
        if (id.length() < 38 || id.charAt(id.length() - 37) != '-')
            throw new IllegalArgumentException("Invalid archive ID");
        UUID.fromString(id.substring(id.length() - 36));
        String name = id.substring(0, id.length() - 37);
        validName(name);
        return name;
    }

    public CompletableFuture<String> backup(DungeonDefinition dungeon) {
        File source = managed(dungeon.getFolder());
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID();
        File destination = child(new File(root, "backups/" + dungeon.getWorldName()), id);
        return filesystem(
                source,
                () -> {
                    rejectLinks(source.toPath());
                    File staging = managed(new File(destination.getParentFile(), ".pending-" + id));
                    try {
                        FileUtils.copyDirectory(
                                source, staging, file -> !RUNTIME_FILES.contains(file.getName()));
                        Files.move(
                                staging.toPath(),
                                destination.toPath(),
                                StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        if (staging.exists()) FileUtils.deleteDirectory(staging);
                    }
                    var copies = directories(destination.getParentFile());
                    int excess = copies.size() - 10;
                    for (String copy : copies) {
                        if (excess <= 0) break;
                        if (copy.equals(id)) continue;
                        FileUtils.deleteDirectory(child(destination.getParentFile(), copy));
                        excess--;
                    }
                    return id;
                });
    }

    public String backupRevision(DungeonDefinition dungeon, String id) {
        File copy = child(new File(root, "backups/" + dungeon.getWorldName()), id);
        File file = new File(copy, "config.yml");
        if (!copy.isDirectory()) throw new IllegalArgumentException("Backup not found");
        try {
            requireBackupIdentity(file.toPath(), dungeon.getDungeonId());
        } catch (IOException failure) {
            throw new IllegalArgumentException("Backup configuration is unreadable", failure);
        }
        return id + "/" + copy.lastModified() + "/" + file.lastModified();
    }

    private void requireBackupIdentity(Path config, UUID identity) throws IOException {
        if (!(RepoContent.readYaml(config).get("dungeon") instanceof Map<?, ?> values)
                || !identity.toString().equalsIgnoreCase(Objects.toString(values.get("id"), "")))
            throw new IllegalArgumentException("Backup belongs to a different dungeon identity");
    }

    public CompletableFuture<String> restore(DungeonDefinition dungeon, String id) {
        backupRevision(dungeon, id);
        String name = dungeon.getWorldName();
        UUID identity = dungeon.getDungeonId();
        File live = managed(dungeon.getFolder());
        File copy = child(new File(root, "backups/" + name), id);
        UUID operation = UUID.randomUUID();
        return filesystem(
                        live,
                        () -> {
                            var files = new RepoFiles(root.toPath(), live.toPath().getParent());
                            Path stage = files.stage(operation);
                            try {
                                rejectLinks(live.toPath());
                                rejectLinks(copy.toPath());
                                Path replacement = stage.resolve("restore");
                                FileUtils.copyDirectory(
                                        copy,
                                        replacement.toFile(),
                                        file -> !RUNTIME_FILES.contains(file.getName()));
                                requireBackupIdentity(replacement.resolve("config.yml"), identity);
                                files.install(operation, name, replacement);
                                return files;
                            } catch (IOException | RuntimeException failure) {
                                try {
                                    files.rollback(operation);
                                    RepoContent.deleteTree(stage);
                                } catch (IOException | RuntimeException rollbackFailure) {
                                    failure.addSuppressed(rollbackFailure);
                                }
                                throw failure;
                            }
                        })
                .thenCompose(
                        files ->
                                owner(() -> runtime.dungeonCatalog().loadDungeon(live) != null)
                                        .handle(
                                                (loaded, failure) ->
                                                        failure == null
                                                                && Boolean.TRUE.equals(loaded))
                                        .thenCompose(
                                                loaded ->
                                                        filesystem(
                                                                live,
                                                                () -> {
                                                                    if (loaded) {
                                                                        files.commit(
                                                                                operation, name,
                                                                                true);
                                                                        RepoContent.deleteTree(
                                                                                files.stage(
                                                                                        operation));
                                                                        try {
                                                                            files.prune(
                                                                                    name,
                                                                                    operation);
                                                                        } catch (
                                                                                IOException
                                                                                        cleanup) {
                                                                            runtime.environment()
                                                                                    .logger()
                                                                                    .warn(
                                                                                            "Backup restored; could not prune old safety backups",
                                                                                            cleanup);
                                                                        }
                                                                        return "Restored "
                                                                                + id
                                                                                + "; safety backup repo-"
                                                                                + operation;
                                                                    }
                                                                    files.rollback(operation);
                                                                    RepoContent.deleteTree(
                                                                            files.stage(operation));
                                                                    return null;
                                                                }))
                                        .thenCompose(
                                                result ->
                                                        result != null
                                                                ? CompletableFuture.completedFuture(
                                                                        result)
                                                                : owner(
                                                                        () -> {
                                                                            if (runtime.dungeonCatalog()
                                                                                            .loadDungeon(
                                                                                                    live)
                                                                                    == null)
                                                                                throw new IllegalStateException(
                                                                                        "Prior files restored, but reload also failed; maintenance retained");
                                                                            throw new IllegalStateException(
                                                                                    "Backup did not load; previous template restored");
                                                                        })));
    }

    public void requireRestorableArchive(String id) {
        archiveRevision(id);
        String name = archivedDungeonName(id);
        if (child(runtime.dungeonFiles(), name).exists()
                || runtime.dungeonCatalog().get(name) != null)
            throw new IllegalArgumentException("Dungeon name is already in use");
    }

    public CompletableFuture<String> archiveAction(String id, boolean purge) {
        String name = archivedDungeonName(id);
        File source = child(new File(root, "archives"), id);
        File destination = child(runtime.dungeonFiles(), name);
        if (!source.isDirectory()) throw new IllegalArgumentException("Archive not found");
        if (!purge) requireRestorableArchive(id);
        return filesystem(
                        source,
                        () -> {
                            rejectLinks(source.toPath());
                            if (purge) FileUtils.deleteDirectory(source);
                            else
                                Files.move(
                                        source.toPath(),
                                        destination.toPath(),
                                        StandardCopyOption.ATOMIC_MOVE);
                            return true;
                        })
                .thenCompose(
                        ignored -> {
                            if (purge)
                                return CompletableFuture.completedFuture(
                                        "Archive permanently purged");
                            return owner(
                                            () ->
                                                    runtime.dungeonCatalog()
                                                                    .loadDungeon(destination)
                                                            != null)
                                    .handle(
                                            (loaded, failure) ->
                                                    failure == null && Boolean.TRUE.equals(loaded))
                                    .thenCompose(
                                            loaded -> {
                                                if (loaded)
                                                    return CompletableFuture.completedFuture(
                                                            "Archive restored; maintenance remains"
                                                                    + " enabled");
                                                return filesystem(
                                                        source,
                                                        () -> {
                                                            Files.move(
                                                                    destination.toPath(),
                                                                    source.toPath(),
                                                                    StandardCopyOption.ATOMIC_MOVE);
                                                            throw new IllegalStateException(
                                                                    "Archived content did not load;"
                                                                            + " files returned to"
                                                                            + " archive");
                                                        });
                                            });
                        });
    }

    private File child(File parent, String name) {
        validName(name);
        return managed(new File(parent, name));
    }

    private File managed(File file) {
        try {
            Path base = root.getCanonicalFile().toPath();
            Path path = file.getCanonicalFile().toPath();
            if (!path.startsWith(base) || path.equals(base) || Files.isSymbolicLink(file.toPath()))
                throw new IllegalArgumentException("Unsafe storage path");
            return file;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot resolve storage path", failure);
        }
    }

    private static void validName(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Invalid storage ID");
    }

    private static List<String> directories(File root) {
        File[] entries = root.listFiles();
        if (entries == null) return List.of();
        return Arrays.stream(entries)
                .filter(File::isDirectory)
                .filter(f -> !f.getName().startsWith("."))
                .filter(f -> !Files.isSymbolicLink(f.toPath()))
                .map(File::getName)
                .sorted()
                .toList();
    }

    private static void rejectLinks(Path source) throws IOException {
        try (var walk = Files.walk(source)) {
            if (walk.anyMatch(Files::isSymbolicLink))
                throw new IOException("Symbolic links are not accepted in managed content storage");
        }
    }

    private <T> CompletableFuture<T> filesystem(File file, Callable<T> action) {
        return ConfigurationFile.filesystemAsync(runtime.environment().plugin(), file, action);
    }

    private <T> CompletableFuture<T> owner(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        Runnable work =
                () -> {
                    try {
                        result.complete(action.get());
                    } catch (Exception failure) {
                        result.completeExceptionally(failure);
                    }
                };
        try {
            if (Bukkit.isPrimaryThread()) work.run();
            else Bukkit.getScheduler().runTask(runtime.environment().plugin(), work);
        } catch (RuntimeException rejected) {
            result.completeExceptionally(rejected);
        }
        return result;
    }
}
