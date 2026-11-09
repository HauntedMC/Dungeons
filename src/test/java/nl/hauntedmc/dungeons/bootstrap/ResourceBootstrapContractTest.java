package nl.hauntedmc.dungeons.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class ResourceBootstrapContractTest {
    @TempDir Path directory;

    @Test
    void bootstrapSynchronizesBundledConfigAndLanguageWithoutReplacingOperatorEditsAndCanRepeat()
            throws Exception {
        var diskField = LangUtils.class.getDeclaredField("messagesYaml");
        diskField.setAccessible(true);
        var jarField = LangUtils.class.getDeclaredField("jarMessages");
        jarField.setAccessible(true);
        var oldDisk = diskField.get(null);
        var oldJar = jarField.get(null);
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
            assertTrue(Files.isDirectory(directory.resolve("players")));
            assertEquals(
                    "Operator help Alex",
                    LangUtils.getMessage(
                            "commands.play.usage", false, LangUtils.placeholder("player", "Alex")));
            var loaded =
                    YamlConfiguration.loadConfiguration(directory.resolve("messages.yml").toFile());
            assertEquals("[Custom]", loaded.getString("general.prefix"));
            assertNotNull(loaded.getString("commands.list.line"));
            String previous = Files.readString(directory.resolve("messages.yml"));
            bootstrap.initializeCoreState();
            assertEquals(previous, Files.readString(directory.resolve("messages.yml")));
            verify(f.paper.plugin, times(1)).saveConfig();
        } finally {
            diskField.set(null, oldDisk);
            jarField.set(null, oldJar);
        }
    }
}
