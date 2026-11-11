package nl.hauntedmc.dungeons.runtime.staff;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.persistence.StateDocument;
import nl.hauntedmc.dungeons.persistence.StateStore;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** Server-thread operations shared by staff interfaces. Maintenance survives process restarts. */
public final class StaffOperations implements AutoCloseable {
    public enum State {
        WAITING,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    public static final class Operation {
        public UUID id = UUID.randomUUID();
        public final String actor;
        public final String action;
        public final String target;
        public final String reason;
        public final Instant created = Instant.now();
        public State state = State.WAITING;
        public String detail = "Waiting for active runs and worlds to drain";
        private java.util.function.BooleanSupplier repositoryCancellation;

        private Operation(String actor, String action, String target, String reason) {
            this.actor = actor;
            this.action = action;
            this.target = target;
            this.reason = reason;
        }

        public String shortId() {
            return id.toString().substring(0, 8);
        }
    }

    private record Confirmation(
            String actor,
            String action,
            String target,
            String revision,
            String parameters,
            long expires,
            UUID token) {}

    private record Pending(
            Operation operation,
            String capability,
            CommandSender actor,
            Supplier<CompletableFuture<String>> work,
            boolean resume,
            Object previousMaintenance) {}

    private final LongSupplier clock;
    private final DungeonsRuntime runtime;
    private final StateDocument document;
    private final YamlConfiguration state = new YamlConfiguration();
    private final Map<String, Confirmation> confirmations = new HashMap<>();
    private final LinkedHashMap<UUID, Operation> operations = new LinkedHashMap<>();
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final Map<String, CompletableFuture<Boolean>> disposing = new HashMap<>();
    private final Deque<String> problems = new ArrayDeque<>();
    private BukkitTask ticker;
    private boolean closed;
    private final List<Runnable> closeActions = new ArrayList<>();

    public void onClose(Runnable action) {
        requireOpen();
        closeActions.add(Objects.requireNonNull(action));
    }

    public StaffOperations(DungeonsRuntime runtime) {
        this(runtime, System::nanoTime);
    }

    StaffOperations(DungeonsRuntime runtime, LongSupplier clock) {
        this.clock = clock;
        this.runtime = runtime;
        this.document =
                runtime.stateStore().document(new StateStore.Key("staff-state", "server", null));
        try {
            document.loadInto(state);
        } catch (InvalidConfigurationException failure) {
            throw new IllegalStateException(
                    "Cannot load database staff state; admissions must remain stopped", failure);
        }
        for (String section : List.of("maintenance", "restrictions", "operations"))
            if (state.contains(section) && !state.isConfigurationSection(section))
                throw new IllegalStateException("Invalid staff state section: " + section);
        var maintenance = state.getConfigurationSection("maintenance");
        if (maintenance != null)
            for (String name : maintenance.getKeys(false))
                if (!(maintenance.get(name) instanceof String reason) || reason.isBlank())
                    throw new IllegalStateException("Invalid maintenance record: " + name);
        var restrictions = state.getConfigurationSection("restrictions");
        if (restrictions != null)
            for (String player : restrictions.getKeys(false)) {
                UUID.fromString(player);
                var scopes = restrictions.getConfigurationSection(player);
                if (scopes == null) throw new IllegalStateException("Invalid entry restrictions");
                for (String scope : scopes.getKeys(false)) {
                    var record = scopes.getConfigurationSection(scope);
                    if (record == null
                            || !(record.get("until") instanceof Number)
                            || record.getLong("until") < 0
                            || !record.isString("reason"))
                        throw new IllegalStateException("Invalid entry restriction for " + player);
                }
            }
        var history = state.getConfigurationSection("operations");
        if (history != null)
            for (String key : history.getKeys(false)) {
                var op =
                        new Operation(
                                history.getString(key + ".actor", "unknown"),
                                history.getString(key + ".action", "unknown"),
                                history.getString(key + ".target", "all"),
                                history.getString(key + ".reason", "Previous operation"));
                op.id = UUID.fromString(key);
                op.state = State.valueOf(history.getString(key + ".state", "CANCELLED"));
                op.detail = history.getString(key + ".detail", "");
                if (op.state == State.WAITING || op.state == State.RUNNING) {
                    op.state = State.CANCELLED;
                    op.detail = "Interrupted by restart; maintenance remains enabled";
                }
                operations.put(op.id, op);
            }
    }

