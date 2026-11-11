package nl.hauntedmc.dungeons.runtime.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.content.function.CheckpointFunction;
import nl.hauntedmc.dungeons.runtime.instance.listener.PlayListener;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpawnPointContractTest {
    @TempDir Path directory;

    private YamlConfiguration settings() {
        var config = new YamlConfiguration();
        config.set("players.lives", 0);
        config.set("locations.starts.crypt.x", 10.5);
        config.set("locations.starts.crypt.y", 70);
        config.set("locations.starts.crypt.z", 20.5);
        config.set("locations.starts.crypt.yaw", 90);
        config.set("locations.starts.tower.x", 100.5);
        config.set("locations.starts.tower.y", 80);
        config.set("locations.starts.tower.z", 200.5);
        return config;
    }

    @Test
    void teamEntryStartLateJoinAndDeathsUseOneSelectionUntilCheckpoint() throws Exception {
        var config = settings();
        config.set("locations.lobby.enabled", true);
        config.set("locations.lobby.spawn.x", -10.5);
        config.set("locations.lobby.spawn.y", 70);
        config.set("locations.lobby.spawn.z", -20.5);
        try (var f = new RunFixture(directory, config)) {
            f.run.loadGame();
            Location selected = f.run.getStartLoc().clone();
            assertTrue(
                    f.dungeon.getStartSpawns().values().stream()
                            .anyMatch(loc -> loc.getX() == selected.getX()));
            assertSame(f.world, selected.getWorld());
            var firstState = new PlayerState();
            var secondState = new PlayerState();
            var first = f.session(firstState);
            var second = f.session(secondState);
            f.run.addPlayer(first);
            f.run.addPlayer(second);
            assertEquals(-10.5, firstState.location.getX());
            assertEquals(selected, first.getDungeonRespawn());
            assertEquals(selected, second.getDungeonRespawn());
            f.run.startGame();
            f.run.startGame();
            assertEquals(selected, firstState.location);
            assertEquals(selected, secondState.location);
            // A definition edit cannot change the already prepared run.
            f.dungeon.updateStartSpawn("crypt", new Location(null, 900, 90, 900));
            var lateState = new PlayerState();
            var late = f.session(lateState);
            f.run.addPlayer(late);
            assertEquals(selected, late.getDungeonRespawn());
            assertEquals(selected, lateState.location);
            var listener = (PlayListener) f.run.getListener();
            var death = mock(PlayerDeathEvent.class);
            when(death.getEntity()).thenReturn(firstState.player);
            var respawn = mock(PlayerRespawnEvent.class);
            when(respawn.getPlayer()).thenReturn(firstState.player);
            listener.onPlayerDeath(death);
            listener.onPlayerRespawn(respawn);
            verify(respawn).setRespawnLocation(selected);
            var checkpoint = new CheckpointFunction();
            Location checkpointLocation = new Location(f.world, 300.5, 75.5, 400.5);
            checkpoint.setLocation(checkpointLocation);
            checkpoint.runFunction(null, List.of(first));
            listener.onPlayerDeath(death);
            listener.onPlayerRespawn(respawn);
            verify(respawn).setRespawnLocation(checkpointLocation);
            assertEquals(selected, second.getDungeonRespawn());
            assertEquals(selected, f.run.getStartLoc());
        }
    }

    @Test
    void eachPreparedRunSelectsOnceAndOpenReuseDoesNotSelectAgain() throws Exception {
        try (var f = new RunFixture(directory, settings())) {
            var dungeon = spy(f.dungeon);
            var a = new Location(null, 10.5, 70, 20.5, 90, 0);
            var b = new Location(null, 100.5, 80, 200.5);
            doReturn(a, b).when(dungeon).selectStartSpawn(any());
            var first = new RunFixture.Run(dungeon, f.world);
            var second = new RunFixture.Run(dungeon, f.world);
            first.loadGame();
            second.loadGame();
            assertEquals(a.getX(), first.getStartLoc().getX());
            assertEquals(b.getX(), second.getStartLoc().getX());
            first.startGame();
            first.startGame();
            verify(dungeon, times(2)).selectStartSpawn(any());

            var openDungeon =
                    spy(
                            new nl.hauntedmc.dungeons.content.dungeon.OpenDungeon(
                                    f.paper.runtime,
                                    java.nio.file.Files.createDirectories(directory.resolve("open"))
                                            .toFile(),
                                    settings()));
            doReturn(true).when(openDungeon).publishReadyInstance(any());
            doReturn(b).when(openDungeon).selectStartSpawn(any());
            var open =
                    new nl.hauntedmc.dungeons.runtime.instance.play.OpenInstance(openDungeon, null);
            open.instanceWorld = f.world;
            open.loadGame();
            var player = f.session(new PlayerState());
            open.addPlayer(player);
            open.startGame();
            when(f.paper.plugin.isEnabled()).thenReturn(true);
            open.removePlayer(player, true);
            assertTrue(open.getPlayers().isEmpty());
            var newcomerState = new PlayerState();
            var newcomer = f.session(newcomerState);
            open.addPlayer(newcomer);
            assertSame(open, newcomer.getInstance());
            assertEquals(open.getStartLoc(), newcomerState.location);
            assertEquals(open.getStartLoc(), newcomer.getDungeonRespawn());
            verify(openDungeon).selectStartSpawn(any());
        }
    }

    @Test
    void savedProgressWinsOverSelectedStartEvenWhenEntryStartsTheRun() throws Exception {
        try (var f = new RunFixture(directory, settings())) {
            f.run.loadGame();
            var state = new PlayerState();
            var session = f.session(state);
            Location saved = new Location(null, 500.5, 90, 600.5, 45, -10);
            session.setDungeonSavePoint(saved, "crypt");
            f.run.addPlayer(session);
            // Run the ordinary delayed start and savepoint restoration in tick order.
            List.copyOf(f.scheduler.jobs).stream()
                    .filter(job -> job.period == 0)
                    .sorted(java.util.Comparator.comparingLong(job -> job.delay))
                    .forEach(CapturedScheduler.Job::run);
            saved.setWorld(f.world);
            assertTrue(f.run.isStarted());
            assertEquals(saved, state.location);
            assertEquals(saved, session.getDungeonRespawn());
            f.run.startGame();
            assertEquals(saved, session.getDungeonRespawn());
        }
    }

    @Test
    void editorUsesFirstPointAndEmptyRunUsesWorldSpawn() throws Exception {
        try (var f = new RunFixture(directory, settings())) {
            var editor = new EditableInstance(f.dungeon, new CountDownLatch(1));
            editor.instanceWorld = f.world;
            editor.loadGame();
            assertEquals(10.5, editor.getStartLoc().getX());
            f.dungeon.removeStartSpawn("crypt");
            f.dungeon.removeStartSpawn("tower");
            var fallback = new Location(f.world, -20.5, 60, 30.5, 45, 10);
            when(f.world.getSpawnLocation()).thenReturn(fallback);
            f.run.loadGame();
            assertEquals(fallback, f.run.getStartLoc());
            var persisted = new YamlConfiguration();
            persisted.load(f.dungeon.getFolder().toPath().resolve("config.yml").toFile());
            var reloaded = f.paper.dungeon(f.dungeon.getFolder().toPath(), persisted);
            assertTrue(reloaded.getStartSpawns().isEmpty());
        }
    }

    @Test
    void failedConfigWritesRestoreThePersistedCollection() throws Exception {
        try (var f = new RunFixture(directory, settings())) {
            f.dungeon.saveConfig();
            var original = f.dungeon.getStartSpawns();
            String persisted = f.dungeon.getConfig().saveToString();
            try (var files =
                    mockStatic(
                            nl.hauntedmc.dungeons.config.ConfigurationFile.class,
                            CALLS_REAL_METHODS)) {
                files.when(
                                () ->
                                        nl.hauntedmc.dungeons.config.ConfigurationFile.saveSnapshot(
                                                eq(f.paper.plugin),
                                                any(java.io.File.class),
                                                anyString()))
                        .thenThrow(new IllegalStateException("Disk write failed"));
                for (Runnable mutation :
                        List.<Runnable>of(
                                () -> f.dungeon.addStartSpawn("new", new Location(null, 1, 70, 2)),
                                () ->
                                        f.dungeon.updateStartSpawn(
                                                "crypt", new Location(null, 3, 70, 4)),
                                () -> f.dungeon.removeStartSpawn("tower"),
                                () -> f.dungeon.setStartSpawn(new Location(null, 5, 70, 6)))) {
                    assertThrows(IllegalStateException.class, mutation::run);
                    assertEquals(original, f.dungeon.getStartSpawns());
                    assertEquals(persisted, f.dungeon.getConfig().saveToString());
                }
            }
        }
    }

    @Test
    void legacySetterPreservesAdditionalPointsAcrossConfigSyncAndReload() throws Exception {
        try (var f = new RunFixture(directory, settings())) {
            var first = new Location(f.world, 5.5, 65, 6.5, 180, 20);
            f.dungeon.setStartSpawn(first);
            f.dungeon.getStartSpawn().setX(-100);
            var persisted = new YamlConfiguration();
            persisted.load(f.dungeon.getFolder().toPath().resolve("config.yml").toFile());
            // Exercise default synchronization too, not only a raw YAML read.
            f.paper.runtime.defaultDungeonConfig().loadFromString("locations: {start: null}\n");
            var reloaded = f.paper.dungeon(f.dungeon.getFolder().toPath(), persisted);
            assertEquals(
                    List.of("crypt", "tower"), List.copyOf(reloaded.getStartSpawns().keySet()));
            assertEquals(5.5, reloaded.getStartSpawn().getX());
            assertEquals(180, reloaded.getStartSpawn().getYaw());
            assertNull(reloaded.getStartSpawn().getWorld());
            assertSame(f.world, first.getWorld());
        }
    }
}
