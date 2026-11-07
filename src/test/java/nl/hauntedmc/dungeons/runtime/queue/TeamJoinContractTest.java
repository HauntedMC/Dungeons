package nl.hauntedmc.dungeons.runtime.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.runtime.team.*;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class TeamJoinContractTest {
    @TempDir Path directory;

    static DungeonTeamService teams(RunFixture f) {
        f.paper.config.set("team.unstarted_expiry_seconds", 0);
        var queues =
                new DungeonQueueRegistry(
                        f.paper.runtime.playerSessions(), f.paper.runtime.activeInstances());
        f.paper.runtime.setQueueRegistry(queues);
        var teams =
                new DungeonTeamService(f.paper.plugin, f.paper.runtime.playerSessions(), queues);
        f.paper.runtime.setTeamService(teams);
        return teams;
    }

    static PlayerState player(RunFixture f) {
        var state = new PlayerState();
        f.session(state);
        f.paper
                .bukkit
                .when(() -> Bukkit.getPlayer(state.player.getUniqueId()))
                .thenReturn(state.player);
        return state;
    }

    static DungeonTeam team(
            DungeonTeamService service, PlayerState leader, PlayerState... members) {
        assertTrue(service.handleCreateTeam(leader.player));
        for (var member : members) {
            assertTrue(service.handleInvite(leader.player, member.player));
            assertTrue(service.handleAcceptInvite(member.player));
        }
        return service.getTeam(leader.player.getUniqueId());
    }

    @Test
    void
            teamJoinWaitsForItsLeaderAdmitsAvailableMembersAndRefundsOfflineMembersWithoutStealingBusyOnes()
                    throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var service = teams(f);
            var leader = player(f);
            var joining = player(f);
            var offline = player(f);
            var busy = player(f);
            var team = team(service, leader, joining, offline, busy);
            var sessions = f.paper.runtime.playerSessions();
            var leaderSession = sessions.get(leader.player);
            var queue =
                    new DungeonQueueEntry(
                            leaderSession, f.dungeon, "", team.getMemberIds(), team.getTeamId());
            service.markQueued(team, f.dungeon.getWorldName(), "");
            for (var id : team.getMemberIds()) sessions.get(id).setAwaitingDungeon(true);
            assertTrue(
                    sessions.get(offline.player)
                            .reserveAccessKey(f.dungeon.getWorldName(), PlayerState.stack(1)));
            offline.online = false;
            queue.scheduleTeamJoin(f.paper.plugin, service, sessions);
            var join = f.scheduler.jobs.getFirst();
            join.run();
            assertFalse(join.cancelled);
            assertNull(sessions.get(joining.player).getInstance());
            f.run.addPlayer(leaderSession);
            var otherRun = new RunFixture.Run(f.dungeon, f.world);
            otherRun.addPlayer(sessions.get(busy.player));
            join.run();
            assertTrue(join.cancelled);
            assertSame(f.run, sessions.get(joining.player).getInstance());
            assertSame(otherRun, sessions.get(busy.player).getInstance());
            assertNull(sessions.get(offline.player).getInstance());
            assertTrue(team.isStarted());
            assertTrue(
                    team.getMemberIds().stream()
                            .noneMatch(id -> sessions.get(id).isAwaitingDungeon()));
            assertFalse(sessions.get(offline.player).hasReservedAccessKey());
            verify(offline.inventory, never()).addItem(any(ItemStack.class));
            offline.online = true;
            sessions.get(offline.player).flushPendingAccessKeyRefund();
            sessions.get(offline.player).flushPendingAccessKeyRefund();
            assertEquals(1, offline.contents[0].getAmount());
            assertTrue(sessions.get(offline.player).getRewardItems().isEmpty());
        }
    }

    @Test
    void missingTeamsAndLeaderPreparationTimeoutsReleaseWaitingFlagsAndRefundEveryReservationOnce()
            throws Exception {
        for (boolean teamRemoved : List.of(false, true)) {
            try (var f =
                    new RunFixture(
                            directory.resolve(Boolean.toString(teamRemoved)),
                            new YamlConfiguration())) {
                var service = teams(f);
                var leader = player(f);
                var member = player(f);
                var team = team(service, leader, member);
                var sessions = f.paper.runtime.playerSessions();
                var source = sessions.get(leader.player);
                var target = sessions.get(member.player);
                for (var session : List.of(source, target)) {
                    session.setAwaitingDungeon(true);
                    assertTrue(
                            session.reserveAccessKey(
                                    f.dungeon.getWorldName(), PlayerState.stack(1)));
                }
                service.markQueued(team, f.dungeon.getWorldName(), "");
                var queue =
                        new DungeonQueueEntry(
                                source, f.dungeon, "", team.getMemberIds(), team.getTeamId());
                queue.scheduleTeamJoin(f.paper.plugin, service, sessions);
                var retry = f.scheduler.jobs.getFirst();
                if (teamRemoved) service.shutdown();
                else {
                    for (int tick = 0; tick < 199; tick++) retry.run();
                    assertFalse(retry.cancelled);
                    assertTrue(source.hasReservedAccessKey());
                }
                retry.run();
                retry.run();
                assertTrue(retry.cancelled);
                for (var session : List.of(source, target)) {
                    assertFalse(session.isAwaitingDungeon());
                    assertFalse(session.hasReservedAccessKey());
                    assertNull(session.getInstance());
                }
                assertEquals(1, leader.inventory.getContents()[0].getAmount());
                assertEquals(1, member.inventory.getContents()[0].getAmount());
                verify(leader.player, times(2)).saveData();
                verify(member.player, times(2)).saveData();
                if (!teamRemoved) assertFalse(team.isQueued());
            }
        }
    }
}
