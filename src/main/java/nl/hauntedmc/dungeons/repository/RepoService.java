package nl.hauntedmc.dungeons.repository;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import nl.hauntedmc.dungeons.config.*;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.persistence.DungeonSchema;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.staff.StaffOperations;
import nl.hauntedmc.dungeons.runtime.staff.StaffPermissions;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/** Coordinates portable content, fenced publication, and owner-thread world replacement. */
public final class RepoService implements AutoCloseable {
    private record Target(String name, UUID id, Path folder, DungeonDefinition definition) {}

    private record Prepared(
            UUID operation,
            RepoStore.Entry entry,
            RepoStore.Release release,
            RepoContent.Snapshot snapshot,
            RepoValidation.Result validation) {}

    private final DungeonsRuntime runtime;
    private final RepoStore store;
    private final RepoFiles files;
    private final RepoContent.Limits limits;
    private final ThreadPoolExecutor worker;
    private final Map<String, UUID> reservations = new HashMap<>();
    private final Set<CompletableFuture<?>> ownerCalls = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean fetching = new AtomicBoolean();
    private final Set<CompletableFuture<?>> pendingWorkers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> waitingPulls = new HashMap<>();
    private final RepoApprovals<Prepared> forceApprovals = new RepoApprovals<>();

    private record RollbackPlan(
            UUID dungeon, RepoVersion version, String bump, String message, UUID head) {}

    private final RepoApprovals<RollbackPlan> rollbackApprovals = new RepoApprovals<>();
    private final Map<StaffOperations.Operation, Job> transferProgress = new IdentityHashMap<>();
    private final ThreadLocal<Job> currentJob = new ThreadLocal<>();

    private static final class Job {
        private volatile String progress = "Queued repository transfer";
        private boolean cancelled;
        private boolean committing;

        synchronized boolean cancel() {
            if (committing) return false;
            cancelled = true;
            return true;
        }

        synchronized void check() throws IOException {
            if (cancelled) throw new IOException("Repository operation cancelled");
        }

        synchronized void commit() throws IOException {
            check();
            committing = true;
            progress = "Committing release or installation";
        }
    }

    private volatile List<RepoStore.Entry> catalog = List.of();
    private volatile boolean closed;
    private volatile String catalogProblem = "Not fetched yet";
    private volatile long fetchedAt;
    private volatile long nextFetch;
    private long backoff = 30;
    private BukkitTask ticker;
    private final ThreadLocal<Long> deadline = new ThreadLocal<>();
    private final int timeoutSeconds;
    private final String minecraft;

    public RepoService(DungeonsRuntime runtime) {
        this.runtime = runtime;
        this.store =
                new RepoStore(
                        runtime.database().database(),
                        () -> {
                            try {
                                check();
                            } catch (IOException stopped) {
                                throw new CompletionException(stopped);
                            }
                        });
        this.limits =
                new RepoContent.Limits(
                        runtime.config()
                                .getLong("repository.max-snapshot-bytes", 16L * 1024 * 1024 * 1024),
                        runtime.config().getInt("repository.max-files", 100000));
        this.timeoutSeconds = runtime.config().getInt("repository.operation-timeout-seconds", 1800);
        int queued = runtime.config().getInt("repository.queued-operations", 8);
        if (timeoutSeconds < 30 || queued < 1 || queued > 128)
            throw new IllegalArgumentException("Invalid repository operation limits");
        this.minecraft = Bukkit.getMinecraftVersion();
        this.worker =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(queued),
                        task -> {
                            var thread = new Thread(task, "Dungeons-content-repository");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
        try {
            this.files =
                    new RepoFiles(
                            runtime.environment().plugin().getDataFolder().toPath(),
                            runtime.dungeonFiles().toPath());
            submit(
                            () -> {
                                files.recover(store);
                                try {
                                    var cached =
                                            RepoCatalog.read(files.root().resolve("index.yml"));
                                    catalog = cached.entries();
                                    fetchedAt = cached.timestamp();
                                } catch (IOException invalidCache) {
                                    runtime.environment()
                                            .plugin()
                                            .getSLF4JLogger()
                                            .warn(
                                                    "Ignoring invalid repository discovery cache",
                                                    invalidCache);
                                }
                                return null;
                            })
                    .join();
        } catch (IOException | RuntimeException failure) {
            worker.shutdownNow();
            throw new IllegalStateException(
                    "Repository recovery failed; content loading is unsafe", failure);
        }
    }

    public void start() {
        nextFetch = System.currentTimeMillis();
        ticker =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                runtime.environment().plugin(),
                                () -> {
                                    transferProgress.forEach(
                                            (operation, job) -> {
                                                if (operation.state
                                                        == nl.hauntedmc.dungeons.runtime.staff
                                                                .StaffOperations.State.RUNNING)
                                                    operation.detail = job.progress;
                                            });
                                    reapWaitingPulls();
                                    if (!closed
                                            && System.currentTimeMillis() >= nextFetch
                                            && fetching.compareAndSet(false, true)) {
                                        fetch().whenComplete(
                                                        (result, failure) -> fetching.set(false));
                                    }
                                },
                                20,
                                200);
    }

    private void reapWaitingPulls() {
        if (closed) return;
        rollbackApprovals.expired();
        for (var prepared : forceApprovals.expired())
            submit(
                    () -> {
                        RepoContent.deleteTree(files.stage(prepared.operation()));
                        return null;
                    });
        for (var entry : List.copyOf(waitingPulls.entrySet())) {
            var operation =
                    runtime.staffOperations().operations().stream()
                            .filter(candidate -> candidate.id.equals(entry.getKey()))
                            .findFirst()
                            .orElse(null);
            if (operation == null) {
                waitingPulls.remove(entry.getKey());
                continue;
            }
            if (operation.state == StaffOperations.State.CANCELLED
                    || operation.state == StaffOperations.State.FAILED
                    || operation.state == StaffOperations.State.SUCCEEDED) {
                waitingPulls.remove(entry.getKey());
                transferProgress.remove(operation);
                submit(
                        () -> {
                            if (operation.state != StaffOperations.State.SUCCEEDED)
                                RepoContent.deleteTree(files.stage(entry.getValue()));
                            return null;
                        });
            }
        }
    }

    public boolean busy(String dungeon) {
        return closed || reservations.containsKey(dungeon);
    }