    public void start() {
        requireOpen();
        if (ticker != null) return;
        Bukkit.getPluginManager()
                .registerEvents(new StaffObserverListener(runtime), runtime.environment().plugin());
        if (ticker == null)
            ticker =
                    Bukkit.getScheduler()
                            .runTaskTimer(runtime.environment().plugin(), this::tick, 20L, 20L);
    }

    public void observe(Player actor, DungeonInstance target) {
        requireOpen();
        StaffPermissions.require(actor, "observe", target.getDungeon().getWorldName());
        if (!target.isPlayInstance()
                || !target.isReady()
                || target.getInstanceWorld() == null
                || target.getDisposalState() != DungeonInstance.DisposalState.ACTIVE)
            throw new IllegalArgumentException("Instance is not available for observation");
        var session = runtime.playerSessions().get(actor);
        if (session == null) throw new IllegalArgumentException("Player session unavailable");
        if (runtime.queueRegistry().getQueue(session) != null
                || runtime.preparations().contains(session)
                || session.hasReservedAccessKey())
            throw new IllegalArgumentException(
                    "Cancel the pending admission and settle reserved keys before observing");
        session.beginObservation(target);
        try {
            actor.setGameMode(GameMode.SPECTATOR);
            Location destination = target.getStartLoc();
            if (destination == null) destination = target.getInstanceWorld().getSpawnLocation();
            if (!actor.teleport(destination))
                throw new IllegalStateException("Observation teleport rejected");
            audit(
                    actor,
                    "observe",
                    target.getInstanceId().toString(),
                    "Staff observation",
                    "started");
        } catch (RuntimeException failure) {
            session.stopObservation();
            throw failure;
        }
    }

    public void stopObservers(DungeonInstance instance) {
        for (var session : runtime.playerSessions().snapshot())
            if (session.getObservingInstance() == instance) session.stopObservation();
    }

    public boolean maintenance(String dungeon) {
        return closed
                || state.contains("maintenance.all")
                || state.contains("maintenance." + dungeon);
    }

    public String maintenanceReason(String dungeon) {
        return state.getString(
                "maintenance." + dungeon, state.getString("maintenance.all", "Maintenance"));
    }

    public void maintenance(CommandSender actor, String dungeon, boolean enabled, String reason) {
        requireOpen();
        StaffPermissions.require(actor, "maintenance", dungeon.equals("all") ? null : dungeon);
        requireReason(reason);
        if (!enabled
                && (pending.containsKey(dungeon)
                        || pending.containsKey("all")
                        || dungeon.equals("all") && !pending.isEmpty()))
            throw new IllegalArgumentException(
                    "An operation is still waiting/running. Cancel it or wait for its result.");
        if (!enabled
                && instances(dungeon).stream()
                        .anyMatch(
                                i ->
                                        i.isEditInstance()
                                                || i.getDisposalState()
                                                        != DungeonInstance.DisposalState.ACTIVE))
            throw new IllegalArgumentException(
                    "Close editors and resolve retained world cleanup before resuming admissions");
        String path = "maintenance." + dungeon;
        Object before = state.get(path);
        persist(next -> next.set(path, enabled ? reason : null));
        audit(actor, "maintenance", dungeon, reason, before + " -> " + (enabled ? reason : "open"));
        if (enabled) cancelStarts(dungeon);
    }

    public String entryBlock(UUID player, String dungeon) {
        if (maintenance(dungeon)) return "Maintenance: " + maintenanceReason(dungeon);
        for (String scope : List.of("all", dungeon)) {
            String base = "restrictions." + player + "." + scope;
            if (state.contains(base)
                    && (state.getLong(base + ".until") == 0
                            || state.getLong(base + ".until") > System.currentTimeMillis()))
                return "Entry suspended: " + state.getString(base + ".reason", "Contact staff");
        }
        return null;
    }

