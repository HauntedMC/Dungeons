package nl.hauntedmc.dungeons.model.dungeon;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

/** One cancellable owner of creation, publication and admission for an isolated run. */
final class InstancePreparation implements PreparationRegistry.Pending {
    private static final String FAILED = "instance.lifecycle.load-failed";
    private static final String TIMEOUT = "instance.lifecycle.load-timeout";

    private final DungeonDefinition dungeon;
    private final Player player;
    private final DungeonPlayerSession session;
    private final @Nullable DungeonDifficulty difficulty;
    private final @Nullable UUID leader;
    private final Consumer<PlayableInstance> prepareAdmission;
    private final long admissionDelay;
    private final long deadline;
    private final PreparationRegistry registry;
    private final CountDownLatch initialized = new CountDownLatch(1);
    private final CountDownLatch published = new CountDownLatch(1);
    // null is active; an empty reason is explicit cancellation without a player notification.
    private final AtomicReference<String> cancellation = new AtomicReference<>();
    private final AtomicBoolean disposalRequested = new AtomicBoolean();
    private final AtomicReference<BukkitTask> creationTask = new AtomicReference<>();
    private volatile PlayableInstance instance;
    private volatile Throwable creationFailure;
    private boolean admitted;
    private boolean cleaning;

    InstancePreparation(
            DungeonDefinition dungeon,
            Player player,
            DungeonPlayerSession session,
            @Nullable DungeonDifficulty difficulty,
            @Nullable UUID leader,
            Duration timeout,
            Consumer<PlayableInstance> prepareAdmission,
            long admissionDelay) {
        this.dungeon = dungeon;
        this.player = player;
        this.session = session;
        this.difficulty = difficulty;
        this.leader = leader;
        this.prepareAdmission = prepareAdmission;
        this.admissionDelay = admissionDelay;
        this.deadline = System.nanoTime() + timeout.toNanos();
        this.registry = dungeon.runtime().preparations();
    }

    boolean start() {
        if (!registry.register(session, this)) return false;
        session.setAwaitingDungeon(true);
        try {
            message("instance.lifecycle.loading");
            Bukkit.getScheduler().runTaskAsynchronously(dungeon.plugin(), this::awaitPreparation);
            return true;
        } catch (Throwable failure) {
            fail(FAILED, failure);
            return false;
        }
    }

    private boolean isCancelled() {
        return cancellation.get() != null;
    }

    private long remaining() {
        return Math.max(0L, deadline - System.nanoTime());
    }

