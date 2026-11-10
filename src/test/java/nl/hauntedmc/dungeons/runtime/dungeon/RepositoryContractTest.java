package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.registry.DungeonTypeRegistry;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class RepositoryContractTest {
    @TempDir Path directory;

    @Test
    void invalidUuidIsRejectedBeforeDefaultsRewriteTheMap() throws Exception {
        try (var fixture = new Fixture(directory)) {
            Path folder = Files.createDirectory(directory.resolve("invalid-id"));
            String original = "# Operator content must remain byte-for-byte intact\ndungeon:\n  type: static\n  id: broken\n";
            Path config = folder.resolve("config.yml");
            Files.writeString(config, original);
            assertNull(fixture.repository.loadDungeon(folder.toFile()));
            assertEquals(original, Files.readString(config));
            assertFalse(Files.exists(folder.resolve("players")));
        }
    }

    @Test
    void duplicateDungeonIdentitiesAreIsolatedAndIdsResolveWithoutRenamingMaps() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var shared = UUID.randomUUID();
            for (String name : List.of("first", "duplicate", "healthy")) {
                Path folder = Files.createDirectory(directory.resolve(name));
                Files.writeString(folder.resolve("config.yml"), "dungeon:\n  type: static\n  id: '"
                        + (name.equals("healthy") ? UUID.randomUUID() : shared) + "'\n");
            }
            fixture.scheduler.jobs.getFirst().run();
            assertEquals(2, fixture.repository.getLoadedDungeons().size());
            assertNotNull(fixture.repository.get("healthy"));
            assertNotNull(fixture.repository.get(shared.toString()));
            assertEquals(1, fixture.repository.loadFailures().size());
        }
    }

    @Test
    void malformedDungeonReloadRetainsTheWorkingDefinitionUntilAReplacementLoads()
            throws Exception {
        try (var fixture = new Fixture(directory)) {
            Path folder = Files.createDirectory(directory.resolve("healthy"));
            Path file = folder.resolve("config.yml");
            Files.writeString(file, "dungeon:\n  type: static\n");
            fixture.scheduler.jobs.getFirst().run();
            var working = fixture.repository.get("healthy");
            assertNotNull(working);
            Files.writeString(file, "dungeon: [unfinished");
            fixture.repository.reloadAll();
            assertSame(working, fixture.repository.get("healthy"));
            fixture.scheduler.jobs.getLast().run();
            assertSame(working, fixture.repository.get("healthy"));
            assertEquals("dungeon: [unfinished", Files.readString(file));
        }
    }

    @Test
    void discoveryLoadsHealthyNeighborsWithoutOverwritingMalformedOrUnknownDungeonConfigs()
            throws Exception {
        try (var fixture = new Fixture(directory)) {
            Path healthy = Files.createDirectory(directory.resolve("healthy"));
            Files.writeString(healthy.resolve("config.yml"), "dungeon:\n  type: static\n");
            Path broken = Files.createDirectory(directory.resolve("broken")).resolve("config.yml");
            Files.writeString(broken, "dungeon: [unfinished");
            Path unknown =
                    Files.createDirectory(directory.resolve("unknown")).resolve("config.yml");
            Files.writeString(unknown, "dungeon:\n  type: nonexistent\n");
            Files.writeString(directory.resolve("notes.txt"), "not a dungeon");
            fixture.scheduler.jobs.getFirst().run();
            assertInstanceOf(StaticDungeon.class, fixture.repository.get("healthy"));
            assertEquals(1, fixture.repository.getLoadedDungeons().size());
            assertEquals("dungeon: [unfinished", Files.readString(broken));
            assertEquals("dungeon:\n  type: nonexistent\n", Files.readString(unknown));
            assertEquals(4, fixture.repository.get("healthy").getMaxTeamSize());
        }
    }

    @Test
    void savesBlockLaunchesAndEditsUntilCompleteAndLaunchArgumentsReachTheDungeonUnchanged() {
        try (var fixture = new Fixture(directory)) {
            var dungeon = mock(DungeonDefinition.class);
            var player = mock(Player.class);
            var playerId = UUID.randomUUID();
            var leaderId = UUID.randomUUID();
            when(player.isOnline()).thenReturn(true);
            fixture.paper.bukkit.when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);
            when(dungeon.getWorldName()).thenReturn("crypt");
            fixture.repository.put(dungeon);
            when(dungeon.isSaving()).thenReturn(true);
            assertFalse(fixture.repository.createInstance("missing", player, "hard", 3, leaderId));
            assertFalse(fixture.repository.editDungeon("missing", playerId));
            assertFalse(fixture.repository.createInstance("crypt", player, "hard", 3, leaderId));
            assertFalse(fixture.repository.editDungeon("crypt", playerId));
            verify(dungeon, never()).instantiate(any(), anyString(), anyInt(), any());
            verify(dungeon, never()).edit(any());
            when(dungeon.isSaving()).thenReturn(false);
            when(dungeon.instantiate(player, "hard", 3, leaderId)).thenReturn(true);
            assertTrue(fixture.repository.createInstance("crypt", player, "hard", 3, leaderId));
            assertTrue(fixture.repository.editDungeon("crypt", playerId));
            verify(dungeon).instantiate(player, "hard", 3, leaderId);
            verify(dungeon).edit(playerId);
        }
    }

    static final class Fixture implements AutoCloseable {
        final PaperBoundary paper = new PaperBoundary();
        final CapturedScheduler scheduler = new CapturedScheduler(paper);
        final Map<String, Class<? extends DungeonDefinition>> originalTypes =
                DungeonTypeRegistry.getDungeonTypes();
        final DungeonRepository repository;

        Fixture(Path directory) {
            paper.runtime.setDungeonFiles(directory.toFile());
            paper.runtime.setDefaultDungeonConfig(new YamlConfiguration());
            paper.bukkit.when(Bukkit::getWorldContainer).thenReturn(directory.toFile());
            paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(paper.plugin.getResource("dungeons/config_default.yml"))
                    .thenAnswer(
                            call ->
                                    getClass()
                                            .getClassLoader()
                                            .getResourceAsStream("dungeons/config_default.yml"));
            DungeonTypeRegistry.register(StaticDungeon.class, "static");
            repository = new DungeonRepository(paper.runtime);
            paper.runtime.setDungeonCatalog(repository);
        }

        @Override
        public void close() {
            DungeonTypeRegistry.clear();
            originalTypes.forEach((name, type) -> DungeonTypeRegistry.register(type, name));
            paper.close();
        }
    }
    @Test
    void unrelatedOrMalformedPlayerFilesCannotPreventLoadingValidDungeonAndPlayerData()
            throws Exception {
        try (var fixture = new Fixture(directory)) {
            Path folder = Files.createDirectory(directory.resolve("healthy"));
            Files.writeString(folder.resolve("config.yml"), "dungeon:\n  type: static\n");
            Path players = Files.createDirectory(folder.resolve("players"));
            var validId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            var brokenId = UUID.fromString("00000000-0000-0000-0000-000000000002");
            Files.writeString(players.resolve(validId + ".yml"), "AccessCooldown: 4102444800000\n");
            Files.writeString(players.resolve("notes.yml"), "operator: notes\n");
            Files.writeString(players.resolve(brokenId + ".yml"), "AccessCooldown: [unfinished");
            fixture.scheduler.jobs.getFirst().run();
            var dungeon = fixture.repository.get("healthy");
            assertNotNull(dungeon);
            assertNull(dungeon.playerDataStore().getAccessCooldown(validId));
            assertNull(dungeon.playerDataStore().getAccessCooldown(brokenId));
            assertEquals("operator: notes\n", Files.readString(players.resolve("notes.yml")));
            assertEquals(
                    "AccessCooldown: [unfinished",
                    Files.readString(players.resolve(brokenId + ".yml")));
        }
    }
}
