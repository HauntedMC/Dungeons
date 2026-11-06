package nl.hauntedmc.dungeons.runtime.team;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TeamTransitionsContractTest {
    @TempDir Path directory;
    static DungeonTeamService service(RunFixture f) {
        f.paper.config.set("team.unstarted_expiry_seconds",0);
        var queues=new DungeonQueueRegistry(f.paper.runtime.playerSessions(),f.paper.runtime.activeInstances());
        f.paper.runtime.setQueueRegistry(queues);
        var service=new DungeonTeamService(f.paper.plugin,f.paper.runtime.playerSessions(),queues);
        f.paper.runtime.setTeamService(service); return service;
    }
    static PlayerState player(RunFixture f) {
        var state=new PlayerState(); f.session(state);
        f.paper.bukkit.when(() -> Bukkit.getPlayer(state.player.getUniqueId())).thenReturn(state.player);
        return state;
    }
    @Test
    void revokedDeniedAndDissolvedInvitesCannotBeAcceptedAndKicksKeepMembershipIndexesConsistent() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var service=service(f); var leader=player(f); var member=player(f); var outsider=player(f);
            service.handleCreateTeam(leader.player); var team=service.getTeam(leader.player.getUniqueId());
            service.handleInvite(leader.player,member.player); service.handleRevokeInvite(leader.player,member.player);
            service.handleAcceptInvite(member.player); assertNull(service.getTeam(member.player.getUniqueId()));
            service.handleInvite(leader.player,member.player); service.handleDenyInvite(member.player);
            service.handleAcceptInvite(member.player); assertNull(service.getTeam(member.player.getUniqueId()));
            service.handleInvite(leader.player,member.player); service.handleAcceptInvite(member.player);
            assertSame(team,service.getTeam(member.player.getUniqueId()));
            service.handleKickMember(member.player,leader.player); assertEquals(2,team.size());
            service.handleKickMember(leader.player,member.player);
            assertNull(service.getTeam(member.player.getUniqueId())); assertEquals(1,team.size());
            service.handleInvite(leader.player,outsider.player); service.handleDeleteTeam(leader.player);
            assertNull(service.getTeamById(team.getTeamId())); assertNull(service.getTeam(leader.player.getUniqueId()));
            service.handleAcceptInvite(outsider.player); assertNull(service.getTeam(outsider.player.getUniqueId()));
        }
    }
}
