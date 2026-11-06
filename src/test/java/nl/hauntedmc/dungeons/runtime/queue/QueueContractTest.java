package nl.hauntedmc.dungeons.runtime.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

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
        return new DungeonQueueEntry(leader, dungeon, "", List.of(member.getPlayer().getUniqueId()), UUID.randomUUID());
    }
}
