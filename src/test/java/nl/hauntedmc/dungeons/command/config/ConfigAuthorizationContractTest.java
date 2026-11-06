package nl.hauntedmc.dungeons.command.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigAuthorizationContractTest {
    @TempDir Path directory;
    @Test
    void permissionForAnotherDungeonCannotModifyThisDungeonButASpecificGrantCanPersistTheChange() throws Exception {
        try (var paper = new PaperBoundary()) {
            var crypt = paper.dungeon(directory.resolve("crypt"), new YamlConfiguration());
            var other = paper.dungeon(directory.resolve("other"), new YamlConfiguration());
            var catalog = mock(DungeonRepository.class);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(crypt, other));
            when(catalog.get("crypt")).thenReturn(crypt);
            paper.runtime.setDungeonCatalog(catalog);
            var sender = mock(Player.class);
            when(sender.hasPermission("dungeons.edit.other")).thenReturn(true);
            var commands = new DungeonConfigCommandHandler(catalog, mock(PlayerSessionRegistry.class), mock(ActiveInstanceRegistry.class));
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
}
