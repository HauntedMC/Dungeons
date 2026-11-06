package nl.hauntedmc.dungeons.runtime.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.registry.DungeonTypeRegistry;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryContractTest {
    @TempDir Path directory;

    @Test
    void discoveryLoadsHealthyNeighborsWithoutOverwritingMalformedOrUnknownDungeonConfigs() throws Exception {
        try (var fixture = new Fixture(directory)) {
            Path healthy = Files.createDirectory(directory.resolve("healthy"));
            Files.writeString(healthy.resolve("config.yml"), "dungeon:\n  type: static\n");
            Path broken = Files.createDirectory(directory.resolve("broken")).resolve("config.yml");
            Files.writeString(broken, "dungeon: [unfinished");
            Path unknown = Files.createDirectory(directory.resolve("unknown")).resolve("config.yml");
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

    static final class Fixture implements AutoCloseable {
        final PaperBoundary paper = new PaperBoundary();
        final CapturedScheduler scheduler = new CapturedScheduler(paper);
        final Map<String, Class<? extends DungeonDefinition>> originalTypes = DungeonTypeRegistry.getDungeonTypes();
        final DungeonRepository repository;
        Fixture(Path directory) {
            paper.runtime.setDungeonFiles(directory.toFile());
            paper.runtime.setDefaultDungeonConfig(new YamlConfiguration());
            paper.bukkit.when(Bukkit::getWorldContainer).thenReturn(directory.toFile());
            paper.bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(paper.plugin.getResource("dungeons/config_default.yml"))
                    .thenAnswer(call -> getClass().getClassLoader().getResourceAsStream("dungeons/config_default.yml"));
            DungeonTypeRegistry.register(StaticDungeon.class, "static");
            repository = new DungeonRepository(paper.runtime);
            paper.runtime.setDungeonCatalog(repository);
        }
        @Override public void close() {
            DungeonTypeRegistry.clear();
            originalTypes.forEach((name, type) -> DungeonTypeRegistry.register(type, name));
            paper.close();
        }
    }
}
