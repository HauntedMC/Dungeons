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
import nl.hauntedmc.dungeons.runtime.instance.listener.PlayListener;
import nl.hauntedmc.dungeons.support.RunFixture;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class PresentationContractTest {
    @TempDir Path directory;

    @Test
    void discoveryAndRunSettingsShowActualPermissionsEvenWithCommandsRestricted() throws Exception {
        var config = new YamlConfiguration();
        config.set("rules.building.break_blocks", false);
        config.set("rules.building.place_blocks", true);
        config.set("rules.combat.pvp", true);
        config.set("rules.movement.ender_pearls", false);
        try (var fixture = new RunFixture(directory, config)) {
            LangUtils.initialize();
            var runtime = fixture.paper.runtime;
            var command = new CommandContractTest.CommandFixture(fixture);
            var player = command.caller.player;
            when(player.locale()).thenReturn(Locale.ENGLISH);
            command.permissions.add("dungeons.play");
            var service = new ExperienceService(runtime);
            runtime.setExperience(service);

            command.execute("info", "crypt");
            var briefing = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(briefing.capture());
            assertTrue(
                    briefing.getAllValues().stream()
                            .anyMatch(
                                    line ->
                                            containsClick(
                                                    line,
                                                    ClickEvent.runCommand(
                                                            "/dungeon info crypt settings"))));
            clearInvocations(player);

            command.execute("info", "crypt", "settings");
            var preview = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(preview.capture());
            var expected = preview.getAllValues().stream().map(ComponentUtils::plainText).toList();
            assertTrue(expected.stream().anyMatch(line -> line.contains("Break blocks Blocked")));
            assertTrue(expected.stream().anyMatch(line -> line.contains("Place blocks Allowed")));
            assertTrue(expected.stream().anyMatch(line -> line.contains("PvP Allowed")));
            assertTrue(expected.stream().anyMatch(line -> line.contains("Ender Pearls Blocked")));

            var session = runtime.playerSessions().get(player);
            session.setInstance(fixture.run);
            when(player.getWorld()).thenReturn(fixture.world);
            service.joined(fixture.run, session);
            command.permissions.clear();
            var event =
                    new PlayerCommandPreprocessEvent(
                            player, "/du rules settings", java.util.Set.of());
            new PlayListener(fixture.run).onCommand(event);
            assertFalse(event.isCancelled());
            clearInvocations(player);

            command.execute("rules", "settings");
            var active = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(active.capture());
            assertEquals(
                    expected,
                    active.getAllValues().stream()
                            .limit(expected.size())
                            .map(ComponentUtils::plainText)
                            .toList());
        } finally {
            LangUtils.clear();
        }
    }

    @Test
    void objectiveActionsFollowProgressAndEmptyHintsGiveFeedback() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var yaml = new YamlConfiguration();
            yaml.set("objectives.vault.title", "Find the vault");
            yaml.save(new java.io.File(fixture.dungeon.getFolder(), "experience.yml"));
            when(fixture.world.getSpawnLocation())
                    .thenReturn(new org.bukkit.Location(fixture.world, 0, 70, 0));
            fixture.run.loadGame();
            var command = new CommandContractTest.CommandFixture(fixture);
            command.permissions.add("dungeons.play");
            var player = command.caller.player;
            when(player.locale()).thenReturn(Locale.ENGLISH);
            var runtime = fixture.paper.runtime;
            var service = new ExperienceService(runtime);
            runtime.setExperience(service);
            var session = runtime.playerSessions().get(player);
            session.setInstance(fixture.run);
            session.setDungeonRespawn(new org.bukkit.Location(fixture.world, 12, 71, 9));
            service.joined(fixture.run, session);
            LangUtils.initialize();

            clearInvocations(player);
            command.execute("rules");
            var rules = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(rules.capture());
            var facts = rules.getAllValues().stream().map(ComponentUtils::plainText).toList();
            assertTrue(facts.stream().anyMatch(line -> line.contains("Lives ∞")));
            assertTrue(
                    facts.stream()
                            .anyMatch(
                                    line ->
                                            line.contains("Checkpoint")
                                                    && line.contains("12, 71, 9")));
            assertTrue(facts.stream().anyMatch(line -> line.contains("Objectives 0/1 complete")));
            clearInvocations(player);

            command.execute("objectives", "hint");
            var output = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(output.capture());
            assertTrue(
                    output.getAllValues().stream()
                            .map(ComponentUtils::plainText)
                            .anyMatch(line -> line.contains("No hints available")));

            clearInvocations(player);
            command.execute("objectives", "pin", "vault");
            var pinned = ArgumentCaptor.forClass(Component.class);
            verify(player).sendMessage(pinned.capture());
            assertTrue(
                    ComponentUtils.plainText(pinned.getValue())
                            .contains("Pinned · Find the vault"));

            service.mechanics(fixture.run)
                    .progress()
                    .advance("vault", player.getUniqueId(), 1, "found");
            clearInvocations(player);
            command.execute("objectives");
            var completed = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(completed.capture());
            assertTrue(
                    completed.getAllValues().stream()
                            .map(ComponentUtils::plainText)
                            .anyMatch(line -> line.contains("✓ Find the vault")));
            assertTrue(
                    completed.getAllValues().stream()
                            .noneMatch(
                                    line ->
                                            containsClick(
                                                    line,
                                                    ClickEvent.runCommand(
                                                            "/dungeon objectives pin vault"))));
        } finally {
            LangUtils.clear();
        }
    }

    @Test
    void statusShowsPendingPreparationBeforeAFreshQueueEntryHasBeenRendered() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var command = new CommandContractTest.CommandFixture(fixture);
            var player = command.caller.player;
            when(player.locale()).thenReturn(Locale.ENGLISH);
            var runtime = fixture.paper.runtime;
            runtime.setQueueRegistry(command.queues);
            var service = new ExperienceService(runtime);
            runtime.setExperience(service);
            var session = runtime.playerSessions().get(player);
            var pending =
                    mock(nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry.Pending.class);
            when(pending.dungeon()).thenReturn(fixture.dungeon);
            runtime.preparations().register(session, pending);
            session.setAwaitingDungeon(true);
            LangUtils.initialize();

            command.execute("status");

            var output = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(output.capture());
            assertTrue(
                    output.getAllValues().stream()
                            .map(ComponentUtils::plainText)
                            .anyMatch(line -> line.contains("Preparing dungeon")));
            assertTrue(
                    output.getAllValues().stream()
                            .anyMatch(
                                    line ->
                                            containsClick(
                                                    line,
                                                    ClickEvent.runCommand("/dungeon leave"))));
        } finally {
            LangUtils.clear();
        }
    }

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
                                                    ClickEvent.runCommand(
                                                            "/dungeon help @discovery"))));
            assertTrue(
                    messages.stream()
                            .noneMatch(
                                    line ->
                                            ComponentUtils.plainText(line)
                                                    .startsWith("Dungeons ›")));

            clearInvocations(player);
            command.execute("help", "@discovery");
            var discovery = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(discovery.capture());
            assertTrue(
                    discovery.getAllValues().stream()
                            .anyMatch(
                                    line ->
                                            containsClick(
                                                    line,
                                                    ClickEvent.runCommand("/dungeon help play"))));
            clearInvocations(player);
            command.permissions.add("dungeons.staff.inspect");
            command.execute("help", "play");
            var usage = ArgumentCaptor.forClass(Component.class);
            verify(player, atLeastOnce()).sendMessage(usage.capture());
            var displayed = usage.getAllValues().stream().map(ComponentUtils::plainText).toList();
            assertTrue(displayed.getFirst().contains("/dungeon play"));
            assertTrue(displayed.stream().skip(1).noneMatch(line -> line.contains("/dungeon")));
            assertTrue(displayed.stream().anyMatch(line -> line.contains("<map> <difficulty>")));
            assertTrue(
                    usage.getAllValues().stream()
                            .map(ComponentUtils::plainText)
                            .noneMatch(line -> line.contains("/dungeon player")));
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
            assertTrue(lines.stream().anyMatch(line -> line.contains("› Minimal ✓")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("› Balanced")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("› Geluid ×")));
            assertTrue(lines.stream().anyMatch(line -> line.contains("› Titels ✓")));
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