    public void restrict(
            CommandSender actor, UUID player, String dungeon, long until, String reason) {
        requireOpen();
        StaffPermissions.require(actor, "restrict", dungeon.equals("all") ? null : dungeon);
        requireReason(reason);
        persist(
                next -> {
                    String path = "restrictions." + player + "." + dungeon;
                    if (until < 0) next.set(path, null);
                    else {
                        next.set(path + ".until", until);
                        next.set(path + ".reason", reason);
                        next.set(path + ".actor", actor.getName());
                    }
                });
        if (until >= 0) {
            DungeonPlayerSession session = runtime.playerSessions().get(player);
            if (session != null
                    && (dungeon.equals("all") || dungeon.equals(pendingDungeon(session))))
                cancelStart(session);
        }
        audit(
                actor,
                "entry-restriction",
                player + "/" + dungeon,
                reason,
                until < 0 ? "removed" : "until=" + until);
    }

    /** The underlying preparation owns cancellation; clearing an awaiting flag is insufficient. */
    public void cancelStart(DungeonPlayerSession session) {
        runtime.preparations().cancel(session);
        var queue = runtime.queueRegistry().getQueue(session);
        if (queue != null) runtime.dungeonQueueCoordinator().discardQueue(queue);
        var team = runtime.teamService().getTeam(session.getPlayer().getUniqueId());
        if (team != null && team.isQueued()) runtime.teamService().clearQueued(team);
        if (session.getInstance() == null) {
            String name = session.getReservedAccessKeyDungeon();
            var definition = name == null ? null : runtime.dungeonCatalog().get(name);
            if (definition != null) definition.refundReservedAccessKey(session);
            else session.refundReservedAccessKey();
            session.setAwaitingDungeon(false);
        }
    }

    private String pendingDungeon(DungeonPlayerSession session) {
        var queue = runtime.queueRegistry().getQueue(session);
        if (queue != null) return queue.getDungeon().getWorldName();
        String preparation = runtime.preparations().dungeonName(session);
        return preparation == null ? session.getReservedAccessKeyDungeon() : preparation;
    }

    public void cancelStarts(String dungeon) {
        runtime.preparations().cancelDungeon(dungeon.equals("all") ? null : dungeon);
        for (var queue : runtime.queueRegistry().snapshotQueues())
            if (dungeon.equals("all") || dungeon.equals(queue.getDungeon().getWorldName()))
                runtime.dungeonQueueCoordinator().discardQueue(queue);
        runtime.teamService().clearQueuedStarts(dungeon.equals("all") ? null : dungeon);
        for (var session : runtime.playerSessions().snapshot())
            if (session.getInstance() == null
                    && session.hasReservedAccessKey()
                    && (dungeon.equals("all")
                            || dungeon.equals(session.getReservedAccessKeyDungeon())))
                cancelStart(session);
    }

    public List<DungeonInstance> instances(String dungeon) {
        return runtime.activeInstances().getActiveInstances().stream()
                .filter(i -> dungeon.equals("all") || i.getDungeon().getWorldName().equals(dungeon))
                .toList();
    }

    public DungeonInstance instance(String input) {
        var found =
                instances("all").stream()
                        .filter(
                                i ->
                                        i.getInstanceId().toString().startsWith(input)
                                                || input.equals(i.getInstanceWorldName()))
                        .toList();
        if (found.size() != 1)
            throw new IllegalArgumentException("Instance not found or ID is ambiguous: " + input);
        return found.getFirst();
    }

    public String impact(String target) {
        var instances = instances(target);
        long players = instances.stream().mapToLong(i -> i.getPlayers().size()).sum();
        long queues =
                runtime.queueRegistry().snapshotQueues().stream()
                        .filter(
                                q ->
                                        target.equals("all")
                                                || target.equals(q.getDungeon().getWorldName()))
                        .count();
        return players
                + " participants, "
                + instances.size()
                + " managed worlds, "
                + queues
                + " queues";
    }

