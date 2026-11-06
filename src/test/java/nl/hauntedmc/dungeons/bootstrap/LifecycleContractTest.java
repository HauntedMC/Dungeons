package nl.hauntedmc.dungeons.bootstrap;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

class LifecycleContractTest {
    @Test
    void shutdownClearsPendingStartsBeforeDisposalAndOneBrokenRunCannotAbandonOthers() {
        try (var paper = new PaperBoundary()) {
            var queues = mock(DungeonQueueRegistry.class);
            var coordinator = mock(DungeonQueueCoordinator.class);
            var teams = mock(DungeonTeamService.class);
            var catalog = mock(DungeonRepository.class);
            var sessions = mock(PlayerSessionRegistry.class);
            paper.runtime.setQueueRegistry(queues);
            paper.runtime.setDungeonQueueCoordinator(coordinator);
            paper.runtime.setTeamService(teams);
            paper.runtime.setDungeonCatalog(catalog);
            paper.runtime.setPlayerSessions(sessions);
            var pending = mock(DungeonQueueEntry.class);
            when(queues.snapshotQueues()).thenReturn(List.of(pending));
            var dungeon = mock(DungeonDefinition.class);
            var broken = mock(DungeonInstance.class);
            var healthy = mock(DungeonInstance.class);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(dungeon));
            when(dungeon.getInstances()).thenReturn(List.of(broken, healthy));
            doThrow(new IllegalStateException("unload failed")).when(broken).dispose();

            new LifecycleCoordinator(paper.runtime, mock(CoreBootstrap.class)).shutdown();

            var order = inOrder(coordinator, teams, broken, healthy);
            order.verify(coordinator).discardQueue(pending);
            order.verify(teams).clearQueuedStarts(null);
            order.verify(teams).shutdown();
            order.verify(broken).dispose();
            order.verify(healthy).dispose();
        }
    }

    @Test
    void reloadRefundsOrphanedReservationsAndClearsWaitingStateBeforeReloadingTheCatalog() {
        try (var paper = new PaperBoundary()) {
            var queues = mock(DungeonQueueRegistry.class);
            var teams = mock(DungeonTeamService.class);
            var catalog = mock(DungeonRepository.class);
            var sessions = mock(PlayerSessionRegistry.class);
            paper.runtime.setQueueRegistry(queues);
            paper.runtime.setTeamService(teams);
            paper.runtime.setDungeonCatalog(catalog);
            paper.runtime.setPlayerSessions(sessions);
            var player = mock(Player.class);
            var session = mock(DungeonPlayerSession.class);
            paper.bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            when(sessions.get(player)).thenReturn(session);
            when(session.hasReservedAccessKey()).thenReturn(true);
            when(session.getReservedAccessKeyDungeon()).thenReturn("deleted-dungeon");

            new LifecycleCoordinator(paper.runtime, mock(CoreBootstrap.class)).reloadAllDungeons();

            var order = inOrder(session, catalog);
            order.verify(session).refundReservedAccessKey();
            order.verify(session).setAwaitingDungeon(false);
            order.verify(session).restoreCapturedHotbar();
            order.verify(catalog).reloadAll();
        }
    }
}
