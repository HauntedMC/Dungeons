package nl.hauntedmc.dungeons.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class DatabaseStateStoreTest {
    @Test void cancelledAndTimedOutDurabilityBarriersCloseAdmissionWithoutCancellingDatabaseWork() {
        var database = mock(DungeonDatabase.class);
        var store = new DatabaseStateStore(database, 50);
        var cancelled = new CompletableFuture<Void>();
        cancelled.cancel(false);
        assertThrows(IllegalStateException.class, () -> store.await(cancelled));
        verify(database).unavailable(any(java.util.concurrent.CancellationException.class));
        var pending = new CompletableFuture<Void>();
        assertThrows(IllegalStateException.class, () -> store.await(pending));
        verify(database).unavailable(any(java.util.concurrent.TimeoutException.class));
        assertFalse(pending.isDone());
        pending.complete(null);
    }
}
