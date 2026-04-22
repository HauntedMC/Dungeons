package nl.hauntedmc.dungeons.generation.room;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.annotation.TypeKey;
import nl.hauntedmc.dungeons.config.ConfigSerializableModel;
import nl.hauntedmc.dungeons.event.RoomDoorChangeEvent;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.util.entity.ParticleUtils;
import nl.hauntedmc.dungeons.util.world.SimpleLocation;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Door metadata attached to a room connector.
 *
 * <p>The door stores the blocks that should be restored when the door closes and emits
 * {@link RoomDoorChangeEvent} instances when its state changes at runtime.
 */
@TypeKey(id = "dungeons.serializable.connector_door")
@SerializableAs("dungeons.serializable.connector_door")
public class ConnectorDoor implements ConfigSerializableModel {
    @PersistedField private String namespace = "";
    @PersistedField private List<SimpleLocation> locations = new ArrayList<>();
    @PersistedField private boolean startOpen = true;
    @PersistedField private boolean disableSound = false;
    private InstanceRoom room;
    private WeakReference<World> world;
    private boolean open;
    private Map<SimpleLocation, StructurePieceBlock> blocks = new HashMap<>();
    private boolean hasAdjacentRoom;

        private ConnectorDoor() {}

    /**
     * Creates a connector door using the connector position as default namespace seed.
     */
    public ConnectorDoor(Connector connector) {
        this.namespace = defaultNamespace(connector == null ? null : connector.getLocation());
    }

    /**
     * Creates a connector door with an explicit namespace.
     */
    public ConnectorDoor(String namespace) {
        this.namespace = namespace == null ? "" : namespace;
    }

        static String defaultNamespace(SimpleLocation location) {
        if (location == null) {
            return "";
        }

        int x = (int) location.getX();
        int y = (int) location.getY();
        int z = (int) location.getZ();
        return "Door_" + x + "," + y + "," + z;
    }

    @Override
        public void postDeserialize() {
        this.namespace = this.namespace == null ? "" : this.namespace;
        this.locations = this.locations == null ? new ArrayList<>() : this.locations;
        this.blocks = this.blocks == null ? new HashMap<>() : this.blocks;
    }

    /**
     * Toggles whether a world-space location belongs to this door.
     */
    public void toggleLocation(Location loc) {
        this.toggleLocation(SimpleLocation.from(loc));
    }

    /**
     * Toggles whether a room-space location belongs to this door.
     */
    public void toggleLocation(SimpleLocation loc) {
        if (this.locations.contains(loc)) {
            this.removeLocation(loc);
        } else {
            this.addLocation(loc);
        }
    }

    /**
     * Adds a world-space location to this door.
     */
    public void addLocation(Location loc) {
        this.addLocation(SimpleLocation.from(loc));
    }

    /**
     * Adds a room-space location to this door.
     */
    public void addLocation(SimpleLocation loc) {
        this.locations.add(loc);
    }

    /**
     * Removes a world-space location from this door.
     */
    public void removeLocation(Location loc) {
        this.removeLocation(SimpleLocation.from(loc));
    }

    /**
     * Removes a room-space location from this door.
     */
    public void removeLocation(SimpleLocation loc) {
        this.locations.remove(loc);
    }

    /**
     * Binds this door to a runtime room and world.
     */
    public void initialize(InstanceRoom room, World world) {
        this.room = room;
        this.world = new WeakReference<>(world);
    }

    /**
     * Toggles the door open/closed state.
     */
    public void toggleDoor() {
        if (this.world != null) {
            if (this.open) {
                this.closeDoor();
            } else {
                this.openDoor();
            }
        }
    }

    /**
     * Opens the door by clearing tracked door blocks and firing a change event.
     */
    public void openDoor() {
        if (this.world == null) {
            return;
        }

        World world = this.world.get();
        if (world != null) {
            this.open = true;

            // Opening removes the cached door blocks so adjoining rooms become passable without
            // altering the original schematic snapshot stored in `blocks`.
            for (Entry<SimpleLocation, StructurePieceBlock> pair : this.blocks.entrySet()) {
                Location target = pair.getKey().asLocation(world);
                world.getBlockAt(target).setType(Material.AIR);
            }

            if (!this.locations.isEmpty() && !this.disableSound) {
                world.playSound(
                        this.locations.getFirst().asLocation(world), "block.iron_door.open", 1.0F, 0.8F);
            }

            DungeonInstance instance = this.room == null ? null : this.room.getInstance();
            Bukkit.getPluginManager()
                    .callEvent(new RoomDoorChangeEvent(instance, this.room, this, DoorAction.OPEN));
        }
    }

    /**
     * Closes the door by restoring tracked door blocks and firing a change event.
     */
    public void closeDoor() {
        if (this.world == null) {
            return;
        }

        World world = this.world.get();
        if (world != null) {
            this.open = false;

            // Closing replays the captured block states that were removed when the door opened.
            for (Entry<SimpleLocation, StructurePieceBlock> pair : this.blocks.entrySet()) {
                Location target = pair.getKey().asLocation(world);
                pair.getValue().placeAt(target);
            }

            if (!this.locations.isEmpty() && !this.disableSound) {
                world.playSound(
                        this.locations.getFirst().asLocation(world), "block.iron_door.close", 1.0F, 0.8F);
            }

            DungeonInstance instance = this.room == null ? null : this.room.getInstance();
            Bukkit.getPluginManager()
                    .callEvent(new RoomDoorChangeEvent(instance, this.room, this, DoorAction.CLOSE));
        }
    }

