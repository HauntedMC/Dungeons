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
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

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

    @Test
    void shutdownRestoresAndDetachesOnlinePlayersBeforeDisposingTheirWorlds() {
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
            var run = mock(DungeonInstance.class);
            var dungeon = mock(DungeonDefinition.class);
            paper.bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            when(sessions.get(player)).thenReturn(session);
            when(session.getInstance()).thenReturn(run);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(dungeon));
            when(dungeon.getInstances()).thenReturn(List.of(run));

            new LifecycleCoordinator(paper.runtime, mock(CoreBootstrap.class)).shutdown();

            var order = inOrder(session, run);
            order.verify(session).restoreCapturedHotbar();
            order.verify(run).removePlayer(session);
            order.verify(run).dispose();
        }
    }

    @Test
    void reloadingOneDungeonRestoresItsPlayersAndRefundsOnlyItsPendingStarts() throws Exception {
        try (var f =
                new nl.hauntedmc.dungeons.support.RunFixture(
                        directory, new org.bukkit.configuration.file.YamlConfiguration())) {
            var other =
                    f.paper.dungeon(
                            directory.resolve("other"),
                            new org.bukkit.configuration.file.YamlConfiguration());
            var selected = new nl.hauntedmc.dungeons.support.PlayerState();
            var selectedSession = f.session(selected);
            var unrelated = new nl.hauntedmc.dungeons.support.PlayerState();
            var unrelatedSession = f.session(unrelated);
            var active = new nl.hauntedmc.dungeons.support.PlayerState();
            var activeSession = f.session(active);
            var origin = active.location.clone();
            f.run.addPlayer(activeSession);
            f.dungeon.getInstances().add(f.run);
            selectedSession.reserveAccessKey(
                    "crypt", nl.hauntedmc.dungeons.support.PlayerState.stack(1));
            unrelatedSession.reserveAccessKey(
                    "other", nl.hauntedmc.dungeons.support.PlayerState.stack(1));
            selectedSession.setAwaitingDungeon(true);
            unrelatedSession.setAwaitingDungeon(true);
            var queues =
                    new DungeonQueueRegistry(
                            f.paper.runtime.playerSessions(), f.paper.runtime.activeInstances());
            var own = new DungeonQueueEntry(selectedSession, f.dungeon, "");
            var neighbor = new DungeonQueueEntry(unrelatedSession, other, "");
            queues.enqueue(own);
            queues.enqueue(neighbor);
            var coordinator = mock(DungeonQueueCoordinator.class);
            var teams = mock(DungeonTeamService.class);
            var catalog = mock(DungeonRepository.class);
            when(catalog.get("crypt")).thenReturn(f.dungeon);
            when(catalog.get("other")).thenReturn(other);
            f.paper.runtime.setQueueRegistry(queues);
            f.paper.runtime.setDungeonQueueCoordinator(coordinator);
            f.paper.runtime.setTeamService(teams);
            f.paper.runtime.setDungeonCatalog(catalog);
            f.paper
                    .bukkit
                    .when(Bukkit::getOnlinePlayers)
                    .thenReturn(List.of(selected.player, unrelated.player, active.player));
            new LifecycleCoordinator(f.paper.runtime, mock(CoreBootstrap.class))
                    .reloadDungeon(f.dungeon);
            org.junit.jupiter.api.Assertions.assertNull(activeSession.getInstance());
            org.junit.jupiter.api.Assertions.assertEquals(origin, active.location);
            org.junit.jupiter.api.Assertions.assertFalse(selectedSession.hasReservedAccessKey());
            org.junit.jupiter.api.Assertions.assertFalse(selectedSession.isAwaitingDungeon());
            org.junit.jupiter.api.Assertions.assertTrue(unrelatedSession.hasReservedAccessKey());
            org.junit.jupiter.api.Assertions.assertTrue(unrelatedSession.isAwaitingDungeon());
            org.mockito.Mockito.verify(coordinator).discardQueue(own);
            org.mockito.Mockito.verify(coordinator, org.mockito.Mockito.never())
                    .discardQueue(neighbor);
            org.mockito.Mockito.verify(teams).clearQueuedStarts("crypt");
            org.mockito.Mockito.verify(catalog).remove(f.dungeon);
            org.mockito.Mockito.verify(catalog).loadDungeon(f.dungeon.getFolder());
        }
    }
}
