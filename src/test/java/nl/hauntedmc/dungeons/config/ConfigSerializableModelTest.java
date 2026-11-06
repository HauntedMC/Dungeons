package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import org.junit.jupiter.api.Test;

class ConfigSerializableModelTest {
    @Test
    void restoresConcreteSubtypeThroughBaseTypeAndNestedCollections() {
        ConfigSerializableModel.pluginSerializables.add(Child.class);
        try {
            Map<String, Object> child = Map.of("TYPE--", "test.child", "value", 7L);
            Base restored = ConfigSerializableModel.deserialize(Base.class, child);
            assertEquals(7, assertInstanceOf(Child.class, restored).value);

            Container container =
                    ConfigSerializableModel.deserialize(
                            Container.class,
                            Map.of(
                                    "single",
                                    child,
                                    "children",
                                    List.of(child),
                                    "named",
                                    Map.of("first", child)));
            assertEquals(7, assertInstanceOf(Child.class, container.single).value);
            assertEquals(7, assertInstanceOf(Child.class, container.children.getFirst()).value);
            assertEquals(7, assertInstanceOf(Child.class, container.named.get("first")).value);

            Container roundTrip =
                    ConfigSerializableModel.deserialize(Container.class, container.serialize());
            assertEquals(7, assertInstanceOf(Child.class, roundTrip.children.getFirst()).value);
        } finally {
            ConfigSerializableModel.pluginSerializables.remove(Child.class);
        }
    }

    abstract static class Base implements ConfigSerializableModel {}

    @TypeKey(id = "test.child")
    static class Child extends Base {
        @PersistedField int value;
    }

    static class Container implements ConfigSerializableModel {
        @PersistedField Base single;
        @PersistedField List<Base> children;
        @PersistedField Map<String, Base> named;
    }
}
