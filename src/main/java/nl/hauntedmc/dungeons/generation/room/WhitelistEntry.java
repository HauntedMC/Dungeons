package nl.hauntedmc.dungeons.generation.room;

import java.util.Objects;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.jetbrains.annotations.Nullable;

/**
 * Weighted whitelist/blacklist entry that references a branching room.
 *
 * <p>Entries may also carry an editor icon material, which is why equality falls back to the room
 * or material identity rather than object identity.
 */
@TypeKey(id = "dungeons.serializable.whitelist_entry")
@SerializableAs("dungeons.serializable.whitelist_entry")
public class WhitelistEntry implements ConfigSerializableModel {
    private BranchingDungeon dungeon;
    @PersistedField private String roomName;
    @PersistedField private String materialName;
    @PersistedField private double weight;

        private WhitelistEntry() {}

    /**
     * Creates an entry that points to the given room and inherits its current weight.
     */
    public WhitelistEntry(BranchingRoomDefinition room) {
        this.dungeon = room.getDungeon();
        this.roomName = room.getNamespace();
        this.weight = room.getWeight();
    }

    @Override
        public void postDeserialize() {
        if (this.roomName != null) {
            this.roomName = this.roomName.trim();
            if (this.roomName.isEmpty()) {
                this.roomName = null;
            }
        }

        if (this.materialName != null) {
            this.materialName = this.materialName.trim();
            if (this.materialName.isEmpty()) {
                this.materialName = null;
            }
        }

        this.weight = Math.max(0.0, this.weight);
    }

    @Override
        public boolean isDeserializedValid() {
        return this.roomName != null;
    }

    /**
     * Updates the referenced room namespace.
     */
    public void setRoom(BranchingRoomDefinition room) {
        this.roomName = room.getNamespace();
    }

    /**
     * Sets the icon material used to represent this entry in editors.
     */
    public void setMaterial(Material mat) {
        this.materialName = mat.name();
    }

    /**
     * Sets selection weight for random room picks, clamped to non-negative values.
     */
    public void setWeight(double weight) {
        this.weight = Math.max(0.0, weight);
    }

    /**
     * Returns the configured icon material, if valid.
     */
    @Nullable
    public Material getMaterial() {
        try {
            return Material.valueOf(this.materialName != null ? this.materialName : "STRUCTURE_BLOCK");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * Resolves the referenced room from the stored dungeon context.
     */
    @Nullable
    public BranchingRoomDefinition getRoom() {
        return this.dungeon == null ? null : this.dungeon.getRoom(this.roomName);
    }

    /**
     * Resolves the referenced room using an explicit dungeon context.
     */
    public BranchingRoomDefinition getRoom(BranchingDungeon dungeon) {
        return dungeon.getRoom(this.roomName);
    }

    /**
     * Returns the dungeon used to resolve this entry.
     */
    public BranchingDungeon getDungeon() {
        return this.dungeon;
    }

    /**
     * Sets the dungeon used to resolve this entry.
     */
    public void setDungeon(BranchingDungeon dungeon) {
        this.dungeon = dungeon;
    }

    /**
     * Returns the referenced room namespace.
     */
    public String getRoomName() {
        return this.roomName;
    }

    /**
     * Returns the random-selection weight.
     */
    public double getWeight() {
        return this.weight;
    }

    @Override
        public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof WhitelistEntry other)) {
            return false;
        }

        String thisKey = this.roomName != null ? this.roomName : this.materialName;
        String otherKey = other.roomName != null ? other.roomName : other.materialName;
        return Objects.equals(thisKey, otherKey);
    }

    @Override
        public int hashCode() {
        return Objects.hash(this.roomName != null ? this.roomName : this.materialName);
    }
}
