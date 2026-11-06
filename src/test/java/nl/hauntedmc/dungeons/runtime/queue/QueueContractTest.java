package nl.hauntedmc.dungeons.runtime.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class QueueContractTest {
    final PlayerSessionRegistry sessions = mock(PlayerSessionRegistry.class);
    final ActiveInstanceRegistry instances = mock(ActiveInstanceRegistry.class);
    final DungeonQueueRegistry queues = new DungeonQueueRegistry(sessions, instances);
    final DungeonDefinition dungeon = mock(DungeonDefinition.class);

    @Test
    void replacingOverlappingTeamQueuesEvictsOldMembershipAndRefundsEveryOldMember() {
        DungeonPlayerSession alice = player();
        DungeonPlayerSession bob = player();
        DungeonPlayerSession carol = player();
        DungeonQueueEntry original = team(alice, bob);
        DungeonQueueEntry replacement = team(bob, carol);
        queues.enqueue(original);
        queues.enqueue(replacement);

        assertEquals(List.of(replacement), queues.snapshotQueues());
        assertNull(queues.getQueue(alice));
        assertSame(replacement, queues.getQueue(bob));
        assertSame(replacement, queues.getQueue(carol));
        verify(dungeon).refundReservedAccessKey(alice);
        verify(dungeon).refundReservedAccessKey(bob);
        verify(dungeon, never()).refundReservedAccessKey(carol);
        verify(alice).setAwaitingDungeon(false);
    }

    @Test
    void staleQueueCleanupCannotRemoveOrRefundMembersOfTheirReplacementQueue() {
        DungeonPlayerSession alice = player();
        DungeonPlayerSession bob = player();
        DungeonPlayerSession carol = player();
        DungeonQueueEntry original = team(alice, bob);
        DungeonQueueEntry replacement = team(bob, carol);
        queues.enqueue(original);
        queues.enqueue(replacement);
        clearInvocations(dungeon, alice, bob, carol);

        queues.unqueue(original);

        assertSame(replacement, queues.getQueue(bob));
        assertSame(replacement, queues.getQueue(carol));
        assertEquals(List.of(replacement), queues.snapshotQueues());
        verify(dungeon, never()).refundReservedAccessKey(bob);
        verify(bob, never()).setAwaitingDungeon(false);
    }

    @Test
    void fifoSelectsFirstStartableQueueAndDiscardsOfflineEntriesWithoutSkippingTheirSuccessor() {
        DungeonPlayerSession offline = player();
        DungeonPlayerSession waiting = player();
        DungeonPlayerSession ready = player();
        DungeonPlayerSession later = player();
        when(offline.getPlayer().isOnline()).thenReturn(false);
        DungeonQueueEntry invalid = new DungeonQueueEntry(offline, dungeon, "invalid");
        DungeonQueueEntry blocked = new DungeonQueueEntry(waiting, dungeon, "blocked");
        DungeonQueueEntry firstReady = new DungeonQueueEntry(ready, dungeon, "ready");
        DungeonQueueEntry secondReady = new DungeonQueueEntry(later, dungeon, "ready");
        when(instances.canStartDungeonNow(dungeon, 1, "ready")).thenReturn(true);
        queues.enqueue(invalid);
        queues.enqueue(blocked);
        queues.enqueue(firstReady);
        queues.enqueue(secondReady);

        assertSame(firstReady, queues.getNextInLine());
        assertNull(queues.getQueue(offline));
        assertSame(blocked, queues.getQueue(waiting));
        assertEquals(List.of(blocked, firstReady, secondReady), queues.snapshotQueues());
        verify(dungeon).refundReservedAccessKey(offline);
        queues.snapshotQueues().clear();
        assertEquals(3, queues.snapshotQueues().size());
    }

    DungeonPlayerSession player() {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        DungeonPlayerSession session = mock(DungeonPlayerSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(sessions.get(id)).thenReturn(session);
        return session;
    }

    DungeonQueueEntry team(DungeonPlayerSession leader, DungeonPlayerSession member) {
        return new DungeonQueueEntry(
                leader, dungeon, "", List.of(member.getPlayer().getUniqueId()), UUID.randomUUID());
    }
}
