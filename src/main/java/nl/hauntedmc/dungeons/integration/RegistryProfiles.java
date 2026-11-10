package nl.hauntedmc.dungeons.integration;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Bounded, identity-bound language snapshots. No SQL or future waits occur on message delivery. */
public final class RegistryProfiles implements AutoCloseable {
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
                || pending.size() >= 4096
                || profiles.size() >= 4096 && !profiles.containsKey(id)
                || retryAfter.size() >= 4096 && !retryAfter.containsKey(id)) return;
        Long retry = retryAfter.get(id);
        if (retry != null && clock.getAsLong() - retry < 0) return;
        Object token = new Object();
        if (pending.putIfAbsent(id, token) != null) return;
        try {
            players.whenReady(id)
                    .thenCompose(
                            identity -> {
                                if (identity.isEmpty())
                                    return CompletableFuture.<Profile>completedFuture(
                                            new Profile(
                                                    0,
                                                    null,
                                                    clock.getAsLong()
                                                            + TimeUnit.SECONDS.toNanos(5)));
                                PlayerIdentity current = identity.get();
                                if (!id.equals(current.uuid()))
                                    throw new IllegalStateException("Identity mismatch");
                                if (!players.supports(DataRegistryFeature.LANGUAGE))
                                    return CompletableFuture.completedFuture(
                                            new Profile(
                                                    current.playerId(),
                                                    null,
                                                    clock.getAsLong()
                                                            + TimeUnit.SECONDS.toNanos(30)));
                                return players.findLanguageForIdentity(current.playerId(), id)
                                        .thenApply(
                                                language ->
                                                        new Profile(
                                                                current.playerId(),
                                                                language.filter(
                                                                                value ->
                                                                                        value
                                                                                                        .playerId()
                                                                                                == current
                                                                                                        .playerId())
                                                                        .map(
                                                                                value ->
                                                                                        Language
                                                                                                .fromCode(
                                                                                                        value
                                                                                                                .effectiveLanguage())
                                                                                                .filter(
                                                                                                        Language
                                                                                                                ::isLocalizable)
                                                                                                .orElse(
                                                                                                        null))
                                                                        .orElse(null),
                                                                clock.getAsLong()
                                                                        + TimeUnit.SECONDS.toNanos(
                                                                                30)));
                            })
                    .toCompletableFuture()
                    .orTimeout(5, TimeUnit.SECONDS)
                    .whenComplete(
                            (profile, failure) -> {
                                synchronized (this) {
                                    if (pending.remove(id, token) && !closed) {
                                        if (failure == null && profile != null) {
                                            profiles.put(id, profile);
                                            retryAfter.remove(id);
                                        } else
                                            retryAfter.put(
                                                    id,
                                                    clock.getAsLong()
                                                            + TimeUnit.SECONDS.toNanos(5));
                                    }
                                }
                            });
        } catch (RuntimeException failure) {
            if (pending.remove(id, token))
                retryAfter.put(id, clock.getAsLong() + TimeUnit.SECONDS.toNanos(5));
        }
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
