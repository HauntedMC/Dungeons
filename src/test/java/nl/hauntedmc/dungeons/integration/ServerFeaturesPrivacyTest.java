package nl.hauntedmc.dungeons.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import nl.hauntedmc.dungeons.api.DungeonsApi.Disclosure;
import nl.hauntedmc.featureframework.api.service.CapabilityRef;
import nl.hauntedmc.featureframework.api.service.CapabilityRegistry;
import nl.hauntedmc.serverfeatures.api.ServerFeaturesApi;
import nl.hauntedmc.serverfeatures.api.capability.privacy.PlayerPrivacyApi;
import nl.hauntedmc.serverfeatures.api.capability.privacy.PrivacyDecision;
import org.bukkit.Server;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;

class ServerFeaturesPrivacyTest {
    @Test
    void resolvesCurrentCapabilityForEveryOperationAndHandlesFeatureRemoval() {
        var server = mock(Server.class);
        var services = mock(ServicesManager.class);
        var api = mock(ServerFeaturesApi.class);
        var registry = mock(CapabilityRegistry.class);
        @SuppressWarnings("unchecked")
        var reference = (CapabilityRef<PlayerPrivacyApi>) mock(CapabilityRef.class);
        var privacy = mock(PlayerPrivacyApi.class);
        UUID viewer = UUID.randomUUID(), target = UUID.randomUUID();
        when(server.getServicesManager()).thenReturn(services);
        when(services.load(ServerFeaturesApi.class)).thenReturn(api);
        when(api.capabilities()).thenReturn(registry);
        when(registry.reference(PlayerPrivacyApi.class)).thenReturn(reference);
        when(reference.get()).thenReturn(Optional.of(privacy));
        when(privacy.canView(viewer, target))
                .thenReturn(CompletableFuture.completedFuture(PrivacyDecision.ALLOW));
        assertEquals(
                Disclosure.ALLOW,
                ServerFeaturesPrivacy.friends(server, viewer, target).toCompletableFuture().join());
        when(reference.get()).thenReturn(Optional.empty());
        assertEquals(
                Disclosure.UNAVAILABLE,
                ServerFeaturesPrivacy.friends(server, viewer, target).toCompletableFuture().join());
        when(services.load(ServerFeaturesApi.class)).thenReturn(null);
        assertEquals(
                Disclosure.UNAVAILABLE,
                ServerFeaturesPrivacy.friends(server, viewer, target).toCompletableFuture().join());
        verify(privacy, times(1)).canView(viewer, target);
    }
}
