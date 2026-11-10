package nl.hauntedmc.dungeons.integration;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dataregistry.api.player.PlayerDataVisibility;
import nl.hauntedmc.dungeons.api.DungeonsApi.Disclosure;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Live disclosure policy. Never cache grants or duplicate the proxy's friendship authority. */
public final class DungeonPrivacy implements AutoCloseable {
    private final PlayerData players;
    private final BooleanSupplier available;
    private final BiFunction<UUID, UUID, CompletionStage<Disclosure>> friends;
    private final Set<CompletableFuture<?>> pending = new HashSet<>();
    private final long timeoutMillis;
    private volatile boolean closed;

    public DungeonPrivacy(
            PlayerData players,
            BooleanSupplier available,
            BiFunction<UUID, UUID, CompletionStage<Disclosure>> friends) {
        this(players, available, friends, 5_000);
    }

    DungeonPrivacy(
            PlayerData players,
            BooleanSupplier available,
            BiFunction<UUID, UUID, CompletionStage<Disclosure>> friends,
            long timeoutMillis) {
        this.players = Objects.requireNonNull(players);
        this.available = Objects.requireNonNull(available);
        this.friends = Objects.requireNonNull(friends);
        if (timeoutMillis <= 0)
            throw new IllegalArgumentException("Privacy timeout must be positive");
        this.timeoutMillis = timeoutMillis;
    }

    public CompletionStage<Disclosure> canView(UUID viewer, UUID target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        return query(
                        () -> {
                            if (viewer.equals(target))
                                return CompletableFuture.completedFuture(Disclosure.ALLOW);
                            requirePrivacy();
                            return players.findPrivacy(target)
                                    .thenCompose(
                                            visibility ->
                                                    switch (visibility) {
                                                        case PUBLIC ->
                                                                CompletableFuture.completedFuture(
                                                                        Disclosure.ALLOW);
                                                        case PRIVATE ->
                                                                CompletableFuture.completedFuture(
                                                                        Disclosure.DENY);
                                                        case FRIENDS ->
                                                                friends.apply(viewer, target);
                                                    });
                        })
                .exceptionally(failure -> Disclosure.UNAVAILABLE);
    }

    public CompletionStage<Set<Long>> publicPlayerIds(Collection<Long> candidates) {
        var ids = new LinkedHashSet<>(Objects.requireNonNull(candidates, "candidates"));
        if (ids.size() > 512 || ids.stream().anyMatch(id -> id == null || id <= 0))
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Expected at most 512 positive canonical player IDs"));
        return query(
                () -> {
                    requirePrivacy();
                    if (ids.isEmpty()) return CompletableFuture.completedFuture(Set.of());
                    return players.findPrivacy(ids)
                            .thenApply(
                                    settings -> {
                                        Objects.requireNonNull(
                                                settings,
                                                "DataRegistry returned null privacy settings");
                                        var visible = new LinkedHashSet<Long>();
                                        for (Long id : ids) {
                                            // The batch API promises explicit defaults; incomplete
                                            // replies cannot grant access.
                                            if (!settings.containsKey(id)
                                                    || settings.get(id) == null)
                                                throw new IllegalStateException(
                                                        "DataRegistry returned incomplete privacy"
                                                            + " settings");
                                            if (settings.get(id) == PlayerDataVisibility.PUBLIC)
                                                visible.add(id);
                                        }
                                        return Set.copyOf(visible);
                                    });
                });
    }

    private void requirePrivacy() {
        if (!players.supports(DataRegistryFeature.PRIVACY))
            throw new IllegalStateException("DataRegistry privacy is unavailable");
    }

    private <T> CompletableFuture<T> query(Supplier<CompletionStage<T>> operation) {
        var result = new CompletableFuture<T>();
        synchronized (pending) {
            if (closed || !available.getAsBoolean() || pending.size() >= 128)
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Dungeon privacy is unavailable"));
            pending.add(result);
        }
        result.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .whenComplete(
                        (value, failure) -> {
                            synchronized (pending) {
                                pending.remove(result);
                            }
                        });
        try {
            Objects.requireNonNull(operation.get(), "Privacy operation returned null")
                    .whenComplete(
                            (value, failure) -> {
                                if (closed || !available.getAsBoolean())
                                    result.completeExceptionally(
                                            new IllegalStateException(
                                                    "Dungeon privacy has stopped"));
                                else if (failure != null) result.completeExceptionally(failure);
                                else if (value == null)
                                    result.completeExceptionally(
                                            new IllegalStateException(
                                                    "Privacy operation returned no result"));
                                else result.complete(value);
                            });
        } catch (RuntimeException | LinkageError failure) {
            result.completeExceptionally(failure);
        }
        // Callers may cancel their observation without releasing our bound on active work.
        return result.copy();
    }

    @Override
    public void close() {
        List<CompletableFuture<?>> active;
        synchronized (pending) {
            closed = true;
            active = List.copyOf(pending);
        }
        for (var result : active)
            result.completeExceptionally(new IllegalStateException("Dungeon privacy has stopped"));
    }
}
