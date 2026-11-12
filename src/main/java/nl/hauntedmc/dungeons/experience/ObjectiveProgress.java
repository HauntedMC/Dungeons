package nl.hauntedmc.dungeons.experience;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Pure objective state; scope and ordered stages are independent of presentation. */
public final class ObjectiveProgress {
    public record Change(String kind, String id, UUID player, int value) {}

    public record View(
            String id,
            String title,
            int progress,
            int target,
            boolean completed,
            boolean optional) {}

    private static final UUID RUN = new UUID(0, 0);
    private final ExperienceDefinition definition;
    private final Consumer<Change> changes;
    private final Map<UUID, Map<String, Integer>> counts = new HashMap<>();
    private final Map<UUID, Set<String>> revealed = new HashMap<>();
    private final Map<UUID, Set<String>> transitions = new HashMap<>();
    private final Map<UUID, Set<String>> events = new HashMap<>();
    private final Map<String, Integer> capturedTargets = new HashMap<>();
    private final Map<UUID, Set<String>> activated = new HashMap<>();
    private final Set<String> nonGating = new HashSet<>();

    public ObjectiveProgress(ExperienceDefinition definition, Consumer<Change> changes) {
        this.definition = definition;
        this.changes = changes;
        for (var track : definition.tracks().values())
            if (track.optional())
                for (var stage : track.stages()) nonGating.addAll(stage.objectives());
    }

    private UUID owner(ExperienceDefinition.Objective goal, UUID player) {
        return goal.scope() == ExperienceDefinition.Scope.RUN ? RUN : player;
    }

    public int count(String id, UUID player) {
        var goal = definition.objectives().get(id);
        return goal == null
                ? 0
                : counts.getOrDefault(owner(goal, player), Map.of()).getOrDefault(id, 0);
    }

    public int target(String id) {
        var goal = definition.objectives().get(id);
        return capturedTargets.getOrDefault(id, goal.target());
    }

    public void capturedTarget(String id, int target, UUID player) {
        capturedTargets.putIfAbsent(id, Math.max(0, target));
        if (target == 0 && active(id, player)) completeGoal(id, player);
    }

    public void begin(UUID player) {
        for (var track : definition.tracks().values()) {
            if (player.equals(RUN) && track.scope() == ExperienceDefinition.Scope.PLAYER) continue;
            var stage = currentStage(track, player);
            if (stage != null)
                transition(
                        "stage-started",
                        track.id() + "/" + stage.id(),
                        track.scope() == ExperienceDefinition.Scope.RUN ? RUN : player,
                        0);
        }
    }

    public boolean completed(String id, UUID player) {
        return count(id, player) >= target(id);
    }

    public boolean active(String id, UUID player) {
        if (!definition.objectives().containsKey(id)) return false;
        for (var track : definition.tracks().values()) {
            for (var stage : track.stages()) {
                if (stage.objectives().contains(id)) return currentStage(track, player) == stage;
            }
        }
        return true;
    }

    public ExperienceDefinition.Stage currentStage(ExperienceDefinition.Track track, UUID player) {
        UUID owner = track.scope() == ExperienceDefinition.Scope.RUN ? RUN : player;
        if (!track.autoStart() && !activated.getOrDefault(owner, Set.of()).contains(track.id()))
            return null;
        return nextStage(track, player);
    }

    private ExperienceDefinition.Stage nextStage(ExperienceDefinition.Track track, UUID player) {
        for (var stage : track.stages())
            if (!stage.objectives().stream()
                    .filter(id -> !definition.objectives().get(id).optional())
                    .allMatch(id -> completed(id, player))) return stage;
        return null;
    }

    /** Explicitly activate an unlocked stage without granting or skipping previous objectives. */
    public void activateStage(String qualifiedId, UUID player) {
        String[] parts = qualifiedId.split("/", -1);
        var track = parts.length == 2 ? definition.tracks().get(parts[0]) : null;
        if (track == null)
            throw new IllegalArgumentException("Use track/stage for stage activation");
        var stage = nextStage(track, player);
        if (stage == null) return;
        if (!stage.id().equals(parts[1]))
            throw new IllegalArgumentException(
                    "Previous required objectives must be completed first");
        UUID owner = track.scope() == ExperienceDefinition.Scope.RUN ? RUN : player;
        activated.computeIfAbsent(owner, ignored -> new HashSet<>()).add(track.id());
        transition("stage-started", qualifiedId, owner, 0);
    }

    public List<String> savedReveals(UUID player) {
        return revealed.getOrDefault(player, Set.of()).stream()
                .filter(id -> definition.objectives().get(id).persistent())
                .sorted()
                .toList();
    }

