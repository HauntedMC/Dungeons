package nl.hauntedmc.dungeons.experience;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import nl.hauntedmc.dungeons.content.function.FinishDungeonFunction;
import nl.hauntedmc.dungeons.content.function.SpawnMobFunction;
import nl.hauntedmc.dungeons.event.ExperienceEvent;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.featureframework.hud.HudFrame;
import nl.hauntedmc.featureframework.lifecycle.CleanupSequence;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/** Per-instance optional mechanics. Created only for dungeons with enabled definitions. */
public final class ExperienceRun implements AutoCloseable {
    private record Tracked(UUID entity, String group) {}

    private static final class EncounterState {
        int wave = -1;
        int generation;
        final List<SpawnMobFunction> spawners = new ArrayList<>();
        int pending;
        int starts;
        int waveStarts;
        String state = "idle";
        final Set<UUID> alive = new HashSet<>();
    }

    private final PlayableInstance instance;
    private final ExperienceService service;
    private final ExperienceDefinition definition;
    private final ObjectiveProgress progress;
    private final Map<UUID, Tracked> entities = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> captured = new HashMap<>();
    private final Map<String, EncounterState> encounters = new LinkedHashMap<>();
    private final List<SpawnMobFunction> spawners = new ArrayList<>();
    private final Set<String> phases = new HashSet<>();
    private final Set<UUID> restored = new HashSet<>();
    private boolean initialized;
    private boolean closed;

    ExperienceRun(PlayableInstance instance, ExperienceService service) {
        this.instance = instance;
        this.service = service;
        this.definition = instance.experienceDefinition();
        progress =
                new ObjectiveProgress(
                        definition,
                        change -> {
                            Bukkit.getPluginManager()
                                    .callEvent(
                                            new ExperienceEvent(
                                                    instance,
                                                    change.kind(),
                                                    change.id(),
                                                    change.player(),
                                                    change.value()));
                            service.changed(instance, change);
                        });
        definition.encounters().keySet().forEach(id -> encounters.put(id, new EncounterState()));
    }

    public ObjectiveProgress progress() {
        return progress;
    }

    public ExperienceDefinition definition() {
        return definition;
    }

    public void restore(nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession session) {
        UUID id = session.getPlayer().getUniqueId();
        if (!restored.add(id)) return;
        String dungeonId = instance.getDungeon().getDungeonId().toString();
        if (session.getExperienceSavePoint(instance.getDungeon().getWorldName(), dungeonId) != null
                && !session.incompatibleExperienceCheckpoint(
                        dungeonId, definition.fingerprint(), difficulty())) {
            progress.restore(
                    id,
                    session.experienceCheckpoint(
                            dungeonId, definition.fingerprint(), difficulty()));
            progress.restoreMetadata(
                    id,
                    session.experienceSavedReveals(dungeonId),
                    session.experienceSavedTracks(dungeonId));
        }
    }

    String difficulty() {
        return instance.getDifficulty() == null ? "" : instance.getDifficulty().getNamespace();
    }

    private Location location(ExperienceDefinition.Point point) {
        return new Location(instance.getInstanceWorld(), point.x(), point.y(), point.z());
    }

    private boolean matches(ExperienceDefinition.Objective goal, Entity entity) {
        boolean selector =
                goal.selector().equalsIgnoreCase("type:" + entity.getType().name())
                        || goal.selector().equalsIgnoreCase(entity.getType().name());
        if (goal.selector().startsWith("group:")) {
            var tracked = entities.get(entity.getUniqueId());
            selector = tracked != null && goal.selector().substring(6).equals(tracked.group);
        }
        return selector
                && (goal.point() == null
                        || entity.getLocation().distanceSquared(location(goal.point()))
                                <= goal.point().radius() * goal.point().radius());
    }

