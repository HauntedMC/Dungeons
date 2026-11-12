package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.RunFixture;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExperienceCompatibilityTest {
    @TempDir Path directory;
    private static final String CONTENT =
            """
            version: 1
            objectives:
              seals: {scope: player, persistent: true, target: 3, title: Seals, hint: Find seals}
            tracks:
              journey:
                scope: player
                stages:
                  first: {title: First, objectives: [seals]}
            groups:
              guards:
                entity: ZOMBIE
                boss: Guard
                location: {x: 0, y: 70, z: 0}
            encounters:
              arena:
                title: Arena
                waves:
                  first: {groups: [guards]}
            """;

    private static YamlConfiguration content() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(CONTENT);
        return yaml;
    }

    @Test
    void cosmeticChangesPreserveDurableProgressAndMetadata() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var session = fixture.session(new PlayerState());
            var original = ExperienceDefinition.parse(content());
            String id = fixture.dungeon.getDungeonId().toString();
            session.setExperienceSavePoint(
                    new Location(fixture.world, 1, 70, 1),
                    "crypt",
                    id,
                    original.fingerprint(),
                    "hard",
                    Map.of("seals", 2),
                    java.util.List.of("seals"),
                    java.util.List.of("journey"));
            var yaml = content();
            yaml.set("objectives.seals.title", "New title");
            yaml.set("objectives.seals.description", "New description");
            yaml.set("objectives.seals.hint", "A clearer hint");
            yaml.set("tracks.journey.stages.first.title", "Welcome");
            yaml.set("groups.guards.boss", "Renamed boss");
            yaml.set("encounters.arena.title", "New arena name");
            var edited = ExperienceDefinition.parse(yaml);
            assertEquals(original.fingerprint(), edited.fingerprint());
            assertNotEquals(original.legacyFingerprint(), edited.legacyFingerprint());
            assertFalse(session.incompatibleExperienceCheckpoint(id, edited, "hard"));
            assertEquals(Map.of("seals", 2), session.experienceCheckpoint(id, edited, "hard"));
            assertEquals(java.util.List.of("seals"), session.experienceSavedReveals(id));
            assertEquals(java.util.List.of("journey"), session.experienceSavedTracks(id));
            assertTrue(session.incompatibleExperienceCheckpoint(id, edited, "easy"));
            assertTrue(session.experienceCheckpoint(id, edited, "easy").isEmpty());
        }
    }

    @Test
    void removedObjectivesChangedTargetsAndScopesRejectSavedProgress() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var session = fixture.session(new PlayerState());
            var original = ExperienceDefinition.parse(content());
            String id = fixture.dungeon.getDungeonId().toString();
            session.setExperienceSavePoint(
                    new Location(fixture.world, 1, 70, 1),
                    "crypt",
                    id,
                    original.fingerprint(),
                    "hard",
                    Map.of("seals", 2));
            for (String change : java.util.List.of("remove", "target", "scope")) {
                var yaml = content();
                switch (change) {
                    case "remove" -> {
                        yaml.set("objectives.seals", null);
                        yaml.set("tracks", null);
                    }
                    case "target" -> yaml.set("objectives.seals.target", 4);
                    case "scope" -> {
                        yaml.set("objectives.seals.scope", "run");
                        yaml.set("objectives.seals.persistent", false);
                        yaml.set("tracks.journey.scope", "run");
                    }
                }
                var edited = ExperienceDefinition.parse(yaml);
                assertTrue(session.incompatibleExperienceCheckpoint(id, edited, "hard"), change);
                assertTrue(session.experienceCheckpoint(id, edited, "hard").isEmpty(), change);
            }
        }
    }

    @Test
    void unchangedLegacyCheckpointCanUpgradeButUnverifiableLegacyEditsCannot() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var session = fixture.session(new PlayerState());
            var definition = ExperienceDefinition.parse(content());
            String id = fixture.dungeon.getDungeonId().toString();
            var point = new Location(fixture.world, 1, 70, 1);
            session.setExperienceSavePoint(
                    point, "crypt", id, definition.legacyFingerprint(), "hard", Map.of("seals", 2));
            assertFalse(session.incompatibleExperienceCheckpoint(id, definition, "hard"));
            assertEquals(Map.of("seals", 2), session.experienceCheckpoint(id, definition, "hard"));
            var yaml = content();
            yaml.set("objectives.seals.hint", "New hint");
            var cosmetic = ExperienceDefinition.parse(yaml);
            assertTrue(session.incompatibleExperienceCheckpoint(id, cosmetic, "hard"));
            session.setExperienceSavePoint(
                    point,
                    "crypt",
                    id,
                    definition.fingerprint(),
                    "hard",
                    session.experienceCheckpoint(id, definition, "hard"));
            assertFalse(session.incompatibleExperienceCheckpoint(id, cosmetic, "hard"));
            assertEquals(Map.of("seals", 2), session.experienceCheckpoint(id, cosmetic, "hard"));
        }
    }
}
