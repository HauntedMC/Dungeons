package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.queue.*;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class CommandContractTest {
    @TempDir Path directory;

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
            assertFalse(f.dungeon.isMarkedForDelete());
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
            f.paper.bukkit.when(() -> Bukkit.getPlayer("Other")).thenReturn(other.player);
            command.execute("play", "crypt", "Other");
            verifyNoInteractions(command.coordinator);
            command.permissions.add("dungeons.play.send");
            command.execute("play", "crypt:hard", "Other");
            verify(command.coordinator).sendToDungeon(other.player, "crypt", "hard");
            clearInvocations(command.coordinator);
            session.setAwaitingDungeon(true);
            command.execute("play", "crypt", "Other");
            verifyNoInteractions(command.coordinator);
            session.setAwaitingDungeon(false);
            session.setInstance(f.run);
            command.execute("play", "crypt", "Other");
            verifyNoInteractions(command.coordinator);
            session.setInstance(null);
            f.dungeon.setSaveConfig("dungeon.enabled", false);
            command.execute("play", "crypt", "Other");
            verifyNoInteractions(command.coordinator);
        }
    }

    @Test
    void deletionRequiresAFreshConfirmationAndDeletesOnlyTheSelectedDungeonFolder()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.admin");
            Path neighbor = directory.resolve("unrelated.txt");
            Files.writeString(neighbor, "keep me");
            command.execute("delete", "crypt");
            assertTrue(f.dungeon.isMarkedForDelete());
            assertTrue(Files.isDirectory(f.dungeon.getFolder().toPath()));
            verify(command.catalog, never()).remove(any());
            f.scheduler.jobs.getLast().run();
            assertFalse(f.dungeon.isMarkedForDelete());
            command.execute("delete", "crypt");
            assertTrue(Files.isDirectory(f.dungeon.getFolder().toPath()));
            command.execute("delete", "crypt");
            assertFalse(Files.exists(f.dungeon.getFolder().toPath()));
            verify(command.catalog).remove(f.dungeon);
            assertEquals("keep me", Files.readString(neighbor));
        }
    }

    @Test
    void unsafeImportNamesAndEscapingSymlinksCannotLoadOrModifyWorlds() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.admin");
            Path worlds = Files.createDirectories(directory.resolve("worlds"));
            f.paper.bukkit.when(Bukkit::getWorldContainer).thenReturn(worlds.toFile());
            Path outside = Files.createDirectories(directory.resolve("outside"));
            Files.writeString(outside.resolve("level.dat"), "protected world");
            Files.createSymbolicLink(worlds.resolve("link"), outside);
            for (String name : List.of("../outside", "/absolute", "a/b", "a\\b", ".", "..", "link"))
                command.execute("import", name);
            verify(command.catalog, never())
                    .loadDungeon(any(java.io.File.class), anyString(), anyString());
            assertEquals("protected world", Files.readString(outside.resolve("level.dat")));
            assertTrue(Files.isDirectory(f.dungeon.getFolder().toPath()));
            assertTrue(f.scheduler.asynchronous.isEmpty());
        }
    }

    @Test
    void disablingPersistsTheSettingAndInvalidatesOnlyQueuesForThatDungeon() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.admin");
            var ownSession = f.paper.runtime.playerSessions().get(command.caller.player);
            var own = new DungeonQueueEntry(ownSession, f.dungeon, "");
            command.queues.enqueue(own);
            var otherDungeon = f.paper.dungeon(directory.resolve("other"), new YamlConfiguration());
            var otherSession = f.session(new PlayerState());
            var other = new DungeonQueueEntry(otherSession, otherDungeon, "");
            command.queues.enqueue(other);
            command.execute("dungeon", "disable", "crypt");
            assertFalse(f.dungeon.isEnabled());
            assertFalse(
                    YamlConfiguration.loadConfiguration(
                                    new java.io.File(f.dungeon.getFolder(), "config.yml"))
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
            var console = mock(org.bukkit.command.ConsoleCommandSender.class);
            when(console.hasPermission(anyString())).thenReturn(true);
            for (var args :
                    List.of(
                            new String[] {"reload"},
                            new String[] {"reload", "all"},
                            new String[] {"reload", "crypt"})) {
                assertTrue(command.root.onCommand(console, mock(Command.class), "dungeon", args));
            }
            verify(command.lifecycle).reloadConfigs();
            verify(command.lifecycle).reloadAllDungeons();
            verify(command.lifecycle).reloadDungeon(f.dungeon);
            f.paper
                    .bukkit
                    .when(() -> Bukkit.getPlayer("Explorer"))
                    .thenReturn(command.caller.player);
            assertTrue(
                    command.root.onCommand(
                            console,
                            mock(Command.class),
                            "dungeon",
                            new String[] {"play", "crypt", "Explorer"}));
            verify(command.coordinator).sendToDungeon(command.caller.player, "crypt", "");
            command.root.onCommand(
                    console, mock(Command.class), "dungeon", new String[] {"edit", "crypt"});
            assertTrue(f.scheduler.asynchronous.isEmpty());
        }
    }

    @Test
    void successfulImportCopiesAnUnloadedWorldBeforeLoadingAndPersistsItsDimensionAndSpawn()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandFixture(f);
            command.permissions.add("dungeons.admin");
            var unsafe = mock(UnsafeValues.class);
            when(unsafe.getMainLevelName()).thenReturn("world");
            f.paper.bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
            var source = Files.createDirectories(directory.resolve("imported"));
            Files.writeString(source.resolve("level.dat"), "world contents");
            var world = mock(World.class);
            when(world.getName()).thenReturn("imported");
            when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
            var spawn = new Location(world, 4, 70, 6, 30, 10);
            when(world.getSpawnLocation()).thenReturn(spawn);
            f.paper
                    .bukkit
                    .when(() -> Bukkit.createWorld(any(WorldCreator.class)))
                    .thenReturn(world);
            f.paper.bukkit.when(() -> Bukkit.unloadWorld(world, false)).thenReturn(true);
            var imported =
                    new java.util.ArrayList<
                            nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition>();
            when(command.catalog.loadDungeon(any(java.io.File.class)))
                    .thenAnswer(
                            c -> {
                                var dungeon =
                                        new nl.hauntedmc.dungeons.content.dungeon.StaticDungeon(
                                                f.paper.runtime,
                                                c.getArgument(0),
                                                new YamlConfiguration());
                                imported.add(dungeon);
                                return dungeon;
                            });
            command.execute("import", "imported", "nether");
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
            assertEquals("world contents", Files.readString(source.resolve("level.dat")));
            assertTrue(Files.isRegularFile(target.resolve("config.yml")));
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

        CommandFixture(RunFixture f) {
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
                            mock(LootTableRepository.class),
                            mock(FunctionRegistry.class),
                            lifecycle,
                            mock(GuiService.class),
                            "dungeon");
        }

        void execute(String... args) {
            root.onCommand(caller.player, mock(Command.class), "dungeon", args);
        }
    }
}
