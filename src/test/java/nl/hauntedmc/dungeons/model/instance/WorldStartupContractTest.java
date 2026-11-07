package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.SpawnCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

class WorldStartupContractTest {
    @TempDir Path directory;

    @Test
    void allocatingMultipleRunsReservesDistinctFoldersEvenBeforeWorldCreation() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            when(Bukkit.getServer().getLevelDirectory()).thenReturn(directory.resolve("plots"));
            var first = f.run.reserveWorldFolder(false);
            var second = new RunFixture.Run(f.dungeon, f.world).reserveWorldFolder(false);
            assertTrue(first.isDirectory());
            assertTrue(second.isDirectory());
            assertNotEquals(first, second);
            assertEquals("crypt_0", first.getName());
            assertEquals("crypt_1", second.getName());
        }
    }

    @Test
    void disposalRemovesBothLegacyAndMigratedFoldersButKeepsTheTemplate() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            when(Bukkit.getServer().getLevelDirectory()).thenReturn(directory.resolve("plots"));
            Path legacy = Files.createDirectories(directory.resolve("crypt_0/DIM1/region"));
            Files.writeString(legacy.resolve("old.mca"), "legacy");
            Path migrated =
                    Files.createDirectories(
                            directory.resolve("plots/dimensions/minecraft/crypt_0"));
            Files.writeString(migrated.resolve("current.mca"), "modern");
            Path template = f.dungeon.getFolder().toPath();
            Files.writeString(template.resolve("keep.mca"), "template");
            f.run.cleanWorldFiles(
                    new java.util.concurrent.CountDownLatch(0), migrated.toFile(), "crypt_0");
            assertFalse(Files.exists(directory.resolve("crypt_0")));
            assertFalse(Files.exists(migrated));
            assertEquals("template", Files.readString(template.resolve("keep.mca")));
        }
    }

    @Test
    void modernTemplateDoesNotReimportStaleLegacyDimensionsAndMigratesPotionLimits()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            when(Bukkit.getServer().getLevelDirectory()).thenReturn(directory.resolve("plots"));
            Path source = f.dungeon.getFolder().toPath();
            Files.createDirectories(source.resolve("data/minecraft"));
            Files.writeString(source.resolve("data/minecraft/world_gen_settings.dat"), "modern");
            Files.writeString(source.resolve("level.dat"), "obsolete");
            Files.createDirectories(source.resolve("DIM1/region"));
            Files.writeString(source.resolve("DIM1/region/old.mca"), "obsolete");
            Files.createDirectories(source.resolve("region"));
            Files.writeString(source.resolve("region/current.mca"), "current");
            Files.writeString(
                    source.resolve("paper-world.yml"),
                    "chunks:\n"
                            + "  entity-per-chunk-save-limit:\n"
                            + "    potion: 12\n"
                            + "    splash_potion: 20\n");
            f.run.initialize();
            Path copy = directory.resolve("plots/dimensions/minecraft/crypt_0");
            assertEquals("current", Files.readString(copy.resolve("region/current.mca")));
            assertFalse(Files.exists(copy.resolve("level.dat")));
            assertFalse(Files.exists(copy.resolve("DIM1")));
            var yaml =
                    YamlConfiguration.loadConfiguration(copy.resolve("paper-world.yml").toFile());
            assertFalse(yaml.contains("chunks.entity-per-chunk-save-limit.potion"));
            assertEquals(20, yaml.getInt("chunks.entity-per-chunk-save-limit.splash_potion"));
            assertEquals(12, yaml.getInt("chunks.entity-per-chunk-save-limit.lingering_potion"));
            assertTrue(Files.exists(source.resolve("DIM1/region/old.mca")));
        }
    }

    @Test
    void animalAndMonsterSpawningRulesApplyIndependently() throws Exception {
        for (boolean animals : new boolean[] {false, true}) {
            for (boolean monsters : new boolean[] {false, true}) {
                var rules = new YamlConfiguration();
                rules.set("rules.world.freeze_random_ticks", false);
                rules.set("rules.spawning.natural_mobs", true);
                rules.set("rules.spawning.animals", animals);
                rules.set("rules.spawning.monsters", monsters);
                try (var f = new RunFixture(directory, rules)) {
                    when(f.world.getSpawnLocation()).thenReturn(new Location(f.world, 0, 64, 0));
                    f.run.applyWorldRules();
                    for (var category : SpawnCategory.values()) {
                        boolean disabled =
                                category == SpawnCategory.MONSTER
                                        ? !monsters
                                        : category != SpawnCategory.MISC && !animals;
                        verify(f.world, times(disabled ? 1 : 0)).setTicksPerSpawns(category, 0);
                    }
                }
            }
        }
    }

    @Test
    void copiedWorldStartupStripsIdentityAppliesRulesPublishesReadinessAndStartsExactlyOnce()
            throws Exception {
        var rules = new YamlConfiguration();
        rules.set("dungeon.environment", "invalid");
        rules.set("rules.spawning.natural_mobs", false);
        rules.set("rules.world.freeze_random_ticks", false);
        try (var f = new RunFixture(directory, rules)) {
            var source = f.dungeon.getFolder().toPath();
            Files.writeString(source.resolve("level.dat"), "template world");
            Files.writeString(source.resolve("uid.dat"), "template identity");
            Files.writeString(source.resolve("config.yml"), "template settings");
            when(Bukkit.getServer().getLevelDirectory()).thenReturn(directory.resolve("world"));
            f.paper
                    .bukkit
                    .when(() -> Bukkit.createWorld(any(WorldCreator.class)))
                    .thenReturn(f.world);
            var spawn = new Location(f.world, -1, 72, 17);
            when(f.world.getSpawnLocation()).thenReturn(spawn);
            assertSame(f.run, f.run.initialize());
            assertFalse(f.run.isReady());
            assertEquals(
                    "template world", Files.readString(directory.resolve("crypt_0/level.dat")));
            assertFalse(Files.exists(directory.resolve("crypt_0/uid.dat")));
            assertFalse(Files.exists(directory.resolve("crypt_0/config.yml")));
            assertTrue(Files.exists(source.resolve("uid.dat")));
            f.scheduler.immediate.getFirst().run();
            assertTrue(f.run.isReady());
            assertFalse(f.run.hasLoadFailed());
            assertTrue(f.run.awaitInitialization(0, TimeUnit.SECONDS));
            assertEquals(spawn, f.run.getStartLoc());
            verify(f.world).setAutoSave(false);
            verify(f.world).setChunkForceLoaded(-1, 1, false);
            for (var category : SpawnCategory.values())
                if (category != SpawnCategory.MISC) verify(f.world).setTicksPerSpawns(category, 0);
            verify(f.world, never()).setTicksPerSpawns(SpawnCategory.MISC, 0);
            var creator = ArgumentCaptor.forClass(WorldCreator.class);
            f.paper.bukkit.verify(() -> Bukkit.createWorld(creator.capture()));
            assertEquals(World.Environment.NORMAL, creator.getValue().environment());
            assertFalse(creator.getValue().generateStructures());
            f.run.initialize();
            assertEquals(1, f.scheduler.immediate.size());
            f.scheduler.jobs.getLast().run();
            assertTrue(f.run.isStarted());
            f.run.onDispose();
            assertTrue(
                    f.scheduler.jobs.stream()
                            .filter(job -> job.period > 0)
                            .allMatch(job -> job.cancelled));
        }
    }
}
