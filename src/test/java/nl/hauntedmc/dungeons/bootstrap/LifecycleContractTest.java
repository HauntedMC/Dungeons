package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.config.ConfigurationFile;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.RunFixture;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class LifecycleContractTest {
    @TempDir Path directory;

    @Test
    void catalogReplacementWaitsForEditorDisposalAndKeepsOldDefinitionsOnFailure() {
        try (var paper = new PaperBoundary()) {
            paper.runtime.setQueueRegistry(mock(DungeonQueueRegistry.class));
            paper.runtime.setTeamService(mock(DungeonTeamService.class));
            paper.runtime.setPlayerSessions(mock(PlayerSessionRegistry.class));
            var catalog = mock(DungeonRepository.class);
            paper.runtime.setDungeonCatalog(catalog);
            var dungeon = mock(DungeonDefinition.class);
            var editor = mock(DungeonInstance.class);
            when(dungeon.getInstances()).thenReturn(List.of(editor));
            when(catalog.getLoadedDungeons()).thenReturn(List.of(dungeon));
            when(editor.isEditInstance()).thenReturn(true);
            var failedSave = new CompletableFuture<Boolean>();
            var retrySave = new CompletableFuture<Boolean>();
            when(editor.disposeAsync()).thenReturn(failedSave, retrySave);
            var lifecycle = new LifecycleCoordinator(paper.runtime, mock(CoreBootstrap.class));
            var first = lifecycle.reloadAllDungeons();
            assertFalse(first.isDone());
            verify(catalog, never()).reloadAll();
            assertFalse(lifecycle.reloadAllDungeons().join());
            failedSave.complete(false);
            assertFalse(first.join());
            verify(catalog, never()).reloadAll();
            var retry = lifecycle.reloadAllDungeons();
            assertFalse(retry.isDone());
            retrySave.complete(true);
            assertTrue(retry.join());
            verify(catalog).reloadAll();
        }
    }

    @Test
    void failedTeamShutdownCannotPreventWorldDisposalOrPlayerCheckpoints() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var catalog = mock(DungeonRepository.class);
            f.paper.runtime.setDungeonCatalog(catalog);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(f.dungeon));
            f.paper.runtime.setQueueRegistry(mock(DungeonQueueRegistry.class));
            var teams = mock(DungeonTeamService.class);
            f.paper.runtime.setTeamService(teams);
            doThrow(new IllegalStateException("team shutdown failed")).when(teams).shutdown();
            f.dungeon.getInstances().add(f.run);
            f.paper.runtime.activeInstances().registerActiveInstance(f.run);
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(f.world, false)).thenReturn(true);
            assertDoesNotThrow(() -> new LifecycleCoordinator(f.paper.runtime, mock(CoreBootstrap.class)).shutdown());
            assertNull(f.run.getInstanceWorld());
            assertTrue(f.paper.runtime.activeInstances().getActiveInstances().isEmpty());
        }
    }

    @Test
    void oneFailedPreparationCancellationCannotAbandonTheOthers() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var registry = f.paper.runtime.preparations();
            var broken = mock(PreparationRegistry.Pending.class);
            var healthy = mock(PreparationRegistry.Pending.class);
            when(broken.dungeon()).thenReturn(f.dungeon);
            when(healthy.dungeon()).thenReturn(f.dungeon);
            doThrow(new IllegalStateException("refund storage unavailable")).when(broken).cancel();
            var one = f.session(new PlayerState());
            var two = f.session(new PlayerState());
            assertTrue(registry.register(one, broken));
            assertTrue(registry.register(two, healthy));
            assertDoesNotThrow(registry::shutdown);
            verify(healthy).cancel();
            assertTrue(registry.owns(one, broken));
        }
    }

    @Test
    void shutdownSettlesOfflineRunMembersBeforeDisposalAndReleasesDurableSessions()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var catalog = mock(DungeonRepository.class);
            f.paper.runtime.setDungeonCatalog(catalog);
            f.paper.runtime.setQueueRegistry(mock(DungeonQueueRegistry.class));
            f.paper.runtime.setTeamService(mock(DungeonTeamService.class));
            when(catalog.getLoadedDungeons()).thenReturn(List.of(f.dungeon));
            var online = new PlayerState();
            var offline = new PlayerState();
            offline.contents[0] = PlayerState.stack(12);
            var present = f.session(online);
            var absent = f.session(offline);
            f.run.addPlayer(present);
            f.run.addPlayer(absent);
            absent.addReward(PlayerState.stack(3));
            offline.online = false;
            f.dungeon.getInstances().add(f.run);
            f.paper.runtime.activeInstances().registerActiveInstance(f.run);
            f.paper.bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(online.player));
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(f.world, false)).thenReturn(true);
            new LifecycleCoordinator(f.paper.runtime, mock(CoreBootstrap.class)).shutdown();
            assertTrue(present.isExpired());
            assertTrue(absent.isExpired());
            assertEquals(0, f.paper.runtime.playerSessions().size());
            assertNull(f.run.getInstanceWorld());
            f.paper
                    .bukkit
                    .when(() -> Bukkit.getWorld(offline.originWorld.getUID()))
                    .thenReturn(offline.originWorld);
            f.paper.bukkit.when(() -> Bukkit.getWorld("overworld")).thenReturn(offline.originWorld);
            offline.online = true;
            var recreated = new PlayerSessionRegistry(f.paper.plugin).put(offline.player);
            assertTrue(recreated.recoverPendingState());
            assertEquals(12, offline.contents[0].getAmount());
            assertEquals(3, recreated.getRewardItems().getFirst().getAmount());
        }
    }

    @Test
    void shutdownFailureStillFlushesAlreadyCapturedConfigurationSnapshots() throws Exception {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(paper.plugin.isEnabled()).thenReturn(true);
            var config = new ConfigurationFile(paper.plugin);
            config.set("value", "durable");
            var file = directory.resolve("shutdown.yml");
            var saved = config.saveAsync(file.toFile());
            assertFalse(saved.isDone());
            paper.runtime.setQueueRegistry(mock(DungeonQueueRegistry.class));
            var teams = mock(DungeonTeamService.class);
            paper.runtime.setTeamService(teams);
            doThrow(new IllegalStateException("shutdown hook failed")).when(teams).shutdown();
            paper.runtime.setDungeonCatalog(mock(DungeonRepository.class));
            paper.runtime.setPlayerSessions(mock(PlayerSessionRegistry.class));
            assertDoesNotThrow(
                    () ->
                            new LifecycleCoordinator(paper.runtime, mock(CoreBootstrap.class))
                                    .shutdown());
            assertTrue(saved.get(1, TimeUnit.SECONDS));
            assertEquals("value: durable\n", Files.readString(file));
            scheduler.asynchronous.getFirst().run();
        }
    }

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
            var retainedFromPreviousCatalog = mock(DungeonInstance.class);
            var active = mock(ActiveInstanceRegistry.class);
            paper.runtime.setActiveInstances(active);
            when(active.getActiveInstances())
                    .thenReturn(List.of(healthy, retainedFromPreviousCatalog));
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
            verify(retainedFromPreviousCatalog).dispose();
            verify(healthy, times(1)).dispose();
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
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var other = f.paper.dungeon(directory.resolve("other"), new YamlConfiguration());
            var selected = new PlayerState();
            var selectedSession = f.session(selected);
            var unrelated = new PlayerState();
            var unrelatedSession = f.session(unrelated);
            var active = new PlayerState();
            var activeSession = f.session(active);
            var origin = active.location.clone();
            f.run.addPlayer(activeSession);
            f.dungeon.getInstances().add(f.run);
            selectedSession.reserveAccessKey("crypt", PlayerState.stack(1));
            unrelatedSession.reserveAccessKey("other", PlayerState.stack(1));
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
            assertNull(activeSession.getInstance());
            assertEquals(origin, active.location);
            assertFalse(selectedSession.hasReservedAccessKey());
            assertFalse(selectedSession.isAwaitingDungeon());
            assertTrue(unrelatedSession.hasReservedAccessKey());
            assertTrue(unrelatedSession.isAwaitingDungeon());
            verify(coordinator).discardQueue(own);
            verify(coordinator, never()).discardQueue(neighbor);
            verify(teams).clearQueuedStarts("crypt");
            verify(catalog, never()).remove(f.dungeon);
            verify(catalog).loadDungeon(f.dungeon.getFolder());
        }
    }
}
