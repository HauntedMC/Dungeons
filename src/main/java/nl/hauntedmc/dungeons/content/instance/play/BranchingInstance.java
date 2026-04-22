package nl.hauntedmc.dungeons.content.instance.play;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.layout.Layout;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.generation.structure.StructurePieceBlock;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.util.world.LocationUtils;
import nl.hauntedmc.dungeons.world.generator.DungeonChunkGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3i;

/**
 * Playable instance for branching dungeons.
 *
 * <p>Branching instances clone the configured layout, generate room blocks, and build a runtime
 * lookup from room ids to generated room instances.
 */
public class BranchingInstance extends PlayableInstance {
    protected final BranchingDungeon dungeon;
    private Map<UUID, InstanceRoom> roomsByUuid;
    private Collection<InstanceRoom> rooms;
    private Layout builder;

    /**
     * Creates a new BranchingInstance instance.
     */
    public BranchingInstance(BranchingDungeon dungeon, CountDownLatch latch) {
        super(dungeon, latch);
        this.dungeon = dungeon;
    }

    /**
     * Performs initialize.
     */
    @Override
    public DungeonInstance initialize() {
        if (this.initialized) {
            return this;
        } else {
            this.initialized = true;
            this.id = this.dungeon.getInstances().size();
            File instFolder = this.getUniqueWorldName();
                        new File(instFolder, "config.yml").delete();
                        new File(instFolder, "uid.dat").delete();
            this.builder = this.dungeon.getLayout().clone();
            Layout.GenerationResult result = this.builder.generate();
            if (!result.isPassed()) {
                this.logger()
                        .error(
                                "Failed to initialize branching instance for dungeon '{}': {}",
                                this.dungeon.getWorldName(),
                                result.getMsg());
                return null;
            } else {
                this.initializeWorldGeneration();
                return this;
            }
        }
    }

    /**
     * Initializes world generation.
     */
    protected void initializeWorldGeneration() {
        this.roomsByUuid = new HashMap<>();
        this.rooms = this.builder.getAllRooms();
        Map<Vector3i, StructurePieceBlock> blocks = new ConcurrentHashMap<>();

        for (InstanceRoom room : this.rooms) {
            this.roomsByUuid.put(room.getUuid(), room);
            blocks.putAll(room.getBlocksToGenerate());
        }

        Bukkit.getScheduler()
                .runTask(
                        this.plugin(),
                        () ->
                                // World creation must happen on the server thread, but the heavy
                                // block collection work has already been prepared above.
                                super.initializeWorld(
                                                                                new DungeonChunkGenerator(
                                                this.plugin(),
                                                this.builder.getClosedConnectorBlock(),
                                                this.rooms,
                                                blocks)));
    }

    /**
     * Performs on apply world rules.
     */
    @Override
    protected void onApplyWorldRules() {
        this.dungeon.loadGamerulesTo(this.instanceWorld);
    }

    /**
     * Initializes functions.
     */
    @Override
    public void initializeFunctions() {
        for (InstanceRoom room : this.rooms) {
            room.initialize(this);
        }
    }

    /**
     * Performs on load game.
     */
    @Override
    public void onLoadGame() {
        Bukkit.getScheduler().runTaskLater(this.plugin(), this::startGame, 1L);
    }

    /**
     * Performs prepare valid start point.
     */
    public void prepareValidStartPoint() {
        InstanceRoom firstRoom = this.builder.getFirst();
        Location spawn = firstRoom.getSpawn();
        if (spawn != null) {
            spawn.setWorld(this.instanceWorld);
            if (spawn.getBlock().isSolid()) {
                spawn.add(0.0, 1.0, 0.0);
            }
        } else {
            spawn = LocationUtils.findSafeLocationInBox(this.instanceWorld, firstRoom.getBounds());
        }

        this.startLocation = spawn;
    }

    @Nullable public InstanceRoom getRoom(Location loc) {
        for (InstanceRoom room : this.rooms) {
            if (room.getBounds().clone().expand(0.0, 0.0, 0.0, 1.0, 1.0, 1.0).contains(loc.toVector())) {
                return room;
            }
        }

        return null;
    }

    @Nullable public InstanceRoom getRoom(UUID uuid) {
        return this.roomsByUuid.get(uuid);
    }

    /**
     * Returns the dungeon.
     */
    public BranchingDungeon getDungeon() {
        return this.dungeon;
    }

    /**
     * Returns the rooms by uuid.
     */
    public Map<UUID, InstanceRoom> getRoomsByUuid() {
        return this.roomsByUuid;
    }

    /**
     * Returns the rooms.
     */
    public Collection<InstanceRoom> getRooms() {
        return this.rooms;
    }
}
