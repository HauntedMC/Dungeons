package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

class EditorPersistenceContractTest {
    @TempDir Path directory;

    @Test
    @org.junit.jupiter.api.Timeout(10)
    void blockedFilesystemWorkDoesNotBlockTheOwnerOrAllowOverlappingSaves() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var executor = Executors.newSingleThreadExecutor()) {
            Thread owner = Thread.currentThread();
            Path live = Files.createDirectories(directory.resolve("live"));
            Files.writeString(live.resolve("level.dat"), "snapshot");
            // A stale copy in the live world must never replace current editor metadata.
            Files.writeString(live.resolve("functions.yml"), "stale metadata");
            when(f.world.getWorldFolder())
                    .thenAnswer(
                            call -> {
                                assertSame(owner, Thread.currentThread());
                                return live.toFile();
                            });
            doAnswer(
                            call -> {
                                assertSame(owner, Thread.currentThread());
                                return null;
                            })
                    .when(f.world)
                    .save(true);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var editor =
                    new Editor(f) {
                        @Override
                        protected void saveWorldFiles(java.io.File source, java.io.File target)
                                throws java.io.IOException {
                            assertFalse(owner == Thread.currentThread());
                            entered.countDown();
                            try {
                                if (!release.await(3, TimeUnit.SECONDS))
                                    throw new java.io.IOException("test release timed out");
                            } catch (InterruptedException failure) {
                                throw new java.io.IOException(failure);
                            }
                            super.saveWorldFiles(source, target);
                        }
                    };
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var latch = new CountDownLatch(2);
            var saved = editor.saveWorldAsync(latch);
            // Drive metadata independently of the deliberately blocked filesystem worker.
            f.scheduler.asynchronous.getFirst().run();
            var worker = executor.submit(f.scheduler.asynchronous.getLast()::run);
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertFalse(saved.isDone());
                assertTrue(f.dungeon.isSaving());
                assertFalse(editor.saveWorldAsync().join());
                Path metadata = f.dungeon.getFolder().toPath().resolve("functions.yml");
                Files.writeString(metadata, "newer metadata");
                // Cancelling an observer must not release save ownership or lose its latch.
                saved.cancel(false);
                assertTrue(f.dungeon.isSaving());
                release.countDown();
                worker.get(3, TimeUnit.SECONDS);
                assertFalse(f.dungeon.isSaving());
                assertEquals(1, latch.getCount());
                assertEquals("newer metadata", Files.readString(metadata));
                assertEquals(
                        "snapshot",
                        Files.readString(f.dungeon.getFolder().toPath().resolve("level.dat")));
                verify(f.world, times(1)).save(true);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void shutdownDrainsQueuedCopiesBeforeFlushingFinalEditsAndUnloading() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path live = Files.createDirectories(directory.resolve("live"));
            Files.writeString(live.resolve("level.dat"), "before shutdown");
            when(f.world.getWorldFolder()).thenReturn(live.toFile());
            var editor = new Editor(f);
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var first = editor.saveWorldAsync();
            assertFalse(first.isDone());
            var abandonedTasks = List.copyOf(f.scheduler.asynchronous);
            when(f.paper.plugin.isEnabled()).thenReturn(false);
            doAnswer(
                            call -> {
                                Files.writeString(live.resolve("level.dat"), "final edits");
                                return null;
                            })
                    .when(f.world)
                    .save(true);
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(f.world, false)).thenReturn(true);
            assertTrue(editor.disposeAsync().get(3, TimeUnit.SECONDS));
            assertTrue(first.join());
            assertEquals(
                    "final edits",
                    Files.readString(f.dungeon.getFolder().toPath().resolve("level.dat")));
            assertFalse(Files.exists(live));
            // A cancelled scheduler task running late must not replay a copy from a deleted world.
            abandonedTasks.forEach(CapturedScheduler.Job::run);
            assertFalse(f.dungeon.isSaving());
            verify(f.world, times(2)).save(true);
        }
    }

    @Test
    void modernEditorSaveReplacesLegacyAndDeletedWorldDataWhilePreservingDungeonFiles()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path template = f.dungeon.getFolder().toPath();
            Files.createDirectories(template.resolve("DIM1/region"));
            Files.writeString(template.resolve("DIM1/region/obsolete.mca"), "legacy");
            Files.writeString(template.resolve("level.dat"), "legacy metadata");
            Files.createDirectories(template.resolve("entities"));
            Files.writeString(template.resolve("entities/deleted.mca"), "removed entities");
            Files.writeString(template.resolve("config.yml"), "dungeon settings");
            Files.createDirectories(template.resolve("rooms"));
            Files.writeString(template.resolve("rooms/keep.yml"), "room");
            Path live =
                    Files.createDirectories(directory.resolve("live/data/minecraft"))
                            .getParent()
                            .getParent();
            Files.writeString(
                    live.resolve("data/minecraft/world_gen_settings.dat"), "modern settings");
            Files.createDirectories(live.resolve("region"));
            Files.writeString(live.resolve("region/current.mca"), "edited terrain");
            when(f.world.getWorldFolder()).thenReturn(live.toFile());
            assertTrue(new Editor(f).saveWorldAsync(null).get(1, TimeUnit.SECONDS));
            assertFalse(Files.exists(template.resolve("level.dat")));
            assertFalse(Files.exists(template.resolve("DIM1")));
            assertFalse(Files.exists(template.resolve("entities/deleted.mca")));
            assertEquals(
                    "edited terrain", Files.readString(template.resolve("region/current.mca")));
            assertEquals("dungeon settings", Files.readString(template.resolve("config.yml")));
            assertEquals("room", Files.readString(template.resolve("rooms/keep.yml")));
        }
    }

    @Test
    void failedSnapshotCopyLeavesOriginalWorldAndConfigIntact() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path template = f.dungeon.getFolder().toPath();
            Files.writeString(template.resolve("level.dat"), "original world");
            Files.writeString(template.resolve("config.yml"), "original config");
            when(f.world.getWorldFolder()).thenReturn(directory.resolve("absent").toFile());
            assertFalse(new Editor(f).saveWorldAsync(null).get(1, TimeUnit.SECONDS));
            assertEquals("original world", Files.readString(template.resolve("level.dat")));
            assertEquals("original config", Files.readString(template.resolve("config.yml")));
        }
    }

    @Test
    void editorSaveCopiesTheWorldAndFunctionsWithoutReplacingRoomTemplatesOrWorldIdentity()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path source = Files.createDirectories(directory.resolve("live"));
            Files.writeString(source.resolve("level.dat"), "edited world");
            Files.writeString(source.resolve("uid.dat"), "runtime identity");
            Path incoming = Files.createDirectories(source.resolve("rooms"));
            Files.writeString(incoming.resolve("room.yml"), "wrong room");
            Path rooms = Files.createDirectories(f.dungeon.getFolder().toPath().resolve("rooms"));
            Files.writeString(rooms.resolve("room.yml"), "template room");
            when(f.world.getWorldFolder()).thenReturn(source.toFile());
            var editor = new Editor(f);
            var latch = new CountDownLatch(1);
            assertTrue(editor.saveWorldAsync(latch).get(1, TimeUnit.SECONDS));
            assertEquals(0, latch.getCount());
            assertFalse(f.dungeon.isSaving());
            assertEquals(
                    "edited world",
                    Files.readString(f.dungeon.getFolder().toPath().resolve("level.dat")));
            assertEquals("template room", Files.readString(rooms.resolve("room.yml")));
            assertFalse(Files.exists(f.dungeon.getFolder().toPath().resolve("uid.dat")));
            assertTrue(Files.exists(f.dungeon.getFolder().toPath().resolve("functions.yml")));
            verify(f.world).save(true);
        }
    }

    @Test
    void overlappingSavesAreRejectedWhileTheOriginalSaveKeepsItsOwnCompletionAndLatch()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var editor = new Editor(f);
            editor.pending = new CompletableFuture<>();
            var originalLatch = new CountDownLatch(1);
            var rejectedLatch = new CountDownLatch(1);
            var original = editor.saveWorldAsync(originalLatch);
            assertFalse(original.isDone());
            assertTrue(f.dungeon.isSaving());
            assertFalse(editor.saveWorldAsync(rejectedLatch).get(1, TimeUnit.SECONDS));
            assertEquals(0, rejectedLatch.getCount());
            assertEquals(1, originalLatch.getCount());
            assertTrue(f.dungeon.isSaving());
            editor.pending.complete(true);
            assertTrue(original.get(1, TimeUnit.SECONDS));
            assertEquals(0, originalLatch.getCount());
            assertFalse(f.dungeon.isSaving());
            verify(f.world, times(1)).save(true);
        }
    }

    @Test
    void failedWorldFlushCopyAndExceptionalCompletionAllReleaseSavingStateAndWaiters()
            throws Exception {
        for (String failure : List.of("flush", "copy", "future")) {
            try (var f = new RunFixture(directory, new YamlConfiguration())) {
                var editor = new Editor(f);
                when(f.world.getWorldFolder())
                        .thenReturn(directory.resolve("missing-source").toFile());
                if (failure.equals("flush"))
                    doThrow(new IllegalStateException("flush failed")).when(f.world).save(true);
                if (failure.equals("future"))
                    editor.pending =
                            CompletableFuture.failedFuture(
                                    new IllegalStateException("commit failed"));
                var latch = new CountDownLatch(1);
                assertFalse(editor.saveWorldAsync(latch).get(1, TimeUnit.SECONDS));
                assertEquals(0, latch.getCount());
                assertFalse(f.dungeon.isSaving());
            }
        }
    }

    @Test
    void aSynchronousCommitExceptionStillCompletesTheSaveFutureAndReleasesItsLatch()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var editor = new Editor(f);
            editor.throwCommit = true;
            var latch = new CountDownLatch(1);
            var result = assertDoesNotThrow(() -> editor.saveWorldAsync(latch));
            assertFalse(result.get(1, TimeUnit.SECONDS));
            assertEquals(0, latch.getCount());
            assertFalse(f.dungeon.isSaving());
        }
    }

    @Test
    void ownerThreadSerializationFailureCompletesTheEditorSaveWithoutQueueingDiskWork()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path source = Files.createDirectories(directory.resolve("live"));
            when(f.world.getWorldFolder()).thenReturn(source.toFile());
            var broken = mock(DungeonFunction.class);
            when(broken.serialize()).thenThrow(new IllegalStateException("broken serializer"));
            f.dungeon.getFunctions().put(new Location(f.world, 0, 70, 0), broken);
            var editor = new Editor(f);
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var latch = new CountDownLatch(1);
            var result = editor.saveWorldAsync(latch);
            assertTrue(result.isDone());
            assertTrue(f.scheduler.asynchronous.isEmpty());
            assertFalse(result.get(1, TimeUnit.SECONDS));
            assertEquals(0, latch.getCount());
            assertFalse(f.dungeon.isSaving());
        }
    }

    @Test
    void rejectedEditorAdmissionCannotClearTheInventoryOrChangeAnotherRunsPlayerMode()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            var session = f.session(state);
            f.run.addPlayer(session);
            var editor = new Editor(f);
            assertDoesNotThrow(() -> editor.addPlayer(session));
            assertSame(f.run, session.getInstance());
            assertFalse(session.isEditMode());
            assertEquals(GameMode.ADVENTURE, state.gameMode);
            assertEquals(7, state.contents[0].getAmount());
            assertTrue(editor.getPlayers().isEmpty());
            assertTrue(f.run.getPlayers().contains(session));
        }
    }

    @Test
    void cancelledEditorDeparturePreservesInventoryAndClipboardUntilAForcedExitIsAccepted()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var tools = mockStatic(ItemUtils.class)) {
            var functionTool = PlayerState.stack(1);
            var roomTool = PlayerState.stack(1);
            tools.when(ItemUtils::getFunctionTool).thenReturn(functionTool);
            tools.when(ItemUtils::getRoomTool).thenReturn(roomTool);
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            var session = f.session(state);
            var editor = new Editor(f);
            editor.addPlayer(session);
            assertTrue(session.isEditMode());
            assertEquals(GameMode.CREATIVE, state.gameMode);
            state.contents[5] = PlayerState.stack(20);
            var copied = new SetStatusFunction();
            session.beginFunctionCopy(copied);
            session.setActiveFunction(copied);
            session.setChatListening(true);
            session.setAwaitingRoomName(true);
            var manager = Bukkit.getPluginManager();
            doAnswer(
                            c -> {
                                if (c.getArgument(0) instanceof PlayerLeaveDungeonEvent event)
                                    event.setCancelled(true);
                                return null;
                            })
                    .when(manager)
                    .callEvent(any(org.bukkit.event.Event.class));
            editor.removePlayer(session, false);
            assertSame(editor, session.getInstance());
            assertTrue(session.isEditMode());
            assertEquals(20, state.contents[5].getAmount());
            assertSame(copied, session.getCopiedFunction());
            assertTrue(session.isCopying());
            assertTrue(session.isChatListening());
            assertTrue(session.isAwaitingRoomName());
            editor.removePlayer(session, true);
            assertNull(session.getInstance());
            assertFalse(session.isEditMode());
            assertEquals(7, state.contents[0].getAmount());
            assertNull(session.getCopiedFunction());
            assertFalse(session.isChatListening());
            assertFalse(session.isAwaitingRoomName());
            assertEquals(GameMode.SURVIVAL, state.gameMode);
            verify(manager, times(2)).callEvent(any(PlayerLeaveDungeonEvent.class));
        }
    }

    @Test
    void delayedEditorInventoryCannotOverwriteTheSurvivalInventoryAfterDeparture()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var tools = mockStatic(ItemUtils.class)) {
            var functionTool = PlayerState.stack(1);
            var roomTool = PlayerState.stack(1);
            tools.when(ItemUtils::getFunctionTool).thenReturn(functionTool);
            tools.when(ItemUtils::getRoomTool).thenReturn(roomTool);
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            f.paper.entities.when(() -> EntityUtils.getMaxHealth(state.player)).thenReturn(20.0);
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var session = f.session(state);
            var editor = new Editor(f);
            editor.addPlayer(session);
            var pendingInventory =
                    f.scheduler.jobs.stream()
                            .filter(job -> job.delay == 2 && job.period == 0)
                            .findFirst()
                            .orElseThrow();
            state.contents[5] = PlayerState.stack(20);
            editor.removePlayer(session, true);
            assertNull(session.getInstance());
            assertFalse(session.isEditMode());
            assertEquals(7, state.contents[0].getAmount());
            assertNull(state.contents[5]);
            pendingInventory.run();
            assertEquals(7, state.contents[0].getAmount());
            assertNull(state.contents[5]);
        }
    }

    @Test
    void autosaveSkipsAnInFlightSaveAndShutdownCancelsItsTickerWithoutLateFeedback()
            throws Exception {
        for (boolean success : List.of(true, false)) {
            try (var f = new RunFixture(directory, new YamlConfiguration());
                    var tools = mockStatic(ItemUtils.class)) {
                f.paper.config.set("editor.autosave_interval_seconds", 1);
                when(f.paper.plugin.isEnabled()).thenReturn(true);
                var editor = new Editor(f);
                f.dungeon.setEditSession(editor);
                var ticker =
                        f.scheduler.jobs.stream()
                                .filter(job -> job.period == 20)
                                .findFirst()
                                .orElseThrow();
                var session = f.session(new PlayerState());
                editor.addPlayer(session);
                f.scheduler.jobs.stream()
                        .filter(job -> job.delay == 2 && job.period == 0)
                        .findFirst()
                        .orElseThrow()
                        .run();
                editor.pending = new CompletableFuture<>();
                ticker.run();
                assertTrue(f.dungeon.isSaving());
                ticker.run();
                verify(f.world, times(1)).save(true);
                assertTrue(
                        f.scheduler.immediate.isEmpty(),
                        "An overlapping autosave must not report a false save failure");
                when(f.paper.plugin.isEnabled()).thenReturn(false);
                editor.removePlayer(session, true);
                editor.onDispose();
                assertTrue(ticker.cancelled);
                assertSame(editor, f.dungeon.getEditSession());
                editor.pending.complete(success);
                assertFalse(f.dungeon.isSaving());
                assertTrue(
                        f.scheduler.immediate.isEmpty(),
                        "Completing a save after shutdown must not enqueue player feedback");
                ticker.run();
                verify(f.world, times(2)).save(true);
            }
        }
    }

    @Test
    void leavingAnEditorPreservesTaggedSurvivalItemsWithoutHandingThemToAnotherEditor()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var tools = mockStatic(ItemUtils.class)) {
            tools.when(() -> ItemUtils.verifyKeyItem(any())).thenCallRealMethod();
            tools.when(() -> ItemUtils.verifyDungeonItem(any())).thenCallRealMethod();
            var state = new PlayerState();
            state.contents[0] =
                    TaggedItems.stack(
                            Material.STONE,
                            7,
                            Map.of(new NamespacedKey("dungeons", "dungeonkey"), 1));
            state.contents[5] =
                    TaggedItems.stack(
                            Material.STONE,
                            3,
                            Map.of(new NamespacedKey("dungeons", "dungeonitem"), 1));
            var session = f.session(state);
            var other = f.session(new PlayerState());
            var editor = new Editor(f);
            editor.addPlayer(session);
            editor.addPlayer(other);
            editor.removePlayer(session, true);
            assertNull(session.getInstance());
            assertSame(editor, other.getInstance());
            assertEquals(7, state.contents[0].getAmount());
            assertEquals(3, state.contents[5].getAmount());
            assertTrue(ItemUtils.verifyKeyItem(state.contents[0]));
            assertTrue(ItemUtils.verifyDungeonItem(state.contents[5]));
            tools.verify(() -> ItemUtils.giveOrDrop(any(), any(ItemStack.class)), never());
        }
    }

    @Test
    void rejectedMainThreadSaveSchedulingCompletesTheFutureAndReleasesItsWaiter() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var editor = new Editor(f);
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            f.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            doThrow(new IllegalStateException("scheduler stopped"))
                    .when(f.scheduler.scheduler)
                    .runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class));
            var latch = new CountDownLatch(1);
            var result = assertDoesNotThrow(() -> editor.saveWorldAsync(latch));
            assertFalse(result.get(1, TimeUnit.SECONDS));
            assertEquals(0, latch.getCount());
            assertFalse(f.dungeon.isSaving());
            verify(f.world, never()).save(true);
        }
    }

    @Test
    void closingEditorCommitsLastEditsBeforeUnloadingAndDeletingTheLiveWorld() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path live = Files.createDirectories(directory.resolve("live"));
            Files.writeString(live.resolve("level.dat"), "last edits");
            when(f.world.getWorldFolder()).thenReturn(live.toFile());
            f.paper
                    .bukkit
                    .when(() -> Bukkit.unloadWorld(f.world, false))
                    .thenAnswer(
                            call -> {
                                assertEquals(
                                        "last edits",
                                        Files.readString(
                                                f.dungeon
                                                        .getFolder()
                                                        .toPath()
                                                        .resolve("level.dat")));
                                assertTrue(
                                        Files.exists(
                                                f.dungeon
                                                        .getFolder()
                                                        .toPath()
                                                        .resolve("functions.yml")));
                                return true;
                            });
            var editor = new Editor(f);
            f.dungeon.setEditSession(editor);
            editor.dispose();
            editor.dispose();
            verify(f.world, times(1)).save(true);
            assertFalse(Files.exists(live));
            assertNull(editor.getInstanceWorld());
            assertNull(f.dungeon.getEditSession());
        }
    }

    @Test
    void failedFinalSaveRetainsWorldFilesSessionAndRegistrations() throws Exception {
        for (String failure : List.of("flush", "commit", "metadata")) {
            try (var f = new RunFixture(directory, new YamlConfiguration())) {
                Path live = Files.createDirectories(directory.resolve("live"));
                Files.writeString(live.resolve("level.dat"), "recover me");
                when(f.world.getWorldFolder()).thenReturn(live.toFile());
                var editor = new Editor(f);
                f.dungeon.setEditSession(editor);
                f.dungeon.getInstances().add(editor);
                if (failure.equals("flush"))
                    doThrow(new IllegalStateException("disk failure")).when(f.world).save(true);
                if (failure.equals("commit")) editor.throwCommit = true;
                if (failure.equals("metadata"))
                    editor.pending = CompletableFuture.completedFuture(false);
                editor.dispose();
                assertEquals("recover me", Files.readString(live.resolve("level.dat")));
                assertSame(f.world, editor.getInstanceWorld());
                assertSame(editor, f.dungeon.getEditSession());
                assertTrue(f.dungeon.getInstances().contains(editor));
                f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), never());
                editor.throwCommit = false;
                editor.pending = null;
                doNothing().when(f.world).save(true);
                f.paper.bukkit.when(() -> Bukkit.unloadWorld(f.world, false)).thenReturn(true);
                assertTrue(editor.disposeAsync().join());
                assertNull(editor.getInstanceWorld());
                assertNull(f.dungeon.getEditSession());
                assertFalse(f.dungeon.getInstances().contains(editor));
            }
        }
    }

    @Test
    void closingWaitsForEarlierSaveThenTakesAFreshSnapshotBeforeUnload() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path live = Files.createDirectories(directory.resolve("live"));
            when(f.world.getWorldFolder()).thenReturn(live.toFile());
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var editor = new Editor(f);
            var earlier = new CompletableFuture<Boolean>();
            editor.pending = earlier;
            editor.saveWorldAsync();
            editor.dispose();
            verify(f.world, times(1)).save(true);
            f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), never());
            var last = new CompletableFuture<Boolean>();
            editor.pending = last;
            earlier.complete(true);
            verify(f.world, times(2)).save(true);
            f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), never());
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(f.world, false)).thenReturn(true);
            last.complete(true);
            f.scheduler.asynchronous.getLast().run();
            assertFalse(Files.exists(live));
            assertNull(editor.getInstanceWorld());
        }
    }

    @Test
    void shutdownDuringFinalSaveRetainsTheEditorAndLateMetadataCannotUnloadIt() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path live = Files.createDirectories(directory.resolve("live"));
            when(f.world.getWorldFolder()).thenReturn(live.toFile());
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var editor = new Editor(f);
            f.dungeon.setEditSession(editor);
            f.dungeon.getInstances().add(editor);
            editor.pending = new CompletableFuture<>();
            var first = editor.disposeAsync();
            assertFalse(first.isDone());
            when(f.paper.plugin.isEnabled()).thenReturn(false);
            assertFalse(editor.disposeAsync().join());
            assertFalse(first.join());
            editor.pending.complete(true);
            f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), never());
            assertSame(editor, f.dungeon.getEditSession());
            assertTrue(f.dungeon.getInstances().contains(editor));
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(f.world, false)).thenReturn(true);
            assertTrue(editor.disposeAsync().join());
            assertNull(f.dungeon.getEditSession());
        }
    }

    @Test
    void shutdownWithPendingSaveFlushesRecoveryWorldWithoutWaitingOrDeleting() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path live = Files.createDirectories(directory.resolve("live"));
            when(f.world.getWorldFolder()).thenReturn(live.toFile());
            var editor = new Editor(f);
            editor.pending = new CompletableFuture<>();
            editor.saveWorldAsync();
            editor.dispose();
            verify(f.world, times(2)).save(true);
            assertTrue(Files.exists(live));
            f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), never());
        }
    }

    @Test
    void metadataCompletionReturnsToServerThreadBeforeFinalUnload() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path live = Files.createDirectories(directory.resolve("live"));
            when(f.world.getWorldFolder()).thenReturn(live.toFile());
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            var editor = new Editor(f);
            editor.dispose();
            f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), never());
            f.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            for (var task : List.copyOf(f.scheduler.asynchronous)) task.run();
            f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), never());
            assertFalse(f.scheduler.immediate.isEmpty());
            f.paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(f.world, false)).thenReturn(true);
            f.scheduler.immediate.getFirst().run();
            f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), times(1));
            f.scheduler.asynchronous.getLast().run();
            assertFalse(Files.exists(live));
        }
    }

    @Test
    void rejectedMetadataSchedulingRetainsTheWorldAndCompletesTheSave() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path live = Files.createDirectories(directory.resolve("live"));
            when(f.world.getWorldFolder()).thenReturn(live.toFile());
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            doThrow(new IllegalStateException("scheduler stopped"))
                    .when(f.scheduler.scheduler)
                    .runTaskAsynchronously(
                            any(org.bukkit.plugin.Plugin.class), any(Runnable.class));
            var editor = new Editor(f);
            var result = editor.saveWorldAsync();
            assertFalse(result.get(1, TimeUnit.SECONDS));
            assertFalse(f.dungeon.isSaving());
            editor.dispose();
            assertTrue(Files.exists(live));
            f.paper.bukkit.verify(() -> Bukkit.unloadWorld(f.world, false), never());
        }
    }

    static class Editor extends EditableInstance {
        CompletableFuture<Boolean> pending;
        boolean throwCommit;

        Editor(RunFixture f) {
            super(f.dungeon, null);
            this.instanceWorld = f.world;
            this.startLocation = new Location(f.world, 0, 70, 0);
        }

        @Override
        protected CompletableFuture<Boolean> commitWorldAsync(World world) {
            if (throwCommit) throw new IllegalStateException("commit failed");
            return pending == null ? super.commitWorldAsync(world) : pending;
        }
    }
}
