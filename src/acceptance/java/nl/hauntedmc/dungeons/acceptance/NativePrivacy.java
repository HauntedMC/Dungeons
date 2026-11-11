package nl.hauntedmc.dungeons.acceptance;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import nl.hauntedmc.dataregistry.api.player.PlayerDataVisibility;
import nl.hauntedmc.dungeons.api.DungeonsApi;
import org.bukkit.Bukkit;

/** Exercise the real DataRegistry privacy repository through the packaged Dungeons service. */
final class NativePrivacy {
    private NativePrivacy() {}

    static CompletionStage<Void> run(AcceptancePlugin host) throws Exception {
        UUID publicPlayer = UUID.randomUUID(),
                privatePlayer = UUID.randomUUID(),
                friendsPlayer = UUID.randomUUID();
        for (UUID id : List.of(publicPlayer, privatePlayer, friendsPlayer)) host.seedPlayer(id);
        var players = host.runtime().database().database().players();
        var api = Bukkit.getServicesManager().load(DungeonsApi.class);
        return players.savePrivacy(privatePlayer, PlayerDataVisibility.PRIVATE)
                .thenCompose(
                        saved -> {
                            AcceptancePlugin.require(
                                    saved, "Could not save native PRIVATE preference");
                            return players.savePrivacy(friendsPlayer, PlayerDataVisibility.FRIENDS);
                        })
                .thenCompose(
                        saved -> {
                            AcceptancePlugin.require(
                                    saved, "Could not save native FRIENDS preference");
                            return api.canViewPlayerData(publicPlayer, privatePlayer);
                        })
                .thenCompose(
                        decision -> {
                            AcceptancePlugin.require(
                                    decision == DungeonsApi.Disclosure.DENY,
                                    "PRIVATE data became visible");
                            return api.canViewPlayerData(privatePlayer, privatePlayer);
                        })
                .thenCompose(
                        decision -> {
                            AcceptancePlugin.require(
                                    decision == DungeonsApi.Disclosure.ALLOW,
                                    "Self disclosure was rejected");
                            return api.canViewPlayerData(publicPlayer, friendsPlayer);
                        })
                .thenCompose(
                        decision -> {
                            AcceptancePlugin.require(
                                    decision == DungeonsApi.Disclosure.UNAVAILABLE,
                                    "Missing ServerFeatures granted FRIENDS access");
                            return players.findIdentity(publicPlayer);
                        })
                .thenCompose(
                        identity -> {
                            long publicId = identity.orElseThrow().playerId();
                            return players.findIdentity(privatePlayer)
                                    .thenCompose(
                                            hidden ->
                                                    players.findIdentity(friendsPlayer)
                                                            .thenCompose(
                                                                    friend ->
                                                                            api.publicPlayerIds(
                                                                                    List.of(
                                                                                            publicId,
                                                                                            hidden.orElseThrow()
                                                                                                    .playerId(),
                                                                                            friend.orElseThrow()
                                                                                                    .playerId()))))
                                    .thenAccept(
                                            visible ->
                                                    AcceptancePlugin.require(
                                                            visible.equals(Set.of(publicId)),
                                                            "Public candidates included non-public players"));
                        })
                .thenCompose(
                        ignored -> players.savePrivacy(publicPlayer, PlayerDataVisibility.PRIVATE))
                .thenCompose(saved -> api.canViewPlayerData(privatePlayer, publicPlayer))
                .thenAccept(
                        decision ->
                                AcceptancePlugin.require(
                                        decision == DungeonsApi.Disclosure.DENY,
                                        "A cached grant survived a privacy change"));
    }
}
