package nl.hauntedmc.dungeons.content.variable;

import java.util.Collections;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/**
 * Supported mutations for instance variables.
 */
@TypeKey(id = "dungeons.variable.edit_mode")
@SerializableAs("dungeons.variable.edit_mode")
public enum VariableEditMode implements ConfigurationSerializable {
    SET,
    ADD,
    SUBTRACT;

    /**
     * Creates a value from index.
     */
    public static VariableEditMode fromIndex(int index) {
                return switch (index) {
            case 1 -> ADD;
            case 2 -> SUBTRACT;
            default -> SET;
        };
    }

    @NotNull public Map<String, Object> serialize() {
        return Collections.singletonMap("value", this.name());
    }

    /**
     * Performs deserialize.
     */
    public static VariableEditMode deserialize(Map<String, Object> map) {
                return valueOf((String) map.get("value"));
    }
}
