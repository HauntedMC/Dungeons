package nl.hauntedmc.dungeons.localization;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.featureframework.toolkit.io.localization.Language;
import nl.hauntedmc.featureframework.toolkit.log.FrameworkLogger;
import nl.hauntedmc.theme.HauntedMcColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class DungeonMessagesTest {
    @TempDir Path directory;

    private DungeonMessages messages() {
        return new DungeonMessages(
                directory,
                getClass().getClassLoader(),
                FrameworkLogger.noop(),
                audience -> Language.EN);
    }

    @Test
    void sharedThemeAndLegacyTemplatesProduceActualAdventureColors() {
        var messages = messages();
        var text = messages.render("<HauntedMC:Success>Ready &cError <map>", Map.of());
        assertEquals("Ready Error <map>", ComponentUtils.plainText(text));
        assertTrue(text.toString().contains(HauntedMcColor.SUCCESS.textColor().asHexString()));
        assertFalse(ComponentUtils.plainText(text).contains("§"));
    }

    @Test
    void placeholdersCannotInjectTagsLegacyCodesOrOtherPlaceholders() {
        var messages = messages();
        String attack = "<click:run_command:/op bad>&c§l{player}</click>";
        var text =
                messages.render(
                        "<HauntedMC:Text>{reason} {player}",
                        Map.of("reason", attack, "player", "Alex"));
        assertEquals(attack + " Alex", ComponentUtils.plainText(text));
        assertFalse(hasClick(text));
        var roundTrip = ComponentUtils.component(ComponentUtils.serialize(text));
        assertEquals(attack.replace("§", "\\u00a7") + " Alex", ComponentUtils.plainText(roundTrip));
        assertFalse(hasClick(roundTrip));
    }

    @Test
    void componentPlaceholdersRetainExplicitAuthorizedClickActions() {
        var text =
                messages()
                        .render(
                                "Confirm {button}",
                                Map.of(
                                        "button",
                                        Component.text("here")
                                                .clickEvent(
                                                        ClickEvent.suggestCommand(
                                                                "/dungeon help"))));
        assertTrue(hasClick(text));
        assertEquals("Confirm here", ComponentUtils.plainText(text));
    }

    @Test
    void customizationMigrationTranslationsAndReloadHaveDeterministicFallback() throws Exception {
        Files.writeString(
                directory.resolve("messages.yml"),
                "general:\n  prefix: '[Custom]'\ncustom:\n  welcome: 'Hello {name}'\n");
        var messages = messages();
        Files.writeString(
                directory.resolve("lang/messages_EN.yml"),
                "custom:\n  welcome: 'Welcome {name}'\n");
        messages.reloadLocalizationQuietly();
        var audience = Mockito.mock(Player.class);
        assertEquals(
                "Welcome Alex",
                ComponentUtils.plainText(
                        messages.message(
                                "custom.welcome", audience, false, Map.of("name", "Alex"))));
        assertTrue(ComponentUtils.plainText(messages.prefix(null)).startsWith("[Custom]"));
        assertEquals(
                "Hello Alex",
                ComponentUtils.plainText(
                        messages.message("custom.welcome", null, false, Map.of("name", "Alex"))));
        Files.writeString(
                directory.resolve("lang/messages_EN.yml"),
                "custom:\n  welcome: 'Changed {name}'\n");
        messages.reloadLocalizationQuietly();
        assertEquals(
                "Changed Alex",
                ComponentUtils.plainText(
                        messages.message(
                                "custom.welcome", audience, false, Map.of("name", "Alex"))));
        assertFalse(Files.exists(directory.resolve("messages.yml")));
        assertTrue(Files.readString(directory.resolve("lang/messages.yml")).contains("Hello"));
    }

    @Test
    void sharedWordmarkGradientsAreExpandedWithoutLeakingThemeTags() {
        var text =
                messages()
                        .render(
                                "<HauntedMC:WordmarkHaunted>Haunted</HauntedMC><HauntedMC:WordmarkMc>MC</HauntedMC>",
                                Map.of());
        assertEquals("HauntedMC", ComponentUtils.plainText(text));
        assertTrue(text.children().size() > 1);
    }

    @Test
    void gradientsAndRainbowsCannotSplitPlaceholderMarkersOrReparseTheirContents() {
        for (String effect : List.of("gradient:red:blue", "rainbow", "HauntedMC:WordmarkHaunted")) {
            String close = effect.startsWith("HauntedMC") ? "HauntedMC" : effect.split(":")[0];
            var text =
                    messages()
                            .render(
                                    "<" + effect + ">A {name} Z</" + close + ">",
                                    Map.of("name", "<click:run_command:/op bad>&cBad</click>"));
            assertEquals(
                    "A <click:run_command:/op bad>&cBad</click> Z",
                    ComponentUtils.plainText(text),
                    effect);
            assertFalse(hasClick(text));
        }
    }

    @Test
    void literalPrivateUseCharactersAndNestedComponentsSurviveSafeReplacement() {
        var text =
                messages()
                        .render(
                                "\ue000 {first} {second}",
                                Map.of(
                                        "first",
                                        "\ue001{second}",
                                        "second",
                                        Component.text(
                                                "Ready", HauntedMcColor.SUCCESS.textColor())));
        assertEquals("\ue000 \ue001{second} Ready", ComponentUtils.plainText(text));
        assertTrue(text.toString().contains(HauntedMcColor.SUCCESS.textColor().asHexString()));
    }

    @Test
    void legacyRelocationKeepsCustomizationsAndUsesOnlyOneBundledDefaultResource()
            throws Exception {
        Files.writeString(
                directory.resolve("messages.yml"),
                "general:\n"
                        + "  prefix: '&3[Custom]'\n"
                        + "  errors:\n"
                        + "    no-permission: 'Operator custom text'\n");
        messages();
        var migrated =
                YamlConfiguration.loadConfiguration(
                        directory.resolve("lang/messages.yml").toFile());
        assertEquals("&3[Custom]", migrated.getString("general.prefix"));
        assertEquals("Operator custom text", migrated.getString("general.errors.no-permission"));
        assertFalse(Files.exists(directory.resolve("messages.yml")));
        assertNull(getClass().getResource("/messages.yml"));
        assertNotNull(migrated.getString("commands.list.line"));
        migrated.set("general.prefix", "Second custom prefix");
        migrated.save(directory.resolve("lang/messages.yml").toFile());
        messages();
        assertTrue(
                Files.readString(directory.resolve("lang/messages.yml"))
                        .contains("Second custom prefix"));
    }

    @Test
    void anExistingCanonicalFileWinsAndTheOldFileIsArchivedWithoutBeingOverwritten()
            throws Exception {
        Files.createDirectories(directory.resolve("lang"));
        Files.writeString(
                directory.resolve("lang/messages.yml"), "general:\n  prefix: '[Current]'\n");
        String old = "general:\n  prefix: '[Old]'\ncustom: 'Keep this backup'\n";
        Files.writeString(directory.resolve("messages.yml"), old);
        var messages = messages();
        assertEquals("[Current] ", ComponentUtils.plainText(messages.prefix(null)));
        assertFalse(Files.exists(directory.resolve("messages.yml")));
        try (var backups = Files.list(directory.resolve("backups"))) {
            var paths = backups.toList();
            assertEquals(1, paths.size());
            assertEquals(old, Files.readString(paths.getFirst()));
        }
        messages();
        try (var backups = Files.list(directory.resolve("backups"))) {
            assertEquals(1, backups.count());
        }
    }

    @Test
    void aFailedRelocationKeepsTheOriginalContents() throws Exception {
        String old = "general:\n  prefix: '[Old]'\n";
        Files.writeString(directory.resolve("messages.yml"), old);
        Files.writeString(directory.resolve("lang"), "Blocked directory");
        assertThrows(IllegalStateException.class, this::messages);
        assertEquals(old, Files.readString(directory.resolve("messages.yml")));
    }

    private static boolean hasClick(Component component) {
        return component.clickEvent() != null
                || component.children().stream().anyMatch(DungeonMessagesTest::hasClick);
    }
}
