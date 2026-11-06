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
}
