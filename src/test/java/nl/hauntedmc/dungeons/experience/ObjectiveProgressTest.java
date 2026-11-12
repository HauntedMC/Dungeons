package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObjectiveProgressTest {
    private ExperienceDefinition definition(String content) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(content);
        return ExperienceDefinition.parse(yaml);
    }

    @Test
    void vanillaLootRequiresNoMechanicsFileOrSyntheticGoals(@TempDir Path directory)
            throws Exception {
        var definition = ExperienceDefinition.load(directory);
        assertFalse(definition.hasMechanics());
        assertEquals("manual", definition.completion());
        var progress = new ObjectiveProgress(definition, change -> fail("Loot has no progression"));
        assertTrue(progress.views(UUID.randomUUID()).isEmpty());
        assertFalse(progress.allRequiredCompleted(UUID.randomUUID()));
        Files.writeString(
                directory.resolve("experience.yml"), "enabled: false\nobjectives: invalid\n");
        assertFalse(ExperienceDefinition.load(directory).hasMechanics());
    }

    @Test
    void sharedAndPersonalObjectivesDeduplicateIndependently() throws Exception {
        var definition =
                definition(
                        """
                objectives:
                  seals: {source: signal, target: 3}
                  personal: {source: signal, scope: player, target: 2}
                """);
        var changes = new ArrayList<ObjectiveProgress.Change>();
        var progress = new ObjectiveProgress(definition, changes::add);
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        progress.advance("seals", alice, 1, "first-seal");
        progress.advance("seals", bob, 1, "first-seal");
        assertEquals(1, progress.count("seals", bob));
        progress.advance("personal", alice, 1, "same-signal");
        progress.advance("personal", bob, 1, "same-signal");
        assertEquals(1, progress.count("personal", alice));
        assertEquals(1, progress.count("personal", bob));
        progress.advance("seals", alice, Integer.MAX_VALUE, "remaining");
        progress.advance("seals", bob, 1, "duplicate-after-completion");
        assertEquals(3, progress.count("seals", alice));
        assertEquals(
                1,
                changes.stream()
                        .filter(
                                change ->
                                        change.kind().equals("objective-completed")
                                                && change.id().equals("seals"))
                        .count());
    }

    @Test
    void optionalStagesAndHiddenGoalsDoNotRequireAVisibleHud() throws Exception {
        var definition =
                definition(
                        """
                objectives:
                  first: {source: signal}
                  secret: {source: signal, hidden: true, optional: true}
                  second: {source: signal}
                tracks:
                  story:
                    stages:
                      introduction: {objectives: [first, secret]}
                      finale: {objectives: [second]}
                """);
        var changes = new ArrayList<ObjectiveProgress.Change>();
        var progress = new ObjectiveProgress(definition, changes::add);
        UUID player = UUID.randomUUID();
        progress.advance("second", player, 1, "too-early");
        assertEquals(0, progress.count("second", player));
        assertEquals(1, progress.views(player).size());
        progress.reveal("secret", player);
        assertEquals(2, progress.views(player).size());
        progress.advance("first", player, 1, "first");
        assertTrue(progress.active("second", player));
        assertFalse(progress.completed("secret", player));
        progress.advance("second", player, 1, "second");
        assertTrue(progress.allRequiredCompleted(player));
        assertEquals(
                2,
                changes.stream().filter(change -> change.kind().equals("stage-completed")).count());
        assertEquals(
                1,
                changes.stream().filter(change -> change.kind().equals("track-completed")).count());
    }

    @Test
    void onlyExplicitPersonalStateIsSavedWithoutReplayingCompletionEffects() throws Exception {
        var definition =
                definition(
                        """
                objectives:
                  personal: {scope: player, persistent: true, target: 3}
                  ephemeral: {scope: player, target: 2}
                  shared: {scope: run, target: 2}
                """);
        UUID player = UUID.randomUUID();
        var progress = new ObjectiveProgress(definition, change -> {});
        progress.advance("personal", player, 2, null);
        progress.advance("shared", player, 1, null);
        progress.advance("ephemeral", player, 1, null);
        assertEquals(Map.of("personal", 2), progress.save(player));
        var restored =
                new ObjectiveProgress(
                        definition, change -> fail("Restore must not replay graph side effects"));
        restored.restore(player, Map.of("personal", 2, "ephemeral", 2, "shared", 2, "unknown", 10));
        assertEquals(2, restored.count("personal", player));
        assertEquals(0, restored.count("shared", player));
        assertEquals(0, restored.count("ephemeral", player));
    }

    @Test
    void captureCanRepresentAnEmptyArenaAndCannotInventKills() throws Exception {
        var definition =
                definition(
                        "objectives:\n  crystals: {source: destroy, selector: 'type:END_CRYSTAL', capture: true}\n");
        var progress = new ObjectiveProgress(definition, change -> {});
        UUID player = UUID.randomUUID();
        progress.capturedTarget("crystals", 0, player);
        assertTrue(progress.completed("crystals", player));
        assertEquals(0, progress.count("crystals", player));
        assertEquals(0, progress.target("crystals"));
    }

    @Test
    void invalidCompositionIsRejectedBeforeGameplay() {
        for (String yaml :
                new String[] {
                    "completion: objectives",
                    "objectives:\n  broken: {target: 0}",
                    "objectives:\n  broken: {source: reach}",
                    "objectives:\n  broken: {persistent: true, scope: run}",
                    "objectives:\n  broken: {source: signal, capture: true}",
                    "tracks:\n  story:\n    stages:\n      first: {objectives: [missing]}",
                    "objectives:\n  personal: {scope: player}\ntracks:\n  story:\n    stages:\n      first: {objectives: [personal]}"
                }) assertThrows(IllegalArgumentException.class, () -> definition(yaml), yaml);
    }

    @Test
    void optionalTracksCanBeActivatedWithoutBlockingTheMainAdventure() throws Exception {
        var definition =
                definition(
                        """
                completion: objectives
                objectives:
                  main: {target: 1}
                  side: {scope: player, target: 2, persistent: true, hidden: true}
                tracks:
                  sidequest:
                    scope: player
                    optional: true
                    auto_start: false
                    stages:
                      discovery: {objectives: [side]}
                """);
        UUID player = UUID.randomUUID();
        var progress = new ObjectiveProgress(definition, change -> {});
        progress.advance("side", player, 1, "locked");
        assertEquals(0, progress.count("side", player));
        progress.activateStage("sidequest/discovery", player);
        progress.reveal("side", player);
        progress.advance("side", player, 1, "personal");
        progress.advance("main", player, 1, "finish");
        assertTrue(progress.allRequiredCompleted(player));
        assertEquals(java.util.List.of("sidequest"), progress.savedTracks(player));
        assertEquals(java.util.List.of("side"), progress.savedReveals(player));
        var restored =
                new ObjectiveProgress(
                        definition, change -> fail("Restoring must not replay rewards"));
        restored.restore(player, progress.save(player));
        restored.restoreMetadata(
                player, progress.savedReveals(player), progress.savedTracks(player));
        assertTrue(restored.active("side", player));
        assertTrue(restored.visible("side", player));
        assertEquals(1, restored.count("side", player));
    }
}
