package nl.hauntedmc.dungeons.runtime.team;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class TeamLifecycleContractTest {
    @Test
    void invitationsBuildOneTeamStartingLocksItAndTeamTargetingNeverIncludesAnotherRun() {
        try (var paper = new PaperBoundary()) {
            paper.config.set("team.unstarted_expiry_seconds", 0);
            var sessions = mock(PlayerSessionRegistry.class);
            var service = new DungeonTeamService(paper.plugin, sessions, mock(DungeonQueueRegistry.class));
            var leader = player(sessions);
            var member = player(sessions);
            service.handleCreateTeam(leader.getPlayer());
            var team = service.getTeam(leader.getPlayer().getUniqueId());
            service.handleInvite(leader.getPlayer(), member.getPlayer());
            service.handleAcceptInvite(member.getPlayer());
            assertSame(team, service.getTeam(member.getPlayer().getUniqueId()));
            assertEquals(2, team.size());
            assertFalse(team.removeMember(team.getLeaderId()));
            team.getMemberIds().clear();
            assertEquals(2, team.size());
            var run = mock(DungeonInstance.class);
            var world = mock(World.class);
            when(world.getName()).thenReturn("crypt_0");
            when(run.getInstanceWorld()).thenReturn(world);
            when(leader.getInstance()).thenReturn(run);
            when(member.getInstance()).thenReturn(mock(DungeonInstance.class));
            service.markStarted(team.getTeamId(), run);
            assertTrue(team.isLocked());
            assertFalse(team.isQueued());
            var outsider = player(sessions);
            service.handleInvite(leader.getPlayer(), outsider.getPlayer());
            service.handleAcceptInvite(outsider.getPlayer());
            assertNull(service.getTeam(outsider.getPlayer().getUniqueId()));
            assertEquals(2, team.size());
            assertEquals(List.of(leader), service.getActiveMembers(leader, run));
            when(member.getInstance()).thenReturn(run);
            assertEquals(List.of(leader, member), service.getActiveMembers(leader, run));
            service.shutdown();
            assertNull(service.getTeam(leader.getPlayer().getUniqueId()));
            assertNull(service.getTeam(member.getPlayer().getUniqueId()));
        }
    }

    @Test
    void teamTargetResolutionReturnsNoPlayersWhenEveryMemberIsOutsideTheRequestedRun() {
        try (var paper = new PaperBoundary()) {
            paper.config.set("team.unstarted_expiry_seconds", 0);
            var sessions = mock(PlayerSessionRegistry.class);
            var service = new DungeonTeamService(paper.plugin, sessions, mock(DungeonQueueRegistry.class));
            var leader = player(sessions);
            service.handleCreateTeam(leader.getPlayer());
            when(leader.getInstance()).thenReturn(mock(DungeonInstance.class));
            assertTrue(service.getActiveMembers(leader, mock(DungeonInstance.class)).isEmpty());
        }
    }

    DungeonPlayerSession player(PlayerSessionRegistry sessions) {
        var player = mock(Player.class);
        var id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        var session = mock(DungeonPlayerSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(sessions.get(player)).thenReturn(session);
        when(sessions.get(id)).thenReturn(session);
        return session;
    }
}