    private void reserve(Target target, UUID operation) {
        runtime.staffOperations().requireContentWritable(target.name());
        if (target.definition().getEditSession() != null || target.definition().isSaving())
            throw new IllegalArgumentException(
                    "Save and close the editor before publication or initialization");
        if (reservations.putIfAbsent(target.name(), operation) != null)
            throw new IllegalArgumentException("Repository operation already owns this dungeon");
    }

    private void release(Target target, UUID operation) {
        reservations.remove(target.name(), operation);
    }

    private Target target(String name) {
        var dungeon = runtime.dungeonCatalog().get(name);
        if (dungeon == null) throw new IllegalArgumentException("Unknown local dungeon: " + name);
        return new Target(
                dungeon.getWorldName(),
                dungeon.getDungeonId(),
                dungeon.getFolder().toPath(),
                dungeon);
    }

    private static String actorId(CommandSender actor) {
        return actor instanceof Player player
                ? player.getUniqueId().toString()
                : "console:" + actor.getName();
    }

    private void require(CommandSender actor, String capability, String name) {
        if (closed || actor instanceof Player player && !player.isOnline())
            throw new IllegalArgumentException("Repository command is no longer active");
        StaffPermissions.require(actor, "repo." + capability, name);
    }

    private <T> CompletableFuture<T> owner(Supplier<T> action) {
        var result = new CompletableFuture<T>();
        ownerCalls.add(result);
        Runnable run =
                () -> {
                    try {
                        if (closed) throw new IllegalStateException("Repository closed");
                        result.complete(action.get());
                    } catch (Throwable failure) {
                        result.completeExceptionally(failure);
                    } finally {
                        ownerCalls.remove(result);
                    }
                };
        try {
            if (Bukkit.isPrimaryThread()) run.run();
            else if (closed) throw new IllegalStateException("Repository closed");
            else Bukkit.getScheduler().runTask(runtime.environment().plugin(), run);
        } catch (RuntimeException rejected) {
            ownerCalls.remove(result);
            result.completeExceptionally(rejected);
        }
        return result;
    }

    private <T> CompletableFuture<T> submit(Callable<T> action) {
        return submit(null, action);
    }

