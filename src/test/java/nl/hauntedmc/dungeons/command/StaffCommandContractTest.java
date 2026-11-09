package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.function.reward.LootTableRewardsFunction;
import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.gui.staff.StaffMenus;
import nl.hauntedmc.dungeons.model.instance.*;
import nl.hauntedmc.dungeons.runtime.player.StaffPlayerRecords;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

class StaffCommandContractTest {
    @TempDir Path directory;

    private static StaffCommandHandler handler(StaffFixture f) {
        return new StaffCommandHandler(f.runtime, f.lifecycle, ignored -> mock(StaffMenus.class));
    }

    private static void execute(StaffCommandHandler h, StaffFixture f, String... args) {
        h.executeChecked(f.actor, args);
    }

    private static void confirm(StaffCommandHandler h, StaffFixture f, String... args) {
        execute(h, f, args);
        var confirmed = new ArrayList<>(List.of(args));
        confirmed.add("--confirm");
        confirmed.add(f.token());
        execute(h, f, confirmed.toArray(String[]::new));
    }

    @Test
    void inspectionHelpAndCompletionNeverExposeTargetsWithoutPermissions() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var player = f.player("Player");
            f.runtime.activeInstances().registerActiveInstance(f.run.run);
            for (String[] args :
                    List.of(
                            new String[] {},
                            new String[] {"help"},
                            new String[] {"overview"},
                            new String[] {"dungeons"},
                            new String[] {"dungeon", "crypt", "validate"},
                            new String[] {"instances"},
                            new String[] {"instance", f.run.run.getInstanceId().toString()},
                            new String[] {"player", player.player.getUniqueId().toString()},
                            new String[] {"queues"},
                            new String[] {"operations"},
                            new String[] {"problems"},
                            new String[] {"archives"},
                            new String[] {"backups", "crypt"},
                            new String[] {"loot"},
                            new String[] {"keys", "crypt"}))
                assertDoesNotThrow(() -> execute(h, f, args), Arrays.toString(args));
            assertFalse(f.messages.isEmpty());
            f.grants.clear();
            f.messages.clear();
            assertThrows(IllegalArgumentException.class, () -> execute(h, f, "overview"));
            assertTrue(
                    h.handleTabComplete(f.actor, new String[] {"admin", "dungeon", ""}).isEmpty());
            assertTrue(h.handleTabComplete(f.actor, new String[] {"admin", ""}).isEmpty());
            f.grants.add("dungeons.staff.inspect.crypt");
            assertEquals(
                    List.of("crypt"),
                    h.handleTabComplete(f.actor, new String[] {"admin", "dungeon", ""}));
            assertTrue(h.handleTabComplete(f.actor, new String[] {"admin", "purge", ""}).isEmpty());
        }
    }

    @Test
    void everyPlayerInspectionRouteOpensTheExpectedMenuAndInvalidInputIsHandled() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var p = f.player("Staff");
            h.executeChecked(p.player, new String[] {"admin"});
            h.executeChecked(p.player, new String[] {"dungeons"});
            h.executeChecked(p.player, new String[] {"dungeon", "crypt"});
            h.executeChecked(p.player, new String[] {"instances"});
            h.executeChecked(p.player, new String[] {"player", p.player.getUniqueId().toString()});
            h.executeChecked(p.player, new String[] {"queues"});
            h.executeChecked(p.player, new String[] {"operations"});
            h.executeChecked(p.player, new String[] {"loot"});
            verify(h.menus(), times(8))
                    .open(eq(p.player), anyString(), anyString(), anyString(), anyInt());
            assertTrue(h.handleCommand(f.actor, new String[] {"no-such-command"}));
            assertThrows(
                    IllegalArgumentException.class, () -> execute(h, f, "overview", "--confirm"));
            assertThrows(IllegalArgumentException.class, () -> h.playerId("Unknown"));
            assertEquals(p.player.getUniqueId(), h.playerId("Staff"));
            assertEquals(60000, StaffCommandHandler.duration("1M"));
            for (String invalid : List.of("0s", "-1h", "2years", "999999999w"))
                assertThrows(
                        IllegalArgumentException.class,
                        () -> StaffCommandHandler.duration(invalid));
        }
    }

    @Test
    void correctionsRequireValidInputsConfirmationPermissionAndDurableWrites() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            UUID id = UUID.randomUUID();
            execute(h, f, "completion", id.toString(), "crypt", "true", "Verified");
            assertFalse(f.run.dungeon.playerDataStore().isCompleted(id));
            var token = f.token();
            execute(
                    h,
                    f,
                    "completion",
                    id.toString(),
                    "crypt",
                    "true",
                    "Verified",
                    "--confirm",
                    token);
            assertTrue(f.run.dungeon.playerDataStore().isCompleted(id));
            confirm(h, f, "cooldown", id.toString(), "crypt", "1h", "Repair");
            assertNotNull(f.run.dungeon.playerDataStore().getAccessCooldown(id));
            confirm(h, f, "cooldown", id.toString(), "crypt", "clear", "Repair");
            assertNull(f.run.dungeon.playerDataStore().getAccessCooldown(id));
            confirm(h, f, "loot-cooldown", id.toString(), "crypt", "clear", "Repair");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "completion", id.toString(), "crypt", "maybe", "Repair"));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            execute(
                                    h,
                                    f,
                                    "cooldown",
                                    id.toString(),
                                    "crypt",
                                    "bad-duration",
                                    "Repair"));
            f.denials.add("dungeons.staff.progression");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "completion", id.toString(), "crypt", "false", "Repair"));
            assertTrue(f.run.dungeon.playerDataStore().isCompleted(id));
        }
    }

    @Test
    void contentOperationsSerializeAndBlockConcurrentProgressionWrites() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            UUID id = UUID.randomUUID();
            execute(h, f, "RELOAD", "config", "Refresh");
            verify(f.lifecycle).reloadConfigs();
            execute(h, f, "reload", "all", "Refresh");
            when(f.repository.loadDungeon(f.run.dungeon.getFolder())).thenReturn(f.run.dungeon);
            execute(h, f, "reload", "crypt", "Refresh");
            execute(h, f, "backup", "crypt", "Safety copy");
            f.flush();
            assertFalse(
                    new nl.hauntedmc.dungeons.runtime.staff.StaffContentStorage(f.runtime)
                            .backups("crypt")
                            .isEmpty());
            var completion = new CompletableFuture<String>();
            var op =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "crypt",
                            "Waiting for write",
                            () -> completion);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "completion", id.toString(), "crypt", "true", "Repair"));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "maintenance", "crypt", "off", "Resume"));
            assertDoesNotThrow(() -> execute(h, f, "operation", op.shortId()));
            completion.complete("done");
            f.flush();
            assertEquals(
                    nl.hauntedmc.dungeons.runtime.staff.StaffOperations.State.SUCCEEDED, op.state);
        }
    }

    @Test
    void scopedQueueCancellationUsesTheQueueDungeonAndRefundsTheWholeQueue() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var p = f.player("Player");
            var session = f.runtime.playerSessions().get(p.player);
            var queue = new DungeonQueueEntry(session, f.run.dungeon, "");
            f.runtime.queueRegistry().enqueue(queue);
            f.grants.clear();
            f.grants.add("dungeons.staff.queue.crypt");
            execute(h, f, "player", p.player.getUniqueId().toString(), "queue-clear", "Requested");
            assertNull(f.runtime.queueRegistry().getQueue(session));
            assertFalse(session.isAwaitingDungeon());
        }
    }

    @Test
    void scopedPlayerInspectionCannotReadOtherDungeonOrGlobalRecoveryRecords() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var p = f.player("Player");
            var session = f.runtime.playerSessions().get(p.player);
            f.dungeon("private");
            f.runtime.queueRegistry().enqueue(new DungeonQueueEntry(session, f.run.dungeon, ""));
            f.grants.clear();
            f.grants.add("dungeons.staff.inspect.crypt");
            execute(h, f, "player", p.player.getUniqueId().toString());
            assertTrue(f.messages.stream().anyMatch(line -> line.contains("crypt: ")));
            assertFalse(
                    f.messages.stream()
                            .anyMatch(
                                    line ->
                                            line.contains("private: ")
                                                    || line.contains("claimable stacks=")));
            f.ops.cancelStart(session);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> h.playerLines(f.actor, p.player.getUniqueId()));
        }
    }

    @Test
    void scopedHelpAndCompletionRevealOnlyAuthorizedInstanceAndOperationTargets() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var other = f.dungeon("private");
            f.runtime.activeInstances().registerActiveInstance(f.run.run);
            var privateRun = mock(DungeonInstance.class);
            when(privateRun.getDungeon()).thenReturn(other);
            when(privateRun.getInstanceId()).thenReturn(UUID.randomUUID());
            f.runtime.activeInstances().registerActiveInstance(privateRun);
            var visible =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "crypt",
                            "Review",
                            () -> CompletableFuture.completedFuture("done"));
            var hidden =
                    f.ops.drain(
                            f.actor,
                            "reload",
                            "reload",
                            "private",
                            "Review",
                            () -> CompletableFuture.completedFuture("done"));
            f.grants.clear();
            f.grants.add("dungeons.staff.inspect.crypt");
            assertTrue(
                    h.handleTabComplete(f.actor, new String[] {"admin", ""}).contains("dungeon"));
            assertEquals(
                    List.of(f.run.run.getInstanceId().toString().substring(0, 8)),
                    h.handleTabComplete(f.actor, new String[] {"admin", "instance", ""}));
            assertEquals(
                    List.of(visible.shortId()),
                    h.handleTabComplete(f.actor, new String[] {"admin", "operation", ""}));
            f.messages.clear();
            execute(h, f, "help");
            assertTrue(f.messages.stream().anyMatch(line -> line.contains("map info <map>")));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "operation", hidden.shortId()));
        }
    }

    @Test
    void openingTheEditorMakesABackupBeforeInvokingTheCurrentDefinition() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var player = f.player("Builder");
            UUID playerId = player.player.getUniqueId();
            var dungeon = spy(f.run.dungeon);
            f.catalog.put("crypt", dungeon);
            doAnswer(
                            call -> {
                                assertFalse(
                                        new nl.hauntedmc.dungeons.runtime.staff.StaffContentStorage(
                                                        f.runtime)
                                                .backups("crypt")
                                                .isEmpty());
                                return CompletableFuture.completedFuture(true);
                            })
                    .when(dungeon)
                    .editAsync(playerId);
            h.executeChecked(player.player, new String[] {"edit", "crypt", "Open editor"});
            f.flush();
            verify(dungeon).editAsync(playerId);
            assertEquals(
                    nl.hauntedmc.dungeons.runtime.staff.StaffOperations.State.SUCCEEDED,
                    f.ops.operations().getLast().state);
            assertTrue(f.ops.maintenance("crypt"));
        }
    }

    @Test
    void restrictionsCanExpireOrClearWithoutAffectingOtherDungeons() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var p = f.player("Player");
            UUID id = p.player.getUniqueId();
            execute(h, f, "restrict", id.toString(), "crypt", "1h", "Review");
            assertTrue(f.ops.entryBlock(id, "crypt").contains("Review"));
            assertNull(f.ops.entryBlock(id, "other"));
            execute(h, f, "restrict", id.toString(), "crypt", "clear", "Done");
            assertNull(f.ops.entryBlock(id, "crypt"));
            execute(h, f, "restrict", id.toString(), "all", "forever", "Review");
            assertNotNull(f.ops.entryBlock(id, "other"));
            execute(h, f, "restrict", id.toString(), "all", "clear", "Done");
        }
    }

    @Test
    void liveSupportRespectsRecoveryAndValidatesBeforeChangingTimersOrLives() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var p = f.player("Player");
            var session = f.runtime.playerSessions().get(p.player);
            f.runtime.activeInstances().registerActiveInstance(f.run.run);
            f.run.run.completeInitialization(false);
            f.run.run.getConfig().set("runs.time_limit_minutes", 10);
            f.run.run.addPlayer(session);
            f.run.run.startGame();
            String instance = f.run.run.getInstanceId().toString();
            String id = p.player.getUniqueId().toString();
            int before = f.run.run.getTimeLeft();
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "instance", instance, "extend", "60", "a".repeat(257)));
            assertEquals(before, f.run.run.getTimeLeft());
            execute(h, f, "instance", instance, "extend", "60", "Downtime");
            assertEquals(before + 60, f.run.run.getTimeLeft());
            execute(h, f, "player", id, "lives", "3", "Repair");
            assertEquals(3, f.run.run.getPlayerLives().get(p.player.getUniqueId()));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "player", id, "revive", "Repair"));
            f.run.run.getLivingPlayers().remove(session);
            execute(h, f, "player", id, "revive", "Repair");
            assertTrue(f.run.run.getLivingPlayers().contains(session));
            execute(h, f, "player", id, "rescue", "Stuck");
            assertNull(session.getInstance());
            execute(h, f, "player", id, "recover", "Retry");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "instance", instance, "evacuate", "a".repeat(257)));
        }
    }

    @Test
    void observationAndEvacuationUseTheSamePolicy() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var staff = f.player("Observer");
            var p = f.player("Player");
            var session = f.runtime.playerSessions().get(p.player);
            f.runtime.activeInstances().registerActiveInstance(f.run.run);
            f.run.run.completeInitialization(false);
            f.run.run.addPlayer(session);
            h.executeChecked(
                    staff.player,
                    new String[] {"instance", f.run.run.getInstanceId().toString(), "observe"});
            assertTrue(f.runtime.playerSessions().get(staff.player).isObserving());
            h.executeChecked(staff.player, new String[] {"stop-observing"});
            assertFalse(f.runtime.playerSessions().get(staff.player).isObserving());
            confirm(
                    h,
                    f,
                    "instance",
                    f.run.run.getInstanceId().toString(),
                    "evacuate",
                    "Emergency");
            assertTrue(f.run.run.getPlayers().isEmpty());
        }
    }

    @Test
    void evacuationConfirmationIgnoresTimerTicksButRejectsChangedParticipants() throws Exception {
        try (var f = new StaffFixture(directory.resolve("timer"))) {
            var h = handler(f);
            var player = f.player("Participant");
            f.runtime.activeInstances().registerActiveInstance(f.run.run);
            f.run.run.completeInitialization(false);
            f.run.run.addPlayer(f.runtime.playerSessions().get(player.player));
            String instance = f.run.run.getInstanceId().toString();
            execute(h, f, "instance", instance, "evacuate", "Emergency");
            String token = f.token();
            f.run.run.elapsed(5);
            execute(h, f, "instance", instance, "evacuate", "Emergency", "--confirm", token);
            assertTrue(f.run.run.getPlayers().isEmpty());
        }
        try (var f = new StaffFixture(directory.resolve("roster"))) {
            var h = handler(f);
            var first = f.player("First");
            var second = f.player("Second");
            var original = f.runtime.playerSessions().get(first.player);
            var replacement = f.runtime.playerSessions().get(second.player);
            f.runtime.activeInstances().registerActiveInstance(f.run.run);
            f.run.run.completeInitialization(false);
            f.run.run.addPlayer(original);
            String instance = f.run.run.getInstanceId().toString();
            execute(h, f, "instance", instance, "evacuate", "Emergency");
            String token = f.token();
            f.run.run.getPlayers().clear();
            f.run.run.getPlayers().add(replacement);
            execute(h, f, "instance", instance, "evacuate", "Emergency", "--confirm", token);
            assertEquals(List.of(replacement), List.copyOf(f.run.run.getPlayers()));
            assertNotEquals(token, f.token());
            f.run.run.getPlayers().clear();
            f.run.run.getPlayers().add(original);
        }
    }

    @Test
    void destructiveCommandsValidateReasonsBeforeConfirmingOrRemovingAnything() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var table = new LootTable("unused");
            when(f.runtime.lootTableRepository().get("unused")).thenReturn(table);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "loot-remove", "unused", "a".repeat(257)));
            verify(f.runtime.lootTableRepository(), never()).remove(anyString());
            when(f.runtime.lootTableRepository().remove("unused")).thenReturn(true);
            confirm(h, f, "loot-remove", "unused", "Retired");
            verify(f.runtime.lootTableRepository()).remove("unused");
            var reward = new LootTableRewardsFunction(Map.of("loottableName", "unused"));
            reward.initializeFields();
            f.run.dungeon.getFunctions().put(new Location(null, 1, 2, 3), reward);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "loot-remove", "unused", "Retired"));
        }
    }

    @Test
    void compensationAndKeyIssuancePersistOnlineAndOfflineWithoutReplayingAConfirmation()
            throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            var online = f.player("Recipient");
            try (var items = new StaffGuiBoundary(f, online);
                    var materials = mockStatic(Material.class, CALLS_REAL_METHODS)) {
                var material = mock(Material.class);
                when(material.isItem()).thenReturn(true);
                when(material.getMaxStackSize()).thenReturn(64);
                materials.when(() -> Material.matchMaterial("TOKEN")).thenReturn(material);
                UUID offline = UUID.randomUUID();
                confirm(h, f, "compensate", offline.toString(), "TOKEN", "3", "Verified loss");
                assertTrue(
                        StaffPlayerRecords.inspect(f.runtime, offline)
                                .contains("claimable stacks=1"));
                String token = f.token();
                execute(
                        h,
                        f,
                        "compensate",
                        offline.toString(),
                        "TOKEN",
                        "3",
                        "Verified loss",
                        "--confirm",
                        token);
                assertTrue(
                        StaffPlayerRecords.inspect(f.runtime, offline)
                                .contains("claimable stacks=1"));
                confirm(
                        h,
                        f,
                        "compensate",
                        online.player.getUniqueId().toString(),
                        "TOKEN",
                        "2",
                        "Verified loss");
                assertTrue(
                        StaffPlayerRecords.inspect(f.runtime, online.player.getUniqueId())
                                .contains("claimable stacks=1"));
                var definition = spy(f.run.dungeon);
                var key = mock(nl.hauntedmc.dungeons.model.dungeon.AccessKeyDefinition.class);
                var issued = PlayerState.stack(1);
                when(key.getItem()).thenReturn(issued);
                doReturn(key).when(definition).getAccessKey(1);
                doReturn(List.of(key)).when(definition).getAccessKeys();
                doReturn(issued).when(definition).issueAccessKey(key);
                f.catalog.put("crypt", definition);
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                execute(
                                        h,
                                        f,
                                        "keys",
                                        "crypt",
                                        "give",
                                        online.player.getUniqueId().toString(),
                                        "1",
                                        "a".repeat(257)));
                verify(definition, never()).issueAccessKey(any());
                confirm(
                        h,
                        f,
                        "keys",
                        "crypt",
                        "give",
                        online.player.getUniqueId().toString(),
                        "1",
                        "Replacement key");
                verify(definition).issueAccessKey(key);
                assertTrue(
                        StaffPlayerRecords.inspect(f.runtime, online.player.getUniqueId())
                                .contains("claimable stacks=2"));
            }
        }
    }

    @Test
    void archiveRestorePurgeAndMalformattedCommandsCannotSkipConfirmation() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var h = handler(f);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "archive", "all", "Invalid"));
            execute(h, f, "archive", "crypt", "Retired");
            assertTrue(f.run.dungeon.getFolder().exists());
            String token = f.token();
            f.denials.add("dungeons.staff.delete");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> execute(h, f, "archive", "crypt", "Retired", "--confirm", token));
            f.denials.clear();
            execute(h, f, "archive", "crypt", "Retired", "--confirm", token);
            f.flush();
            var storage = new nl.hauntedmc.dungeons.runtime.staff.StaffContentStorage(f.runtime);
            String archive = storage.archives().getFirst();
            assertFalse(f.run.dungeon.getFolder().exists());
            when(f.repository.loadDungeon(f.run.dungeon.getFolder()))
                    .thenAnswer(
                            c -> {
                                f.catalog.put("crypt", f.run.dungeon);
                                return f.run.dungeon;
                            });
            confirm(h, f, "unarchive", archive, "Reopen");
            f.flush();
            assertTrue(f.run.dungeon.getFolder().exists());
            confirm(h, f, "archive", "crypt", "Retire again");
            f.flush();
            String newer = storage.archives().getFirst();
            confirm(h, f, "purge", newer, "Permanent removal");
            f.flush();
            assertTrue(storage.archives().isEmpty());
        }
    }
}
