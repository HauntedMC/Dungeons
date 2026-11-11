package nl.hauntedmc.dungeons.integration;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import org.bukkit.entity.Player;

/** Bounded, identity-bound language snapshots. No SQL or future waits occur on message delivery. */
public final class RegistryProfiles implements AutoCloseable {
    private static final int MAX_PROFILES = 4096;
    private static final long QUERY_TIMEOUT_SECONDS = 5;
    private static final long RETRY_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long PROFILE_NANOS = TimeUnit.SECONDS.toNanos(30);

    private record Profile(long playerId, Language language, long expires) {}

    private final PlayerData players;
    private final Map<UUID, Profile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Object> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> retryAfter = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile boolean closed;

    public RegistryProfiles(PlayerData players) {
        this(players, System::nanoTime);
    }

    RegistryProfiles(PlayerData players, LongSupplier clock) {
        this.players = Objects.requireNonNull(players);
        this.clock = Objects.requireNonNull(clock);
    }

    public synchronized void refresh(UUID id) {
        if (closed
                || pending.size() >= MAX_PROFILES
                || profiles.size() >= MAX_PROFILES && !profiles.containsKey(id)
                || retryAfter.size() >= MAX_PROFILES && !retryAfter.containsKey(id)) return;
        Long retry = retryAfter.get(id);
        if (retry != null && clock.getAsLong() - retry < 0) return;
        Object token = new Object();
        if (pending.putIfAbsent(id, token) != null) return;
        try {
            resolveProfile(id)
                    .toCompletableFuture()
                    .orTimeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .whenComplete((profile, failure) -> finishRefresh(id, token, profile, failure));
        } catch (RuntimeException failure) {
            finishRefresh(id, token, null, failure);
        }
    }

    private CompletionStage<Profile> resolveProfile(UUID id) {
        return players.whenReady(id)
                .thenCompose(
                        identity ->
                                identity.isEmpty()
                                        ? CompletableFuture.completedFuture(
                                                new Profile(
                                                        0, null, clock.getAsLong() + RETRY_NANOS))
                                        : resolveLanguage(id, identity.get()));
    }

    private CompletionStage<Profile> resolveLanguage(UUID id, PlayerIdentity identity) {
        if (!id.equals(identity.uuid())) throw new IllegalStateException("Identity mismatch");
        if (!players.supports(DataRegistryFeature.LANGUAGE)) {
            return CompletableFuture.completedFuture(
                    new Profile(identity.playerId(), null, clock.getAsLong() + PROFILE_NANOS));
        }
        return players.findLanguageForIdentity(identity.playerId(), id)
                .thenApply(
                        settings -> {
                            Language language =
                                    settings.filter(
                                                    value ->
                                                            value.playerId() == identity.playerId())
                                            .flatMap(
                                                    value ->
                                                            Language.fromCode(
                                                                    value.effectiveLanguage()))
                                            .filter(Language::isLocalizable)
                                            .orElse(null);
                            return new Profile(
                                    identity.playerId(),
                                    language,
                                    clock.getAsLong() + PROFILE_NANOS);
                        });
    }

    private synchronized void finishRefresh(
            UUID id, Object token, Profile profile, Throwable failure) {
        if (!pending.remove(id, token) || closed) return;
        if (failure == null && profile != null) {
            profiles.put(id, profile);
            retryAfter.remove(id);
        } else retryAfter.put(id, clock.getAsLong() + RETRY_NANOS);
    }

    public Language language(Player player) {
        if (closed) return clientLanguage(player);
        UUID id = player.getUniqueId();
        Profile profile = profiles.get(id);
        Optional<PlayerIdentity> identity;
        try {
            identity = players.findActiveIdentityCached(id);
        } catch (RuntimeException unavailable) {
            return clientLanguage(player);
        }
        if (profile != null
                && (identity.isEmpty()
                        ? profile.playerId() != 0
                        : !id.equals(identity.get().uuid())
                                || identity.get().playerId() != profile.playerId())) {
            profiles.remove(id, profile);
            profile = null;
        }
        if (profile == null || clock.getAsLong() - profile.expires() >= 0) refresh(id);
        if (profile != null && profile.language() != null) return profile.language();
        return clientLanguage(player);
    }

    private static Language clientLanguage(Player player) {
        var locale = player.locale();
        return locale == null
                ? Language.EN
                : Language.fromCode(locale.getLanguage())
                        .filter(Language::isLocalizable)
                        .orElse(Language.EN);
    }

    public synchronized void forget(UUID id) {
        pending.remove(id);
        profiles.remove(id);
        retryAfter.remove(id);
    }

    @Override
    public synchronized void close() {
        closed = true;
        pending.clear();
        profiles.clear();
        retryAfter.clear();
    }
}
