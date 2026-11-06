package nl.hauntedmc.dungeons.support;

import java.util.HashMap;
import java.util.Map;

/** Restores third-party/global registries that expose registration but no snapshot API. */
public final class StaticMapSnapshot implements AutoCloseable {
    private final Map<Object, Object> registry;
    private final Map<Object, Object> previous;

    @SuppressWarnings("unchecked")
    public StaticMapSnapshot(Class<?> owner, String fieldName) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        registry = (Map<Object, Object>) field.get(null);
        previous = new HashMap<>(registry);
    }

    @Override
    public void close() {
        registry.clear();
        registry.putAll(previous);
    }
}
