package nl.hauntedmc.dungeons.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.RunFixture;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.dungeons.util.text.MessageUtils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

class LocalizedDeliveryTest {
    @TempDir Path directory;

    @Test
    void componentNoticesPreserveLiteralPrefixTextAndInteractiveRewardsUseTheRootCommand()
            throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            LangUtils.initialize();
            var text =
                    LangUtils.notice(
                            Component.text("Heading ")
                                    .append(Component.text("[Dungeons] is literal")));
            assertEquals(
                    "[Dungeons] Heading [Dungeons] is literal", ComponentUtils.plainText(text));
            var state = new PlayerState();
            MessageUtils.sendClickableCommand(
                    state.player,
                    LangUtils.component(
                            "instance.play.rewards.unclaimed-rewards", state.player, true),
                    "dungeon rewards");
            var capture = ArgumentCaptor.forClass(Component.class);
            verify(state.player).sendMessage(capture.capture());
            assertEquals(
                    ClickEvent.runCommand("/dungeon rewards"), capture.getValue().clickEvent());
        } finally {
            LangUtils.clear();
        }
    }

    @Test
    void broadcastsResolveEachRecipientsLanguageAndNeverSendSerializedCodesAsText()
            throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            Files.createDirectories(directory.resolve("lang"));
            Files.writeString(
                    directory.resolve("lang/messages_EN.yml"),
                    "test:\n  notice: '<HauntedMC:Success>Hello {name}'\n");
            Files.writeString(
                    directory.resolve("lang/messages_NL.yml"),
                    "test:\n  notice: '<HauntedMC:Success>Hoi {name}'\n");
            LangUtils.initialize();
            var english = new PlayerState();
            var dutch = new PlayerState();
            when(english.player.locale()).thenReturn(Locale.ENGLISH);
            when(dutch.player.locale()).thenReturn(Locale.forLanguageTag("nl-NL"));
            fixture.run.getPlayers().add(fixture.session(english));
            fixture.run.getPlayers().add(fixture.session(dutch));
            String input = "<click:run_command:/op bad>&cAlex</click>";
            fixture.run.messagePlayers("test.notice", LangUtils.placeholder("name", input));
            var capture = ArgumentCaptor.forClass(Component.class);
            verify(english.player).sendMessage(capture.capture());
            assertEquals("[Dungeons] Hello " + input, ComponentUtils.plainText(capture.getValue()));
            verify(dutch.player).sendMessage(capture.capture());
            assertEquals("[Dungeons] Hoi " + input, ComponentUtils.plainText(capture.getValue()));
            assertFalse(ComponentUtils.plainText(capture.getValue()).contains("§"));
        } finally {
            LangUtils.clear();
        }
    }
}