    public void tick() {
        if (closed || !instance.isStarted()) return;
        if (!initialized) {
            initialized = true;
            for (var group : definition.groups().values())
                if (group.existing()) {
                    double radius = group.point().radius();
                    for (Entity entity :
                            instance.getInstanceWorld()
                                    .getNearbyEntities(
                                            location(group.point()), radius, radius, radius))
                        if (entity.getType().name().equals(group.entity()))
                            entities.put(
                                    entity.getUniqueId(),
                                    new Tracked(entity.getUniqueId(), group.id()));
                }
            for (var encounter : definition.encounters().values())
                if (encounter.autoStart()) start(encounter.id());
        }
        progress.begin(new UUID(0, 0));
        var participants = new ArrayList<>(instance.getPlayers());
        for (var session : participants) {
            if (session.getPlayer() == null || !session.getPlayer().isOnline()) continue;
            restore(session);
            UUID player = session.getPlayer().getUniqueId();
            progress.begin(player);
            for (var goal : definition.objectives().values()) {
                if (!progress.active(goal.id(), player) || progress.completed(goal.id(), player))
                    continue;
                if (goal.capture() && !captured.containsKey(goal.id())) {
                    var cohort = new HashSet<UUID>();
                    for (Entity entity : instance.getInstanceWorld().getEntities())
                        if (matches(goal, entity)) cohort.add(entity.getUniqueId());
                    captured.put(goal.id(), cohort);
                    progress.capturedTarget(goal.id(), cohort.size(), player);
                }
                if (!instance.getLivingPlayers().contains(session) || session.getPlayer().isDead())
                    continue;
                if (session.getPlayer().getWorld() != instance.getInstanceWorld()) continue;
                if (goal.source() == ExperienceDefinition.Source.REACH) {
                    boolean reached =
                            goal.point() != null
                                    && session.getPlayer()
                                                    .getLocation()
                                                    .distanceSquared(location(goal.point()))
                                            <= goal.point().radius() * goal.point().radius();
                    if (goal.selector().startsWith("room:")
                            && instance
                                    instanceof
                                    nl.hauntedmc.dungeons.runtime.instance.play.BranchingInstance
                                            branching) {
                        var room = branching.getRoom(session.getPlayer().getLocation());
                        reached =
                                room != null
                                        && room.getSource()
                                                .getOrigin()
                                                .getNamespace()
                                                .equals(goal.selector().substring(5));
                    }
                    if (reached) progress.advance(goal.id(), player, goal.target(), "reach");
                }
                if (goal.source() == ExperienceDefinition.Source.VARIABLE) {
                    double value = instance.getInstanceVariables().getDouble(goal.selector());
                    if (Double.isFinite(value))
                        progress.advance(
                                goal.id(),
                                player,
                                Math.max(
                                        0,
                                        (int) Math.min(Integer.MAX_VALUE, value)
                                                - progress.count(goal.id(), player)),
                                null);
                }
            }
            if (definition.completion().equals("objectives")
                    && progress.allRequiredCompleted(player)
                    && !service.completed(player, instance)) {
                var finish = new FinishDungeonFunction();
                finish.setLeave(false);
                finish.enable(instance, session.getPlayer().getLocation());
                try {
                    finish.runFunction(null, List.of(session));
                } finally {
                    finish.disable();
                }
            }
        }
        for (var entry : encounters.entrySet()) tickEncounter(entry.getKey(), entry.getValue());
        for (var tracked : new ArrayList<>(entities.values())) {
            Entity entity = Bukkit.getEntity(tracked.entity);
            if (entity instanceof LivingEntity living && living.isValid() && !living.isDead()) {
                var group = definition.groups().get(tracked.group);
                for (double phase : group.phases())
                    if (living.getHealth()
                                            / Math.max(
                                                    1,
                                                    nl.hauntedmc.dungeons.util.entity.EntityUtils
                                                            .getMaxHealth(living))
                                    <= phase
                            && phases.add(tracked.entity + ":" + phase))
                        emit("boss-phase", group.id() + ":" + phase, 0);
            }
        }
    }

    public void signal(
            String selector, UUID actor, ExperienceDefinition.Source source, String eventId) {
        if (actor == null) return;
        for (var goal : definition.objectives().values())
            if (goal.source() == source && goal.selector().equals(selector))
                progress.advance(goal.id(), actor, 1, eventId);
    }

