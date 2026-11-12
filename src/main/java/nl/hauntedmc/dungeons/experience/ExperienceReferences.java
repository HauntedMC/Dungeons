package nl.hauntedmc.dungeons.experience;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonElement;
import nl.hauntedmc.dungeons.runtime.staff.ContentReferences;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;

/** Checks graph references without creating worlds or deserializing arbitrary snapshot objects. */
public final class ExperienceReferences {
    private ExperienceReferences() {}

    public static void dungeon(DungeonDefinition dungeon, ExperienceDefinition definition) {
        if (!definition.hasMechanics()) return;
        ContentReferences.dungeon(
                dungeon,
                value -> {
                    if (value instanceof DungeonElement element
                            && value.getClass()
                                    .getSimpleName()
                                    .matches(
                                            "ExperienceFunction|ExperienceTrigger|ObjectiveCondition")) {
                        var fields = new LinkedHashMap<String, Object>(element.serialize());
                        fields.put("==", TypeMetadataSupport.requiredId(value.getClass()));
                        yaml(fields, definition);
                    }
                });
    }

    public static void yaml(Object value, ExperienceDefinition definition) {
        if (!definition.hasMechanics()) return;
        if (value instanceof Collection<?> list) {
            list.forEach(child -> yaml(child, definition));
            return;
        }
        if (!(value instanceof Map<?, ?> map)) return;
        String type = String.valueOf(map.get("=="));
        String id = map.get("id") instanceof String text ? text : "objective";
        if (type.equals("dungeons.function.experience")) {
            String operation = map.get("operation") instanceof String text ? text : "signal";
            boolean valid =
                    switch (operation) {
                        case "advance", "reveal" -> definition.objectives().containsKey(id);
                        case "signal" ->
                                definition.objectives().values().stream()
                                        .anyMatch(
                                                goal ->
                                                        goal.source()
                                                                        == ExperienceDefinition
                                                                                .Source.SIGNAL
                                                                && goal.selector().equals(id));
                        case "start-encounter", "reset-encounter" ->
                                definition.encounters().containsKey(id);
                        case "activate-stage" -> stage(definition, id);
                        default -> false;
                    };
            if (!valid)
                throw new IllegalArgumentException(
                        "Broken experience function reference: " + operation + " " + id);
        } else if (type.equals("dungeons.condition.objective")
                && !definition.objectives().containsKey(id))
            throw new IllegalArgumentException("Unknown objective condition: " + id);
        else if (type.equals("dungeons.trigger.experience") && !id.equals("*")) {
            String kind = map.get("kind") instanceof String text ? text : "objective-completed";
            boolean valid =
                    kind.startsWith("objective-")
                            ? definition.objectives().containsKey(id)
                            : kind.startsWith("stage-")
                                    ? stage(definition, id)
                                    : kind.startsWith("track-")
                                            ? definition.tracks().containsKey(id)
                                            : kind.startsWith("encounter-")
                                                            || kind.equals("wave-started")
                                                    ? definition.encounters().containsKey(id)
                                                    : true;
            if (!valid)
                throw new IllegalArgumentException(
                        "Broken experience event reference: " + kind + " " + id);
        }
        map.values().forEach(child -> yaml(child, definition));
    }

    private static boolean stage(ExperienceDefinition definition, String id) {
        String[] parts = id.split("/", -1);
        if (parts.length != 2) return false;
        var track = definition.tracks().get(parts[0]);
        return track != null
                && track.stages().stream().anyMatch(stage -> stage.id().equals(parts[1]));
    }
}
