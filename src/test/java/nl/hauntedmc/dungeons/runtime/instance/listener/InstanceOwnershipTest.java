package nl.hauntedmc.dungeons.runtime.instance.listener;

import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.junit.jupiter.api.Test;

class InstanceOwnershipTest {
    @Test
    void worldLeaveUsesTheInstanceOwnerEvenWhenAnotherRuntimeIsPublished() {
        try (var paper = new PaperBoundary()) {
            new CapturedScheduler(paper);
            var player = mock(Player.class);
            var world = mock(World.class);
            var instance = mock(DungeonInstance.class);
            when(instance.getInstanceWorld()).thenReturn(world);
            when(instance.getRuntime()).thenReturn(paper.runtime);
            when(instance.getEnvironment()).thenReturn(paper.runtime.environment());
            var sessions = mock(PlayerSessionRegistry.class);
            var session = mock(DungeonPlayerSession.class);
            when(sessions.get(player)).thenReturn(session);
            when(session.getInstance()).thenReturn(instance);
            paper.runtime.setPlayerSessions(sessions);
            var other = new DungeonsRuntime(paper.plugin);
            var unrelatedSessions = mock(PlayerSessionRegistry.class);
            other.setPlayerSessions(unrelatedSessions);
            RuntimeContext.initialize(other);
            try {
                new InstanceListener(instance)
                        .onPlayerLeaveDungeon(new PlayerChangedWorldEvent(player, world));
                verify(instance).removePlayer(session);
                verifyNoInteractions(unrelatedSessions);
            } finally {
                RuntimeContext.initialize(paper.runtime);
            }
        }
    }
}
