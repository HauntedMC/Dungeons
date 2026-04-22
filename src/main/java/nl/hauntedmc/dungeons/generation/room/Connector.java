package nl.hauntedmc.dungeons.generation.room;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.util.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Branching connector describing where one room may attach to another.
 *
 * <p>Connectors store directional location data, per-connector room filters, success chance, and
 * the door metadata that should be created when the connector links to another room.
 */
@TypeKey(id = "dungeons.serializable.connector")
@SerializableAs("dungeons.serializable.connector")
public class Connector implements Cloneable, ConfigSerializableModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(Connector.class);
    BranchingDungeon dungeon;
    @PersistedField private SimpleLocation location;
    @PersistedField private double successChance = 0.5;
    @PersistedField private List<WhitelistEntry> roomBlacklist = new ArrayList<>();
    @PersistedField private List<WhitelistEntry> roomWhitelist = new ArrayList<>();
    @PersistedField private ConnectorDoor door;

        private Connector() {}

    /**
     * Creates a connector at the given local room position/direction.
     */
    public Connector(SimpleLocation location) {
        this.location = location;
        this.door = new ConnectorDoor(this);
    }

    @Override
        public void postDeserialize() {
        this.roomBlacklist = this.roomBlacklist == null ? new ArrayList<>() : this.roomBlacklist;
        this.roomWhitelist = this.roomWhitelist == null ? new ArrayList<>() : this.roomWhitelist;
        this.successChance = Math.max(0.0, Math.min(1.0, this.successChance));

        if (this.door == null) {
            this.door = new ConnectorDoor(this);
        } else if (this.door.getNamespace() == null || this.door.getNamespace().isBlank()) {
            this.door.setNamespace(ConnectorDoor.defaultNamespace(this.location));
        }
    }

    @Override
        public boolean isDeserializedValid() {
        return this.location != null;
    }

        public List<WhitelistEntry> getValidRooms(
            BranchingDungeon dungeon, BranchingRoomDefinition parent) {
        List<WhitelistEntry> rooms = new ArrayList<>();
        if (this.roomWhitelist != null && !this.roomWhitelist.isEmpty()) {
            for (WhitelistEntry entry : this.roomWhitelist) {
                BranchingRoomDefinition validRoom = entry.getRoom(dungeon);
                if (validRoom != null
                        && !validRoom.getConnectors().isEmpty()
                        && validRoom.canGenerate(parent)) {
                    rooms.add(entry);
                }
            }
        } else {
            for (WhitelistEntry parentEntry : parent.getValidRooms()) {
                BranchingRoomDefinition validRoom = parentEntry.getRoom(dungeon);
                if (validRoom != null && validRoom != parent) {
                    rooms.add(parentEntry);
                }
            }
        }

        if (this.roomBlacklist != null && !this.roomBlacklist.isEmpty()) {
            rooms.removeIf(this.roomBlacklist::contains);
        }

        return rooms;
    }

    /**
     * Returns whether this connector explicitly allows the given destination room.
     */
    public boolean canGenerate(BranchingRoomDefinition room) {
        if (room == null) {
            return false;
        }

        if (this.roomWhitelist != null && !this.roomWhitelist.isEmpty()) {
            for (WhitelistEntry entry : this.roomWhitelist) {
                if (entry.getRoomName().equals(room.getNamespace())) {
                    return true;
                }
            }

            return false;
        } else {
            return true;
        }
    }

    /**
     * Sets the owning dungeon context and propagates it to room filters.
     */
    public void setDungeon(BranchingDungeon dungeon) {
        this.dungeon = dungeon;
        if (this.roomWhitelist != null) {
            this.roomWhitelist.forEach(v -> v.setDungeon(dungeon));
        }

        if (this.roomBlacklist != null) {
            this.roomBlacklist.forEach(v -> v.setDungeon(dungeon));
        }
    }

    /**
     * Creates a translated copy of this connector for a placed room instance.
     */
    public Connector copy(Vector offset) {
        SimpleLocation rotatedLoc = this.location.clone();
        rotatedLoc.shift(offset);
        Connector newCon = this.copy(rotatedLoc);
        newCon.setDoor(this.door == null ? new ConnectorDoor(newCon) : this.door.copy(offset));
        return newCon;
    }

    /**
     * Creates a copy at a pre-rotated location.
     */
    public Connector copy(SimpleLocation rotatedLoc) {
        Connector newCon = new Connector(rotatedLoc);
        newCon.setDungeon(this.dungeon);
        newCon.setRoomWhitelist(
                this.roomWhitelist == null ? new ArrayList<>() : new ArrayList<>(this.roomWhitelist));
        newCon.setRoomBlacklist(
                this.roomBlacklist == null ? new ArrayList<>() : new ArrayList<>(this.roomBlacklist));
        newCon.setSuccessChance(this.successChance);
        newCon.setDoor(this.door == null ? new ConnectorDoor(newCon) : this.door.clone());
        return newCon;
    }

    @Override
        public Connector clone() {
        try {
            Connector clone = (Connector) super.clone();
            clone.location = this.location.clone();
            clone.roomBlacklist =
                    this.roomBlacklist == null ? new ArrayList<>() : new ArrayList<>(this.roomBlacklist);
            clone.roomWhitelist =
                    this.roomWhitelist == null ? new ArrayList<>() : new ArrayList<>(this.roomWhitelist);
            clone.door = this.door == null ? new ConnectorDoor(clone) : this.door.clone();
            return clone;
        } catch (CloneNotSupportedException exception) {
            LOGGER.error(
                    "Failed to clone connector at {} in dungeon '{}'.",
                    this.location,
                    this.dungeon == null ? "<unknown>" : this.dungeon.getWorldName(),
                    exception);
            return null;
        }
    }

    /**
     * Clones this connector while forcing a new facing direction.
     */
    public Connector clone(SimpleLocation.Direction direction) {
        try {
            Connector clone = (Connector) super.clone();
            clone.location =
                                        new SimpleLocation(
                            this.location.getX(), this.location.getY(), this.location.getZ(), direction);
            clone.roomBlacklist =
                    this.roomBlacklist == null ? new ArrayList<>() : new ArrayList<>(this.roomBlacklist);
            clone.roomWhitelist =
                    this.roomWhitelist == null ? new ArrayList<>() : new ArrayList<>(this.roomWhitelist);
            clone.door = this.door == null ? new ConnectorDoor(clone) : this.door.clone();
            return clone;
        } catch (CloneNotSupportedException exception) {
            LOGGER.error(
                    "Failed to clone connector at {} with target direction '{}' in dungeon '{}'.",
                    this.location,
                    direction,
                    this.dungeon == null ? "<unknown>" : this.dungeon.getWorldName(),
                    exception);
            return null;
        }
    }

    /**
     * Returns the owning dungeon definition.
     */
    public BranchingDungeon getDungeon() {
        return this.dungeon;
    }

    /**
     * Returns the connector location in room-local coordinates.
     */
    public SimpleLocation getLocation() {
        return this.location;
    }

    /**
     * Sets the connector location in room-local coordinates.
     */
    public void setLocation(SimpleLocation location) {
        this.location = location;
    }

    /**
     * Returns the chance that generation attempts this connector.
     */
    public double getSuccessChance() {
        return this.successChance;
    }

    /**
     * Sets connector usage chance, clamped to {@code [0,1]}.
     */
    public void setSuccessChance(double successChance) {
        this.successChance = Math.max(0.0, Math.min(1.0, successChance));
    }

    /**
     * Returns connector-specific denied destination rooms.
     */
    public List<WhitelistEntry> getRoomBlacklist() {
        return this.roomBlacklist;
    }

    /**
     * Replaces connector-specific denied destination rooms.
     */
    public void setRoomBlacklist(List<WhitelistEntry> roomBlacklist) {
        this.roomBlacklist = roomBlacklist == null ? new ArrayList<>() : roomBlacklist;
    }

    /**
     * Returns connector-specific allowed destination rooms.
     */
    public List<WhitelistEntry> getRoomWhitelist() {
        return this.roomWhitelist;
    }

    /**
     * Replaces connector-specific allowed destination rooms.
     */
    public void setRoomWhitelist(List<WhitelistEntry> roomWhitelist) {
        this.roomWhitelist = roomWhitelist == null ? new ArrayList<>() : roomWhitelist;
    }

    /**
     * Returns the door metadata attached to this connector.
     */
    public ConnectorDoor getDoor() {
        return this.door;
    }

    /**
     * Sets the connector door metadata; null creates a default door.
     */
    public void setDoor(ConnectorDoor door) {
        this.door = door == null ? new ConnectorDoor(this) : door;
    }
}
