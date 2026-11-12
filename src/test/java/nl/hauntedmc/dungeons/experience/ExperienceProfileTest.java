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

class ExperienceProfileTest {
    @TempDir Path directory;

    @Test
    void failedPreferenceWritesCannotChangeTheCachedPreference() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var player = new PlayerState();
            var session = fixture.session(player);
            session.setExperiencePreference("profile", "balanced");
            fixture.paper.state.failWrites = true;
            assertThrows(
                    IllegalStateException.class,
                    () -> session.setExperiencePreference("profile", "immersive"));
            assertEquals("balanced", session.experiencePreference("profile", "inherit"));
            fixture.paper.state.failWrites = false;
        }
    }

    @Test
    void positionAndProgressCommitTogetherAndRejectIncompatibleSnapshots() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var player = new PlayerState();
            var session = fixture.session(player);
            String dungeon = fixture.dungeon.getDungeonId().toString();
            var first = new Location(fixture.world, 3, 70, 5);
            session.setExperienceSavePoint(
                    first, "crypt", dungeon, "content-v1", "hard", Map.of("seals", 2));
            assertEquals(
                    Map.of("seals", 2),
                    session.experienceCheckpoint(dungeon, "content-v1", "hard"));
            assertTrue(session.experienceCheckpoint(dungeon, "content-v2", "hard").isEmpty());
            assertTrue(session.incompatibleExperienceCheckpoint(dungeon, "content-v1", "easy"));
            String profile =
                    fixture.paper.state.payload(
                            "player-profile", player.player.getUniqueId().toString());
            fixture.paper.state.failWrites = true;
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            session.setExperienceSavePoint(
                                    new Location(fixture.world, 20, 80, 20),
                                    "crypt",
                                    dungeon,
                                    "content-v1",
                                    "hard",
                                    Map.of("seals", 3)));
            assertEquals(
                    profile,
                    fixture.paper.state.payload(
                            "player-profile", player.player.getUniqueId().toString()));
            assertEquals(3, session.getDungeonSavePoint("crypt").getX());
            assertEquals(
                    Map.of("seals", 2),
                    session.experienceCheckpoint(dungeon, "content-v1", "hard"));
            fixture.paper.state.failWrites = false;
            session.discardExperienceCheckpoint("crypt", dungeon);
            assertNull(session.getDungeonSavePoint("crypt"));
            assertFalse(session.incompatibleExperienceCheckpoint(dungeon, "content-v2", "easy"));
        }
    }
}