    private <T> CompletableFuture<T> submit(Job job, Callable<T> action) {
        var result = new CompletableFuture<T>();
        pendingWorkers.add(result);
        try {
            if (closed) throw new RejectedExecutionException("Repository is closed");
            worker.execute(
                    () -> {
                        currentJob.set(job);
                        deadline.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds));
                        try {
                            check();
                            result.complete(action.call());
                        } catch (Throwable failure) {
                            result.completeExceptionally(failure);
                        } finally {
                            deadline.remove();
                            currentJob.remove();
                            pendingWorkers.remove(result);
                        }
                    });
        } catch (RejectedExecutionException overloaded) {
            pendingWorkers.remove(result);
            result.completeExceptionally(
                    new IllegalArgumentException(
                            "Repository operation queue is full or closed", overloaded));
        }
        return result.copy();
    }

    private void progress(String detail) {
        if (currentJob.get() != null) currentJob.get().progress = detail;
    }

    private void check() throws IOException {
        if (currentJob.get() != null) currentJob.get().check();
        if (closed
                || Thread.currentThread().isInterrupted()
                || deadline.get() != null && System.nanoTime() - deadline.get() >= 0)
            throw new IOException("Repository operation stopped or timed out");
    }

    private RepoContent.Snapshot snapshot(Target target, UUID operation) throws IOException {
        progress("Snapshotting saved local content: " + target.name());
        files.managed(target.folder());
        Path stage = files.stage(operation).resolve("snapshot");
        if (Files.getFileStore(stage.getParent()).getUsableSpace()
                < Math.min(limits.bytes(), folderSize(target.folder())) + 16L * 1024 * 1024)
            throw new IOException("Insufficient staging disk space");
        return RepoContent.snapshot(target.folder(), stage, limits);
    }

    private RepoContent.Snapshot inspect(Target target) throws IOException {
        check();
        files.managed(target.folder());
        return RepoContent.inspect(target.folder(), limits);
    }

    private long folderSize(Path folder) throws IOException {
        final long[] size = {0};
        Files.walkFileTree(
                folder,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(
                            Path dir, java.nio.file.attribute.BasicFileAttributes attrs)
                            throws IOException {
                        check();
                        return !dir.equals(folder) && RepoContent.excluded(folder.relativize(dir))
                                ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(
                            Path file, java.nio.file.attribute.BasicFileAttributes attrs)
                            throws IOException {
                        check();
                        if (!RepoContent.excluded(folder.relativize(file)))
                            size[0] = Math.addExact(size[0], attrs.size());
                        return FileVisitResult.CONTINUE;
                    }
                });
        return size[0];
    }

    private final Set<String> validatedBlocks = new HashSet<>();

    private CompletableFuture<Void> validateBlocks(RepoValidation.Result validation) {
        if (validation.blockData().isEmpty()) return CompletableFuture.completedFuture(null);
        var result = new CompletableFuture<Void>();
        ownerCalls.add(result);
        owner(
                        () -> {
                            var iterator = validation.blockData().iterator();
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    try {
                                        if (closed || result.isDone()) {
                                            cancel();
                                            return;
                                        }
                                        for (int count = 0;
                                                count < 128 && iterator.hasNext();
                                                count++) {
                                            String block = iterator.next();
                                            if (!validatedBlocks.contains(block)) {
                                                Bukkit.createBlockData(block);
                                                validatedBlocks.add(block);
                                            }
                                        }
                                        if (!iterator.hasNext()) {
                                            result.complete(null);
                                            cancel();
                                        }
                                    } catch (Throwable failure) {
                                        result.completeExceptionally(failure);
                                        cancel();
                                    } finally {
                                        if (result.isDone()) ownerCalls.remove(result);
                                    }
                                }
                            }.runTaskTimer(runtime.environment().plugin(), 1, 1);
                            return null;
                        })
                .exceptionally(
                        failure -> {
                            ownerCalls.remove(result);
                            result.completeExceptionally(failure);
                            return null;
                        });
        return result;
    }

    private void validatePermissions(
            CommandSender actor,
            String capability,
            String scope,
            RepoValidation.Result validation) {
        require(actor, capability, scope);
        if (validation.scripts()) StaffPermissions.require(actor, "scripts", scope);
        for (String alias : validation.bukkitAliases()) {
            var resolved = ConfigurationSerialization.getClassByAlias(alias);
            if (resolved == null)
                throw new IllegalArgumentException("Unknown serialized content type: " + alias);
            if (nl.hauntedmc.dungeons.content.function.CommandFunction.class.isAssignableFrom(
                    resolved)) StaffPermissions.require(actor, "scripts", scope);
        }
        var known =
                ConfigSerializableModel.pluginSerializables.stream()
                        .map(TypeMetadataSupport::requiredId)
                        .collect(java.util.stream.Collectors.toSet());
        for (String type : validation.modelTypes())
            if (!known.contains(type))
                throw new IllegalArgumentException("Unknown content type: " + type);
    }

    private void audit(CommandSender actor, String action, String target, String detail) {
        runtime.staffOperations()
                .audit(actor, "repo-" + action, target, "Dungeon repository", detail);
    }

    public CompletableFuture<String> initialize(String name, CommandSender actor, boolean created) {
        Target target = target(name);
        if (created) StaffPermissions.require(actor, "edit", target.name());
        else require(actor, "init", target.name());
        UUID operation = UUID.randomUUID();
        reserve(target, operation);
        var barrier = ConfigurationFile.awaitPendingSaves(target.folder().toFile());
        return barrier.thenCompose(
                        ignored ->
                                submit(
                                        () -> {
                                            try {
                                                var existing = RepoLocal.read(target.folder());
                                                if (existing != null) {
                                                    if (!existing.dungeonId().equals(target.id()))
                                                        throw new IOException(
                                                                "Repository identity mismatch");
                                                } else {
                                                    var snapshot = inspect(target);
                                                    RepoValidation.validate(
                                                            snapshot, target.id(), false);
                                                    owner(
                                                                    () -> {
                                                                        if (created)
                                                                            StaffPermissions
                                                                                    .require(
                                                                                            actor,
                                                                                            "edit",
                                                                                            target
                                                                                                    .name());
                                                                        else
                                                                            require(
                                                                                    actor,
                                                                                    "init",
                                                                                    target.name());
                                                                        return null;
                                                                    })
                                                            .join();
                                                    check();
                                                    new RepoLocal(
                                                                    target.id(),
                                                                    null,
                                                                    RepoVersion.DRAFT,
                                                                    snapshot.hash())
                                                            .write(target.folder());
                                                }
                                                owner(
                                                                () -> {
                                                                    if (!created)
                                                                        require(
                                                                                actor,
                                                                                "init",
                                                                                target.name());
                                                                    target.definition()
                                                                            .refreshRepositoryState();
                                                                    target.definition()
                                                                            .refreshRuntimeSettings();
                                                                    audit(
                                                                            actor,
                                                                            "init",
                                                                            target.name(),
                                                                            "Initialized local draft");
                                                                    return null;
                                                                })
                                                        .join();
                                                return "Initialized "
                                                        + target.name()
                                                        + " locally; use /dungeon repo add to publish 1.0.0";
                                            } finally {
                                                RepoContent.deleteTree(files.stage(operation));
                                            }
                                        }))
                .whenComplete(
                        (result, failure) ->
                                owner(
                                        () -> {
                                            release(target, operation);
                                            return null;
                                        }));
    }

    public CompletableFuture<String> fetch() {
        return submit(
                () -> {
                    try {
                        var previous = catalog;
                        catalog = store.catalog();
                        fetchedAt = System.currentTimeMillis();
                        catalogProblem = "";
                        backoff = 30;
                        nextFetch =
                                fetchedAt
                                        + TimeUnit.SECONDS.toMillis(
                                                300 + ThreadLocalRandom.current().nextInt(31));
                        store.cleanup(() -> closed);
                        long added =
                                catalog.stream()
                                        .filter(
                                                e ->
                                                        previous.stream()
                                                                .noneMatch(
                                                                        p ->
                                                                                p.head()
                                                                                        .dungeon()
                                                                                        .equals(
                                                                                                e.head()
                                                                                                        .dungeon())))
                                        .count();
                        long updated =
                                catalog.stream()
                                        .filter(
                                                e ->
                                                        previous.stream()
                                                                .anyMatch(
                                                                        p ->
                                                                                p.head()
                                                                                                .dungeon()
                                                                                                .equals(
                                                                                                        e.head()
                                                                                                                .dungeon())
                                                                                        && !p.head()
                                                                                                .id()
                                                                                                .equals(
                                                                                                        e.head()
                                                                                                                .id())))
                                        .count();
                        return "Fetched "
                                + catalog.size()
                                + " dungeons; "
                                + added
                                + " new, "
                                + updated
                                + " changed. Use repo status/list; no content installed.";
                    } catch (Throwable failure) {
                        catalogProblem = "Fetch failed; cached data may be stale";
                        nextFetch = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(backoff);
                        backoff = Math.min(900, backoff * 2);
                        throw failure;
                    }
                });
    }

    public String cachedScope(String selector) {
        var entry =
                catalog.stream()
                        .filter(
                                e ->
                                        e.name().equalsIgnoreCase(selector)
                                                || e.head()
                                                        .dungeon()
                                                        .toString()
                                                        .equalsIgnoreCase(selector))
                        .findFirst()
                        .orElse(null);
        if (entry == null) return selector;
        var local = runtime.dungeonCatalog().get(entry.head().dungeon().toString());
        return local == null ? entry.name() : local.getWorldName();
    }

    public List<String> cachedNames(CommandSender actor, String capability) {
        return catalog.stream()
                .map(RepoStore.Entry::name)
                .filter(
                        name ->
                                StaffPermissions.allows(
                                        actor, "repo." + capability, cachedScope(name)))
                .toList();
    }

    public String catalogHealth() {
        return "Last fetch: "
                + (fetchedAt == 0 ? "never" : java.time.Instant.ofEpochMilli(fetchedAt))
                + (catalogProblem.isEmpty() ? "" : "; " + catalogProblem);
    }

    private RepoStore.Entry remote(String selector) {
        catalog = store.catalog();
        fetchedAt = System.currentTimeMillis();
        catalogProblem = "";
        UUID local =
                owner(
                                () -> {
                                    var d = runtime.dungeonCatalog().get(selector);
                                    return d == null ? null : d.getDungeonId();
                                })
                        .join();
        UUID parsed = null;
        try {
            parsed = UUID.fromString(selector);
        } catch (IllegalArgumentException ignored) {
        }
        UUID id = parsed == null ? local : parsed;
        RepoStore.Entry byId =
                id == null
                        ? null
                        : catalog.stream()
                                .filter(e -> e.head().dungeon().equals(id))
                                .findFirst()
                                .orElse(null);
        RepoStore.Entry byName =
                catalog.stream()
                        .filter(e -> e.name().equalsIgnoreCase(selector))
                        .findFirst()
                        .orElse(null);
        if (byId != null
                && byName != null
                && !byId.head().dungeon().equals(byName.head().dungeon()))
            throw new IllegalArgumentException("Ambiguous local/repository name; use the UUID");
        RepoStore.Entry found = byId == null ? byName : byId;
        if (found == null)
            throw new IllegalArgumentException("Dungeon is not published in the repository");
        return found;
    }

    public CompletableFuture<String> publish(
            String name, String bump, String message, CommandSender actor, boolean add) {
        Target target = target(name);
        require(actor, "publish", target.name());
        if (!add) RepoVersion.DRAFT.bump(bump);
        UUID operation = UUID.randomUUID();
        reserve(target, operation);
        var job = new Job();
        var tracked =
                runtime.staffOperations()
                        .trackRepository(
                                actor,
                                "repo.publish",
                                "repo-publish",
                                target.name(),
                                operation,
                                job::cancel);
        transferProgress.put(tracked, job);
        String author =
                actor instanceof Player player
                        ? player.getUniqueId().toString()
                        : "console:" + actor.getName();
        return ConfigurationFile.awaitPendingSaves(target.folder().toFile())
                .thenCompose(
                        ignored ->
                                submit(
                                        job,
                                        () -> {
                                            boolean journal = false;
                                            try {
                                                var local = RepoLocal.read(target.folder());
                                                if (local == null
                                                        || !local.dungeonId().equals(target.id()))
                                                    throw new IllegalArgumentException(
                                                            "Initialize this dungeon with /dungeon repo init first");
                                                var head = store.entry(target.id());
                                                if (add && head != null)
                                                    throw new IllegalArgumentException(
                                                            "Dungeon is already published; pull it before pushing");
                                                if (!add && head == null)
                                                    throw new IllegalArgumentException(
                                                            "Use repo add for first publication");
                                                if (!Objects.equals(
                                                        local.releaseId(),
                                                        head == null ? null : head.head().id()))
                                                    throw new IllegalArgumentException(
                                                            "Local base is stale; pull latest and reapply changes");
                                                var snapshot = snapshot(target, operation);
                                                var validation =
                                                        RepoValidation.validate(
                                                                snapshot, target.id(), true);
                                                validateBlocks(validation).join();
                                                owner(
                                                                () -> {
                                                                    validatePermissions(
                                                                            actor,
                                                                            "publish",
                                                                            target.name(),
                                                                            validation);
                                                                    release(target, operation);
                                                                    return null;
                                                                })
                                                        .join();
                                                if (head != null
                                                        && snapshot.hash()
                                                                .equals(head.head().hash()))
                                                    return "No content changes to publish";
                                                files.publication(operation, target.name());
                                                journal = true;
                                                store.begin(
                                                        operation,
                                                        target.id(),
                                                        local.releaseId(),
                                                        snapshot,
                                                        minecraft,
                                                        author,
                                                        message);
                                                upload(operation, snapshot);
                                                progress("Verifying uploaded manifest");
                                                store.seal(operation, snapshot.items());
                                                owner(
                                                                () -> {
                                                                    validatePermissions(
                                                                            actor,
                                                                            "publish",
                                                                            target.name(),
                                                                            validation);
                                                                    if (runtime.dungeonCatalog()
                                                                                    .get(
                                                                                            target
                                                                                                    .name())
                                                                            != target.definition())
                                                                        throw new IllegalArgumentException(
                                                                                "Dungeon was replaced or archived during upload; publish again from the current working copy");
                                                                    runtime.staffOperations()
                                                                            .requireContentWritable(
                                                                                    target.name());
                                                                    if (reservations.putIfAbsent(
                                                                                    target.name(),
                                                                                    operation)
                                                                            != null)
                                                                        throw new IllegalArgumentException(
                                                                                "Another content operation owns the working copy");
                                                                    return null;
                                                                })
                                                        .join();
                                                RepoStore.Release published;
                                                job.commit();
                                                try {
                                                    published =
                                                            store.publish(
                                                                    operation, target.name(), bump);
                                                } catch (RuntimeException ambiguous) {
                                                    published = store.published(operation);
                                                    if (published == null) throw ambiguous;
                                                }
                                                new RepoLocal(
                                                                target.id(),
                                                                published.id(),
                                                                published.version(),
                                                                published.hash())
                                                        .write(target.folder());
                                                files.complete(operation);
                                                journal = false;
                                                String detail =
                                                        "Published "
                                                                + target.name()
                                                                + " "
                                                                + published.version();
                                                owner(
                                                                () -> {
                                                                    audit(
                                                                            actor,
                                                                            "publish",
                                                                            target.name(),
                                                                            detail);
                                                                    return null;
                                                                })
                                                        .join();
                                                catalog = store.catalog();
                                                return detail;
                                            } catch (Throwable failure) {
                                                if (journal)
                                                    try {
                                                        if (store.published(operation) == null) {
                                                            files.complete(operation);
                                                            journal = false;
                                                        }
                                                    } catch (Throwable unknown) {
                                                        failure.addSuppressed(unknown);
                                                    }
                                                throw failure;
                                            } finally {
                                                if (!journal)
                                                    RepoContent.deleteTree(files.stage(operation));
                                            }
                                        }))
                .whenComplete(
                        (result, failure) ->
                                owner(
                                        () -> {
                                            release(target, operation);
                                            transferProgress.remove(tracked);
                                            runtime.staffOperations()
                                                    .finishRepository(
                                                            actor, tracked, result, failure);
                                            return null;
                                        }));
    }

    private void upload(UUID operation, RepoContent.Snapshot snapshot) throws IOException {
        int file = 0;
        for (var item : snapshot.items()) {
            check();
            progress(
                    "Uploading file "
                            + (file + 1)
                            + "/"
                            + snapshot.items().size()
                            + ": "
                            + item.path());
            store.file(operation, file, item);
            if (!item.directory())
                try (var input = Files.newInputStream(snapshot.root().resolve(item.path()))) {
                    int chunk = 0;
                    while (true) {
                        check();
                        byte[] bytes = input.readNBytes(RepoContent.CHUNK_BYTES);
                        if (bytes.length == 0) break;
                        String hash = DungeonSchema.checksum(bytes);
                        if (!store.referenceIfPresent(operation, file, chunk, hash, bytes.length))
                            store.chunk(operation, file, chunk, RepoContent.encode(bytes));
                        chunk++;
                    }
                }
            file++;
        }
    }

    public CompletableFuture<List<String>> list(CommandSender actor, int page) {
        if (page < 1) throw new IllegalArgumentException("Invalid page");
        return submit(
                () -> {
                    var entries = store.catalog();
                    catalog = entries;
                    return owner(
                                    () ->
                                            entries.stream()
                                                    .filter(
                                                            e ->
                                                                    StaffPermissions.allows(
                                                                            actor,
                                                                            "repo.inspect",
                                                                            e.name()))
                                                    .skip((long) (page - 1) * 20)
                                                    .limit(20)
                                                    .map(
                                                            e ->
                                                                    e.name()
                                                                            + " "
                                                                            + e.head().version()
                                                                            + " "
                                                                            + e.head().dungeon())
                                                    .toList())
                            .join();
                });
    }

    public CompletableFuture<List<String>> history(String selector, CommandSender actor, int page) {
        return submit(
                () -> {
                    var entry = remote(selector);
                    owner(
                                    () -> {
                                        require(actor, "inspect", entry.name());
                                        return null;
                                    })
                            .join();
                    return store.history(entry.head().dungeon(), page).stream()
                            .map(
                                    r ->
                                            r.version()
                                                    + " "
                                                    + r.created()
                                                    + " "
                                                    + r.actor()
                                                    + "@"
                                                    + r.server()
                                                    + " "
                                                    + r.message())
                            .toList();
                });
    }

    public CompletableFuture<List<String>> status(
            String selector, CommandSender actor, boolean diff) {
        return status(selector, actor, diff, 1);
    }

    public CompletableFuture<List<String>> status(
            String selector, CommandSender actor, boolean diff, int page) {
        if (page < 1 || page > 100000) throw new IllegalArgumentException("Invalid page");
        if (selector != null && runtime.dungeonCatalog().get(selector) == null)
            return submit(
                    () -> {
                        var entry = remote(selector);
                        owner(
                                        () -> {
                                            require(actor, "inspect", entry.name());
                                            return null;
                                        })
                                .join();
                        return List.of(
                                entry.name() + " NOT INSTALLED; latest " + entry.head().version());
                    });
        List<Target> targets =
                selector == null
                        ? runtime.dungeonCatalog().getLoadedDungeons().stream()
                                .filter(
                                        d ->
                                                StaffPermissions.allows(
                                                        actor, "repo.inspect", d.getWorldName()))
                                .map(d -> target(d.getWorldName()))
                                .toList()
                        : List.of(target(selector));
        for (var target : targets) require(actor, "inspect", target.name());
        return submit(
                () -> {
                    var lines = new ArrayList<String>();
                    lines.add(catalogHealth());
                    for (var target : targets) {
                        UUID op = UUID.randomUUID();
                        try {
                            var local = RepoLocal.read(target.folder());
                            if (local == null || !local.dungeonId().equals(target.id())) {
                                lines.add(target.name() + " UNINITIALIZED (repo init required)");
                                continue;
                            }
                            var snapshot = inspect(target);
                            boolean dirty = !snapshot.hash().equals(local.contentHash());
                            var remote =
                                    catalog.stream()
                                            .filter(e -> e.head().dungeon().equals(target.id()))
                                            .findFirst()
                                            .orElse(null);
                            lines.add(
                                    target.name()
                                            + " "
                                            + local.version()
                                            + " "
                                            + (local.releaseId() == null ? "DRAFT " : "")
                                            + (dirty ? "DIRTY" : "CLEAN")
                                            + (remote == null
                                                    ? " REMOTE UNKNOWN"
                                                    : !remote.head().id().equals(local.releaseId())
                                                            ? " BEHIND " + remote.head().version()
                                                            : " UP TO DATE"));
                            if (diff && local.releaseId() != null) {
                                var base = store.published(local.releaseId());
                                if (base == null || !base.dungeon().equals(target.id()))
                                    throw new IOException("Base release missing");
                                var before = new TreeMap<String, RepoContent.Item>();
                                for (var item : store.manifest(base)) before.put(item.path(), item);
                                for (var item : snapshot.items()) {
                                    var old = before.remove(item.path());
                                    if (old == null) lines.add("+ " + item.path());
                                    else if (!old.equals(item)) lines.add("~ " + item.path());
                                }
                                before.keySet().forEach(path -> lines.add("- " + path));
                            }
                        } catch (IOException | IllegalArgumentException invalid) {
                            lines.add(target.name() + " INVALID: " + invalid.getMessage());
                        } finally {
                            RepoContent.deleteTree(files.stage(op));
                        }
                    }
                    if (diff) {
                        var output = new ArrayList<String>();
                        output.add(lines.getFirst());
                        if (lines.size() > 1) output.add(lines.get(1));
                        output.addAll(
                                lines.stream()
                                        .skip(2L + (long) (page - 1) * 20)
                                        .limit(20)
                                        .toList());
                        output.add(
                                "Diff page "
                                        + page
                                        + "; "
                                        + Math.max(0, lines.size() - 2)
                                        + " changed paths");
                        return List.copyOf(output);
                    }
                    return List.copyOf(lines);
                });
    }

    public CompletableFuture<String> pull(
            String selector,
            RepoVersion version,
            boolean force,
            String token,
            CommandSender actor) {
        String initialScope = cachedScope(selector);
        require(actor, "pull", initialScope);
        if (force) require(actor, "force", initialScope);
        var approved = token == null ? null : forceApprovals.claim(actorId(actor), token);
        var job = new Job();
        var transfer =
                runtime.staffOperations()
                        .trackRepository(
                                actor,
                                "repo.pull",
                                "repo-download",
                                initialScope,
                                UUID.randomUUID(),
                                job::cancel);
        transferProgress.put(transfer, job);
        return this.<Prepared>submit(
                        job,
                        () -> {
                            var entry = remote(selector);
                            Target local = owner(() -> findLocal(entry)).join();
                            String scope = local == null ? entry.name() : local.name();
                            owner(
                                            () -> {
                                                require(actor, "pull", scope);
                                                if (force) require(actor, "force", scope);
                                                return null;
                                            })
                                    .join();
                            RepoStore.Release release =
                                    version == null
                                            ? entry.head()
                                            : store.find(entry.head().dungeon(), version);
                            if (release.format() != RepoStore.FORMAT
                                    || !release.minecraft().equals(minecraft))
                                throw new IllegalArgumentException(
                                        "Release requires Minecraft "
                                                + release.minecraft()
                                                + " and content format "
                                                + release.format());
                            if (release.bytes() > limits.bytes()
                                    || release.files() > limits.files()
                                    || release.bytes() < 0
                                    || release.files() < 1)
                                throw new IOException(
                                        "Release exceeds configured repository limits");
                            if (approved != null) {
                                if (!approved.release().equals(release.id())) {
                                    RepoContent.deleteTree(
                                            files.stage(approved.payload().operation()));
                                    throw new IllegalArgumentException(
                                            "Confirmation targets a different release; request a new forced pull");
                                }
                                return approved.payload();
                            }
                            UUID op = UUID.randomUUID();
                            try {
                                var snapshot = download(op, release);
                                var validation =
                                        RepoValidation.validate(snapshot, release.dungeon(), true);
                                validateBlocks(validation).join();
                                owner(
                                                () -> {
                                                    validatePermissions(
                                                            actor, "pull", scope, validation);
                                                    return null;
                                                })
                                        .join();
                                return new Prepared(op, entry, release, snapshot, validation);
                            } catch (Throwable failure) {
                                RepoContent.deleteTree(files.stage(op));
                                throw failure;
                            }
                        })
                .whenComplete(
                        (result, failure) ->
                                owner(
                                        () -> {
                                            transferProgress.remove(transfer);
                                            if (failure != null && approved != null)
                                                submit(
                                                        () -> {
                                                            RepoContent.deleteTree(
                                                                    files.stage(
                                                                            approved.payload()
                                                                                    .operation()));
                                                            return null;
                                                        });
                                            runtime.staffOperations()
                                                    .finishRepository(
                                                            actor,
                                                            transfer,
                                                            failure == null
                                                                    ? "Download verified"
                                                                    : null,
                                                            failure);
                                            return null;
                                        }))
                .thenCompose(
                        prepared ->
                                owner(
                                        () -> {
                                            Target local = findLocal(prepared.entry());
                                            String name =
                                                    local == null
                                                            ? prepared.entry().name()
                                                            : local.name();
                                            try {
                                                require(actor, "pull", name);
                                                if (force) require(actor, "force", name);
                                                var operation =
                                                        runtime.staffOperations()
                                                                .repositoryDrain(
                                                                        actor,
                                                                        "repo.pull",
                                                                        "repo-pull",
                                                                        name,
                                                                        "Repository pull "
                                                                                + prepared
                                                                                        .operation(),
                                                                        () ->
                                                                                ConfigurationFile
                                                                                        .awaitPendingSaves(
                                                                                                runtime
                                                                                                        .dungeonFiles())
                                                                                        .thenCompose(
                                                                                                ignored ->
                                                                                                        submit(
                                                                                                                job,
                                                                                                                () ->
                                                                                                                        install(
                                                                                                                                prepared,
                                                                                                                                name,
                                                                                                                                version,
                                                                                                                                force,
                                                                                                                                token,
                                                                                                                                actor,
                                                                                                                                approved
                                                                                                                                                == null
                                                                                                                                        ? null
                                                                                                                                        : approved
                                                                                                                                                .revision()))));
                                                runtime.staffOperations()
                                                        .repositoryCancellation(
                                                                operation, job::cancel);
                                                transferProgress.put(operation, job);
                                                waitingPulls.put(
                                                        operation.id, prepared.operation());
                                                return "Pull operation "
                                                        + operation.shortId()
                                                        + ": "
                                                        + operation.detail;
                                            } catch (RuntimeException failure) {
                                                submit(
                                                        () -> {
                                                            RepoContent.deleteTree(
                                                                    files.stage(
                                                                            prepared.operation()));
                                                            return null;
                                                        });
                                                throw failure;
                                            }
                                        }));
    }

    private Target findLocal(RepoStore.Entry entry) {
        var byId = runtime.dungeonCatalog().get(entry.head().dungeon().toString());
        if (byId != null) return target(byId.getWorldName());
        var byName =
                runtime.dungeonCatalog().getLoadedDungeons().stream()
                        .filter(d -> d.getWorldName().equalsIgnoreCase(entry.name()))
                        .findFirst()
                        .orElse(null);
        if (byName != null && !byName.getDungeonId().equals(entry.head().dungeon()))
            throw new IllegalArgumentException(
                    "Local name belongs to another dungeon UUID; archive it first");
        if (byName == null) {
            var children = runtime.dungeonFiles().listFiles();
            if (children == null)
                throw new IllegalArgumentException("Cannot inspect local dungeon directory");
            for (var child : children)
                if (child.getName().equalsIgnoreCase(entry.name())
                        && !child.getName().equals(entry.name()))
                    throw new IllegalArgumentException(
                            "An unloaded local directory collides with the repository name; resolve it first");
        }
        return byName == null ? null : target(byName.getWorldName());
    }

    private RepoContent.Snapshot download(UUID op, RepoStore.Release release) throws IOException {
        if (release.bytes() < 0
                || release.bytes() > limits.bytes()
                || release.files() < 1
                || release.files() > limits.files())
            throw new IOException("Release exceeds repository limits");
        var manifest = store.manifest(release);
        Path staging = files.stage(op).resolve("install");
        Files.createDirectories(staging);
        if (Files.getFileStore(staging).getUsableSpace() < release.bytes() + 16L * 1024 * 1024)
            throw new IOException("Insufficient space for download");
        int number = 0;
        for (var item : manifest) {
            check();
            progress(
                    "Downloading file "
                            + (number + 1)
                            + "/"
                            + manifest.size()
                            + ": "
                            + item.path());
            Path destination = staging.resolve(item.path());
            if (item.directory()) Files.createDirectories(destination);
            else {
                Files.createDirectories(destination.getParent());
                long written = 0;
                try (var output =
                        Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                    for (int chunk = 0; written < item.size(); chunk++) {
                        check();
                        byte[] bytes = RepoContent.decode(store.chunk(release.id(), number, chunk));
                        if (bytes.length == 0
                                || bytes.length > item.size() - written
                                || bytes.length
                                        != Math.min(RepoContent.CHUNK_BYTES, item.size() - written))
                            throw new IOException("Invalid file chunk length");
                        output.write(bytes);
                        written += bytes.length;
                    }
                }
                if (!RepoContent.hashFile(destination).equals(item.hash()))
                    throw new IOException("File checksum mismatch: " + item.path());
            }
            number++;
        }
        return new RepoContent.Snapshot(staging, manifest, release.hash(), release.bytes());
    }

    private String install(
            Prepared prepared,
            String name,
            RepoVersion requested,
            boolean force,
            String token,
            CommandSender actor,
            String approvedRevision)
            throws Exception {
        boolean swapped = false;
        boolean retainedForApproval = false;
        try {
            check();
            Path live = files.live(name);
            boolean previous = Files.exists(live, LinkOption.NOFOLLOW_LINKS);
            String revision = "absent";
            if (previous) {
                var config = RepoContent.readYaml(live.resolve("config.yml"));
                if (!(config.get("dungeon") instanceof Map<?, ?> dungeon)
                        || !prepared.release().dungeon().toString().equals(dungeon.get("id")))
                    throw new IOException("Local dungeon UUID does not match release");
                var target = new Target(name, prepared.release().dungeon(), live, null);
                var snapshot = inspect(target);
                {
                    RepoLocal local;
                    try {
                        local = RepoLocal.read(live);
                    } catch (IOException invalidMetadata) {
                        if (!force) throw invalidMetadata;
                        local = null;
                    }
                    revision =
                            snapshot.hash()
                                    + ":"
                                    + (local == null ? "uninitialized" : local.releaseId());
                    if (!force
                            && (local == null
                                    || !local.dungeonId().equals(prepared.release().dungeon())
                                    || !snapshot.hash().equals(local.contentHash())))
                        throw new IllegalArgumentException(
                                "Local content changed or is uninitialized; use --force after reviewing repo diff");
                    if (!force
                            && requested == null
                            && local != null
                            && local.version().compareTo(prepared.release().version()) > 0)
                        throw new IllegalArgumentException(
                                "Pull would downgrade; select --version explicitly");
                    if (!force
                            && local != null
                            && Objects.equals(local.releaseId(), prepared.release().id()))
                        return "Already installed " + prepared.release().version();
                }
            }
            String finalRevision = revision;
            boolean confirmed =
                    owner(
                                    () -> {
                                        validatePermissions(
                                                actor, "pull", name, prepared.validation());
                                        if (!force) return true;
                                        require(actor, "force", name);
                                        if (approvedRevision != null) {
                                            if (!approvedRevision.equals(finalRevision))
                                                throw new IllegalArgumentException(
                                                        "Saved local content changed after confirmation; request a new forced pull");
                                            return true;
                                        }
                                        var approval =
                                                forceApprovals.issue(
                                                        actorId(actor),
                                                        prepared.release().id(),
                                                        finalRevision,
                                                        prepared);
                                        String command =
                                                "/dungeon repo pull "
                                                        + prepared.entry().head().dungeon()
                                                        + " --version "
                                                        + prepared.release().version()
                                                        + " --force --confirm "
                                                        + approval.token();
                                        LangUtils.sendRaw(
                                                actor,
                                                "Confirm saved local content replacement within 30 seconds: "
                                                        + command);
                                        return false;
                                    })
                            .join();
            if (!confirmed) {
                retainedForApproval = true;
                return "Preflight complete; confirmation required. No content replaced";
            }
            owner(
                            () -> {
                                mergeLocalSettings(prepared.snapshot().root(), live, previous);
                                return null;
                            })
                    .join();
            new RepoLocal(
                            prepared.release().dungeon(),
                            prepared.release().id(),
                            prepared.release().version(),
                            prepared.release().hash())
                    .write(prepared.snapshot().root());
            // install writes its journal before the first rename; rollback also handles partial
            // swaps.
            if (currentJob.get() != null) currentJob.get().commit();
            swapped = true;
            files.install(prepared.operation(), name, prepared.snapshot().root());
            owner(
                            () -> {
                                var loaded = runtime.dungeonCatalog().loadDungeon(live.toFile());
                                if (loaded == null
                                        || !loaded.getDungeonId()
                                                .equals(prepared.release().dungeon()))
                                    throw new IllegalStateException("Replacement failed to load");
                                return null;
                            })
                    .join();
            files.commit(prepared.operation(), name, previous);
            swapped = false;
            try {
                files.prune(name);
            } catch (IOException retentionFailure) {
                runtime.environment()
                        .plugin()
                        .getSLF4JLogger()
                        .warn(
                                "Installed repository release; old backup cleanup will need retry",
                                retentionFailure);
            }
            owner(
                            () -> {
                                audit(
                                        actor,
                                        "pull",
                                        name,
                                        "Installed " + prepared.release().version());
                                return null;
                            })
                    .join();
            return "Installed "
                    + name
                    + " "
                    + prepared.release().version()
                    + (previous
                            ? "; previous files retained as safety backup"
                            : "; disabled locally until configured and enabled");
        } catch (Throwable failure) {
            if (swapped) {
                try {
                    files.rollback(prepared.operation());
                    owner(
                                    () -> {
                                        Path live = runtime.dungeonFiles().toPath().resolve(name);
                                        if (Files.exists(live)) {
                                            if (runtime.dungeonCatalog().loadDungeon(live.toFile())
                                                    == null)
                                                throw new IllegalStateException(
                                                        "Prior definition reload failed");
                                        } else {
                                            var loaded = runtime.dungeonCatalog().get(name);
                                            if (loaded != null)
                                                runtime.dungeonCatalog().remove(loaded);
                                        }
                                        return null;
                                    })
                            .join();
                } catch (Throwable rollback) {
                    failure.addSuppressed(rollback);
                }
            }
            throw failure;
        } finally {
            if (!retainedForApproval) RepoContent.deleteTree(files.stage(prepared.operation()));
        }
    }

    private void mergeLocalSettings(Path staging, Path live, boolean previous) {
        try {
            var incoming = new YamlConfiguration();
            ConfigurationFile.loadSnapshot(incoming, staging.resolve("config.yml").toFile());
            var existing = new YamlConfiguration();
            if (previous)
                ConfigurationFile.loadSnapshot(existing, live.resolve("config.yml").toFile());
            incoming.set(
                    "dungeon.enabled",
                    previous ? existing.getBoolean("dungeon.enabled", true) : false);
            incoming.set("locations.exit", existing.get("locations.exit"));
            incoming.setComments(
                    "dungeon.enabled",
                    List.of("LOCAL — NOT SYNCED: playable on this server only."));
            incoming.setComments(
                    "locations.exit",
                    List.of("LOCAL — NOT SYNCED: external destination on this server."));
            ConfigurationFile.saveSnapshot(
                    runtime.environment().plugin(),
                    staging.resolve("config.yml").toFile(),
                    incoming.saveToString());
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot preserve local deployment settings", failure);
        }
    }

    public CompletableFuture<String> rollback(
            String selector,
            RepoVersion version,
            String bump,
            String message,
            String token,
            CommandSender actor) {
        RepoVersion.DRAFT.bump(bump);
        String author =
                actor instanceof Player player
                        ? player.getUniqueId().toString()
                        : "console:" + actor.getName();
        var approved = token == null ? null : rollbackApprovals.claim(actorId(actor), token);
        var job = new Job();
        var tracked = new AtomicReference<StaffOperations.Operation>();
        return this.<String>submit(
                        job,
                        () -> {
                            var entry = remote(selector);
                            var source = store.find(entry.head().dungeon(), version);
                            if (source.format() != RepoStore.FORMAT
                                    || !source.minecraft().equals(minecraft))
                                throw new IllegalArgumentException(
                                        "Historical content is incompatible with this server");
                            UUID operation = UUID.randomUUID();
                            tracked.set(
                                    owner(
                                                    () ->
                                                            runtime.staffOperations()
                                                                    .trackRepository(
                                                                            actor,
                                                                            "repo.publish",
                                                                            "repo-rollback",
                                                                            entry.name(),
                                                                            operation,
                                                                            job::cancel))
                                            .join());
                            try {
                                var plan =
                                        new RollbackPlan(
                                                source.dungeon(),
                                                version,
                                                bump,
                                                message,
                                                entry.head().id());
                                if (approved == null) {
                                    owner(
                                                    () -> {
                                                        require(actor, "publish", entry.name());
                                                        var approval =
                                                                rollbackApprovals.issue(
                                                                        actorId(actor),
                                                                        source.id(),
                                                                        entry.head()
                                                                                .id()
                                                                                .toString(),
                                                                        plan);
                                                        LangUtils.sendRaw(
                                                                actor,
                                                                "Confirm publication within 30 seconds: /dungeon repo rollback "
                                                                        + entry.head().dungeon()
                                                                        + " "
                                                                        + version
                                                                        + " "
                                                                        + bump
                                                                        + (message.isBlank()
                                                                                ? ""
                                                                                : " " + message)
                                                                        + " --confirm "
                                                                        + approval.token());
                                                        return null;
                                                    })
                                            .join();
                                    return "Rollback publication requires confirmation; history unchanged";
                                }
                                if (!approved.release().equals(source.id())
                                        || !approved.payload().equals(plan))
                                    throw new IllegalArgumentException(
                                            "Rollback parameters or repository head changed; request a new confirmation");
                                var snapshot = download(operation, source);
                                var validation =
                                        RepoValidation.validate(snapshot, source.dungeon(), true);
                                validateBlocks(validation).join();
                                owner(
                                                () -> {
                                                    validatePermissions(
                                                            actor,
                                                            "publish",
                                                            entry.name(),
                                                            validation);
                                                    return null;
                                                })
                                        .join();
                                if (source.hash().equals(entry.head().hash()))
                                    return "Historical content already matches repository head";
                                store.begin(
                                        operation,
                                        source.dungeon(),
                                        entry.head().id(),
                                        snapshot,
                                        source.minecraft(),
                                        author,
                                        message);
                                for (int file = 0; file < snapshot.items().size(); file++) {
                                    check();
                                    var item = snapshot.items().get(file);
                                    store.file(operation, file, item);
                                    long count =
                                            item.directory()
                                                    ? 0
                                                    : (item.size() + RepoContent.CHUNK_BYTES - 1)
                                                            / RepoContent.CHUNK_BYTES;
                                    for (int chunk = 0; chunk < count; chunk += 128) {
                                        check();
                                        store.reference(operation, source.id(), file, chunk);
                                    }
                                }
                                store.seal(operation, snapshot.items());
                                owner(
                                                () -> {
                                                    validatePermissions(
                                                            actor,
                                                            "publish",
                                                            entry.name(),
                                                            validation);
                                                    return null;
                                                })
                                        .join();
                                RepoStore.Release published;
                                job.commit();
                                try {
                                    published = store.publish(operation, entry.name(), bump);
                                } catch (RuntimeException ambiguous) {
                                    published = store.published(operation);
                                    if (published == null) throw ambiguous;
                                }
                                String detail =
                                        "Published rollback "
                                                + version
                                                + " as "
                                                + published.version()
                                                + "; use repo pull to install";
                                owner(
                                                () -> {
                                                    audit(actor, "rollback", entry.name(), detail);
                                                    return null;
                                                })
                                        .join();
                                catalog = store.catalog();
                                return detail;
                            } finally {
                                RepoContent.deleteTree(files.stage(operation));
                            }
                        })
                .whenComplete(
                        (result, failure) ->
                                owner(
                                        () -> {
                                            if (tracked.get() != null)
                                                runtime.staffOperations()
                                                        .finishRepository(
                                                                actor,
                                                                tracked.get(),
                                                                result,
                                                                failure);
                                            return null;
                                        }));
    }

    @Override
    public void close() {
        closed = true;
        if (ticker != null) ticker.cancel();
        ownerCalls.forEach(
                future ->
                        future.completeExceptionally(
                                new IllegalStateException("Repository closed")));
        ownerCalls.clear();
        pendingWorkers.forEach(
                future ->
                        future.completeExceptionally(
                                new IllegalStateException("Repository closed")));
        pendingWorkers.clear();
        worker.shutdownNow();
        reservations.clear();
        transferProgress.clear();
        waitingPulls.clear();
        forceApprovals.clear();
        rollbackApprovals.clear();
    }
}
