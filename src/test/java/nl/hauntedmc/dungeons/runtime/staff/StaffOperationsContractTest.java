package nl.hauntedmc.dungeons.runtime.staff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.model.instance.*;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.queue.*;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;

class StaffOperationsContractTest {
    @TempDir Path directory;

    @Test
    void confirmationsAreActorActionTargetRevisionParameterBoundExpiringAndSingleUse()
            throws Exception {
        try (var f = new Fixture(directory)) {
            var now = new AtomicLong(1);
            var ops = new StaffOperations(f.run.paper.runtime, now::get);
            var other = actor("Other");
            UUID token = ops.confirm(f.actor, "archive", "crypt", "v1", "reason", null);
            assertNotNull(ops.confirm(other, "archive", "crypt", "v1", "reason", token.toString()));
            assertNotNull(
                    ops.confirm(f.actor, "archive", "other", "v1", "reason", token.toString()));
            token = ops.confirm(f.actor, "archive", "crypt", "v1", "reason", null);
            assertNotNull(
                    ops.confirm(f.actor, "archive", "crypt", "v2", "reason", token.toString()));
            token = ops.confirm(f.actor, "archive", "crypt", "v1", "reason", null);
            now.addAndGet(TimeUnit.SECONDS.toNanos(31));
            assertNotNull(
                    ops.confirm(f.actor, "archive", "crypt", "v1", "reason", token.toString()));
            token = ops.confirm(f.actor, "archive", "crypt", "v1", "reason", null);
            assertNull(ops.confirm(f.actor, "archive", "crypt", "v1", "reason", token.toString()));
            assertNotNull(
                    ops.confirm(f.actor, "archive", "crypt", "v1", "reason", token.toString()));
        }
    }

    @Test
    void maintenanceAndRestrictionsSurviveReconstructionAndPreventDirectAdmission()
            throws Exception {
        try (var f = new Fixture(directory)) {
            var session = f.run.session(new PlayerState());
            f.ops.maintenance(f.actor, "crypt", true, "Repairing content");
            assertFalse(f.run.dungeon.isEnabled());
            assertFalse(f.run.dungeon.instantiate(session.getPlayer()));
            assertTrue(new StaffOperations(f.run.paper.runtime).maintenance("crypt"));
            f.ops.maintenance(f.actor, "crypt", false, "Ready");
            f.ops.restrict(f.actor, session.getPlayer().getUniqueId(), "crypt", 0, "Abuse review");
            assertTrue(
                    new StaffOperations(f.run.paper.runtime)
                            .entryBlock(session.getPlayer().getUniqueId(), "crypt")
                            .contains("Abuse review"));
            f.run.run.addPlayer(session);
            assertNull(session.getInstance());
            assertTrue(f.run.run.getPlayers().isEmpty());
        }
    }