    /** Confirmation authorizes exactly one actor, action, revision and parameter set. */
    public UUID confirm(
            CommandSender actor,
            String action,
            String target,
            String revision,
            String parameters,
            String token) {
        requireOpen();
        String identity =
                actor instanceof Player p
                        ? p.getUniqueId().toString()
                        : "console:" + actor.getName();
        Confirmation previous = confirmations.get(identity);
        if (previous != null
                && previous.expires > clock.getAsLong()
                && previous.action.equals(action)
                && previous.target.equals(target)
                && previous.revision.equals(revision)
                && previous.parameters.equals(parameters)
                && previous.token.toString().equals(token)) {
            confirmations.remove(identity);
            return null;
        }
        UUID next = UUID.randomUUID();
        confirmations.put(
                identity,
                new Confirmation(
                        identity,
                        action,
                        target,
                        revision,
                        parameters,
                        clock.getAsLong() + TimeUnit.SECONDS.toNanos(30),
                        next));
        return next;
    }

    public Operation drain(
            CommandSender actor,
            String capability,
            String action,
            String target,
            String reason,
            Supplier<CompletableFuture<String>> work) {
        return drainInternal(actor, capability, action, target, reason, work, false);
    }

    public Operation repositoryDrain(
            CommandSender actor,
            String capability,
            String action,
            String target,
            String reason,
            Supplier<CompletableFuture<String>> work) {
        return drainInternal(actor, capability, action, target, reason, work, true);
    }

    private Operation drainInternal(
            CommandSender actor,
            String capability,
            String action,
            String target,
            String reason,
            Supplier<CompletableFuture<String>> work,
            boolean resume) {
        requireOpen();
        StaffPermissions.require(actor, capability, target.equals("all") ? null : target);
        requireReason(reason);
        if (pending.containsKey(target)
                || pending.containsKey("all")
                || target.equals("all") && !pending.isEmpty())
            throw new IllegalArgumentException("A conflicting operation is already pending");
        requireContentWritable(target);
        Object before = state.get("maintenance." + target);
        persist(next -> next.set("maintenance." + target, reason));
        audit(actor, "maintenance", target, reason, before + " -> " + reason);
        cancelStarts(target);
        Operation op = new Operation(actor.getName(), action, target, reason);
        operations.put(op.id, op);
        remember(op);
        pending.put(target, new Pending(op, capability, actor, work, resume, before));
        trim();
        audit(actor, action, target, reason, op.shortId() + " WAITING");
        tick();
        return op;
    }

    public void repositoryCancellation(
            Operation operation, java.util.function.BooleanSupplier cancellation) {
        requireOpen();
        if (operation.state == State.WAITING || operation.state == State.RUNNING)
            operation.repositoryCancellation = cancellation;
    }

    public Operation trackRepository(
            CommandSender actor,
            String capability,
            String action,
            String target,
            UUID id,
            java.util.function.BooleanSupplier cancellation) {
        requireOpen();
        StaffPermissions.require(actor, capability, target);
        var operation = new Operation(actor.getName(), action, target, "Dungeon repository");
        operation.id = id;
        operation.state = State.RUNNING;
        operation.detail = "Queued repository transfer";
        operation.repositoryCancellation = cancellation;
        operations.put(id, operation);
        remember(operation);
        trim();
        audit(actor, action, target, "Dungeon repository", operation.shortId() + " RUNNING");
        return operation;
    }

    public void finishRepository(
            CommandSender actor, Operation operation, String detail, Throwable failure) {
        if (closed) return;
        operation.repositoryCancellation = null;
        if (operation.state != State.CANCELLED) {
            operation.state = failure == null ? State.SUCCEEDED : State.FAILED;
            operation.detail =
                    failure == null
                            ? detail
                            : Objects.toString(failure.getMessage(), "Repository operation failed");
        }
        remember(operation);
        audit(
                actor,
                operation.action,
                operation.target,
                "Dungeon repository",
                operation.shortId() + " " + operation.state + " " + operation.detail);
    }

