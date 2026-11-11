package nl.hauntedmc.dungeons.runtime.staff;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import nl.hauntedmc.dungeons.content.function.CommandFunction;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonElement;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.util.metadata.PersistedFieldSupport;

/** Traverses persisted mechanics, including private wrapper children, with cycle protection. */
public final class ContentReferences {
    private ContentReferences() {}

    public static void dungeon(DungeonDefinition dungeon, Consumer<Object> visitor) {
        if (dungeon.asBranching() != null) {
            for (var room : dungeon.asBranching().getUniqueRooms().values())
                visit(room.getFunctionsMapRelative(), visitor);
        } else visit(dungeon.getFunctions(), visitor);
    }

    public static void visit(Object root, Consumer<Object> visitor) {
        walk(root, visitor, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static void walk(Object value, Consumer<Object> visitor, Set<Object> seen) {
        if (value == null || !seen.add(value)) return;
        if (value instanceof Collection<?> list) {
            for (Object child : list) walk(child, visitor, seen);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Object child : map.values()) walk(child, visitor, seen);
            return;
        }
        if (!(value instanceof DungeonElement)) return;
        visitor.accept(value);
        for (var field : PersistedFieldSupport.persistedFields(value.getClass())) {
            try {
                walk(field.field().get(value), visitor, seen);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("Cannot inspect persisted mechanic graph", failure);
            }
        }
    }

    public static boolean containsScript(DungeonFunction root) {
        var found = new AtomicBoolean();
        visit(
                root,
                value -> {
                    if (value instanceof CommandFunction) found.set(true);
                });
        return found.get();
    }
}
