package nl.hauntedmc.dungeons.command.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.layout.BranchingLayout;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

class ConfigAuthorizationContractTest {
    @TempDir Path directory;

    @Test
    void permissionForAnotherDungeonCannotModifyThisDungeonButASpecificGrantCanPersistTheChange()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var crypt = paper.dungeon(directory.resolve("crypt"), new YamlConfiguration());
            var other = paper.dungeon(directory.resolve("other"), new YamlConfiguration());
            var catalog = mock(DungeonRepository.class);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(crypt, other));
            when(catalog.get("crypt")).thenReturn(crypt);
            paper.runtime.setDungeonCatalog(catalog);
            var sender = mock(Player.class);
            when(sender.hasPermission("dungeons.edit.other")).thenReturn(true);
            var commands =
                    new DungeonConfigCommandHandler(
                            catalog,
                            mock(PlayerSessionRegistry.class),
                            mock(ActiveInstanceRegistry.class));
            var request = new String[] {"config", "crypt", "max-team-size", "2"};
            assertTrue(commands.handleCommand(sender, request));
            assertEquals(4, crypt.getMaxTeamSize());
            when(sender.hasPermission("dungeons.edit.crypt")).thenReturn(true);
            assertTrue(commands.handleCommand(sender, request));
            assertEquals(2, crypt.getMaxTeamSize());
            var stored = new YamlConfiguration();
            stored.load(directory.resolve("crypt/config.yml").toFile());
            assertEquals(2, stored.getInt("team.max_size"));
        }
    }

    @Test
    void nonFiniteGeneratorValuesCannotBePersistedOrTriggerAGeneratorReload() {
        try (var paper = new PaperBoundary()) {
            var dungeon = mock(BranchingDungeon.class);
            var settings = new YamlConfiguration();
            settings.set("generator.branching.trunk.straightness", 0.5);
            when(dungeon.getConfig()).thenReturn(settings);
            when(dungeon.getWorldName()).thenReturn("crypt");
            when(dungeon.asBranching()).thenReturn(dungeon);
            when(dungeon.getLayout()).thenReturn(mock(BranchingLayout.class));
            var catalog = mock(DungeonRepository.class);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(dungeon));
            when(catalog.get("crypt")).thenReturn(dungeon);
            paper.runtime.setDungeonCatalog(catalog);
            var sender = mock(Player.class);
            when(sender.hasPermission("dungeons.edit.crypt")).thenReturn(true);
            var commands =
                    new DungeonConfigCommandHandler(
                            catalog,
                            mock(PlayerSessionRegistry.class),
                            mock(ActiveInstanceRegistry.class));
            for (String invalid : new String[] {"NaN", "Infinity", "-Infinity", "1e309"}) {
                commands.handleCommand(
                        sender, new String[] {"config", "crypt", "trunk.straightness", invalid});
                assertEquals(
                        0.5, settings.getDouble("generator.branching.trunk.straightness"), invalid);
            }
            verify(dungeon, never()).saveConfig();
            verify(dungeon, never()).reloadGenerationSettings();
        }
    }

    @Test
    void typedConfigurationCommandsPersistBooleansNumbersEnumsListsAndWorldIndependentLocations()
            throws Exception {
        try (var f =
                new nl.hauntedmc.dungeons.support.RunFixture(directory, new YamlConfiguration())) {
            var catalog = mock(DungeonRepository.class);
            when(catalog.get("crypt")).thenReturn(f.dungeon);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(f.dungeon));
            f.paper.runtime.setDungeonCatalog(catalog);
            var state = new nl.hauntedmc.dungeons.support.PlayerState();
            when(state.player.hasPermission("dungeons.edit.crypt")).thenReturn(true);
            var commands =
                    new DungeonConfigCommandHandler(
                            catalog,
                            f.paper.runtime.playerSessions(),
                            f.paper.runtime.activeInstances());
            var cases =
                    List.of(
                            List.of("enabled", "false", "dungeon.enabled", false),
                            List.of("max-team-size", "3", "team.max_size", 3),
                            List.of("gamemode", "creative", "players.gamemode", "CREATIVE"),
                            List.of("dimension", "nether", "dungeon.environment", "NETHER"),
                            List.of(
                                    "join-commands",
                                    "say hello,give {player} minecraft:stone",
                                    "players.join_commands",
                                    List.of("say hello", "give {player} minecraft:stone")),
                            List.of(
                                    "block-break-whitelist",
                                    "STONE,DIRT",
                                    "rules.building.break_whitelist",
                                    List.of("STONE", "DIRT")));
            for (var row : cases) {
                commands.handleCommand(
                        state.player,
                        new String[] {"config", "crypt", (String) row.get(0), (String) row.get(1)});
                assertEquals(
                        row.get(3), f.dungeon.getConfig().get((String) row.get(2)), row.toString());
            }
            commands.handleCommand(
                    state.player, new String[] {"config", "crypt", "max-team-size", "3.5"});
            commands.handleCommand(
                    state.player, new String[] {"config", "crypt", "gamemode", "invalid"});
            commands.handleCommand(
                    state.player,
                    new String[] {
                        "config", "crypt", "block-break-whitelist", "STONE,NO_SUCH_BLOCK"
                    });
            var file = new YamlConfiguration();
            file.load(directory.resolve("crypt/config.yml").toFile());
            for (var row : cases) assertEquals(row.get(3), file.get((String) row.get(2)));
            commands.handleCommand(
                    state.player, new String[] {"config", "crypt", "start-location", "here"});
            file.load(directory.resolve("crypt/config.yml").toFile());
            var reloaded = f.paper.dungeon(directory.resolve("crypt"), file);
            org.junit.jupiter.api.Assertions.assertNull(reloaded.getStartSpawn().getWorld());
            org.junit.jupiter.api.Assertions.assertFalse(file.contains("locations.start.world"));
            assertEquals(state.location.getX(), f.dungeon.getStartSpawn().getX());
            assertEquals(state.location.getYaw(), f.dungeon.getStartSpawn().getYaw());
            org.junit.jupiter.api.Assertions.assertSame(
                    state.originWorld, state.location.getWorld());
        }
    }
}