    public void cancel(CommandSender actor, String id, String reason) {
        requireOpen();
        requireReason(reason);
        Operation op = operation(id);
        StaffPermissions.require(actor, "maintenance", op.target.equals("all") ? null : op.target);
        if (op.state == State.RUNNING && op.repositoryCancellation != null) {
            if (!op.repositoryCancellation.getAsBoolean())
                throw new IllegalArgumentException(
                        "Publication is committing; inspect its result before rollback");
            op.state = State.CANCELLED;
            op.detail = "Cancellation requested; an in-flight database batch will settle first";
            remember(op);
            audit(actor, "cancel", id, reason, op.detail);
            return;
        }
        if (op.state != State.WAITING)
            throw new IllegalArgumentException(
                    "Only waiting operations or uncommitted repository transfers can be cancelled safely");
        pending.remove(op.target);
        disposing.remove(op.target);
        op.state = State.CANCELLED;
        op.detail = "Cancelled; maintenance remains enabled";
        remember(op);
        audit(actor, "cancel", id, reason, op.detail);
    }

    public Operation operation(String id) {
        var matches =
                operations.values().stream().filter(o -> o.id.toString().startsWith(id)).toList();
        if (matches.size() != 1)
            throw new IllegalArgumentException("Operation not found or ID is ambiguous");
        return matches.getFirst();
    }

    public List<Operation> operations() {
        return List.copyOf(operations.values());
    }

    public List<String> problems() {
        return List.copyOf(problems);
    }

    private void tick() {
        if (closed) return;
        for (var session : runtime.playerSessions().snapshot())
            if (session.isObserving()) {
                var instance = session.getObservingInstance();
                if (!StaffPermissions.allows(
                        session.getPlayer(), "observe", instance.getDungeon().getWorldName())) {
                    try {
                        session.stopObservation();
                    } catch (RuntimeException failure) {
                        problem(
                                "Observer restoration failed: "
                                        + session.getPlayer().getUniqueId());
                    }
                }
            }
        for (var work : List.copyOf(pending.values())) {
            if (work.operation.state != State.WAITING) continue;
            try {
                advance(work);
            } catch (RuntimeException failure) {
                finish(work, null, failure);
            }
        }
    }

    private void advance(Pending p) {
        Operation op = p.operation;
        if (!StaffPermissions.allows(
                p.actor, p.capability, op.target.equals("all") ? null : op.target))
            throw new IllegalStateException("Permission was revoked");
        if (p.actor instanceof Player player && !player.isOnline())
            throw new IllegalStateException("Requesting staff member disconnected");
        if (runtime.preparations().count(op.target.equals("all") ? null : op.target) > 0) {
            op.detail = "Waiting for pending admissions to settle";
            return;
        }
        var worlds = instances(op.target);
        if (worlds.stream().anyMatch(i -> !i.getPlayers().isEmpty())) {
            op.detail = "Draining: " + impact(op.target);
            return;
        }
        if (!worlds.isEmpty()) {
            var attempt = disposing.get(op.target);
            if (attempt == null) {
                var futures = worlds.stream().map(DungeonInstance::disposeAsync).toList();
                attempt =
                        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                                .thenApply(
                                        ignored ->
                                                futures.stream()
                                                        .allMatch(
                                                                f ->
                                                                        Boolean.TRUE.equals(
                                                                                f.join())));
                disposing.put(op.target, attempt);
            }
            if (!attempt.isDone()) return;
            disposing.remove(op.target);
            if (!Boolean.TRUE.equals(attempt.getNow(false))) {
                finish(
                        p,
                        null,
                        new IllegalStateException(
                                "World disposal failed; inspect instances and retry"));
                return;
            }
            if (!instances(op.target).isEmpty()) return;
        }
        op.state = State.RUNNING;
        op.detail = "Applying " + op.action;
        remember(op);
        Objects.requireNonNull(p.work.get(), "Operation returned no completion")
                .whenComplete((detail, failure) -> owner(() -> finish(p, detail, failure)));
    }

