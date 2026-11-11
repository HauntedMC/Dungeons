package nl.hauntedmc.dungeons.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import nl.hauntedmc.dataregistry.api.player.PlayerData;
import nl.hauntedmc.dungeons.api.DungeonsApi;
import nl.hauntedmc.dungeons.persistence.DungeonDatabase;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DungeonServiceTest {
    @TempDir Path directory;

    @Test
    void publicSnapshotsAreImmutableHealthGatedAndRemovedWithTheirResourceGeneration()
            throws Exception {
        try (var paper = new PaperBoundary()) {
            var database = mock(DungeonDatabase.class);
            when(database.serverId()).thenReturn("test-backend");
            when(database.players()).thenReturn(mock(PlayerData.class));
            when(paper.runtime.database().database()).thenReturn(database);
            var catalog = mock(DungeonRepository.class);
            var dungeon = paper.dungeon(directory.resolve("crypt"), new YamlConfiguration());
            paper.runtime.setDungeonCatalog(catalog);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(dungeon));
            var server = mock(Server.class);
            var services = mock(ServicesManager.class);
            var scheduler = mock(BukkitScheduler.class);
            var task = mock(BukkitTask.class);
            when(paper.plugin.getServer()).thenReturn(server);
            when(server.getServicesManager()).thenReturn(services);
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(paper.plugin), any(Runnable.class), eq(20L), eq(20L)))
                    .thenReturn(task);
            var api = new DungeonService(paper.runtime);
            api.start();
            assertEquals("test-backend", api.serverId());
            assertEquals(dungeon.getDungeonId(), api.catalog().getFirst().id());
            assertThrows(UnsupportedOperationException.class, () -> api.catalog().clear());
            when(paper.runtime.database().ready()).thenReturn(false);
            assertTrue(api.catalog().isEmpty());
            paper.runtime.resources().quiesce();
            paper.runtime.setDatabase(null);
            assertFalse(api.ready());
            assertTrue(api.catalog().isEmpty());
            verify(task).cancel();
            verify(services).unregister(DungeonsApi.class, api);
            paper.runtime.resources().cleanup();
        }
    }
}