    public void death(Entity entity, UUID killer, boolean destroyed) {
        for (var goal : definition.objectives().values()) {
            if (goal.source()
                    != (destroyed
                            ? ExperienceDefinition.Source.DESTROY
                            : ExperienceDefinition.Source.KILL)) continue;
            if (!matches(goal, entity)
                    || goal.capture()
                            && !captured.getOrDefault(goal.id(), Set.of())
                                    .contains(entity.getUniqueId())) continue;
            if (goal.scope() == ExperienceDefinition.Scope.RUN) {
                progress.advance(
                        goal.id(),
                        instance.getPlayers().isEmpty()
                                ? new UUID(0, 0)
                                : instance.getPlayers().getFirst().getPlayer().getUniqueId(),
                        1,
                        entity.getUniqueId().toString());
            } else if (killer != null
                    && instance.getLivingPlayers().stream()
                            .anyMatch(session -> session.getPlayer().getUniqueId().equals(killer)))
                progress.advance(goal.id(), killer, 1, entity.getUniqueId().toString());
        }
        for (var state : encounters.values()) state.alive.remove(entity.getUniqueId());
        entities.remove(entity.getUniqueId());
    }

    public void removed(Entity entity, org.bukkit.event.entity.EntityRemoveEvent.Cause cause) {
        if (closed) return;
        if (cause == org.bukkit.event.entity.EntityRemoveEvent.Cause.UNLOAD
                || cause == org.bukkit.event.entity.EntityRemoveEvent.Cause.PLAYER_QUIT) return;
        if (!(entity instanceof LivingEntity)
                && (cause == org.bukkit.event.entity.EntityRemoveEvent.Cause.EXPLODE
                        || cause == org.bukkit.event.entity.EntityRemoveEvent.Cause.HIT
                        || cause == org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH)) {
            death(entity, null, true);
            return;
        }
        if (cause == org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH) return;
        for (var entry : encounters.entrySet())
            if (entry.getValue().alive.contains(entity.getUniqueId())
                    && entry.getValue().state.equals("active")) {
                entry.getValue().state = "failed";
                entry.getValue().generation++;
                entry.getValue().spawners.forEach(SpawnMobFunction::disable);
                emit("encounter-failed", entry.getKey(), 0);
            }
    }

    public void start(String id) {
        var spec = definition.encounters().get(id);
        if (spec == null) throw new IllegalArgumentException("Unknown encounter: " + id);
        var state = encounters.get(id);
        if (!state.state.equals("idle")) return;
        state.state = "preparing";
        state.starts = instance.getTimeElapsed();
        state.waveStarts = state.starts;
        emit("encounter-started", id, 0);
    }

    public void reset(String id) {
        var state = encounters.get(id);
        if (state == null) throw new IllegalArgumentException("Unknown encounter: " + id);
        state.generation++;
        state.state = "idle";
        state.spawners.forEach(SpawnMobFunction::disable);
        state.spawners.clear();
        for (UUID entity : new HashSet<>(state.alive)) {
            Entity live = Bukkit.getEntity(entity);
            if (live != null) live.remove();
            entities.remove(entity);
        }
        state.alive.clear();
        state.wave = -1;
        state.pending = 0;
        state.state = "idle";
        emit("encounter-reset", id, 0);
    }

