package nl.hauntedmc.dungeons.model.element;

import java.util.Collections;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.NotNull;

/**
 * Enumerates how a function resolves player targets from a trigger event.
 */
@TypeKey(id = "dungeons.function_target_type")
@SerializableAs("dungeons.function_target_type")
public enum FunctionTargetType implements ConfigurationSerializable {
    NONE(0, "None"),
    PLAYER(1, "Player"),
    TEAM(2, "Team"),
    ROOM(3, "Players in Room");

    private final int index;
    private final String display;

    /**
     * Creates a new function target type instance.
     */
    FunctionTargetType(int index, String display) {
        this.index = index;
        this.display = display;
    }

    /**
     * Resolves the target type by its editor index value.
     */
    public static FunctionTargetType intToTargetType(int index) {
                return switch (index) {
            case 1 -> PLAYER;
            case 2 -> TEAM;
            case 3 -> ROOM;
            default -> NONE;
        };
    }

    /**
     * Serializes this value for Bukkit config persistence.
     */
    @Override
    @NotNull
    public Map<String, Object> serialize() {
        return Collections.singletonMap("value", this.name());
    }

    /**
     * Restores a target type from serialized enum data.
     */
    public static FunctionTargetType deserialize(Map<String, Object> map) {
                return valueOf(String.valueOf(map.get("value")));
    }

    /**
     * Returns the editor index associated with this value.
     */
    public int getIndex() {
        return this.index;
    }

    /**
     * Returns the user-facing display label for editor menus.
     */
    public String getDisplay() {
        return this.display;
    }

    /**
     * Returns whether this mode resolves one or more player sessions.
     */
    public boolean isTargetedAtPlayers() {
        return this == PLAYER || this == TEAM;
    }
}
