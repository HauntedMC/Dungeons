package nl.hauntedmc.dungeons.runtime.staff;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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

    private record Swap(File live, File previous, File staging) {}

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
        return backup(dungeon, null);
    }

    private CompletableFuture<String> backup(DungeonDefinition dungeon, String retained) {
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
                        if (copy.equals(retained) || copy.equals(id)) continue;
                        FileUtils.deleteDirectory(child(destination.getParentFile(), copy));
                        excess--;
                    }
                    return id;
                });
    }

    public CompletableFuture<String> restore(DungeonDefinition dungeon, String id) {
        File copy = child(new File(root, "backups/" + dungeon.getWorldName()), id);
        if (!copy.isDirectory()) throw new IllegalArgumentException("Backup not found");
        try {
            var config = new YamlConfiguration();
            ConfigurationFile.loadSnapshot(config, new File(copy, "config.yml"));
        } catch (Exception failure) {
            throw new IllegalArgumentException("Backup configuration is unreadable", failure);
        }
        return backup(dungeon, id)
                .thenCompose(
                        safety -> {
                            var swap =
                                    new Swap(
                                            managed(dungeon.getFolder()),
                                            managed(
                                                    new File(
                                                            root,
                                                            "staging/previous-"
                                                                    + UUID.randomUUID())),
                                            managed(
                                                    new File(
                                                            root,
                                                            "staging/restore-"
                                                                    + UUID.randomUUID())));
                            return filesystem(swap.live, () -> install(swap, copy))
                                    .thenCompose(
                                            ignored ->
                                                    owner(
                                                                    () ->
                                                                            runtime.dungeonCatalog()
                                                                                            .loadDungeon(
                                                                                                    swap.live)
                                                                                    != null)
                                                            .handle(
                                                                    (loaded, failure) ->
                                                                            failure == null
                                                                                    && Boolean.TRUE
                                                                                            .equals(
                                                                                                    loaded)))
                                    .thenCompose(
                                            loaded ->
                                                    loaded
                                                            ? CompletableFuture.completedFuture(
                                                                    "Restored "
                                                                            + id
                                                                            + "; safety backup "
                                                                            + safety)
                                                            : rollback(swap, safety));
                        });
    }

    private boolean install(Swap swap, File copy) throws IOException {
        rejectLinks(swap.live.toPath());
        rejectLinks(copy.toPath());
        FileUtils.copyDirectory(
                copy, swap.staging, file -> !RUNTIME_FILES.contains(file.getName()));
        Files.createDirectories(swap.previous.toPath().getParent());
        Files.move(swap.live.toPath(), swap.previous.toPath(), StandardCopyOption.ATOMIC_MOVE);
        try {
            Files.move(swap.staging.toPath(), swap.live.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            try {
                Files.move(
                        swap.previous.toPath(), swap.live.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException rollback) {
                failure.addSuppressed(rollback);
            }
            throw failure;
        }
        return true;
    }

    private CompletableFuture<String> rollback(Swap swap, String safety) {
        return filesystem(
                        swap.live,
                        () -> {
                            File failed =
                                    managed(new File(root, "staging/failed-" + UUID.randomUUID()));
                            Files.move(
                                    swap.live.toPath(),
                                    failed.toPath(),
                                    StandardCopyOption.ATOMIC_MOVE);
                            Files.move(
                                    swap.previous.toPath(),
                                    swap.live.toPath(),
                                    StandardCopyOption.ATOMIC_MOVE);
                            return false;
                        })
                .thenCompose(
                        ignored ->
                                owner(
                                        () -> {
                                            if (runtime.dungeonCatalog().loadDungeon(swap.live)
                                                    == null)
                                                throw new IllegalStateException(
                                                        "Prior files restored, but reload also"
                                                                + " failed; maintenance retained."
                                                                + " Safety backup "
                                                                + safety);
                                            throw new IllegalStateException(
                                                    "Backup did not load; previous template"
                                                            + " restored. Safety backup "
                                                            + safety);
                                        }));
    }

    public CompletableFuture<String> archiveAction(String id, boolean purge) {
        String name = archivedDungeonName(id);
        File source = child(new File(root, "archives"), id);
        File destination = child(runtime.dungeonFiles(), name);
        if (!source.isDirectory()) throw new IllegalArgumentException("Archive not found");
        if (!purge && (destination.exists() || runtime.dungeonCatalog().get(name) != null))
            throw new IllegalArgumentException("Dungeon name is already in use");
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

    private <T> CompletableFuture<T> filesystem(
            File file, Callable<T> action) {
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