    /**
     * Renders debug particles at each configured door block for one player.
     */
    public void displayParticles(Player player) {
        for (SimpleLocation rawLoc : this.locations) {
            Location loc = rawLoc.asLocation(player.getWorld());
            BoundingBox blockBox =
                                        new BoundingBox(
                            loc.getX(),
                            loc.getY(),
                            loc.getZ(),
                            loc.getX() + 1.0,
                            loc.getY() + 1.0,
                            loc.getZ() + 1.0);
            ParticleUtils.displayBoundingBox(
                                        player, Particle.DUST, new DustOptions(Color.LIME, 0.25F), blockBox);
            loc.setX(loc.getX() + 0.5);
            loc.setY(loc.getY() + 0.7);
            loc.setZ(loc.getZ() + 0.5);
            player.spawnParticle(Particle.END_ROD, loc, 1, 0.0, 0.0, 0.0, 0.01);
        }
    }

    /**
     * Returns a translated copy of this door.
     */
    public ConnectorDoor copy(Vector offset) {
        ConnectorDoor newDoor = this.clone();
        newDoor.locations.clear();

        for (SimpleLocation loc : this.locations) {
            SimpleLocation newLoc = loc.clone();
            newLoc.shift(offset);
            newDoor.addLocation(newLoc);
        }

        newDoor.blocks = new HashMap<>();
        return newDoor;
    }

    /**
     * Returns a copy translated/rotated from one origin into a destination offset.
     */
    public ConnectorDoor copy(Vector destOffset, Vector origin, int rotation) {
        ConnectorDoor newDoor = new ConnectorDoor("");
        newDoor.setStartOpen(this.startOpen);
        newDoor.setDisableSound(this.disableSound);

        for (SimpleLocation loc : this.locations) {
            SimpleLocation adjusted = loc.clone();
            Vector blockOffset = loc.asVector().subtract(origin);
            Vector finalOffset = destOffset.clone().subtract(blockOffset);
            switch (rotation) {
                case -270:
                case 90:
                    finalOffset.add(
                                                        new Vector(
                                    -blockOffset.getBlockZ(), blockOffset.getBlockY(), blockOffset.getBlockX()));
                    break;
                case -180:
                case 180:
                    finalOffset.add(
                                                        new Vector(
                                    -blockOffset.getBlockX(), blockOffset.getBlockY(), -blockOffset.getBlockZ()));
                    break;
                case -90:
                case 270:
                    finalOffset.add(
                                                        new Vector(
                                    -blockOffset.getBlockZ(), blockOffset.getBlockY(), -blockOffset.getBlockX()));
            }

            adjusted.shift(finalOffset);
            newDoor.addLocation(adjusted);
        }

        return newDoor;
    }

    /**
     * Returns a copy transformed for a rotated room placement.
     */
    public ConnectorDoor copy(Vector offset, int rotation, SimpleLocation size) {
        ConnectorDoor newDoor = new ConnectorDoor("");
        newDoor.setStartOpen(this.startOpen);
        newDoor.setDisableSound(this.disableSound);

        for (SimpleLocation loc : this.locations) {
            SimpleLocation adjusted = loc.clone();
            SimpleLocation newLoc = RotatedRoom.rotateLocation(offset, adjusted, rotation, size);
            newLoc.shift(offset.getX(), 0.0, offset.getZ());
            newDoor.addLocation(newLoc);
        }

        return newDoor;
    }

    @Override
        public ConnectorDoor clone() {
        ConnectorDoor clone = new ConnectorDoor(this.namespace);
        clone.startOpen = this.startOpen;
        clone.disableSound = this.disableSound;
        clone.locations = new ArrayList<>(this.locations);
        clone.blocks = new HashMap<>();
        return clone;
    }

    /**
     * Returns the door namespace.
     */
    public String getNamespace() {
        return this.namespace;
    }

    /**
     * Sets the door namespace.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Returns all block locations controlled by this door.
     */
    public List<SimpleLocation> getLocations() {
        return this.locations;
    }

    /**
     * Returns whether this door starts open when an adjacent room exists.
     */
    public boolean isStartOpen() {
        return this.startOpen;
    }

    /**
     * Sets whether this door starts open when an adjacent room exists.
     */
    public void setStartOpen(boolean startOpen) {
        this.startOpen = startOpen;
    }

    /**
     * Returns whether open/close sounds are disabled.
     */
    public boolean isDisableSound() {
        return this.disableSound;
    }

    /**
     * Sets whether open/close sounds are disabled.
     */
    public void setDisableSound(boolean disableSound) {
        this.disableSound = disableSound;
    }

    /**
     * Returns whether the door is currently open.
     */
    public boolean isOpen() {
        return this.open;
    }

    /**
     * Sets the tracked open state without applying world changes.
     */
    public void setOpen(boolean open) {
        this.open = open;
    }

    /**
     * Returns captured blocks used to restore this door when closing.
     */
    public Map<SimpleLocation, StructurePieceBlock> getBlocks() {
        return this.blocks;
    }

    /**
     * Returns whether this connector currently leads to an adjacent room.
     */
    public boolean isHasAdjacentRoom() {
        return this.hasAdjacentRoom;
    }

    /**
     * Sets whether this connector currently leads to an adjacent room.
     */
    public void setHasAdjacentRoom(boolean hasAdjacentRoom) {
        this.hasAdjacentRoom = hasAdjacentRoom;
    }
}
