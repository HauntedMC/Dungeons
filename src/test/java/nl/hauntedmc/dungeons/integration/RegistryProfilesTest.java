package nl.hauntedmc.dungeons.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.*;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class RegistryProfilesTest {
    @Test
    void failedPreferenceQueriesBackOffAndForgetReleasesTheRetry() {
        var data = mock(PlayerData.class);
        var clock = new AtomicLong();
        UUID id = UUID.randomUUID();
        when(data.whenReady(id))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Offline")));
        try (var profiles = new RegistryProfiles(data, clock::get)) {
            for (int i = 0; i < 100; i++) profiles.refresh(id);
            verify(data, times(1)).whenReady(id);
            clock.addAndGet(TimeUnit.SECONDS.toNanos(6));
            profiles.refresh(id);
            verify(data, times(2)).whenReady(id);
            profiles.forget(id);
            profiles.refresh(id);
            verify(data, times(3)).whenReady(id);
        }
    }

    @Test
    void aClosedProfileGenerationUsesClientLocaleWithoutTouchingRegistry() {
        var data = mock(PlayerData.class);
        var player = mock(Player.class);
        when(player.locale()).thenReturn(Locale.forLanguageTag("nl-NL"));
        var profiles = new RegistryProfiles(data);
        profiles.close();
        assertEquals(Language.NL, profiles.language(player));
        verifyNoInteractions(data);
    }

    @Test
    void resolvesOwnerLanguageAsynchronouslyWithoutBlockingAndDropsLateReconnectResults() {
        var data = mock(PlayerData.class);
        when(data.supports(DataRegistryFeature.LANGUAGE)).thenReturn(true);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.locale()).thenReturn(Locale.ENGLISH);
        var identity = new PlayerIdentity(7L, id, "Player");
        when(data.findActiveIdentityCached(id)).thenReturn(Optional.of(identity));
        when(data.whenReady(id))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));
        var language = new CompletableFuture<Optional<PlayerLanguageSettings>>();
        when(data.findLanguageForIdentity(7, id)).thenReturn(language);
        try (var profiles = new RegistryProfiles(data)) {
            assertEquals(Language.EN, profiles.language(player));
            profiles.forget(id);
            language.complete(Optional.of(new PlayerLanguageSettings(7, "NL", "NL")));
            when(data.findLanguageForIdentity(7, id))
                    .thenReturn(
                            CompletableFuture.completedFuture(
                                    Optional.of(new PlayerLanguageSettings(7, "EN", "EN"))));
            profiles.refresh(id);
            assertEquals(Language.EN, profiles.language(player));
            verify(data, times(2)).findLanguageForIdentity(7, id);
        }
    }

    @Test
    void aReplacedCanonicalIdentityNeverInheritsThePreviousAccountsLanguage() {
        var data = mock(PlayerData.class);
        when(data.supports(DataRegistryFeature.LANGUAGE)).thenReturn(true);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.locale()).thenReturn(Locale.ENGLISH);
        when(data.findActiveIdentityCached(id))
                .thenReturn(Optional.of(new PlayerIdentity(7L, id, "Old")));
        when(data.whenReady(id))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                Optional.of(new PlayerIdentity(7L, id, "Old"))));
        when(data.findLanguageForIdentity(7, id))
                .thenReturn(
                        CompletableFuture.completedFuture(
                                Optional.of(new PlayerLanguageSettings(7, "NL", "NL"))));
        var profiles = new RegistryProfiles(data);
        profiles.refresh(id);
        assertEquals(Language.NL, profiles.language(player));
        when(data.findActiveIdentityCached(id))
                .thenReturn(Optional.of(new PlayerIdentity(8L, id, "New")));
        when(data.whenReady(id)).thenReturn(new CompletableFuture<>());
        assertEquals(Language.EN, profiles.language(player));
        profiles.close();
    }

    @Test
    void disabledLanguageSupportUsesClientLocaleWithoutIssuingPreferenceQueries() {
        var data = mock(PlayerData.class);
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.locale()).thenReturn(Locale.forLanguageTag("nl-NL"));
        var identity = new PlayerIdentity(9L, id, "Player");
        when(data.findActiveIdentityCached(id)).thenReturn(Optional.of(identity));
        when(data.whenReady(id))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(identity)));
        try (var profiles = new RegistryProfiles(data)) {
            profiles.refresh(id);
            assertEquals(Language.NL, profiles.language(player));
            assertEquals(Language.NL, profiles.language(player));
            verify(data, never()).findLanguageForIdentity(anyLong(), any());
            verify(data, times(1)).whenReady(id);
        }
    }
}
