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

    @Test
    void aTransientStartFailureRetainsTheSameQueueUntilASuccessfulRetry() {
        try (var f = new Fixture(directory)) {
            var session = f.player();
            f.coordinator.sendToDungeon(session.getPlayer(), "crypt", "hard");
            var queue = f.queues.getQueue(session);
            assertFalse(f.coordinator.tryStartQueuedDungeon(queue));
            assertSame(queue, f.queues.getQueue(session));
            assertTrue(session.isAwaitingDungeon());
            verify(f.dungeon, never()).refundReservedAccessKey(any());
            when(f.catalog.createInstance("crypt", session.getPlayer(), "hard", 1, null)).thenReturn(true);
            assertTrue(f.coordinator.tryStartQueuedDungeon(queue));
            assertNull(f.queues.getQueue(session));
            verify(f.catalog, times(2)).createInstance("crypt", session.getPlayer(), "hard", 1, null);
        }
    }

    @Test
    void teamReservationFailureRollsBackAlreadyReservedKeysBeforeAnyStartOrQueueIsPublished() {
        try (var f = new Fixture(directory)) {
            var leader = f.player();
            var member = f.player();
            var team = new nl.hauntedmc.dungeons.runtime.team.DungeonTeam(leader.getPlayer().getUniqueId());
            team.addMember(member.getPlayer().getUniqueId());
            when(f.teams.getTeam(team.getLeaderId())).thenReturn(team);
            var leaderInventory = f.giveKey(leader);
            var memberInventory = f.giveKey(member);
            when(f.dungeon.reserveIssuedAccessKey(any())).thenReturn(true, false);
            f.coordinator.sendToDungeon(leader.getPlayer(), "crypt", "hard");
            assertTrue(f.queues.snapshotQueues().isEmpty());
            assertFalse(leader.isAwaitingDungeon());
            assertFalse(member.isAwaitingDungeon());
            assertFalse(leader.hasReservedAccessKey());
            assertFalse(member.hasReservedAccessKey());
            var returned = org.mockito.ArgumentCaptor.forClass(org.bukkit.inventory.ItemStack.class);
            verify(leaderInventory).addItem(returned.capture());
            assertEquals(1, returned.getValue().getAmount());
            verify(memberInventory, never()).setItem(anyInt(), any());
            verify(f.catalog, never()).createInstance(anyString(), any(), anyString(), anyInt(), any());
            verify(f.teams, never()).markQueued(any(), anyString(), anyString());
        }
    }

    @Test
    void aRevokedReservedKeyCannotLaunchAndItsPhysicalItemIsRefundedOnlyOnce() {
        try (var f = new Fixture(directory)) {
            var session = f.player();
            var inventory = f.giveKey(session);
            when(f.dungeon.reserveIssuedAccessKey(any())).thenReturn(true);
            when(f.dungeon.isReservedAccessKey(any())).thenReturn(true);
            f.coordinator.sendToDungeon(session.getPlayer(), "crypt", "hard");
            var queue = f.queues.getQueue(session);
            assertTrue(session.hasReservedAccessKey());
            when(f.dungeon.isReservedAccessKey(any())).thenReturn(false);
            assertFalse(f.coordinator.tryStartQueuedDungeon(queue));
            assertNull(f.queues.getQueue(session));
            assertFalse(session.hasReservedAccessKey());
            assertFalse(session.isAwaitingDungeon());
            verify(inventory, times(1)).addItem(any(org.bukkit.inventory.ItemStack.class));
            verify(f.catalog, never()).createInstance(anyString(), any(), anyString(), anyInt(), any());
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
        org.bukkit.inventory.PlayerInventory giveKey(DungeonPlayerSession session) {
            var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
            var held = item(2);
            var template = item(1);
            when(session.getPlayer().getInventory()).thenReturn(inventory);
            when(inventory.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[] {held});
            when(dungeon.getValidKeys()).thenReturn(java.util.List.of(template));
            when(dungeon.isValidKey(held)).thenReturn(template);
            when(dungeon.getFirstKeyAmount(session.getPlayer())).thenReturn(1);
            doAnswer(call -> ((DungeonPlayerSession) call.getArgument(0)).refundReservedAccessKey("crypt")).when(dungeon).refundReservedAccessKey(any());
            return inventory;
        }
        static org.bukkit.inventory.ItemStack item(int amount) {
            var item = mock(org.bukkit.inventory.ItemStack.class);
            var quantity = new java.util.concurrent.atomic.AtomicInteger(amount);
            when(item.getType()).thenReturn(mock(org.bukkit.Material.class));
            when(item.getAmount()).thenAnswer(call -> quantity.get());
            doAnswer(call -> { quantity.set(call.getArgument(0)); return null; }).when(item).setAmount(anyInt());
            when(item.clone()).thenAnswer(call -> item(quantity.get()));
            return item;
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
