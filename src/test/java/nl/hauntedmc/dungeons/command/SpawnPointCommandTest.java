package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.EditableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpawnPointCommandTest {
    @TempDir Path directory;

    @Test
    void commandsManageNamedAndNumberedPointsWithoutReplacingOtherEntries() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            when(state.player.hasPermission("dungeons.staff.edit.crypt")).thenReturn(true);
            var registry = mock(PlayerSessionRegistry.class);
            var session = mock(DungeonPlayerSession.class);
            var editor = mock(EditableInstance.class);
            when(registry.get(state.player)).thenReturn(session);
            when(session.isEditMode()).thenReturn(true);
            when(session.getInstance()).thenReturn(editor);
            when(editor.getDungeon()).thenReturn(f.dungeon);
            when(editor.getRuntime()).thenReturn(f.paper.runtime);
            when(editor.getInstanceWorld()).thenReturn(state.originWorld);
            var handler =
                    new EditCommandHandler(f.paper.plugin, registry, mock(DungeonRepository.class));
            for (String command :
                    List.of(
                            "add",
                            "add Crypt",
                            "add",
                            "add CRYPT",
                            "set missing",
                            "remove missing",
                            "add bad.id")) {
                assertTrue(handler.handleCommand(state.player, args(command)));
            }
            assertEquals(
                    List.of("1", "crypt", "2"), List.copyOf(f.dungeon.getStartSpawns().keySet()));
            Location originalSecond = f.dungeon.getStartSpawn("2");
            state.location.setX(500);
            handler.handleCommand(state.player, args("set CRYPT"));
            assertEquals(500, f.dungeon.getStartSpawn("crypt").getX());
            handler.handleCommand(state.player, args("set"));
            assertEquals(500, f.dungeon.getStartSpawn("1").getX());
            assertEquals(originalSecond, f.dungeon.getStartSpawn("2"));
            handler.handleCommand(state.player, args("remove 1"));
            assertEquals(List.of("crypt", "2"), List.copyOf(f.dungeon.getStartSpawns().keySet()));
            assertEquals(List.of("crypt"), handler.handleTabComplete(state.player, args("tp cr")));
            handler.handleCommand(state.player, args("tp 2"));
            assertEquals(originalSecond.getX(), state.location.getX());
            handler.handleCommand(state.player, args("list"));
            var saved = new YamlConfiguration();
            saved.load(f.dungeon.getFolder().toPath().resolve("config.yml").toFile());
            assertTrue(saved.contains("locations.starts.crypt"));
            assertFalse(saved.contains("locations.starts.1"));
            when(state.player.hasPermission("dungeons.staff.edit.crypt")).thenReturn(false);
            handler.handleCommand(state.player, args("remove crypt"));
            assertEquals(2, f.dungeon.getStartSpawns().size());
            when(session.isEditMode()).thenReturn(false);
            handler.handleCommand(state.player, args("add outside"));
            assertEquals(2, f.dungeon.getStartSpawns().size());
        }
    }

    @Test
    void everySpawnMutationRejectsStaleOwnershipWrongWorldAndPermissionLoss() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            when(state.player.hasPermission("dungeons.staff.edit.crypt")).thenReturn(true);
            var registry = mock(PlayerSessionRegistry.class);
            var session = mock(DungeonPlayerSession.class);
            var editor = mock(EditableInstance.class);
            var operations = mock(nl.hauntedmc.dungeons.runtime.staff.StaffOperations.class);
            f.paper.runtime.setStaffOperations(operations);
            when(operations.maintenance("crypt")).thenReturn(true);
            when(registry.get(state.player)).thenReturn(session);
            when(session.isEditMode()).thenReturn(true);
            when(session.getInstance()).thenReturn(editor);
            when(editor.getDungeon()).thenReturn(f.dungeon);
            when(editor.getRuntime()).thenReturn(f.paper.runtime);
            when(editor.getInstanceWorld()).thenReturn(state.originWorld);
            f.dungeon.setEditSession(editor);
            f.dungeon.addStartSpawn("crypt", new Location(null, 1.5, 70, 2.5));
            var original = f.dungeon.getStartSpawns();
            var handler =
                    new EditCommandHandler(f.paper.plugin, registry, mock(DungeonRepository.class));
            for (String denial : List.of("ownership", "world", "permission")) {
                f.dungeon.setEditSession(
                        denial.equals("ownership") ? mock(EditableInstance.class) : editor);
                when(editor.getInstanceWorld())
                        .thenReturn(denial.equals("world") ? f.world : state.originWorld);
                when(state.player.hasPermission("dungeons.staff.edit.crypt"))
                        .thenReturn(!denial.equals("permission"));
                for (String command : List.of("set", "set crypt", "add tower", "remove crypt")) {
                    handler.handleCommand(state.player, args(command));
                    assertEquals(original, f.dungeon.getStartSpawns(), denial + ": " + command);
                }
            }
            f.dungeon.setEditSession(editor);
            when(editor.getInstanceWorld()).thenReturn(state.originWorld);
            when(state.player.hasPermission("dungeons.staff.edit.crypt")).thenReturn(true);
            handler.handleCommand(state.player, args("set"));
            assertEquals(state.location.getX(), f.dungeon.getStartSpawn().getX());
        }
    }

    @Test
    void publicRoutesDispatchEveryNewActionAndRetainExistingLocationSyntax() {
        for (String command :
                List.of(
                        "add",
                        "add crypt",
                        "set",
                        "set crypt",
                        "list",
                        "tp crypt",
                        "remove crypt")) {
            String[] input = args(command);
            var route =
                    CommandRoutes.ALL.stream()
                            .filter(candidate -> candidate.match(input) != null)
                            .findFirst()
                            .orElseThrow();
            assertEquals(CommandRoutes.Handler.EDIT, route.handler());
            assertArrayEquals(input, route.arguments(route.match(input)));
        }
        String[] legacy = {"edit", "location", "start", "set"};
        var route =
                CommandRoutes.ALL.stream()
                        .filter(candidate -> candidate.match(legacy) != null)
                        .findFirst()
                        .orElseThrow();
        assertArrayEquals(args("set"), route.arguments(route.match(legacy)));
    }

    private static String[] args(String action) {
        return ("edit start " + action).split(" ");
    }
}