    private void awaitPreparation() {
        if (isCancelled()) return;
        try {
            var task = Bukkit.getScheduler().runTask(dungeon.plugin(), this::create);
            creationTask.set(task);
            // Cancellation may have won before the scheduler returned its handle.
            if (isCancelled()) task.cancel();
            if (!published.await(remaining(), TimeUnit.NANOSECONDS)) {
                fail(TIMEOUT, null);
            } else if (isCancelled()) {
                return;
            } else if (creationFailure != null || instance == null) {
                fail(FAILED, creationFailure);
            } else if (!initialized.await(remaining(), TimeUnit.NANOSECONDS)) {
                fail(TIMEOUT, null);
            } else if (!isCancelled()) {
                Bukkit.getScheduler().runTask(dungeon.plugin(), this::prepareAdmission);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            fail(FAILED, failure);
        } catch (Throwable failure) {
            fail(FAILED, failure);
        }
    }

    private void create() {
        if (isCancelled()) return;
        if (!validPlayer() || remaining() == 0L) {
            fail(remaining() == 0L ? TIMEOUT : FAILED, null);
            return;
        }
        try {
            PlayableInstance created = dungeon.createPlaySession(initialized, leader);
            instance = created;
            if (created != null) {
                // Track even a late result until disposal really succeeds. Failed unloads must
                // continue consuming capacity and remain available to normal disposal retries.
                if (!dungeon.instances.contains(created)) dungeon.instances.add(created);
                dungeon.activeInstances().registerActiveInstance(created);
                created.setDifficulty(difficulty);
                if (remaining() == 0L && !isCancelled()) fail(TIMEOUT, null);
            }
        } catch (Throwable failure) {
            creationFailure = failure;
        } finally {
            published.countDown();
            if (isCancelled()) cleanup();
        }
    }

    private boolean validPlayer() {
        return registry.owns(session, this)
                && session.getPlayer() == player
                && player.isOnline()
                && session.isAwaitingDungeon()
                && session.getInstance() == null
                && !session.isObserving();
    }

    private boolean validateAdmission() {
        if (isCancelled()) {
            cleanup();
            return false;
        }
        if (!validPlayer()
                || instance == null
                || instance.hasLoadFailed()
                || !instance.isReady()
                || instance.getDisposalState() != DungeonInstance.DisposalState.ACTIVE) {
            fail(FAILED, null);
            return false;
        }
        if (remaining() == 0L) {
            fail(TIMEOUT, null);
            return false;
        }
        return true;
    }

    private void prepareAdmission() {
        if (!validateAdmission()) return;
        try {
            prepareAdmission.accept(instance);
            if (admissionDelay == 0L) admit();
            else Bukkit.getScheduler().runTaskLater(dungeon.plugin(), this::admit, admissionDelay);
        } catch (Throwable failure) {
            fail(FAILED, failure);
        }
    }

    private void admit() {
        if (!validateAdmission()) return;
        try {
            instance.addPlayer(session);
            if (session.getInstance() != instance) {
                fail(FAILED, null);
                return;
            }
            if (isCancelled() || !registry.owns(session, this)) {
                cleanup();
                return;
            }
            admitted = true;
            registry.remove(session, this);
            try {
                dungeon.completeReservedAccessKey(session);
                message("instance.lifecycle.entered");
            } finally {
                session.setAwaitingDungeon(false);
            }
        } catch (Throwable failure) {
            // Completed admissions remain owned by their run if notification/key finalization
            // throws. Earlier failures are rolled back by cancellation cleanup.
            if (admitted) dungeon.logger().error("Failed to finish dungeon admission.", failure);
            else fail(FAILED, failure);
        }
    }

    private void fail(String message, @Nullable Throwable failure) {
        if (!cancellation.compareAndSet(null, message)) return;
        if (failure != null)
            dungeon.logger()
                    .error(
                            "Failed to prepare instance for dungeon '{}'.",
                            dungeon.getWorldName(),
                            failure);
        cancel();
    }

    @Override
    public DungeonDefinition dungeon() {
        return dungeon;
    }

    @Override
    public void cancel() {
        cancellation.compareAndSet(null, "");
        BukkitTask task = creationTask.get();
        if (task != null) {
            try {
                task.cancel();
            } catch (RuntimeException failure) {
                dungeon.logger()
                        .warn(
                                "Failed to cancel scheduled creation; its cancellation guard"
                                        + " remains active.",
                                failure);
            }
        }
        published.countDown();
        initialized.countDown();
        if (Bukkit.isPrimaryThread()) cleanup();
        else {
            try {
                Bukkit.getScheduler().runTask(dungeon.plugin(), this::cleanup);
            } catch (RuntimeException failure) {
                // Remain owned by the registry so server-thread shutdown/reload can drain us.
                dungeon.logger()
                        .error(
                                "Failed to schedule preparation cleanup; retaining it for"
                                        + " shutdown.",
                                failure);
            }
        }
    }

    private void cleanup() {
        if (admitted || cleaning) return;
        cleaning = true;
        try {
            cleanupOwnedState();
        } finally {
            cleaning = false;
        }
    }

    private void cleanupOwnedState() {
        PlayableInstance created = instance;
        if (created != null && session.getInstance() == created) {
            try {
                // addPlayer can attach the session before a later scheduler/Bukkit operation
                // throws. Restore that partial admission before refunding or disposing its run.
                created.removePlayer(session, true);
            } catch (RuntimeException failure) {
                dungeon.logger()
                        .error(
                                "Failed to restore partial admission; retaining it for cleanup.",
                                failure);
                return;
            }
            if (session.getInstance() == created) return;
        }
        if (created != null && disposalRequested.compareAndSet(false, true)) {
            try {
                dungeon.discardInstance(created);
            } catch (RuntimeException failure) {
                disposalRequested.set(false);
                dungeon.logger()
                        .error(
                                "Failed to dispose cancelled preparation; retaining its"
                                        + " registration.",
                                failure);
            }
        }
        // A stale callback may still dispose its own instance, but must never modify a newer start.
        if (registry.remove(session, this)) {
            try {
                dungeon.refundReservedAccessKey(session);
            } catch (RuntimeException failure) {
                dungeon.logger().error("Failed to refund cancelled preparation.", failure);
            } finally {
                session.setAwaitingDungeon(false);
            }
            String reason = cancellation.get();
            if (reason != null
                    && !reason.isEmpty()
                    && session.getPlayer() == player
                    && player.isOnline()) {
                message(reason);
            }
        }
    }

    private void message(String key) {
        LangUtils.sendMessage(
                player, key, LangUtils.placeholder("dungeon", dungeon.getDisplayNameComponent()));
    }
}
