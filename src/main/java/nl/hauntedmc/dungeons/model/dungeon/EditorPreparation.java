package nl.hauntedmc.dungeons.model.dungeon;

import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance.DisposalState;
import nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns one editor admission, including delayed creation and cancellation during reload/quit. */
final class EditorPreparation implements PreparationRegistry.Pending {
    private final DungeonDefinition dungeon;
    private final UUID playerId;
    private final long deadline;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private DungeonPlayerSession session;
    private Player player;
    private EditableInstance editor;
    private boolean created;
    private boolean admitted;

    EditorPreparation(DungeonDefinition dungeon, UUID playerId, long timeoutSeconds) {
        this.dungeon = dungeon;
        this.playerId = playerId;
        this.deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
    }

    void start() {
        // A reload can replace the definition before a queued start reaches the owner thread.
        if (dungeon.runtime().dungeonCatalog().get(dungeon.getWorldName()) != dungeon) return;
        session = dungeon.playerSessions().get(playerId);
        if (session == null || session.isExpired()) return;
        player = session.getPlayer();
        if (!player.isOnline() || session.getInstance() != null || session.isAwaitingDungeon()) return;
        if (!dungeon.runtime().preparations().register(session, this)) return;
        session.setAwaitingDungeon(true);
        try {
            if (remaining() == 0L) {
                fail("instance.editor.open-timeout");
                return;
            }
            editor = dungeon.getEditSession();
            if (editor == null) {
                created = true;
                editor = dungeon.createEditSession(new CountDownLatch(1));
                if (editor != null) {
                    if (dungeon.getEditSession() == null) dungeon.setEditSession(editor);
                    dungeon.getInstances().add(editor);
                    dungeon.activeInstances().registerActiveInstance(editor);
                }
            }
            if (cancelled.get()) {
                cleanup();
                return;
            }
            if (editor == null || editor.getDisposalState() != DisposalState.ACTIVE) {
                fail("instance.editor.open-failed");
            } else if (editor.isReady() || editor.hasLoadFailed()) {
                finish();
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(dungeon.plugin(), this::await);
            }
        } catch (RuntimeException | Error failure) {
            dungeon.logger().error("Failed to open editor for '{}'.", dungeon.getWorldName(), failure);
            fail("instance.editor.open-failed");
        }
    }

    private long remaining() {
        return Math.max(0L, deadline - System.nanoTime());
    }

    private void await() {
        try {
            boolean loaded = editor.awaitInitialization(remaining(), TimeUnit.NANOSECONDS);
            if (!loaded) cancelled.set(true);
            Bukkit.getScheduler().runTask(dungeon.plugin(), () -> {
                if (!loaded) fail("instance.editor.open-timeout");
                else finish();
            });
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            cancel();
        } catch (RuntimeException failure) {
            // Keep registry ownership so shutdown can drain a rejected cleanup callback.
            cancelled.set(true);
            dungeon.logger().error("Failed to schedule editor admission cleanup.", failure);
        }
    }

    private void finish() {
        if (cancelled.get()
                || remaining() == 0L
                || dungeon.getEditSession() != editor
                || session.isExpired()
                || session.getPlayer() != player
                || !player.isOnline()
                || session.getInstance() != null
                || !dungeon.runtime().preparations().owns(session, this)) {
            fail("instance.editor.open-timeout");
            return;
        }
        if (editor.hasLoadFailed() || !editor.isReady() || editor.getDisposalState() != DisposalState.ACTIVE) {
            fail("instance.editor.open-failed");
            return;
        }
        try {
            editor.addPlayer(session);
            admitted = session.getInstance() == editor;
        } finally {
            if (admitted) {
                dungeon.runtime().preparations().remove(session, this);
                session.setAwaitingDungeon(false);
            } else cancel();
        }
    }

    private void fail(String key) {
        boolean owned = session != null && dungeon.runtime().preparations().owns(session, this);
        cancelled.set(true);
        cleanup();
        if (owned && player != null && player.isOnline() && session.getPlayer() == player)
            LangUtils.sendMessage(player, key,
                    LangUtils.placeholder("dungeon", dungeon.getDisplayName()));
    }

    @Override
    public DungeonDefinition dungeon() {
        return dungeon;
    }

    @Override
    public void cancel() {
        cancelled.set(true);
        if (Bukkit.isPrimaryThread()) cleanup();
        else {
            try {
                Bukkit.getScheduler().runTask(dungeon.plugin(), this::cleanup);
            } catch (RuntimeException failure) {
                dungeon.logger().error("Retaining editor preparation for shutdown cleanup.", failure);
            }
        }
    }

    private void cleanup() {
        if (admitted) return;
        if (editor != null && session != null && session.getInstance() == editor)
            editor.removePlayer(session, true);
        // Only this request's creation can be discarded; never target a replacement editor.
        if (created && editor != null && editor.getPlayers().isEmpty()) dungeon.discardInstance(editor);
        if (session != null && dungeon.runtime().preparations().remove(session, this))
            session.setAwaitingDungeon(false);
    }
}