    public List<String> savedTracks(UUID player) {
        return activated.getOrDefault(player, Set.of()).stream()
                .filter(
                        id ->
                                definition.tracks().get(id).stages().stream()
                                        .flatMap(stage -> stage.objectives().stream())
                                        .anyMatch(
                                                goal ->
                                                        definition
                                                                .objectives()
                                                                .get(goal)
                                                                .persistent()))
                .sorted()
                .toList();
    }

    public void restoreMetadata(UUID player, List<String> reveals, List<String> tracks) {
        for (String id : reveals) {
            var goal = definition.objectives().get(id);
            if (goal != null && goal.persistent())
                revealed.computeIfAbsent(player, ignored -> new HashSet<>()).add(id);
        }
        for (String id : tracks) {
            var track = definition.tracks().get(id);
            if (track != null && track.scope() == ExperienceDefinition.Scope.PLAYER)
                activated.computeIfAbsent(player, ignored -> new HashSet<>()).add(id);
        }
    }

    public void advance(String id, UUID player, int amount, String eventId) {
        var goal = definition.objectives().get(id);
        if (goal == null
                || player == null
                || amount <= 0
                || !active(id, player)
                || completed(id, player)) return;
        UUID owner = owner(goal, player);
        if (eventId != null
                && !events.computeIfAbsent(owner, ignored -> new HashSet<>())
                        .add(id + ":" + eventId)) return;
        Map<String, Integer> state = counts.computeIfAbsent(owner, ignored -> new HashMap<>());
        state.put(id, (int) Math.min(target(id), (long) count(id, player) + amount));
        changes.accept(
                new Change(
                        "objective-progress",
                        id,
                        owner.equals(RUN) ? null : owner,
                        count(id, player)));
        if (completed(id, player)) completeGoal(id, player);
    }

    private void completeGoal(String id, UUID player) {
        UUID owner = owner(definition.objectives().get(id), player);
        transition("objective-completed", id, owner, count(id, player));
        for (var track : definition.tracks().values())
            for (var stage : track.stages()) {
                if (stage.objectives().contains(id)
                        && stage.objectives().stream()
                                .filter(key -> !definition.objectives().get(key).optional())
                                .allMatch(key -> completed(key, player))) {
                    transition("stage-completed", track.id() + "/" + stage.id(), owner, 0);
                    var next = currentStage(track, player);
                    if (next != null)
                        transition("stage-started", track.id() + "/" + next.id(), owner, 0);
                    else transition("track-completed", track.id(), owner, 0);
                }
            }
    }

    private void transition(String kind, String id, UUID owner, int value) {
        if (transitions.computeIfAbsent(owner, ignored -> new HashSet<>()).add(kind + ":" + id))
            changes.accept(new Change(kind, id, owner.equals(RUN) ? null : owner, value));
    }

    public void reveal(String id, UUID player) {
        var goal = definition.objectives().get(id);
        if (goal != null
                && goal.hidden()
                && revealed.computeIfAbsent(owner(goal, player), ignored -> new HashSet<>())
                        .add(id))
            transition("objective-revealed", id, owner(goal, player), count(id, player));
    }

    public boolean visible(String id, UUID player) {
        var goal = definition.objectives().get(id);
        return !goal.hidden() || revealed.getOrDefault(owner(goal, player), Set.of()).contains(id);
    }

    public List<View> views(UUID player) {
        var result = new ArrayList<View>();
        for (var goal : definition.objectives().values())
            if (visible(goal.id(), player) && active(goal.id(), player))
                result.add(
                        new View(
                                goal.id(),
                                goal.title(),
                                count(goal.id(), player),
                                target(goal.id()),
                                completed(goal.id(), player),
                                goal.optional()));
        return List.copyOf(result);
    }

    public boolean allRequiredCompleted(UUID player) {
        return definition.objectives().values().stream()
                        .anyMatch(goal -> !goal.optional() && !nonGating.contains(goal.id()))
                && definition.objectives().values().stream()
                        .filter(goal -> !goal.optional() && !nonGating.contains(goal.id()))
                        .allMatch(goal -> completed(goal.id(), player));
    }

    public Map<String, Integer> save(UUID player) {
        var result = new LinkedHashMap<String, Integer>();
        for (var goal : definition.objectives().values())
            if (goal.persistent()) result.put(goal.id(), count(goal.id(), player));
        return result;
    }

    public void clearPlayer(UUID player) {
        activated.remove(player);
        counts.remove(player);
        revealed.remove(player);
        transitions.remove(player);
        events.remove(player);
    }

    public void restore(UUID player, Map<String, Integer> saved) {
        for (var entry : saved.entrySet()) {
            var goal = definition.objectives().get(entry.getKey());
            if (goal != null && goal.persistent())
                counts.computeIfAbsent(player, ignored -> new HashMap<>())
                        .put(goal.id(), Math.clamp(entry.getValue(), 0, goal.target()));
        }
    }
}
