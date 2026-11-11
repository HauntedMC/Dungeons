package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceBootstrapContractTest {
    @TempDir Path directory;

    @Test
    void bootstrapSynchronizesBundledConfigAndLanguageWithoutReplacingOperatorEditsAndCanRepeat()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            when(f.paper.plugin.getResource(anyString()))
                    .thenAnswer(
                            c -> getClass().getClassLoader().getResourceAsStream(c.getArgument(0)));
            f.paper.config.set("commands.list_page_size", 7);
            doAnswer(
                            c -> {
                                f.paper.config.save(directory.resolve("config.yml").toFile());
                                return null;
                            })
                    .when(f.paper.plugin)
                    .saveConfig();
            var messages = new YamlConfiguration();
            messages.set("general.prefix", "[Custom]");
            messages.set("commands.play.usage", "Operator help {player}");
            messages.save(directory.resolve("messages.yml").toFile());
            var bootstrap = new CoreBootstrap(f.paper.runtime);
            bootstrap.initializeCoreState();
            assertEquals(7, f.paper.runtime.config().getInt("commands.list_page_size"));
            assertEquals(10, f.paper.runtime.config().getInt("instances.max_active"));
            assertTrue(Files.isDirectory(directory.resolve("dungeons")));
            assertFalse(Files.exists(directory.resolve("players")));
            assertEquals(
                    "Operator help Alex",
                    ComponentUtils.plainText(
                            LangUtils.component(
                                    "commands.play.usage",
                                    null,
                                    false,
                                    LangUtils.placeholder("player", "Alex"))));
            assertFalse(Files.exists(directory.resolve("messages.yml")));
            var loaded =
                    YamlConfiguration.loadConfiguration(
                            directory.resolve("lang/messages.yml").toFile());
            assertEquals("[Custom]", loaded.getString("general.prefix"));
            assertNotNull(loaded.getString("commands.list.line"));
            String previous = Files.readString(directory.resolve("lang/messages.yml"));
            bootstrap.initializeCoreState();
            assertEquals(previous, Files.readString(directory.resolve("lang/messages.yml")));
            verify(f.paper.plugin, times(1)).saveConfig();
        } finally {
            LangUtils.clear();
        }
    }
}