    private void finish(Pending pendingWork, String detail, Throwable failure) {
        Operation op = pendingWork.operation;
        if (closed || pending.get(op.target) != pendingWork) return;
        if (failure == null
                && pendingWork.resume
                && Objects.equals(state.get("maintenance." + op.target), op.reason)) {
            try {
                persist(
                        next ->
                                next.set(
                                        "maintenance." + op.target,
                                        pendingWork.previousMaintenance));
            } catch (RuntimeException persistenceFailure) {
                failure = persistenceFailure;
            }
        }
        pending.remove(op.target, pendingWork);
        disposing.remove(op.target);
        op.state = failure == null ? State.SUCCEEDED : State.FAILED;
        op.detail =
                failure == null
                        ? detail
                        : Objects.toString(
                                failure.getMessage(), failure.getClass().getSimpleName());
        try {
            remember(op);
        } catch (RuntimeException persistenceFailure) {
            op.detail +=
                    "; operation history could not be persisted; inspect current state before"
                            + " retrying";
            problem(op.shortId() + ": " + op.detail);
            runtime.environment()
                    .logger()
                    .error("Cannot persist staff operation outcome", persistenceFailure);
        }
        if (failure != null) problem(op.shortId() + ": " + op.detail);
        audit(
                pendingWork.actor,
                op.action,
                op.target,
                op.reason,
                op.shortId() + " " + op.state + " " + op.detail);
        LangUtils.sendRaw(
                pendingWork.actor,
                op.shortId()
                        + " "
                        + op.state
                        + ": "
                        + op.detail
                        + (maintenance(op.target)
                                ? ". Maintenance remains enabled."
                                : ". Admissions resumed."));
    }