    private void tickEncounter(String id, EncounterState state) {
        var spec = definition.encounters().get(id);
        if (state.state.equals("active")) {
            if (spec.timeoutSeconds() > 0
                    && instance.getTimeElapsed() - state.starts >= spec.timeoutSeconds()) {
                state.state = "failed";
                emit("encounter-failed", id, 0);
                return;
            }
            for (UUID entity : state.alive) {
                Entity live = Bukkit.getEntity(entity);
                if (live != null && !live.isValid() && !live.isDead()) {
                    state.state = "failed";
                    emit("encounter-failed", id, 0);
                    return;
                }
            }
            if (state.alive.isEmpty() && state.pending == 0) {
                if (state.wave + 1 >= spec.waves().size()) {
                    state.state = "completed";
                    emit("encounter-completed", id, state.wave + 1);
                } else {
                    state.state = "preparing";
                    state.waveStarts = instance.getTimeElapsed();
                }
            }
        }
        if (state.state.equals("preparing")
                && instance.getTimeElapsed() - state.waveStarts >= spec.preparationSeconds()) {
            state.wave++;
            state.state = "active";
            for (String groupId : spec.waves().get(state.wave)) {
                var group = definition.groups().get(groupId);
                if (group.existing()
                        || instance.getDifficulty() != null
                                && (int)
                                                (group.count()
                                                        * instance.getDifficulty()
                                                                .getMobSpawnScale())
                                        <= 0) {
                    state.state = "failed";
                    emit("encounter-failed", id, 0);
                    return;
                }
                int generation = state.generation;
                var spawner = new SpawnMobFunction();
                spawner.setMob(group.entity());
                spawner.setMaxCount(group.count());
                state.pending++;
                spawner.observeSpawns(
                        entity -> {
                            if (closed
                                    || generation != state.generation
                                    || !state.state.equals("active")) {
                                entity.remove();
                                return;
                            }
                            entities.put(
                                    entity.getUniqueId(),
                                    new Tracked(entity.getUniqueId(), group.id()));
                            state.alive.add(entity.getUniqueId());
                            var plugin = instance.getEnvironment().plugin();
                            var data = entity.getPersistentDataContainer();
                            data.set(
                                    new org.bukkit.NamespacedKey(plugin, "experience_instance"),
                                    org.bukkit.persistence.PersistentDataType.STRING,
                                    instance.getInstanceId().toString());
                            data.set(
                                    new org.bukkit.NamespacedKey(plugin, "experience_encounter"),
                                    org.bukkit.persistence.PersistentDataType.STRING,
                                    id);
                            data.set(
                                    new org.bukkit.NamespacedKey(plugin, "experience_group"),
                                    org.bukkit.persistence.PersistentDataType.STRING,
                                    group.id());
                            data.set(
                                    new org.bukkit.NamespacedKey(plugin, "experience_wave"),
                                    org.bukkit.persistence.PersistentDataType.INTEGER,
                                    state.wave + 1);
                        },
                        () -> {
                            if (generation == state.generation) state.pending--;
                            spawners.remove(spawner);
                            state.spawners.remove(spawner);
                        });
                spawner.enable(instance, location(group.point()));
                spawners.add(spawner);
                state.spawners.add(spawner);
                spawner.runFunction(null, List.of());
            }
            emit("wave-started", id, state.wave + 1);
        }
    }

    public List<HudFrame.Bar> bars() {
        var result = new ArrayList<HudFrame.Bar>();
        for (var tracked : entities.values()) {
            var group = definition.groups().get(tracked.group);
            Entity entity = Bukkit.getEntity(tracked.entity);
            if (entity instanceof org.bukkit.entity.EnderDragon dragon
                    && PlayableInstance.usesNativeDragonBar(dragon)) continue;
            if (!group.boss().isBlank()
                    && entity instanceof LivingEntity living
                    && living.isValid()
                    && !living.isDead()) {
                result.add(
                        new HudFrame.Bar(
                                LangUtils.trusted(group.boss()),
                                (float)
                                        (living.getHealth()
                                                / Math.max(
                                                        1,
                                                        nl.hauntedmc.dungeons.util.entity
                                                                .EntityUtils.getMaxHealth(living))),
                                BossBar.Color.PURPLE,
                                BossBar.Overlay.PROGRESS));
                if (result.size() == 2) return result;
            }
        }
        for (var entry : encounters.entrySet())
            if (entry.getValue().state.equals("active")
                    || entry.getValue().state.equals("preparing")) {
                var spec = definition.encounters().get(entry.getKey());
                result.add(
                        new HudFrame.Bar(
                                LangUtils.trusted(spec.title())
                                        .append(
                                                net.kyori.adventure.text.Component.text(
                                                        " · "
                                                                + Math.max(
                                                                        1,
                                                                        entry.getValue().wave + 1)
                                                                + "/"
                                                                + spec.waves().size())),
                                Math.max(0, entry.getValue().wave) / (float) spec.waves().size(),
                                BossBar.Color.BLUE,
                                BossBar.Overlay.PROGRESS));
                if (result.size() == 2) return result;
            }
        return result;
    }

    private void emit(String kind, String id, int value) {
        Bukkit.getPluginManager().callEvent(new ExperienceEvent(instance, kind, id, null, value));
        service.changed(instance, new ObjectiveProgress.Change(kind, id, null, value));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        var steps = new ArrayList<Runnable>();
        spawners.forEach(spawner -> steps.add(spawner::disable));
        spawners.clear();
        entities.clear();
        encounters.clear();
        captured.clear();
        restored.clear();
        phases.clear();
        CleanupSequence.run(steps);
    }
}
