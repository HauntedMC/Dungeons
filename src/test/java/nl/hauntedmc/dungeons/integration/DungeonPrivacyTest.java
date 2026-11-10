package nl.hauntedmc.dungeons.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerDataVisibility;
import nl.hauntedmc.dungeons.api.DungeonsApi.Disclosure;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;

class DungeonPrivacyTest {
    @Test
    void disclosureIsUncachedAndPrivateDataIsStillVisibleToItsOwner() {
        var players = mock(PlayerData.class);
        when(players.supports(DataRegistryFeature.PRIVACY)).thenReturn(true);
        UUID viewer = UUID.randomUUID(), target = UUID.randomUUID();
        when(players.findPrivacy(target))
                .thenReturn(CompletableFuture.completedFuture(PlayerDataVisibility.PUBLIC));
        try (var privacy =
                new DungeonPrivacy(
                        players,
                        () -> true,
                        (a, b) -> fail("No friendship lookup for PUBLIC or PRIVATE"))) {
            assertEquals(
                    Disclosure.ALLOW, privacy.canView(viewer, target).toCompletableFuture().join());
            when(players.findPrivacy(target))
                    .thenReturn(CompletableFuture.completedFuture(PlayerDataVisibility.PRIVATE));
            assertEquals(
                    Disclosure.DENY, privacy.canView(viewer, target).toCompletableFuture().join());
            assertEquals(
                    Disclosure.ALLOW, privacy.canView(target, target).toCompletableFuture().join());
            verify(players, times(2)).findPrivacy(target);
        }
    }

    @Test
    void friendsRequiresTheLiveAuthoritativeDecisionAndDoesNotCacheGrants() {
        var players = mock(PlayerData.class);
        when(players.supports(DataRegistryFeature.PRIVACY)).thenReturn(true);
        UUID viewer = UUID.randomUUID(), target = UUID.randomUUID();
        when(players.findPrivacy(target))
                .thenReturn(CompletableFuture.completedFuture(PlayerDataVisibility.FRIENDS));
        var authorized = new AtomicBoolean(true);
        try (var privacy =
                new DungeonPrivacy(
                        players,
                        () -> true,
                        (a, b) -> {
                            assertEquals(viewer, a);
                            assertEquals(target, b);
                            return CompletableFuture.completedFuture(
                                    authorized.get() ? Disclosure.ALLOW : Disclosure.DENY);
                        })) {
            assertEquals(
                    Disclosure.ALLOW, privacy.canView(viewer, target).toCompletableFuture().join());
            authorized.set(false);
            assertEquals(
                    Disclosure.DENY, privacy.canView(viewer, target).toCompletableFuture().join());
        }
    }

