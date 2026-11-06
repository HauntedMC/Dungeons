package nl.hauntedmc.dungeons.runtime.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.event.DungeonDisposeEvent;
import nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class TeamTransitionsContractTest {
    @TempDir Path directory;

    static DungeonTeamService service(RunFixture f) {
        f.paper.config.set("team.unstarted_expiry_seconds", 0);
        var queues =
                new DungeonQueueRegistry(
                        f.paper.runtime.playerSessions(), f.paper.runtime.activeInstances());
        f.paper.runtime.setQueueRegistry(queues);
        var service =
                new DungeonTeamService(f.paper.plugin, f.paper.runtime.playerSessions(), queues);
        f.paper.runtime.setTeamService(service);
        return service;
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

    @Test
    void revokedDeniedAndDissolvedInvitesCannotBeAcceptedAndKicksKeepMembershipIndexesConsistent()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var service = service(f);
            var leader = player(f);
            var member = player(f);
            var outsider = player(f);
            service.handleCreateTeam(leader.player);
            var team = service.getTeam(leader.player.getUniqueId());
            service.handleInvite(leader.player, member.player);
            service.handleRevokeInvite(leader.player, member.player);
            service.handleAcceptInvite(member.player);
            assertNull(service.getTeam(member.player.getUniqueId()));
            service.handleInvite(leader.player, member.player);
            service.handleDenyInvite(member.player);
            service.handleAcceptInvite(member.player);
            assertNull(service.getTeam(member.player.getUniqueId()));
            service.handleInvite(leader.player, member.player);
            service.handleAcceptInvite(member.player);
            assertSame(team, service.getTeam(member.player.getUniqueId()));
            service.handleKickMember(member.player, leader.player);
            assertEquals(2, team.size());
            service.handleKickMember(leader.player, member.player);
            assertNull(service.getTeam(member.player.getUniqueId()));
            assertEquals(1, team.size());
            service.handleInvite(leader.player, outsider.player);
            service.handleDeleteTeam(leader.player);
            assertNull(service.getTeamById(team.getTeamId()));
            assertNull(service.getTeam(leader.player.getUniqueId()));
            service.handleAcceptInvite(outsider.player);
            assertNull(service.getTeam(outsider.player.getUniqueId()));
        }
    }

    @Test
    void idleExpiryClearsInvitationsAndCannotExpireAReplacementOrStartedTeam() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var service = service(f);
            f.paper.config.set("team.unstarted_expiry_seconds", 1);
            var leader = player(f);
            var member = player(f);
            service.handleCreateTeam(leader.player);
            var original = service.getTeam(leader.player.getUniqueId());
            var expiry = f.scheduler.jobs.getFirst();
            assertEquals(20, expiry.delay);
            service.handleInvite(leader.player, member.player);
            expiry.run();
            assertNull(service.getTeamById(original.getTeamId()));
            assertNull(service.getTeam(leader.player.getUniqueId()));
            service.handleAcceptInvite(member.player);
            assertNull(service.getTeam(member.player.getUniqueId()));
            service.handleCreateTeam(leader.player);
            var replacement = service.getTeam(leader.player.getUniqueId());
            service.markStarted(replacement.getTeamId(), f.run);
            assertTrue(f.scheduler.jobs.getLast().cancelled);
            expiry.run();
            assertSame(replacement, service.getTeam(leader.player.getUniqueId()));
        }
    }

    @Test
    void startedLeaderDisbandRemovesMembershipIndexesAndClosesRemainingPlayersAtTheDeadline()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("team.disband_shutdown_delay_seconds", 2);
        try (var f = new RunFixture(directory, settings)) {
            var service = service(f);
            var leader = player(f);
            var member = player(f);
            service.handleCreateTeam(leader.player);
            service.handleInvite(leader.player, member.player);
            service.handleAcceptInvite(member.player);
            var leaderSession = f.paper.runtime.playerSessions().get(leader.player);
            var memberSession = f.paper.runtime.playerSessions().get(member.player);
            f.run.addPlayer(leaderSession);
            f.run.addPlayer(memberSession);
            var team = service.getTeam(leader.player.getUniqueId());
            service.markStarted(team.getTeamId(), f.run);
            service.handleDeleteTeam(leader.player);
            assertNull(leaderSession.getInstance());
            assertSame(f.run, memberSession.getInstance());
            f.scheduler.immediate.getFirst().run();
            assertNull(service.getTeam(leader.player.getUniqueId()));
            assertNull(service.getTeam(member.player.getUniqueId()));
            assertNull(service.getTeamById(team.getTeamId()));
            var countdown =
                    f.scheduler.jobs.stream().filter(j -> j.period == 20).findFirst().orElseThrow();
            countdown.run();
            assertSame(f.run, memberSession.getInstance());
            countdown.run();
            assertNull(memberSession.getInstance());
            assertTrue(f.run.getPlayers().isEmpty());
            assertTrue(countdown.cancelled);
        }
    }

    @Test
    void startedTeamsSurviveDisconnectsAndCancelledLeavesButRemoveDepartedMembersAndDisposeIndexes()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var service = service(f);
            var leader = player(f);
            var member = player(f);
            service.handleCreateTeam(leader.player);
            service.handleInvite(leader.player, member.player);
            service.handleAcceptInvite(member.player);
            var team = service.getTeam(leader.player.getUniqueId());
            service.markStarted(team.getTeamId(), f.run);
            var quit = mock(PlayerQuitEvent.class);
            when(quit.getPlayer()).thenReturn(member.player);
            service.onPlayerQuit(quit);
            assertSame(team, service.getTeam(member.player.getUniqueId()));
            var leave =
                    new PlayerLeaveDungeonEvent(
                            f.run, f.paper.runtime.playerSessions().get(member.player));
            leave.setCancelled(true);
            service.onPlayerLeaveDungeon(leave);
            assertEquals(2, team.size());
            leave.setCancelled(false);
            service.onPlayerLeaveDungeon(leave);
            assertNull(service.getTeam(member.player.getUniqueId()));
            assertEquals(1, team.size());
            service.onDungeonDispose(new DungeonDisposeEvent(f.run));
            assertNull(service.getTeamById(team.getTeamId()));
            assertNull(service.getTeam(leader.player.getUniqueId()));
            service.onDungeonDispose(new DungeonDisposeEvent(f.run));
            assertTrue(f.scheduler.immediate.isEmpty());
        }
    }

    @Test
    void shutdownCancelsCountdownsAndPreventsPendingDisbandCallbacksFromSchedulingNewWork()
            throws Exception {
        for (boolean countdownStarted : List.of(false, true)) {
            var settings = new YamlConfiguration();
            settings.set("team.disband_shutdown_delay_seconds", 2);
            try (var f =
                    new RunFixture(
                            directory.resolve(Boolean.toString(countdownStarted)), settings)) {
                var service = service(f);
                var leader = player(f);
                var member = player(f);
                service.handleCreateTeam(leader.player);
                service.handleInvite(leader.player, member.player);
                service.handleAcceptInvite(member.player);
                var team = service.getTeam(leader.player.getUniqueId());
                var leaderSession = f.paper.runtime.playerSessions().get(leader.player);
                var memberSession = f.paper.runtime.playerSessions().get(member.player);
                f.run.addPlayer(leaderSession);
                f.run.addPlayer(memberSession);
                service.markStarted(team.getTeamId(), f.run);
                service.handleDeleteTeam(leader.player);
                var disband = f.scheduler.immediate.getLast();
                if (countdownStarted) disband.run();
                int jobs = f.scheduler.jobs.size();
                service.shutdown();
                disband.run();
                assertEquals(jobs, f.scheduler.jobs.size());
                f.scheduler.jobs.stream()
                        .filter(job -> job.period == 20)
                        .forEach(
                                job -> {
                                    assertTrue(job.cancelled);
                                    job.run();
                                });
                assertSame(f.run, memberSession.getInstance());
                assertNull(service.getTeamById(team.getTeamId()));
            }
        }
    }

    @Test
    void explicitTeamLeaveRemovesIdleMembershipAndRoutesStartedMembersThroughDungeonDeparture()
            throws Exception {
        for (boolean started : List.of(false, true)) {
            try (var f =
                    new RunFixture(
                            directory.resolve(Boolean.toString(started)),
                            new YamlConfiguration())) {
                var service = service(f);
                var leader = player(f);
                var member = player(f);
                service.handleCreateTeam(leader.player);
                service.handleInvite(leader.player, member.player);
                service.handleAcceptInvite(member.player);
                var team = service.getTeam(leader.player.getUniqueId());
                var session = f.paper.runtime.playerSessions().get(member.player);
                if (started) {
                    f.run.addPlayer(f.paper.runtime.playerSessions().get(leader.player));
                    f.run.addPlayer(session);
                    service.markStarted(team.getTeamId(), f.run);
                    var manager = Bukkit.getPluginManager();
                    org.mockito.Mockito.doAnswer(
                                    c -> {
                                        if (c.getArgument(0)
                                                instanceof
                                                nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent
                                                        event) service.onPlayerLeaveDungeon(event);
                                        return null;
                                    })
                            .when(manager)
                            .callEvent(org.mockito.Mockito.any(org.bukkit.event.Event.class));
                }
                assertTrue(service.handleLeaveTeam(member.player));
                assertNull(service.getTeam(member.player.getUniqueId()));
                assertEquals(1, team.size());
                assertNull(session.getInstance());
                if (!started) {
                    service.handleLeaveTeam(leader.player);
                    assertNull(service.getTeamById(team.getTeamId()));
                } else assertSame(team, service.getTeam(leader.player.getUniqueId()));
            }
        }
    }
}
