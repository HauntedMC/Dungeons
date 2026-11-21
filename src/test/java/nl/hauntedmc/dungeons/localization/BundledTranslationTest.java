package nl.hauntedmc.dungeons.localization;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundledTranslationTest {
    @TempDir Path directory;

    private DungeonMessages messages(Language language) {
        return new DungeonMessages(
                directory,
                getClass().getClassLoader(),
                FrameworkLogger.noop(),
                audience -> language);
    }

    @Test
    void consoleUsesTheDefaultCatalogWhilePlayerMessagesUseTheirLanguage() {
        var english = messages(Language.EN);
        var console = mock(ConsoleCommandSender.class);
        var player = mock(Player.class);
        String key = "commands.list.header";
        assertEquals(english.template(key, null), english.template(key, console));
        assertNotEquals(english.template(key, console), english.template(key, player));
        assertEquals(
                english.lines("commands.team.help.lines", null),
                english.lines("commands.team.help.lines", console));
        assertNotEquals(
                english.lines("commands.team.help.lines", console),
                english.lines("commands.team.help.lines", player));
    }

    @Test
    void theExampleIsCopiedAndSelectedWithScalarAndListFallback() throws Exception {
        var english = messages(Language.EN);
        var player = mock(Player.class);
        assertTrue(Files.isRegularFile(directory.resolve("lang/messages_EN.yml")));
        assertFalse(Files.exists(directory.resolve("messages.yml")));
        assertEquals(
                "Dungeon 'crypt' does not exist.",
                ComponentUtils.plainText(
                        english.message(
                                "commands.play.dungeon-not-found",
                                player,
                                false,
                                Map.of("dungeon", "crypt"))));
        assertTrue(
                english.lines("commands.team.help.lines", player)
                        .getFirst()
                        .contains("Create a temporary"));
        assertEquals(
                english.template("input.invalid-integer", null),
                english.template("input.invalid-integer", player));
        assertEquals(
                english.lines("general.items.function-tool.lore", null),
                english.lines("general.items.function-tool.lore", player));
        var dutch = messages(Language.NL);
        assertEquals(
                "Dungeon 'crypt' bestaat niet.",
                ComponentUtils.plainText(
                        dutch.message(
                                "commands.play.dungeon-not-found",
                                player,
                                false,
                                Map.of("dungeon", "crypt"))));
        assertTrue(
                dutch.lines("commands.team.help.lines", player)
                        .getFirst()
                        .contains("Maak een tijdelijk"));
    }

    @Test
    void existingTranslationsSurviveDefaultsAndReloadWhileAnEmptyListSuppressesDefaults()
            throws Exception {
        Files.createDirectories(directory.resolve("lang"));
        Files.writeString(
                directory.resolve("lang/messages_EN.yml"),
                "general:\n"
                        + "  errors:\n"
                        + "    no-permission: 'Operator English'\n"
                        + "commands:\n"
                        + "  team:\n"
                        + "    help:\n"
                        + "      lines: []\n");
        var english = messages(Language.EN);
        var player = mock(Player.class);
        assertEquals("Operator English", english.template("general.errors.no-permission", player));
        assertTrue(english.lines("commands.team.help.lines", player).isEmpty());
        assertTrue(english.template("commands.play.usage", player).contains("/dungeon"));
        Files.writeString(
                directory.resolve("lang/messages_EN.yml"),
                "general:\n  errors:\n    no-permission: 'Edited English'\n");
        english.reloadLocalizationQuietly();
        assertEquals("Edited English", english.template("general.errors.no-permission", player));
        assertEquals(9, english.lines("commands.team.help.lines", player).size());
    }

    @Test
    void dutchGameplayTermsMatchTheHudMenusAndChat() {
        var dutch = messages(Language.NL);
        var player = mock(Player.class);
        var labels =
                Map.of(
                        "commands.ui.topic-guide", "Guide",
                        "experience.guide-title", "Dungeon Guide",
                        "experience.objectives", "Objectives",
                        "experience.spawn", "Spawnpoint",
                        "experience.checkpoint", "Checkpoint",
                        "experience.spectating", "Spectator Mode",
                        "experience.lobby", "Lobby");
        labels.forEach(
                (key, value) ->
                        assertEquals(
                                value,
                                ComponentUtils.plainText(
                                        dutch.message(key, player, false, Map.of())),
                                key));
        assertEquals(
                "Difficulty  Hard",
                ComponentUtils.plainText(
                        dutch.message(
                                "experience.ui-hud-difficulty",
                                player,
                                false,
                                Map.of("value", "Hard"))));
        assertTrue(dutch.template("experience.spawn-explanation", player).contains("checkpoint"));
        assertTrue(dutch.template("experience.ui-leave-progress", player).contains("savepoint"));
        assertEquals(
                " ◆ Volgende wave · Skeletons",
                ComponentUtils.plainText(
                        dutch.message(
                                "experience.wave-started",
                                player,
                                false,
                                Map.of("value", "Skeletons"))));
    }

    @Test
    void startupUpdatesPreviousBundledWordingAndRetainsOperatorOverrides() throws Exception {
        Path lang = Files.createDirectories(directory.resolve("lang"));
        Files.writeString(
                lang.resolve("messages.yml"),
                """
                experience:
                  spawn: '<HauntedMC:Text>Startpunt'
                  objectives: 'Server objectives'
                  balanced: '<HauntedMC:Text>Gebalanceerd'
                commands:
                  edit:
                    help:
                      lines: ['Custom editor help']
                """);
        Files.writeString(
                lang.resolve("messages_NL.yml"),
                """
                experience:
                  balanced: '<HauntedMC:Text>Gebalanceerd'
                """);
        Files.writeString(
                lang.resolve("messages_EN.yml"),
                """
                experience:
                  fresh-start: '<HauntedMC:Text>[Start fresh]'
                  spawn: 'Server spawn'
                """);
        var player = mock(Player.class);
        var dutch = messages(Language.NL);
        assertEquals("<HauntedMC:Text>Spawnpoint", dutch.template("experience.spawn", player));
        assertEquals("Server objectives", dutch.template("experience.objectives", player));
        assertEquals("<HauntedMC:Text>Balanced", dutch.template("experience.balanced", null));
        assertEquals("<HauntedMC:Text>Gebalanceerd", dutch.template("experience.balanced", player));
        assertEquals(
                List.of("Custom editor help"), dutch.lines("commands.edit.help.lines", player));
        var english = messages(Language.EN);
        assertEquals(
                "<HauntedMC:Text>Start fresh", english.template("experience.fresh-start", player));
        assertEquals("Server spawn", english.template("experience.spawn", player));
    }

    @Test
    void everyExampleKeyMatchesTheDefaultTypeAndPlaceholderContract() throws Exception {
        var defaults = resource("lang/messages.yml");
        var example = resource("lang/messages_EN.yml");
        for (String key : example.getKeys(true)) {
            if (example.isConfigurationSection(key)) continue;
            Object value = example.get(key), original = defaults.get(key);
            assertNotNull(original, key);
            assertEquals(original instanceof List<?>, value instanceof List<?>, key);
            assertEquals(placeholders(original), placeholders(value), key);
        }
    }

    private YamlConfiguration resource(String path) throws Exception {
        try (var input =
                        Objects.requireNonNull(
                                getClass().getClassLoader().getResourceAsStream(path));
                var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        }
    }

    private static Set<String> placeholders(Object value) {
        var matcher = Pattern.compile("\\{[a-zA-Z0-9_.-]+}").matcher(String.valueOf(value));
        var names = new HashSet<String>();
        while (matcher.find()) names.add(matcher.group());
        return names;
    }
}
