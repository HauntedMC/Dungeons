package nl.hauntedmc.dungeons.experience;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.api.DungeonExperienceApi;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.featureframework.hud.HudFrame;
import nl.hauntedmc.featureframework.hud.HudService;
import nl.hauntedmc.featureframework.lifecycle.CleanupSequence;
import org.bukkit.Bukkit;

/** Read-only observer presentation. Observers never enter player progression or completion maps. */
final class ObservationHud implements AutoCloseable {
    private final DungeonsRuntime runtime;
    private final Supplier<HudService> backend;
    private final Function<PlayableInstance, ExperiencePresentation> settings;
    private final Map<UUID, HudService.Lease> leases = new HashMap<>();

    ObservationHud(
            DungeonsRuntime runtime,
            Supplier<HudService> backend,
            Function<PlayableInstance, ExperiencePresentation> settings) {
        this.runtime = runtime;
        this.backend = backend;
        this.settings = settings;
    }

    Optional<DungeonExperienceApi.Snapshot> view(UUID id) {
        var session = runtime.playerSessions().get(id);
        if (session == null
                || !session.isObserving()
                || !(session.getObservingInstance() instanceof PlayableInstance instance))
            return Optional.empty();
        return Optional.of(
                new DungeonExperienceApi.Snapshot(
                        instance.getDungeon().getDungeonId(),
                        "observing",
                        instance.getDungeon().getDisplayNameComponent(),
                        instance.getDifficulty() == null
                                ? ""
                                : instance.getDifficulty().getNamespace(),
                        instance.getTimeElapsed(),
                        instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0
                                ? instance.getTimeLeft()
                                : null,
                        null,
                        List.of()));
    }

    void tick() {
        var seen = new java.util.HashSet<UUID>();
        for (var player : Bukkit.getOnlinePlayers()) {
            var session = runtime.playerSessions().get(player);
            if (session == null
                    || !session.isObserving()
                    || !(session.getObservingInstance() instanceof PlayableInstance instance))
                continue;
            UUID id = player.getUniqueId();
            seen.add(id);
            var policy = settings.apply(instance);
            var hud = backend.get();
            if (hud == null
                    || !policy.flags().get("enabled")
                    || !policy.flags().get("channels.sidebar")) {
                release(id);
                continue;
            }
            var lease = leases.get(id);
            if (lease == null || !lease.active()) {
                lease =
                        hud.claim(
                                "Dungeons:observation",
                                id,
                                HudService.Channel.SIDEBAR,
                                HudService.SESSION,
                                Duration.ofSeconds(10));
                leases.put(id, lease);
            }
            lease.renew(Duration.ofSeconds(10));
            var lines = new ArrayList<Component>();
            lines.add(LangUtils.component("experience.observing", player, false, Map.of()));
            lines.add(Component.text("/dungeon guide"));
            lines.add(Component.text("/dungeon leave"));
            String profile = policy.profile(session.experiencePreference("profile", "inherit"));
            lease.update(
                    profile.equals("immersive")
                            ? null
                            : new HudFrame.Sidebar(
                                    instance.getDungeon().getDisplayNameComponent(), lines));
        }
        for (UUID id : new ArrayList<>(leases.keySet())) if (!seen.contains(id)) release(id);
    }

    void release(UUID id) {
        var lease = leases.remove(id);
        if (lease != null) lease.close();
    }

    @Override
    public void close() {
        var steps = new ArrayList<Runnable>();
        leases.values().forEach(lease -> steps.add(lease::close));
        leases.clear();
        CleanupSequence.run(steps);
    }
}
