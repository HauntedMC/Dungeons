package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;

import org.bukkit.*;

import java.util.*;

/** Seeds the output of the separately tested layout stage; run and room behavior stays real. */
public final class BranchingRunFixture {
    public final BranchingDungeon dungeon = mock(BranchingDungeon.class);
    public final List<InstanceRoom> rooms = new ArrayList<>();
    public final Run run;
    private final Map<UUID, InstanceRoom> byId = new HashMap<>();

    public BranchingRunFixture(RunFixture f) throws ReflectiveOperationException {
        when(dungeon.getRuntime()).thenReturn(f.paper.runtime);
        when(dungeon.getEnvironment()).thenReturn(f.paper.runtime.environment());
        when(dungeon.getConfig()).thenReturn(f.run.getConfig());
        when(dungeon.getWorldName()).thenReturn("branch");
        when(dungeon.getFolder()).thenReturn(f.dungeon.getFolder());
        when(dungeon.getStartRooms()).thenReturn(new HashMap<>());
        when(dungeon.getUniqueRooms()).thenReturn(new HashMap<>());
        run = new Run(dungeon, f.world);
        var roomField = BranchingInstance.class.getDeclaredField("rooms");
        roomField.setAccessible(true);
        roomField.set(run, rooms);
        var idField = BranchingInstance.class.getDeclaredField("roomsByUuid");
        idField.setAccessible(true);
        idField.set(run, byId);
    }

    public void add(InstanceRoom room) {
        rooms.add(room);
        byId.put(room.getUuid(), room);
        room.initialize(run);
    }

    public static final class Run extends BranchingInstance {
        private Run(BranchingDungeon dungeon, World world) {
            super(dungeon, null);
            instanceWorld = world;
            startLocation = new Location(world, 0, 70, 0);
        }
    }
}
