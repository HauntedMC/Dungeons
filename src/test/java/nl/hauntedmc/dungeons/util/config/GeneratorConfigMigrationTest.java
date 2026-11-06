package nl.hauntedmc.dungeons.util.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorConfigMigrationTest {
    @TempDir Path folder;

    @Test
    void importsBeforeDefaultsAndPreservesNamedBranchesThroughSyncAndReload() throws Exception {
        File legacy =
                writeLegacy(
                        "generation.yml",
                        """
                generator:
                  layout: minecrafty
                  room_target:
                    min: 3
                    max: 5
                    enforce_minimum: false
                branching:
                  trunk:
                    room_pool: [crypt, hall]
                    straightness: 0.8
                  branches:
                    treasure:
                      room_pool: [vault]
                      eligible_depth: '2+'
                      occurrences: '1-2'
                      enforce_occurrences: true
                """);
        YamlConfiguration config = new YamlConfiguration();
        config.set("rules.combat.pvp", true);
        File main = folder.resolve("config.yml").toFile();
        assertTrue(GeneratorConfigMigration.migrate(legacy, main, config));
        ConfigSyncUtils.syncMissingAndObsolete(bundledDefaults(), config, Set.of("rules"));
        config.save(main);
        YamlConfiguration saved = new YamlConfiguration();
        saved.load(main);

        assertEquals("minecrafty", saved.getString("generator.layout"));
        assertEquals(3, saved.getInt("generator.room_target.min"));
        assertFalse(saved.getBoolean("generator.room_target.enforce_minimum", true));
        assertEquals(
                List.of("crypt", "hall"),
                saved.getStringList("generator.branching.trunk.room_pool"));
        assertEquals(0.8, saved.getDouble("generator.branching.trunk.straightness"));
        assertEquals("2+", saved.getString("generator.branching.branches.treasure.eligible_depth"));
        assertEquals("1-2", saved.getString("generator.branching.branches.treasure.occurrences"));
        assertEquals(
                List.of("vault"),
                saved.getStringList("generator.branching.branches.treasure.room_pool"));
        assertTrue(saved.getBoolean("rules.combat.pvp"));
        assertFalse(saved.contains("branching"));
        assertFalse(legacy.exists());
        assertTrue(folder.resolve("generation.yml.migrated").toFile().exists());

        saved.set("generator.branching.branches.treasure", null);
        assertFalse(GeneratorConfigMigration.migrate(legacy, main, saved));
        assertFalse(saved.contains("generator.branching.branches.treasure"));
    }

    @Test
    void explicitUnifiedSettingsWinWhileMissingSiblingSettingsAreImported() throws Exception {
        File legacy =
                writeLegacy(
                        "generation.yml",
                        """
                generator:
                  layout: minecrafty
                  room_target:
                    min: 3
                    max: 9
                branching:
                  trunk:
                    room_pool: [old]
                    straightness: 0.9
                """);
        YamlConfiguration config = new YamlConfiguration();
        config.set("generator.layout", "branching");
        config.set("generator.room_target.min", 6);
        config.set("generator.branching.trunk.room_pool", List.of());
        GeneratorConfigMigration.migrate(legacy, folder.resolve("config.yml").toFile(), config);

        assertEquals("branching", config.getString("generator.layout"));
        assertEquals(6, config.getInt("generator.room_target.min"));
        assertEquals(9, config.getInt("generator.room_target.max"));
        assertEquals(List.of(), config.getStringList("generator.branching.trunk.room_pool"));
        assertEquals(0.9, config.getDouble("generator.branching.trunk.straightness"));
    }

    @Test
    void migratesTheLegacyDefaultTemplateAndKeepsExistingArchives() throws Exception {
        File legacy =
                writeLegacy(
                        "generator_settings_default.yml",
                        "generator:\n  layout: minecrafty\nbranching:\n  branches: {}\n");
        Files.writeString(
                folder.resolve("generator_settings_default.yml.migrated"), "previous backup");
        YamlConfiguration config = new YamlConfiguration();
        File main = folder.resolve("config_default.yml").toFile();
        GeneratorConfigMigration.migrate(legacy, main, config);

        assertEquals(
                "minecrafty",
                YamlConfiguration.loadConfiguration(main).getString("generator.layout"));
        assertTrue(config.isConfigurationSection("generator.branching.branches"));
        assertEquals(
                "previous backup",
                Files.readString(folder.resolve("generator_settings_default.yml.migrated")));
        assertTrue(folder.resolve("generator_settings_default.yml.migrated.1").toFile().exists());
    }

    @Test
    void malformedLegacyYamlDoesNotOverwriteTheMainFileOrArchiveTheSource() throws Exception {
        File legacy = writeLegacy("generation.yml", "generator: [broken\n");
        Path main = folder.resolve("config.yml");
        Files.writeString(main, "existing: true\n");
        assertThrows(
                InvalidConfigurationException.class,
                () ->
                        GeneratorConfigMigration.migrate(
                                legacy, main.toFile(), new YamlConfiguration()));
        assertEquals("existing: true\n", Files.readString(main));
        assertTrue(legacy.exists());
    }

    @Test
    void failedFirstTemplateMigrationCanBeRepairedWithoutDefaultValuesMaskingLegacySettings()
            throws Exception {
        File legacy = writeLegacy("generator_settings_default.yml", "generator: [broken\n");
        File main = folder.resolve("config_default.yml").toFile();
        YamlConfiguration defaults = bundledDefaults();
        assertThrows(
                InvalidConfigurationException.class,
                () -> GeneratorConfigMigration.loadTemplate(legacy, main, defaults));
        assertFalse(main.exists());
        assertTrue(legacy.exists());

        Files.writeString(
                legacy.toPath(),
                "generator:\n  layout: minecrafty\nbranching:\n  trunk:\n    straightness: 0.9\n");
        YamlConfiguration repaired = GeneratorConfigMigration.loadTemplate(legacy, main, defaults);
        assertEquals("minecrafty", repaired.getString("generator.layout"));
        assertEquals(0.9, repaired.getDouble("generator.branching.trunk.straightness"));
        assertEquals(12, repaired.getInt("generator.branching.trunk.room_target.min"));
        assertTrue(main.exists());
        assertFalse(legacy.exists());
    }

    @Test
    void templateMigrationPreservesExplicitValuesAndWorksOnSubsequentLoads() throws Exception {
        File legacy =
                writeLegacy(
                        "generator_settings_default.yml",
                        "generator:\n  layout: minecrafty\nbranching:\n  trunk:\n    straightness: 0.9\n");
        File main = folder.resolve("config_default.yml").toFile();
        Files.writeString(main.toPath(), "generator:\n  layout: branching\n");
        YamlConfiguration first =
                GeneratorConfigMigration.loadTemplate(legacy, main, bundledDefaults());
        assertEquals("branching", first.getString("generator.layout"));
        assertEquals(0.9, first.getDouble("generator.branching.trunk.straightness"));
        first.set("generator.branching.trunk.straightness", 0.3);
        first.save(main);
        YamlConfiguration second =
                GeneratorConfigMigration.loadTemplate(legacy, main, bundledDefaults());
        assertEquals(0.3, second.getDouble("generator.branching.trunk.straightness"));
    }

    @Test
    void invalidLegacySectionsAreRejectedBeforeWritingOrModifyingTheTarget() throws Exception {
        File legacy =
                writeLegacy(
                        "generation.yml",
                        "generator:\n  layout: minecrafty\nbranching: [invalid]\n");
        File main = folder.resolve("config.yml").toFile();
        YamlConfiguration target = new YamlConfiguration();
        assertThrows(
                InvalidConfigurationException.class,
                () -> GeneratorConfigMigration.migrate(legacy, main, target));
        assertFalse(main.exists());
        assertTrue(target.getKeys(true).isEmpty());
        assertTrue(legacy.exists());
    }

    @Test
    void explicitMalformedUnifiedSectionsAreReportedWithoutBeingOverwritten() throws Exception {
        File legacy =
                writeLegacy("generation.yml", "branching:\n  trunk:\n    straightness: 0.9\n");
        File main = folder.resolve("config.yml").toFile();
        YamlConfiguration target = new YamlConfiguration();
        target.set("generator.branching", false);
        target.save(main);
        assertThrows(
                InvalidConfigurationException.class,
                () -> GeneratorConfigMigration.migrate(legacy, main, target));
        assertEquals(false, YamlConfiguration.loadConfiguration(main).get("generator.branching"));
        assertTrue(legacy.exists());
    }

    @Test
    void failedSaveKeepsTheLegacyFile() throws Exception {
        File legacy = writeLegacy("generation.yml", "generator:\n  layout: minecrafty\n");
        assertThrows(
                IOException.class,
                () ->
                        GeneratorConfigMigration.migrate(
                                legacy, folder.toFile(), new YamlConfiguration()));
        assertTrue(legacy.exists());
        assertFalse(folder.resolve("generation.yml.migrated").toFile().exists());
    }

    @Test
    void newMapTemplateContainsGeneratorSettingsWithoutASeparateFileOrTopLevelBranching()
            throws Exception {
        YamlConfiguration config = bundledDefaults();
        assertEquals("branching", config.getString("generator.layout"));
        assertEquals(12, config.getInt("generator.branching.trunk.room_target.min"));
        assertTrue(config.isConfigurationSection("generator.branching.branches"));
        assertFalse(config.contains("branching"));
        assertNull(getClass().getResource("/dungeons/generator_settings_default.yml"));
    }

    private File writeLegacy(String name, String yaml) throws IOException {
        return Files.writeString(folder.resolve(name), yaml).toFile();
    }

    private YamlConfiguration bundledDefaults() throws IOException {
        try (var reader =
                new InputStreamReader(
                        getClass().getResourceAsStream("/dungeons/config_default.yml"),
                        StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
}