    public CompletableFuture<String> archive(DungeonDefinition dungeon) {
        requireOpen();
        if (!maintenance(dungeon.getWorldName()) || !instances(dungeon.getWorldName()).isEmpty())
            throw new IllegalArgumentException(
                    "Archive requires maintenance and fully unloaded instances");
        File root = new File(runtime.environment().plugin().getDataFolder(), "archives");
        try {
            if (!dungeon.getFolder()
                            .getCanonicalFile()
                            .getParentFile()
                            .equals(runtime.dungeonFiles().getCanonicalFile())
                    || Files.isSymbolicLink(dungeon.getFolder().toPath()))
                throw new IllegalArgumentException("Unsafe dungeon archive source");
            if (!root.getCanonicalFile()
                            .getParentFile()
                            .equals(
                                    runtime.environment()
                                            .plugin()
                                            .getDataFolder()
                                            .getCanonicalFile())
                    || Files.isSymbolicLink(root.toPath()))
                throw new IllegalArgumentException("Unsafe dungeon archive destination");
        } catch (IOException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        String id = dungeon.getWorldName() + "-" + UUID.randomUUID();
        File target = new File(root, id);
        return ConfigurationFile.filesystemAsync(
                        runtime.environment().plugin(),
                        dungeon.getFolder(),
                        () -> {
                            Files.createDirectories(root.toPath());
                            Files.move(
                                    dungeon.getFolder().toPath(),
                                    target.toPath(),
                                    StandardCopyOption.ATOMIC_MOVE);
                            return "Archived as " + id;
                        })
                .thenCompose(
                        message ->
                                onOwner(
                                        () -> {
                                            runtime.dungeonCatalog().remove(dungeon);
                                            return message;
                                        }));
    }

    public void audit(
            CommandSender actor, String action, String target, String reason, String outcome) {
        requireReason(reason);
        String line =
                Instant.now()
                        + " actor="
                        + safe(actor.getName())
                        + " action="
                        + safe(action)
                        + " target="
                        + safe(target)
                        + " reason="
                        + safe(reason)
                        + " result="
                        + safe(outcome)
                        + "\n";
        runtime.environment().logger().info(line.strip());
        runtime.stateStore().await(runtime.stateStore().audit(line));
    }

    public void problem(String detail) {
        problems.addFirst(Instant.now() + " " + detail);
        while (problems.size() > 50) problems.removeLast();
    }

    private void remember(Operation op) {
        persist(
                next -> {
                    String path = "operations." + op.id;
                    next.set(path + ".actor", op.actor);
                    next.set(path + ".action", op.action);
                    next.set(path + ".target", op.target);
                    next.set(path + ".reason", op.reason);
                    next.set(path + ".state", op.state.name());
                    next.set(path + ".detail", op.detail);
                    var section = next.getConfigurationSection("operations");
                    if (section != null) {
                        var keys = new ArrayList<>(section.getKeys(false));
                        for (int i = 0; i < keys.size() - 100; i++)
                            next.set("operations." + keys.get(i), null);
                    }
                });
    }

    private void persist(Consumer<YamlConfiguration> change) {
        var candidate = new YamlConfiguration();
        try {
            candidate.loadFromString(state.saveToString());
        } catch (InvalidConfigurationException impossible) {
            throw new IllegalStateException(impossible);
        }
        change.accept(candidate);
        document.save(candidate.saveToString());
        try {
            state.loadFromString(candidate.saveToString());
        } catch (InvalidConfigurationException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 256)
            throw new IllegalArgumentException("Provide a reason of 1–256 characters");
    }

    private static String safe(String text) {
        return text.replace('\n', ' ').replace('\r', ' ');
    }

    private void trim() {
        while (operations.size() > 100) {
            var completed =
                    operations.values().stream()
                            .filter(o -> o.state != State.WAITING && o.state != State.RUNNING)
                            .findFirst();
            if (completed.isEmpty()) break;
            operations.remove(completed.get().id);
        }
    }

    private void owner(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else if (!closed) Bukkit.getScheduler().runTask(runtime.environment().plugin(), action);
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Staff operations have shut down");
    }

    public boolean contentBusy(String dungeon) {
        return pending.containsKey(dungeon)
                || pending.containsKey("all")
                || runtime.contentRepository() != null && runtime.contentRepository().busy(dungeon);
    }

    public void requireContentWritable(String dungeon) {
        requireOpen();
        if (contentBusy(dungeon))
            throw new IllegalArgumentException(
                    "A content operation owns this dungeon; wait for its result");
    }

    private <T> CompletableFuture<T> onOwner(Supplier<T> action) {
        var future = new CompletableFuture<T>();
        try {
            requireOpen();
            Runnable complete =
                    () -> {
                        try {
                            requireOpen();
                            future.complete(action.get());
                        } catch (Exception failure) {
                            future.completeExceptionally(failure);
                        }
                    };
            if (Bukkit.isPrimaryThread()) complete.run();
            else Bukkit.getScheduler().runTask(runtime.environment().plugin(), complete);
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (ticker != null) ticker.cancel();
        for (var action : List.copyOf(closeActions)) {
            try {
                action.run();
            } catch (RuntimeException failure) {
                runtime.environment().logger().error("Staff interface shutdown failed", failure);
            }
        }
        closeActions.clear();
        for (var session : runtime.playerSessions().snapshot())
            if (session.isObserving()) {
                try {
                    session.stopObservation();
                } catch (RuntimeException failure) {
                    runtime.environment()
                            .logger()
                            .error("Observer restoration failed during shutdown", failure);
                }
            }
        for (Pending work : List.copyOf(pending.values())) {
            work.operation.state = State.CANCELLED;
            work.operation.detail =
                    "Interrupted by shutdown; maintenance remains enabled; inspect state before"
                            + " retrying";
            try {
                remember(work.operation);
                audit(
                        work.actor,
                        work.operation.action,
                        work.operation.target,
                        work.operation.reason,
                        work.operation.detail);
            } catch (RuntimeException failure) {
                runtime.environment()
                        .logger()
                        .error("Cannot persist interrupted staff operation", failure);
            }
        }
        pending.clear();
        disposing.clear();
        confirmations.clear();
    }
}
