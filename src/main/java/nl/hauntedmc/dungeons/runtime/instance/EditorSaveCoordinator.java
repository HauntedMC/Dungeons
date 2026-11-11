package nl.hauntedmc.dungeons.runtime.instance;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.config.ConfigurationFile;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/** Owns editor save admission, phase transitions and the final-save barrier before disposal. */
public final class EditorSaveCoordinator {
    public enum State {
        IDLE,
        CAPTURING,
        WRITING
    }

    /** Bukkit snapshot work belongs to the target; completion bookkeeping must be thread-safe. */
    interface Target {
        boolean canSave(boolean finalSave);

        boolean isSaving();

        void markSaving(boolean saving);

        void captureSnapshot();

        CompletableFuture<Boolean> persistSnapshot();

        void flushRecoverySnapshot();
    }

    private final Plugin plugin;
    private final String name;
    private final Target target;
    private volatile CompletableFuture<Boolean> active;
    private volatile State state = State.IDLE;

    EditorSaveCoordinator(Plugin plugin, String name, Target target) {
        this.plugin = plugin;
        this.name = name;
        this.target = target;
    }

    public State state() {
        return state;
    }

    public CompletableFuture<Boolean> save(@Nullable CountDownLatch latch) {
        return save(latch, false);
    }

    private CompletableFuture<Boolean> save(@Nullable CountDownLatch latch, boolean finalSave) {
        CompletableFuture<Boolean> observed =
                ConfigurationFile.onOwnerThread(
                        plugin,
                        () -> {
                            if (!target.canSave(finalSave) || target.isSaving())
                                return CompletableFuture.completedFuture(false);
                            var result = new CompletableFuture<Boolean>();
                            active = result;
                            target.markSaving(true);
                            state = State.CAPTURING;
                            try {
                                target.captureSnapshot();
                                state = State.WRITING;
                                Objects.requireNonNull(target.persistSnapshot())
                                        .whenComplete(
                                                (success, failure) -> {
                                                    if (failure != null) log(failure);
                                                    finish(
                                                            result,
                                                            failure == null
                                                                    && Boolean.TRUE.equals(
                                                                            success));
                                                });
                            } catch (Throwable failure) {
                                log(failure);
                                finish(result, false);
                            }
                            return result;
                        });
        observed.whenComplete(
                (success, failure) -> {
                    if (latch != null) latch.countDown();
                });
        // Cancellation is only an observer action; the admitted save still owns its latch/state.
        return observed.copy();
    }

    /** Always takes a fresh final snapshot after any earlier save, including shutdown draining. */
    public CompletableFuture<Boolean> prepareDisposal() {
        if (active != null && !active.isDone()) {
            if (!plugin.isEnabled()) {
                ConfigurationFile.flushPendingSaves();
                if (!active.isDone()) {
                    target.flushRecoverySnapshot();
                    return CompletableFuture.completedFuture(false);
                }
                return save(null, true);
            }
            return active.handle((success, failure) -> true)
                    .thenCompose(
                            ignored ->
                                    plugin.isEnabled()
                                            ? save(null, true)
                                            : CompletableFuture.completedFuture(false));
        }
        return save(null, true);
    }

    private void finish(CompletableFuture<Boolean> result, boolean success) {
        // Publish the completed phase before releasing admission to a newer owner-thread save.
        state = State.IDLE;
        target.markSaving(false);
        result.complete(success);
    }

    private void log(Throwable failure) {
        plugin.getSLF4JLogger()
                .error("Failed to save editor world for dungeon '{}'.", name, failure);
    }
}