    @Test
    void drainingWaitsForParticipantsAndWorldDisposalAndLeavesMaintenanceEnabled()
            throws Exception {
        try (var f = new Fixture(directory)) {
            var instance = mock(DungeonInstance.class);
            when(instance.getDungeon()).thenReturn(f.run.dungeon);
            var participants =
                    new ArrayList<DungeonPlayerSession>();
            participants.add(f.run.session(new PlayerState()));
            when(instance.getPlayers()).thenReturn(participants);
            var disposal = new CompletableFuture<Boolean>();
            when(instance.disposeAsync()).thenReturn(disposal);
            f.run.paper.runtime.activeInstances().registerActiveInstance(instance);
            f.ops.start();
            var applied = new AtomicBoolean();
            var operation =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "crypt",
                            "Fix content",
                            () -> {
                                applied.set(true);
                                return CompletableFuture.completedFuture("loaded");
                            });
            assertEquals(StaffOperations.State.WAITING, operation.state);
            verify(instance, never()).disposeAsync();
            participants.clear();
            f.run.scheduler.jobs.getLast().run();
            assertFalse(applied.get());
            f.run.paper.runtime.activeInstances().unregisterActiveInstance(instance);
            disposal.complete(true);
            f.run.scheduler.jobs.getLast().run();
            assertTrue(applied.get());
            assertEquals(StaffOperations.State.SUCCEEDED, operation.state);
            assertTrue(f.ops.maintenance("crypt"));
        }
    }

    @Test
    void disposalFailureAndPermissionRevocationCannotApplyPendingWork() throws Exception {
        try (var f = new Fixture(directory)) {
            var instance = mock(DungeonInstance.class);
            when(instance.getDungeon()).thenReturn(f.run.dungeon);
            when(instance.getPlayers()).thenReturn(List.of());
            when(instance.disposeAsync()).thenReturn(CompletableFuture.completedFuture(false));
            f.run.paper.runtime.activeInstances().registerActiveInstance(instance);
            var work = new AtomicBoolean();
            var op =
                    f.ops.drain(
                            f.actor,
                            "delete",
                            "archive",
                            "crypt",
                            "Broken world",
                            () -> {
                                work.set(true);
                                return CompletableFuture.completedFuture("bad");
                            });
            assertEquals(StaffOperations.State.FAILED, op.state);
            assertFalse(work.get());
            assertTrue(Files.isDirectory(f.run.dungeon.getFolder().toPath()));
            assertTrue(f.ops.maintenance("crypt"));
        }
    }

    @Test
    void failedConfigurationSaveRetainsTheDurableValueAndExternalExitSurvivesReload()
            throws Exception {
        try (var f = new Fixture(directory)) {
            f.run.dungeon.setSaveConfig("team.max_size", 3);
            Path config = f.run.dungeon.getFolder().toPath().resolve("config.yml");
            Files.delete(config);
            Files.createDirectory(config);
            Files.writeString(config.resolve("obstacle"), "keep");
            assertThrows(
                    RuntimeException.class, () -> f.run.dungeon.setSaveConfig("team.max_size", 7));
            assertEquals(3, f.run.dungeon.getMaxTeamSize());
            Files.delete(config.resolve("obstacle"));
            Files.delete(config);
            var state = new PlayerState();
            UUID world = state.originWorld.getUID();
            f.run.paper.bukkit.when(() -> Bukkit.getWorld(world)).thenReturn(state.originWorld);
            f.run.dungeon.setExit(state.location);
            var stored = new YamlConfiguration();
            stored.load(config.toFile());
            assertEquals(world.toString(), stored.getString("locations.exit.world_uuid"));
            var replacement = f.run.paper.dungeon(f.run.dungeon.getFolder().toPath(), stored);
            assertSame(state.originWorld, replacement.getExitLoc().getWorld());
        }
    }

    @Test
    void observationUsesRecoveryWithoutParticipantEntryCommandsLivesOrMembership()
            throws Exception {
        try (var f = new Fixture(directory)) {
            var player = new PlayerState();
            when(player.player.hasPermission(anyString())).thenReturn(true);
            when(player.player.teleport(any(Location.class)))
                    .thenAnswer(
                            call -> {
                                player.location = call.getArgument(0);
                                return true;
                            });
            f.run.paper
                    .bukkit
                    .when(() -> Bukkit.getWorld(player.originWorld.getUID()))
                    .thenReturn(player.originWorld);
            var session = f.run.session(player);
            f.run.run.completeInitialization(false);
            f.ops.observe(player.player, f.run.run);
            assertTrue(session.isObserving());
            assertNull(session.getInstance());
            assertTrue(f.run.run.getPlayers().isEmpty());
            assertTrue(f.run.run.getLivingPlayers().isEmpty());
            assertTrue(f.run.run.getPlayerLives().isEmpty());
            assertEquals(GameMode.SPECTATOR, player.player.getGameMode());
            assertTrue(session.stopObservation());
            assertFalse(session.isObserving());
            assertEquals(GameMode.SURVIVAL, player.player.getGameMode());
            assertSame(player.originWorld, player.location.getWorld());
        }
    }

    @Test
    void playerCorrectionsCommitOnlyAfterSuccessfulStorageAndRespectProtectedRecords()
            throws Exception {
        try (var f = new Fixture(directory)) {
            UUID id = UUID.randomUUID();
            var store = f.run.dungeon.playerDataStore();
            store.correct(id, record -> record.set("Finished", true));
            assertTrue(store.isCompleted(id));
            Path target = f.run.dungeon.getFolder().toPath().resolve("players/" + id + ".yml");
            f.run.paper.state.failWrites = true;
            assertThrows(
                    RuntimeException.class,
                    () -> store.correct(id, record -> record.set("Finished", false)));
            assertTrue(store.isCompleted(id));
        }
    }

    @Test
    void revokedPermissionsAndRestartNeverReplayPendingWork() throws Exception {
        try (var f = new Fixture(directory)) {
            var instance = mock(DungeonInstance.class);
            when(instance.getDungeon()).thenReturn(f.run.dungeon);
            var participant = f.run.session(new PlayerState());
            when(instance.getPlayers()).thenReturn(List.of(participant));
            f.run.paper.runtime.activeInstances().registerActiveInstance(instance);
            f.ops.start();
            var applied = new AtomicBoolean();
            var operation =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "crypt",
                            "Review",
                            () -> {
                                applied.set(true);
                                return CompletableFuture.completedFuture("done");
                            });
            var reconstructed = new StaffOperations(f.run.paper.runtime);
            assertEquals(
                    StaffOperations.State.CANCELLED,
                    reconstructed.operation(operation.shortId()).state);
            assertTrue(reconstructed.maintenance("crypt"));
            when(f.actor.hasPermission("dungeons.staff.reload")).thenReturn(false);
            when(f.actor.hasPermission("dungeons.staff.reload.crypt")).thenReturn(false);
            f.run.scheduler.jobs.getLast().run();
            assertEquals(StaffOperations.State.FAILED, operation.state);
            assertFalse(applied.get());
        }
    }

    @Test
    void backupsExcludeProgressionAndRestorePreservesCurrentProgressionAndLedger()
            throws Exception {
        try (var f = new Fixture(directory)) {
            f.run.dungeon.setSaveConfig("team.max_size", 3);
            Path source = f.run.dungeon.getFolder().toPath();
            Files.writeString(source.resolve("content.txt"), "original content");
            UUID player = UUID.randomUUID();
            f.run.dungeon.playerDataStore().correct(player, record -> record.set("Finished", true));
            Files.writeString(source.resolve("access_keys.yml"), "current: first\n");
            var storage = new StaffContentStorage(f.run.paper.runtime);
            var backup = storage.backup(f.run.dungeon);
            ConfigurationFile.flushPendingSaves();
            String id = backup.join();
            Path copy = directory.resolve("backups/crypt/" + id);
            assertFalse(Files.exists(copy.resolve("players")));
            assertFalse(Files.exists(copy.resolve("access_keys.yml")));
            Files.writeString(source.resolve("content.txt"), "changed");
            f.run.dungeon
                    .playerDataStore()
                    .correct(player, record -> record.set("Finished", false));
            Files.writeString(source.resolve("access_keys.yml"), "current: updated\n");
            when(f.run.paper.runtime.dungeonCatalog().loadDungeon(any(File.class)))
                    .thenReturn(f.run.dungeon);
            for (int count = 0; count < 9; count++) {
                var extra = storage.backup(f.run.dungeon);
                ConfigurationFile.flushPendingSaves();
                extra.join();
            }
            assertEquals(10, storage.backups("crypt").size());
            var restored = storage.restore(f.run.dungeon, id);
            for (int pass = 0; pass < 5 && !restored.isDone(); pass++) {
                ConfigurationFile.flushPendingSaves();
                for (var job : List.copyOf(f.run.scheduler.immediate)) job.run();
            }
            assertTrue(restored.isDone());
            restored.join();
            assertEquals("original content", Files.readString(source.resolve("content.txt")));
            assertFalse(Files.exists(source.resolve("access_keys.yml")));
            assertFalse(Files.exists(source.resolve("players/" + player + ".yml")));
            assertFalse(f.run.dungeon.playerDataStore().isCompleted(player));
        }
    }

    private static CommandSender actor(String name) {
        var actor = mock(CommandSender.class);
        when(actor.getName()).thenReturn(name);
        when(actor.hasPermission(anyString())).thenReturn(true);
        return actor;
    }

    private static final class Fixture implements AutoCloseable {
        final RunFixture run;
        final StaffOperations ops;
        final CommandSender actor = actor("Staff");

        Fixture(Path folder) throws Exception {
            run = new RunFixture(folder, new YamlConfiguration());
            var runtime = run.paper.runtime;
            runtime.setDungeonFiles(folder.toFile());
            var queue =
                    new DungeonQueueRegistry(runtime.playerSessions(), runtime.activeInstances());
            runtime.setQueueRegistry(queue);
            var teams = new DungeonTeamService(run.paper.plugin, runtime.playerSessions(), queue);
            runtime.setTeamService(teams);
            var coordinator =
                    new DungeonQueueCoordinator(
                            run.paper.plugin,
                            runtime.playerSessions(),
                            runtime.activeInstances(),
                            queue,
                            teams);
            queue.setQueueCoordinator(coordinator);
            runtime.setDungeonQueueCoordinator(coordinator);
            var catalog = mock(DungeonRepository.class);
            when(catalog.get("crypt")).thenReturn(run.dungeon);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(run.dungeon));
            runtime.setDungeonCatalog(catalog);
            ops = new StaffOperations(runtime);
            runtime.setStaffOperations(ops);
        }

        @Override
        public void close() {
            ops.close();
            ConfigurationFile.flushPendingSaves();
            run.close();
        }
    }
}
