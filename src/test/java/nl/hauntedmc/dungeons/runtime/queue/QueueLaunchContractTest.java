package nl.hauntedmc.dungeons.runtime.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.UUID;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueueLaunchContractTest {
    @TempDir Path directory;

    @Test
    void aFullDungeonQueuesARealSessionAndSuccessfulRetryRemovesTheQueueWithoutRefunding() {
        try (var f = new Fixture(directory)) {
            DungeonPlayerSession session = f.player();
            f.coordinator.sendToDungeon(session.getPlayer(), "crypt", "hard");
            assertTrue(session.isAwaitingDungeon());
            DungeonQueueEntry queue = f.queues.getQueue(session);
            assertNotNull(queue);
            verify(f.catalog, never()).createInstance(anyString(), any(), anyString(), anyInt(), any());
            when(f.capacity.canStartDungeonNow(f.dungeon, 1, "hard")).thenReturn(true);
            when(f.catalog.createInstance("crypt", session.getPlayer(), "hard", 1, null)).thenReturn(true);
            assertTrue(f.coordinator.tryStartQueuedDungeon(queue));
            assertTrue(f.queues.snapshotQueues().isEmpty());
            verify(f.catalog).createInstance("crypt", session.getPlayer(), "hard", 1, null);
            verify(f.dungeon, never()).refundReservedAccessKey(any());
        }
    }

    @Test
    void permanentInvalidationWhileWaitingDiscardsTheQueueAndClearsWaitingState() {
        for (String reason : new String[] {"disabled", "cooldown", "team-limit", "offline"}) {
            try (var f = new Fixture(directory)) {
                var session = f.player();
                f.coordinator.sendToDungeon(session.getPlayer(), "crypt", "hard");
                var queue = f.queues.getQueue(session);
                assertNotNull(queue);
                switch (reason) {
                    case "disabled" -> when(f.dungeon.isEnabled()).thenReturn(false);
                    case "cooldown" -> when(f.dungeon.hasAccessCooldown(session.getPlayer())).thenReturn(true);
                    case "team-limit" -> when(f.dungeon.canEverFitTeamSize(1)).thenReturn(false);
                    case "offline" -> when(session.getPlayer().isOnline()).thenReturn(false);
                    default -> throw new AssertionError(reason);
                }
                assertFalse(f.coordinator.tryStartQueuedDungeon(queue), reason);
                assertNull(f.queues.getQueue(session), reason);
                assertFalse(session.isAwaitingDungeon(), reason);
                verify(f.dungeon).refundReservedAccessKey(session);
                verify(f.catalog, never()).createInstance(anyString(), any(), anyString(), anyInt(), any());
            }
        }
    }

    static class Fixture implements AutoCloseable {
        final PaperBoundary paper = new PaperBoundary();
        final PlayerSessionRegistry sessions = new PlayerSessionRegistry(paper.plugin);
        final ActiveInstanceRegistry capacity = mock(ActiveInstanceRegistry.class);
        final DungeonQueueRegistry queues = new DungeonQueueRegistry(sessions, capacity);
        final DungeonTeamService teams = mock(DungeonTeamService.class);
        final DungeonRepository catalog = mock(DungeonRepository.class);
        final DungeonDefinition dungeon = mock(DungeonDefinition.class);
        final DungeonQueueCoordinator coordinator = new DungeonQueueCoordinator(paper.plugin, sessions, capacity, queues, teams);
        final YamlConfiguration settings = new YamlConfiguration();
        Fixture(Path directory) {
            when(paper.plugin.getDataFolder()).thenReturn(directory.toFile());
            when(dungeon.getWorldName()).thenReturn("crypt");
            when(dungeon.isEnabled()).thenReturn(true);
            when(dungeon.canFitTeamSize(anyInt())).thenReturn(true);
            when(dungeon.canEverFitTeamSize(anyInt())).thenReturn(true);
            when(dungeon.getConfig()).thenReturn(settings);
            when(catalog.get("crypt")).thenReturn(dungeon);
            coordinator.setDungeonCatalog(catalog);
            queues.setQueueCoordinator(coordinator);
        }
        DungeonPlayerSession player() {
            Player player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.isOnline()).thenReturn(true);
            return sessions.put(player);
        }
        @Override public void close() { paper.close(); }
    }
}
