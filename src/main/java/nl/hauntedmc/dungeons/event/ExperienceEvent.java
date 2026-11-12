package nl.hauntedmc.dungeons.event;

import java.util.UUID;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;

/** A successful objective/stage/encounter transition, scoped to a run or individual participant. */
public final class ExperienceEvent extends DungeonEvent {
    private final String kind;
    private final String id;
    private final UUID player;
    private final int value;

    public ExperienceEvent(
            PlayableInstance instance, String kind, String id, UUID player, int value) {
        super(instance);
        this.kind = kind;
        this.id = id;
        this.player = player;
        this.value = value;
    }

    public String kind() {
        return kind;
    }

    public String id() {
        return id;
    }

    public UUID player() {
        return player;
    }

    public int value() {
        return value;
    }
}
