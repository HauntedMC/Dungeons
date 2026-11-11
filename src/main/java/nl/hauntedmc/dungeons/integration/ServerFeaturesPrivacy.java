package nl.hauntedmc.dungeons.integration;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import nl.hauntedmc.dungeons.api.DungeonsApi.Disclosure;
import nl.hauntedmc.serverfeatures.api.ServerFeaturesApi;
import nl.hauntedmc.serverfeatures.api.capability.privacy.PlayerPrivacyApi;
import org.bukkit.Server;

/** Loaded only when ServerFeatures is installed. Resolve the live capability on every operation. */
final class ServerFeaturesPrivacy {
    private ServerFeaturesPrivacy() {}

    static CompletionStage<Disclosure> friends(Server server, UUID viewer, UUID target) {
        var api = server.getServicesManager().load(ServerFeaturesApi.class);
        var capability =
                api == null
                        ? null
                        : api.capabilities().reference(PlayerPrivacyApi.class).get().orElse(null);
        if (capability == null) return CompletableFuture.completedFuture(Disclosure.UNAVAILABLE);
        return capability
                .canView(viewer, target)
                .thenApply(
                        decision ->
                                switch (decision) {
                                    case ALLOW -> Disclosure.ALLOW;
                                    case DENY -> Disclosure.DENY;
                                    case UNAVAILABLE -> Disclosure.UNAVAILABLE;
                                });
    }
}
