package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.runtime.queue.*;
import nl.hauntedmc.dungeons.runtime.staff.StaffOperations;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class CommandContractTest {
    @TempDir Path directory;

    @Test
    void anAdmittedPlayerCanLeaveWithoutGainingPermissionToStartOtherDungeons() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var commands = new CommandFixture(fixture);
            var session = fixture.paper.runtime.playerSessions().get(commands.caller.player);
            fixture.run.addPlayer(session);
            commands.permissions.clear();
            commands.execute("play", "crypt");
            verifyNoInteractions(commands.coordinator);
            assertEquals(fixture.run, session.getInstance());
            commands.execute("leave");
            assertEquals(null, session.getInstance());
        }
    }

    @Test
    void listLabelsKeepTheirComponentsAndCommandOutputHasNoEscapedFormattingCodes()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.play");
            LangUtils.initialize();
            command.execute("list");
            var messages = ArgumentCaptor.forClass(Component.class);
            verify(command.caller.player, Mockito.atLeastOnce()).sendMessage(messages.capture());
            assertEquals(
                    2,
                    messages.getAllValues().size(),
                    "One map needs only a heading and its entry");
            for (var message : messages.getAllValues()) {
                String plain = ComponentUtils.plainText(message);
                assertFalse(plain.contains("§"), plain);
                assertFalse(plain.contains("\\u00a7"), plain);
                assertFalse(plain.contains("<HauntedMC:"), plain);
            }
        } finally {
            LangUtils.clear();
        }
    }

    @Test
    void databaseOutagesStillAllowReadOnlyServerDiagnostics() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.inspect");
            command.permissions.add("dungeons.staff.reload");
            var staff = mock(StaffCommandHandler.class);
            command.root.setStaffCommandHandler(staff);
            when(f.paper.runtime.database().ready()).thenReturn(false);
            command.execute("server", "status");
            command.execute("server", "reload", "config");
            verify(staff)
                    .handleCommand(
                            eq(command.caller.player),
                            AdditionalMatchers.aryEq(new String[] {"overview"}));
            verify(staff, never())
                    .handleCommand(
                            eq(command.caller.player),
                            AdditionalMatchers.aryEq(new String[] {"reload-config"}));
        }
    }

    @Test
    void mapSpecificStaffCommandsCannotDispatchServerWideOperations() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.reload");
            command.permissions.add("dungeons.staff.maintenance");
            var staff = mock(StaffCommandHandler.class);
            command.root.setStaffCommandHandler(staff);
            command.execute("map", "reload", "all");
            command.execute("map", "maintenance", "ALL", "on");
            verifyNoInteractions(staff);
        }
    }

    @Test
    void canonicalSettingsCompleteTypedValuesForAnAuthorizedMap() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.edit.crypt");
            var args = new String[] {"map", "settings", "crypt", "set", "enabled", ""};
            assertEquals(
                    List.of("false", "true"),
                    command.root.onTabComplete(
                            command.caller.player, mock(Command.class), "dungeon", args));
            command.permissions.remove("dungeons.staff.edit.crypt");
            assertEquals(
                    List.of(),
                    command.root.onTabComplete(
                            command.caller.player, mock(Command.class), "dungeon", args));
        }
    }

    @Test
    void editorOpenWithoutReasonDispatchesAndCompletesScopedDungeonNames() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.edit.crypt");
            var staff = mock(StaffCommandHandler.class);
            command.root.setStaffCommandHandler(staff);
            command.execute("edit", "open", "crypt");
            verify(staff)
                    .handleCommand(
                            eq(command.caller.player),
                            AdditionalMatchers.aryEq(new String[] {"edit", "crypt"}));
            assertEquals(
                    List.of("crypt"),
                    command.root.onTabComplete(
                            command.caller.player,
                            mock(Command.class),
                            "dungeon",
                            new String[] {"edit", "open", ""}));
        }
    }

    @Test
    void keyDefinitionChangesUseTheirOwnCapabilityAndConfirmTheExactMapIdentity() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            var dungeon = Mockito.spy(f.dungeon);
            when(command.catalog.get("crypt")).thenReturn(dungeon);
            var operations = mock(StaffOperations.class);
            f.paper.runtime.setStaffOperations(operations);
            Mockito.doReturn(0).when(dungeon).removeAllAccessKeys();
            command.permissions.add("dungeons.staff.keys.crypt");
            when(operations.confirm(any(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(true, false);
            command.execute("map", "keys", "clear", "crypt");
            verify(dungeon).removeAllAccessKeys();
            var first = dungeon.getDungeonId();
            var second = UUID.randomUUID();
            when(dungeon.getDungeonId()).thenReturn(second);
            command.execute("map", "keys", "clear", "crypt");
            var revisions = ArgumentCaptor.forClass(String.class);
            verify(operations, Mockito.times(2))
                    .confirm(
                            eq(command.caller.player),
                            eq("key-clear"),
                            eq("crypt"),
                            revisions.capture(),
                            anyString());
            assertTrue(revisions.getAllValues().getFirst().contains(first.toString()));
            assertTrue(revisions.getAllValues().getLast().contains(second.toString()));
            Assertions.assertNotEquals(
                    revisions.getAllValues().getFirst(), revisions.getAllValues().getLast());
            verify(operations, Mockito.times(2)).requireContentWritable("crypt");
        }
    }

    @Test
    void canonicalUuidTargetsRetainScopedAuthorizationAndDeniedCompletionDoesNotFallBack()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            String id = f.dungeon.getDungeonId().toString();
            when(command.catalog.get(id)).thenReturn(f.dungeon);
            command.permissions.add("dungeons.staff.edit.crypt");
            command.execute("map", "disable", id);
            assertFalse(f.dungeon.isEnabled());
            command.execute("help", "map", "settings", "crypt");
            verify(command.caller.player, Mockito.atLeastOnce())
                    .sendMessage(
                            ArgumentMatchers.argThat(
                                    (Component message) ->
                                            ComponentUtils.plainText(message)
                                                    .contains("/dungeon map settings crypt")));
            command.permissions.clear();
            assertEquals(
                    List.of(),
                    command.root.onTabComplete(
                            command.caller.player,
                            mock(Command.class),
                            "dungeon",
                            new String[] {""}));
        }
    }

    @Test
    void rootPermissionDenialPreventsGameplayAndDestructiveCommandDispatch() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.clear();
            for (String[] args :
                    List.of(
                            new String[] {"play", "crypt"},
                            new String[] {"delete", "crypt"},
                            new String[] {"import", "source"},
                            new String[] {"reload"})) command.execute(args);
            verifyNoInteractions(command.coordinator, command.lifecycle, command.catalog);
            assertTrue(Files.isDirectory(f.dungeon.getFolder().toPath()));
        }
    }

    @Test
    void playCommandsForwardTheCorrectPlayerAndRejectUnauthorizedBusyOrDisabledStarts()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.play");
            command.execute("play", "crypt");
            verify(command.coordinator).sendToDungeon(command.caller.player, "crypt", "");
            clearInvocations(command.coordinator);
            var other = new PlayerState();
            var session = f.session(other);
            f.paper.bukkit.when(() -> Bukkit.getPlayerExact("Other")).thenReturn(other.player);
            command.execute("play", "crypt", "Other");
            verifyNoInteractions(command.coordinator);
            command.permissions.add("dungeons.play.send");
            command.execute("player", "Other", "send", "crypt");
            verify(command.coordinator).sendSoloToDungeon(other.player, "crypt", "");
            clearInvocations(command.coordinator);
            session.setAwaitingDungeon(true);
            command.execute("player", "Other", "send", "crypt");
            verifyNoInteractions(command.coordinator);
            session.setAwaitingDungeon(false);
            session.setInstance(f.run);
            command.execute("player", "Other", "send", "crypt");
            verifyNoInteractions(command.coordinator);
            session.setInstance(null);
            f.dungeon.setSaveConfig("dungeon.enabled", false);
            command.execute("player", "Other", "send", "crypt");
            verifyNoInteractions(command.coordinator);
        }
    }

    @Test
    void unsafeImportNamesAndEscapingSymlinksCannotLoadOrModifyWorlds() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.edit");
            Path worlds = Files.createDirectories(directory.resolve("worlds"));
            f.paper.bukkit.when(Bukkit::getWorldContainer).thenReturn(worlds.toFile());
            Path outside = Files.createDirectories(directory.resolve("outside"));
            Files.writeString(outside.resolve("level.dat"), "protected world");
            Files.createSymbolicLink(worlds.resolve("link"), outside);
            for (String name : List.of("../outside", "/absolute", "a/b", "a\\b", ".", "..", "link"))
                command.execute("map", "import", name, "imported");
            verify(command.catalog, never()).loadDungeon(any(File.class), anyString(), anyString());
            assertEquals("protected world", Files.readString(outside.resolve("level.dat")));
            assertTrue(Files.isDirectory(f.dungeon.getFolder().toPath()));
            assertTrue(f.scheduler.asynchronous.isEmpty());
        }
    }

    @Test
    void playCompletionRequiresRepositoryInitializationButTestplayUsesLocalMaps() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            var uninitialized = Mockito.spy(f.dungeon);
            Mockito.doReturn(false).when(uninitialized).isEnabled();
            Mockito.doReturn(true).when(uninitialized).prepareTestPlay(command.caller.player);
            when(command.catalog.getLoadedDungeons()).thenReturn(List.of(uninitialized));
            when(command.catalog.get("crypt")).thenReturn(uninitialized);
            command.permissions.add("dungeons.play");
            var bukkitCommand = mock(Command.class);
            assertEquals(
                    List.of(),
                    command.root.onTabComplete(
                            command.caller.player,
                            bukkitCommand,
                            "dungeon",
                            new String[] {"play", ""}));
            command.execute("edit", "testplay", "crypt");
            verify(uninitialized, never()).prepareTestPlay(any());
            command.permissions.add("dungeons.staff.testplay.crypt");
            assertEquals(
                    List.of("crypt"),
                    command.root.onTabComplete(
                            command.caller.player,
                            bukkitCommand,
                            "dungeon",
                            new String[] {"edit", "testplay", ""}));
            command.execute("edit", "testplay", "crypt");
            verify(uninitialized).prepareTestPlay(command.caller.player);
        }
    }

    @Test
    void enablingAMapResumesLocalMaintenanceWithPermission() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var command = new CommandFixture(f.run);
            command.permissions.add("dungeons.staff.edit");
            f.ops.maintenance(f.actor, "crypt", true, "open-editor");
            assertFalse(f.run.dungeon.isEnabled());

            command.execute("map", "enable", "crypt");
            assertTrue(f.ops.mapMaintenance("crypt"));

            command.permissions.add("dungeons.staff.maintenance");
            command.execute("map", "enable", "crypt");
            assertTrue(f.run.dungeon.isEnabled());
            assertFalse(f.ops.mapMaintenance("crypt"));
        }
    }

    @Test
    void disablingPersistsTheSettingAndInvalidatesOnlyQueuesForThatDungeon() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.edit");
            var ownSession = f.paper.runtime.playerSessions().get(command.caller.player);
            var own = new DungeonQueueEntry(ownSession, f.dungeon, "");
            command.queues.enqueue(own);
            var otherDungeon = f.paper.dungeon(directory.resolve("other"), new YamlConfiguration());
            var otherSession = f.session(new PlayerState());
            var other = new DungeonQueueEntry(otherSession, otherDungeon, "");
            command.queues.enqueue(other);
            command.execute("map", "disable", "crypt");
            assertFalse(f.dungeon.isEnabled());
            assertFalse(
                    YamlConfiguration.loadConfiguration(
                                    new File(f.dungeon.getFolder(), "config.yml"))
                            .getBoolean("dungeon.enabled"));
            verify(command.coordinator).discardQueue(own);
            verify(command.coordinator, never()).discardQueue(other);
        }
    }

    @Test
    void consoleCommandsRouteReloadScopesAndCanSendAnOnlinePlayerWithoutPlayerOnlyEditorActions()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            var console = mock(ConsoleCommandSender.class);
            when(console.hasPermission(anyString())).thenReturn(true);
            f.paper
                    .bukkit
                    .when(() -> Bukkit.getPlayerExact("Explorer"))
                    .thenReturn(command.caller.player);
            assertTrue(
                    command.root.onCommand(
                            console,
                            mock(Command.class),
                            "dungeon",
                            new String[] {"player", "Explorer", "send", "crypt"}));
            verify(command.coordinator).sendSoloToDungeon(command.caller.player, "crypt", "");
            command.root.onCommand(
                    console, mock(Command.class), "dungeon", new String[] {"edit", "crypt"});
            assertTrue(f.scheduler.asynchronous.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void successfulImportCopiesAnUnloadedWorldBeforeLoadingAndPersistsItsDimensionAndSpawn(
            boolean modernOnly) throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.edit");
            var source = Files.createDirectories(directory.resolve("imported"));
            Files.writeString(source.resolve("level.dat"), "world contents");
            if (modernOnly) FileUtils.deleteDirectory(source.toFile());
            var world = mock(World.class);
            when(world.getName()).thenReturn("imported");
            var migrated =
                    Files.createDirectories(
                            directory.resolve("world/dimensions/minecraft/imported"));
            Files.writeString(migrated.resolve("level.dat"), "world contents");
            when(world.getWorldFolder()).thenReturn(migrated.toFile());
            when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
            var spawn = new Location(world, 4, 70, 6, 30, 10);
            when(world.getSpawnLocation()).thenReturn(spawn);
            f.paper
                    .bukkit
                    .when(() -> Bukkit.createWorld(any(WorldCreator.class)))
                    .thenReturn(world);
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            var imported = new ArrayList<DungeonDefinition>();
            when(command.catalog.loadDungeon(any(File.class)))
                    .thenAnswer(
                            c -> {
                                var dungeon =
                                        new StaticDungeon(
                                                f.paper.runtime,
                                                c.getArgument(0),
                                                new YamlConfiguration());
                                imported.add(dungeon);
                                return dungeon;
                            });
            command.execute("map", "import", "imported", "imported", "--environment", "nether");
            assertEquals(1, f.scheduler.asynchronous.size());
            assertTrue(imported.isEmpty());
            f.scheduler.asynchronous.getFirst().run();
            var target = directory.resolve("dungeons/imported");
            assertEquals("world contents", Files.readString(target.resolve("level.dat")));
            assertTrue(imported.isEmpty());
            f.scheduler.readyImmediate.remove().run();
            assertEquals(1, imported.size());
            var loaded = imported.getFirst();
            assertEquals("NETHER", loaded.getConfig().getString("dungeon.environment"));
            assertEquals(4, loaded.getLobbySpawn().getX());
            assertEquals(70, loaded.getLobbySpawn().getY());
            assertEquals(30, loaded.getLobbySpawn().getYaw());
            if (!modernOnly)
                assertEquals("world contents", Files.readString(source.resolve("level.dat")));
            assertTrue(Files.isRegularFile(target.resolve("config.yml")));
            FileUtils.deleteDirectory(target.toFile());
        }
    }

    @Test
    void creationCopiesTheUnloadedWorldBeforeLoadingAndConfiguringTheDungeon() throws Exception {
        try (var _ =
                        new StaticMapSnapshot(
                                nl.hauntedmc.dungeons.registry.DungeonTypeRegistry.class, "types");
                var f = new RunFixture(directory, new YamlConfiguration())) {
            nl.hauntedmc.dungeons.registry.DungeonTypeRegistry.register(
                    StaticDungeon.class, "static");
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.edit");
            var source = Files.createDirectories(directory.resolve("temporary-world"));
            Files.writeString(source.resolve("level.dat"), "generated world");
            var world = mock(World.class);
            when(world.getWorldFolder()).thenReturn(source.toFile());
            when(world.getSpawnLocation()).thenReturn(new Location(world, 2, 72, 9));
            f.paper
                    .bukkit
                    .when(() -> Bukkit.createWorld(any(WorldCreator.class)))
                    .thenReturn(world);
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(world, true)).thenReturn(true);
            var created = new ArrayList<DungeonDefinition>();
            when(command.catalog.loadDungeon(any(File.class), eq("static"), eq("")))
                    .thenAnswer(
                            call -> {
                                var dungeon =
                                        new StaticDungeon(
                                                f.paper.runtime,
                                                call.getArgument(0),
                                                new YamlConfiguration());
                                created.add(dungeon);
                                return dungeon;
                            });

            command.execute("map", "create", "created", "static");
            assertTrue(created.isEmpty());
            assertTrue(Files.exists(source));
            assertEquals(1, f.scheduler.asynchronous.size());
            f.scheduler.asynchronous.getFirst().run();
            assertEquals(
                    "generated world",
                    Files.readString(directory.resolve("dungeons/created/level.dat")));
            assertFalse(Files.exists(source));
            assertTrue(created.isEmpty());
            f.scheduler.readyImmediate.remove().run();
            assertEquals(1, created.size());
            assertEquals("static", created.getFirst().getConfig().getString("dungeon.type"));
            assertEquals(72, created.getFirst().getLobbySpawn().getY());
        }
    }

    @Test
    void creationRejectsAnExistingUnloadedModernWorld() throws Exception {
        try (var _ =
                        new StaticMapSnapshot(
                                nl.hauntedmc.dungeons.registry.DungeonTypeRegistry.class, "types");
                var f = new RunFixture(directory, new YamlConfiguration())) {
            nl.hauntedmc.dungeons.registry.DungeonTypeRegistry.register(
                    StaticDungeon.class, "static");
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.edit");
            Files.createDirectories(directory.resolve("world/dimensions/minecraft/existing"));
            command.execute("map", "create", "existing", "static");
            command.execute("map", "create", "invalid-type", "nonexistent");
            f.paper.bukkit.verify(() -> Bukkit.createWorld(any(WorldCreator.class)), never());
            assertTrue(f.scheduler.asynchronous.isEmpty());
        }
    }

    @Test
    void canonicalDebugCommandsReachTheInstanceActions() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.staff.edit");
            var instance = mock(BranchingInstance.class);
            var room = mock(InstanceRoom.class);
            when(room.getDoors())
                    .thenReturn(
                            Map.of(
                                    "north",
                                    mock(
                                            nl.hauntedmc.dungeons.generation.room.ConnectorDoor
                                                    .class)));
            when(instance.getRoom(any(Location.class))).thenReturn(room);
            var session = f.paper.runtime.playerSessions().get(command.caller.player);
            when(instance.getRuntime()).thenReturn(f.paper.runtime);
            var inspected = mock(BranchingDungeon.class);
            when(inspected.getWorldName()).thenReturn("crypt");
            when(instance.getDungeon()).thenReturn(inspected);
            session.setInstance(instance);
            command.permissions.add("dungeons.staff.diagnostics");
            command.execute("edit", "test", "doors", "all");
            verifyNoInteractions(room);
            when(instance.isTestPlay()).thenReturn(true);
            command.execute("edit", "test", "doors", "north");
            command.execute("edit", "test", "doors", "all");
            command.execute("edit", "test", "map");
            verify(room, Mockito.times(1)).toggleDoor("north");
            verify(room, Mockito.times(1)).toggleValidDoors(true);
            verify(instance, Mockito.times(1)).giveDungeonMap(command.caller.player);
            command.permissions.remove("dungeons.staff.diagnostics");
            clearInvocations(room, instance);
            command.execute("edit", "test", "doors", "all");
            verifyNoInteractions(room, instance);
        }
    }

    static final class CommandFixture {
        final PlayerState caller = new PlayerState();
        final Set<String> permissions = new HashSet<>(Set.of("dungeons.core"));
        final DungeonRepository catalog = mock(DungeonRepository.class);
        final DungeonQueueCoordinator coordinator = mock(DungeonQueueCoordinator.class);
        final LifecycleCoordinator lifecycle = mock(LifecycleCoordinator.class);
        final DungeonQueueRegistry queues;
        final DungeonCommand root;

        final GuiService gui = mock(GuiService.class);

        CommandFixture(RunFixture f) {
            var content = mock(nl.hauntedmc.dungeons.repository.RepoService.class);
            f.paper.runtime.setContentRepository(content);
            when(content.initialize(anyString(), ArgumentMatchers.any(), ArgumentMatchers.eq(true)))
                    .thenReturn(
                            java.util.concurrent.CompletableFuture.completedFuture("Initialized"));
            f.session(caller);
            when(caller.player.hasPermission(anyString()))
                    .thenAnswer(c -> permissions.contains(c.getArgument(0)));
            when(catalog.get("crypt")).thenReturn(f.dungeon);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(f.dungeon));
            var server = mock(Server.class);
            when(f.paper.plugin.getServer()).thenReturn(server);
            queues =
                    new DungeonQueueRegistry(
                            f.paper.runtime.playerSessions(), f.paper.runtime.activeInstances());
            var teams =
                    new DungeonTeamService(
                            f.paper.plugin, f.paper.runtime.playerSessions(), queues);
            root =
                    new DungeonCommand(
                            f.paper.plugin,
                            f.paper.runtime.playerSessions(),
                            catalog,
                            f.paper.runtime.activeInstances(),
                            coordinator,
                            queues,
                            teams,
                            mock(FunctionRegistry.class),
                            lifecycle,
                            gui,
                            "dungeon");
        }

        void execute(String... args) {
            root.onCommand(caller.player, mock(Command.class), "dungeon", args);
        }
    }
}
