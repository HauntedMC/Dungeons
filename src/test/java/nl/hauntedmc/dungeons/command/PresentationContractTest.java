package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.dungeons.experience.ExperienceService;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.support.RunFixture;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class PresentationContractTest {
    @TempDir Path directory;

    @Test
    void helpUsesClickableTopicsAndPagesInsteadOfDumpingEveryRoute() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandContractTest.CommandFixture(fixture);
            var player = command.caller.player;
            when(player.locale()).thenReturn(Locale.ENGLISH);
            command.permissions.addAll(
                    java.util.List.of("dungeons.play", "dungeons.play.difficulty"));
            LangUtils.initialize();
            command.execute("help");
            var capture = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(capture.capture());
            var messages = capture.getAllValues();
            assertTrue(messages.size() <= 9);
            assertTrue(
                    messages.stream()
                            .flatMap(
                                    line ->
                                            java.util.stream.StreamSupport.stream(
                                                    line.iterable(ComponentIteratorType.DEPTH_FIRST)
                                                            .spliterator(),
                                                    false))
                            .anyMatch(part -> NamedTextColor.AQUA.equals(part.color())));
            assertTrue(
                    messages.stream()
                            .anyMatch(
                                    line ->
                                            containsClick(
                                                    line,
                                                    ClickEvent.runCommand("/dungeon help play"))));
            assertTrue(
                    messages.stream()
                            .noneMatch(
                                    line ->
                                            ComponentUtils.plainText(line)
                                                    .startsWith("Dungeons ›")));

            clearInvocations(player);
            command.execute("help", "play");
            var usage = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(usage.capture());
            assertTrue(
                    usage.getAllValues().stream()
                            .anyMatch(
                                    line ->
                                            containsClick(
                                                    line,
                                                    ClickEvent.suggestCommand("/dungeon play "))));
        } finally {
            LangUtils.clear();
        }
    }

    @Test
    void entryCardShowsCurrentAvailabilityAndAnActionWithoutRawConfigurationValues()
            throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandContractTest.CommandFixture(fixture);
            var player = command.caller.player;
            when(player.locale()).thenReturn(Locale.ENGLISH);
            command.permissions.add("dungeons.play");
            fixture.paper.runtime.setExperience(new ExperienceService(fixture.paper.runtime));
            LangUtils.initialize();

            command.execute("info", "crypt");
            var capture = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(capture.capture());
            var lines = capture.getAllValues();
            assertTrue(lines.size() <= 5);
            assertTrue(
                    lines.stream()
                            .anyMatch(
                                    line ->
                                            containsClick(
                                                    line,
                                                    ClickEvent.runCommand("/dungeon play crypt"))));
            assertTrue(
                    lines.stream()
                            .flatMap(
                                    line ->
                                            java.util.stream.StreamSupport.stream(
                                                    line.iterable(ComponentIteratorType.DEPTH_FIRST)
                                                            .spliterator(),
                                                    false))
                            .anyMatch(part -> NamedTextColor.GREEN.equals(part.color())));
            assertTrue(
                    lines.stream()
                            .map(ComponentUtils::plainText)
                            .noneMatch(line -> line.contains("Available: true")));
        } finally {
            LangUtils.clear();
        }
    }

    @Test
    void hudMarksTheSelectedProfileAndUsesDistinctToggleSymbols() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandContractTest.CommandFixture(fixture);
            var player = command.caller.player;
            when(player.locale()).thenReturn(Locale.forLanguageTag("nl-NL"));
            command.permissions.add("dungeons.play");
            fixture.paper.runtime.setExperience(new ExperienceService(fixture.paper.runtime));
            LangUtils.initialize();
            var session = fixture.paper.runtime.playerSessions().get(player);
            session.setExperiencePreference("profile", "minimal");
            session.setExperiencePreference("sounds", "false");

            command.execute("hud");

            var capture = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(capture.capture());
            var lines = capture.getAllValues().stream().map(ComponentUtils::plainText).toList();
            assertTrue(lines.stream().anyMatch(line -> line.contains("[Minimal ✓]")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("[Balanced]")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("[Geluid ×]")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("[Titels ✓]")));
            assertTrue(
                    capture.getAllValues().stream()
                            .anyMatch(
                                    line ->
                                            containsClick(
                                                    line,
                                                    ClickEvent.runCommand(
                                                            "/dungeon hud sounds true"))));
        } finally {
            LangUtils.clear();
        }
    }

    private static boolean containsClick(Component line, ClickEvent click) {
        for (Component part : line.iterable(ComponentIteratorType.DEPTH_FIRST))
            if (click.equals(part.clickEvent())) return true;
        return false;
    }
}
