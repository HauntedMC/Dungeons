package nl.hauntedmc.dungeons.persistence;

import static org.mockito.Mockito.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import nl.hauntedmc.dungeons.listener.DatabaseLoginListener;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;

class DatabaseLoginListenerTest {
    @Test void loginRequiresHealthyPersistenceAndCanonicalPrefetchBeforeAdmission() {
        try (var paper = new PaperBoundary()) {
            var store = mock(StateStore.class);
            paper.runtime.setStateStore(store);
            var listener = new DatabaseLoginListener(paper.runtime);
            UUID id = UUID.randomUUID();
            var event = mock(AsyncPlayerPreLoginEvent.class);
            when(event.getUniqueId()).thenReturn(id);
            when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            when(store.prefetch(id)).thenReturn(CompletableFuture.completedFuture(null));
            listener.onLogin(event);
            verify(store).prefetch(id);
            verify(event, never()).disallow(any(AsyncPlayerPreLoginEvent.Result.class), any(net.kyori.adventure.text.Component.class));
            when(store.prefetch(id)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Identity not ready")));
            listener.onLogin(event);
            verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(net.kyori.adventure.text.Component.class));
            verify(paper.runtime.database(), never()).close();
        }
    }
    @Test void rejectedLoginsDoNoWorkAndUnhealthyDatabaseRejectsBeforePrefetch() {
        try (var paper = new PaperBoundary()) {
            var store = mock(StateStore.class);
            paper.runtime.setStateStore(store);
            var listener = new DatabaseLoginListener(paper.runtime);
            var event = mock(AsyncPlayerPreLoginEvent.class);
            when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.KICK_BANNED);
            listener.onLogin(event);
            verifyNoInteractions(store);
            when(event.getLoginResult()).thenReturn(AsyncPlayerPreLoginEvent.Result.ALLOWED);
            when(paper.runtime.database().ready()).thenReturn(false);
            listener.onLogin(event);
            verifyNoInteractions(store);
            verify(event).disallow(eq(AsyncPlayerPreLoginEvent.Result.KICK_OTHER), any(net.kyori.adventure.text.Component.class));
        }
    }
}