    @Test
    void publicBatchesExcludeBothFriendsAndPrivateBeforeConsumersAssignRanks() {
        var players = mock(PlayerData.class);
        when(players.supports(DataRegistryFeature.PRIVACY)).thenReturn(true);
        when(players.findPrivacy(anyCollection()))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                Map.of(
                                        1L,
                                        PlayerDataVisibility.PRIVATE,
                                        2L,
                                        PlayerDataVisibility.PUBLIC,
                                        3L,
                                        PlayerDataVisibility.FRIENDS,
                                        4L,
                                        PlayerDataVisibility.PUBLIC)));
        try (var privacy =
                new DungeonPrivacy(
                        players,
                        () -> true,
                        (a, b) -> fail("Public rankings never resolve friendships"))) {
            var allowed =
                    privacy.publicPlayerIds(List.of(1L, 2L, 2L, 3L, 4L))
                            .toCompletableFuture()
                            .join();
            assertEquals(Set.of(2L, 4L), allowed);
            assertThrows(UnsupportedOperationException.class, () -> allowed.clear());
            verify(players).findPrivacy(Set.of(1L, 2L, 3L, 4L));
        }
    }

    @Test
    void missingOrBrokenPrivacyNeverDefaultsToPublic() {
        var players = mock(PlayerData.class);
        UUID viewer = UUID.randomUUID(), target = UUID.randomUUID();
        try (var privacy =
                new DungeonPrivacy(
                        players,
                        () -> true,
                        (a, b) -> CompletableFuture.completedFuture(Disclosure.UNAVAILABLE))) {
            assertEquals(
                    Disclosure.UNAVAILABLE,
                    privacy.canView(viewer, target).toCompletableFuture().join());
            assertThrows(
                    CompletionException.class,
                    () -> privacy.publicPlayerIds(List.of(1L)).toCompletableFuture().join());
            when(players.supports(DataRegistryFeature.PRIVACY)).thenReturn(true);
            when(players.findPrivacy(target))
                    .thenThrow(new IllegalStateException("Backend unavailable"));
            assertEquals(
                    Disclosure.UNAVAILABLE,
                    privacy.canView(viewer, target).toCompletableFuture().join());
            when(players.findPrivacy(anyCollection()))
                    .thenReturn(CompletableFuture.completedFuture(Map.of()));
            assertThrows(
                    CompletionException.class,
                    () -> privacy.publicPlayerIds(List.of(1L)).toCompletableFuture().join());
            doReturn(CompletableFuture.completedFuture(PlayerDataVisibility.FRIENDS))
                    .when(players)
                    .findPrivacy(target);
            assertEquals(
                    Disclosure.UNAVAILABLE,
                    privacy.canView(viewer, target).toCompletableFuture().join());
        }
    }

    @Test
    void inputBoundsAndShutdownRejectWorkAndIgnoreLateGrants() {
        var players = mock(PlayerData.class);
        when(players.supports(DataRegistryFeature.PRIVACY)).thenReturn(true);
        UUID viewer = UUID.randomUUID(), target = UUID.randomUUID();
        var backend = new CompletableFuture<PlayerDataVisibility>();
        when(players.findPrivacy(target)).thenReturn(backend);
        var privacy = new DungeonPrivacy(players, () -> true, (a, b) -> fail());
        assertThrows(
                CompletionException.class,
                () -> privacy.publicPlayerIds(List.of(-1L)).toCompletableFuture().join());
        assertThrows(
                CompletionException.class,
                () ->
                        privacy.publicPlayerIds(LongStream.rangeClosed(1, 513).boxed().toList())
                                .toCompletableFuture()
                                .join());
        var pending = privacy.canView(viewer, target);
        privacy.close();
        assertEquals(Disclosure.UNAVAILABLE, pending.toCompletableFuture().join());
        backend.complete(PlayerDataVisibility.PUBLIC);
        assertEquals(Disclosure.UNAVAILABLE, pending.toCompletableFuture().join());
        assertEquals(
                Disclosure.UNAVAILABLE,
                privacy.canView(viewer, viewer).toCompletableFuture().join());
        verify(players, times(1)).findPrivacy(target);
    }

    @Test
    void boundedInflightQueriesHaveDeadlinesAndCannotSurvivePersistenceLoss() throws Exception {
        var players = mock(PlayerData.class);
        when(players.supports(DataRegistryFeature.PRIVACY)).thenReturn(true);
        UUID viewer = UUID.randomUUID(), target = UUID.randomUUID();
        var backend = new CompletableFuture<PlayerDataVisibility>();
        when(players.findPrivacy(target)).thenReturn(backend);
        var healthy = new AtomicBoolean(true);
        try (var privacy = new DungeonPrivacy(players, healthy::get, (a, b) -> fail())) {
            var admitted = new ArrayList<CompletionStage<Disclosure>>();
            for (int i = 0; i < 128; i++) admitted.add(privacy.canView(viewer, target));
            assertEquals(
                    Disclosure.UNAVAILABLE,
                    privacy.canView(viewer, target).toCompletableFuture().join());
            verify(players, times(128)).findPrivacy(target);
            healthy.set(false);
            backend.complete(PlayerDataVisibility.PUBLIC);
            for (var result : admitted)
                assertEquals(Disclosure.UNAVAILABLE, result.toCompletableFuture().join());
        }
        when(players.findPrivacy(target)).thenReturn(new CompletableFuture<>());
        try (var privacy = new DungeonPrivacy(players, () -> true, (a, b) -> fail(), 25)) {
            assertEquals(
                    Disclosure.UNAVAILABLE,
                    privacy.canView(viewer, target).toCompletableFuture().get(2, TimeUnit.SECONDS));
        }
    }
}
