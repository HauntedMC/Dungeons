package nl.hauntedmc.dungeons.runtime.staff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaffFailureContractTest {
    @TempDir Path directory;

    @Test
    void exceptionalAndSynchronousDisposalFailuresCannotStallOtherOperations() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var world = mock(DungeonInstance.class);
            when(world.getDungeon()).thenReturn(f.run.dungeon);
            when(world.getPlayers()).thenReturn(List.of());
            when(world.disposeAsync())
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new IllegalStateException("unload failure")));
            f.runtime.activeInstances().registerActiveInstance(world);
            var operation =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "crypt",
                            "Repair",
                            () -> CompletableFuture.completedFuture("unexpected"));
            assertEquals(StaffOperations.State.FAILED, operation.state);
            assertTrue(f.ops.maintenance("crypt"));
            f.runtime.activeInstances().unregisterActiveInstance(world);
            var next =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "crypt",
                            "Retry",
                            () -> CompletableFuture.completedFuture("done"));
            assertEquals(StaffOperations.State.SUCCEEDED, next.state);
            f.runtime.activeInstances().registerActiveInstance(world);
            when(world.disposeAsync())
                    .thenThrow(new IllegalStateException("synchronous unload failure"));
            var thrown =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "crypt",
                            "Retry",
                            () -> CompletableFuture.completedFuture("unexpected"));
            assertEquals(StaffOperations.State.FAILED, thrown.state);
            assertFalse(f.ops.contentBusy("crypt"));
            f.runtime.activeInstances().unregisterActiveInstance(world);
        }
    }

    @Test
    void historyWriteFailureDoesNotRunWorkOrLeaveTheDungeonOperationLockedForever()
            throws Exception {
        try (var f = new StaffFixture(directory)) {
            var participant = f.player("Participant");
            var world = mock(DungeonInstance.class);
            when(world.getDungeon()).thenReturn(f.run.dungeon);
            var players =
                    new ArrayList<>(List.of(f.runtime.playerSessions().get(participant.player)));
            when(world.getPlayers()).thenReturn(players);
            var disposal = CompletableFuture.completedFuture(true);
            when(world.disposeAsync()).thenReturn(disposal);
            f.runtime.activeInstances().registerActiveInstance(world);
            f.ops.start();
            var applied = new AtomicBoolean();
            var operation =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "crypt",
                            "Repair",
                            () -> {
                                applied.set(true);
                                return CompletableFuture.completedFuture("done");
                            });
            f.run.paper.state.failWrites = true;
            players.clear();
            f.runtime.activeInstances().unregisterActiveInstance(world);
            assertDoesNotThrow(f::tick);
            assertFalse(applied.get());
            assertEquals(StaffOperations.State.FAILED, operation.state);
            assertFalse(f.ops.contentBusy("crypt"));
            assertTrue(f.ops.maintenance("crypt"));
        }
    }

    @Test
    void shutdownCancelsWaitingWorkAndIgnoresLateSuccessfulCallbacksEvenWhenHistoryIsUnwritable()
            throws Exception {
        try (var f = new StaffFixture(directory)) {
            var completion = new CompletableFuture<String>();
            var operation =
                    f.ops.drain(f.actor, "reload", "reload", "crypt", "Repair", () -> completion);
            assertEquals(StaffOperations.State.RUNNING, operation.state);
            f.run.paper.state.failWrites = true;
            assertDoesNotThrow(f.ops::close);
            completion.complete("late success");
            assertEquals(StaffOperations.State.CANCELLED, operation.state);
            assertThrows(
                    IllegalStateException.class,
                    () -> f.ops.maintenance(f.actor, "crypt", false, "Resume"));
        }
    }

    @Test
    void failedRestoreLoadsRollBackFilesAndFailedUnarchiveRemainsRetryable() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var storage = new StaffContentStorage(f.runtime);
            f.run.dungeon.setSaveConfig("team.max_size", 3);
            Path source = f.run.dungeon.getFolder().toPath();
            Files.writeString(source.resolve("content.txt"), "backup");
            var copied = storage.backup(f.run.dungeon);
            f.flush();
            String id = copied.join();
            Files.writeString(source.resolve("content.txt"), "current");
            when(f.repository.loadDungeon(any(File.class)))
                    .thenThrow(new IllegalStateException("bad candidate"))
                    .thenReturn(f.run.dungeon);
            var restored = storage.restore(f.run.dungeon, id);
            f.flush();
            assertThrows(CompletionException.class, restored::join);
            assertEquals("current", Files.readString(source.resolve("content.txt")));
            String archive = "retired-" + UUID.randomUUID();
            Path archived = directory.resolve("archives/" + archive);
            Files.createDirectories(archived);
            Files.writeString(archived.resolve("config.yml"), "dungeon: {}\n");
            when(f.repository.loadDungeon(any(File.class))).thenReturn(null);
            var unarchived = storage.archiveAction(archive, false);
            f.flush();
            assertThrows(CompletionException.class, unarchived::join);
            assertTrue(Files.isDirectory(archived));
            assertFalse(Files.exists(directory.resolve("retired")));
            var purged = storage.archiveAction(archive, true);
            f.flush();
            purged.join();
            assertFalse(Files.exists(archived));
        }
    }

    @Test
    void managedStorageRejectsTraversalSymlinksAndActiveWorldArchival() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var storage = new StaffContentStorage(f.runtime);
            assertThrows(IllegalArgumentException.class, () -> storage.backups("../escape"));
            assertThrows(
                    IllegalArgumentException.class, () -> storage.archivedDungeonName("invalid"));
            assertThrows(IllegalArgumentException.class, () -> f.ops.archive(f.run.dungeon));
            Path outside = Files.createTempDirectory("dungeons-storage-test-");
            try {
                Files.createSymbolicLink(directory.resolve("backups"), outside);
                assertThrows(IllegalArgumentException.class, () -> storage.backup(f.run.dungeon));
                f.ops.maintenance(f.actor, "crypt", true, "Retirement");
                Files.createSymbolicLink(directory.resolve("archives"), outside);
                assertThrows(IllegalArgumentException.class, () -> f.ops.archive(f.run.dungeon));
                assertTrue(f.run.dungeon.getFolder().exists());
            } finally {
                Files.delete(directory.resolve("backups"));
                Files.deleteIfExists(directory.resolve("archives"));
                Files.delete(outside);
            }
            Files.createSymbolicLink(
                    f.run.dungeon.getFolder().toPath().resolve("link"),
                    directory.resolve("staff.yml"));
            var rejected = storage.backup(f.run.dungeon);
            f.flush();
            assertThrows(CompletionException.class, rejected::join);
        }
    }

    @Test
    void schedulerRejectionReportsAnArchivalFailureInsteadOfLeavingAnIncompleteFuture()
            throws Exception {
        try (var f = new StaffFixture(directory)) {
            f.ops.maintenance(f.actor, "crypt", true, "Retirement");
            f.run.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            when(f.run.scheduler.scheduler.runTask(eq(f.run.paper.plugin), any(Runnable.class)))
                    .thenThrow(new IllegalStateException("scheduler stopped"));
            var archived = f.ops.archive(f.run.dungeon);
            // The file move finishes, but its catalog callback cannot reach the owner thread.
            f.run.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            f.flush();
            assertTrue(archived.isDone());
            assertThrows(CompletionException.class, archived::join);
        }
    }

    @Test
    void perDungeonRestrictionDoesNotCancelAnotherDungeonsQueue() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var other = f.dungeon("other");
            var player = f.player("Player");
            var session = f.runtime.playerSessions().get(player.player);
            var queue = new DungeonQueueEntry(session, other, "");
            f.runtime.queueRegistry().enqueue(queue);
            f.ops.restrict(f.actor, player.player.getUniqueId(), "crypt", 0, "Review");
            assertSame(queue, f.runtime.queueRegistry().getQueue(session));
            f.ops.restrict(f.actor, player.player.getUniqueId(), "other", 0, "Review");
            assertNull(f.runtime.queueRegistry().getQueue(session));
        }
    }

    @Test
    void successfulArchivalUnarchiveAndCancellationLeaveMaintenanceExplicit() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var op =
                    f.ops.drain(
                            f.actor,
                            "delete",
                            "archive",
                            "crypt",
                            "Retire",
                            () -> f.ops.archive(f.run.dungeon));
            f.flush();
            assertEquals(StaffOperations.State.SUCCEEDED, op.state);
            assertFalse(f.run.dungeon.getFolder().exists());
            var storage = new StaffContentStorage(f.runtime);
            String id = storage.archives().getFirst();
            assertEquals("crypt", storage.archivedDungeonName(id));
            assertNotNull(storage.archiveRevision(id));
            when(f.repository.loadDungeon(any(File.class))).thenReturn(f.run.dungeon);
            var restored = storage.archiveAction(id, false);
            f.flush();
            restored.join();
            assertTrue(f.run.dungeon.getFolder().exists());
            assertTrue(f.ops.maintenance("crypt"));
        }
    }
}
