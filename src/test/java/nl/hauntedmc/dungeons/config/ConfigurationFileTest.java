package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class ConfigurationFileTest {
    @TempDir Path directory;

    @Test
    void disabledPluginSavesInlineAndCompletesTheFuture() throws Exception {
        ConfigurationFile config = new ConfigurationFile(disabledPlugin());
        config.set("value", "saved");
        Path file = directory.resolve("config.yml");
        assertTrue(config.saveAsync(file.toFile()).get(1, TimeUnit.SECONDS));
        assertEquals("value: saved\n", java.nio.file.Files.readString(file));
    }

    @Test
    void serializationFailuresCompleteTheFutureAsFailed() throws Exception {
        ConfigurationFile config =
                new ConfigurationFile(disabledPlugin()) {
                    @Override
                    public String saveToString() {
                        throw new IllegalArgumentException("broken payload");
                    }
                };
        assertFalse(
                config.saveAsync(directory.resolve("broken.yml").toFile())
                        .get(1, TimeUnit.SECONDS));
    }

    private static Plugin disabledPlugin() {
        return (Plugin)
                Proxy.newProxyInstance(
                        Plugin.class.getClassLoader(),
                        new Class<?>[] {Plugin.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "isEnabled" -> false;
                                    case "getSLF4JLogger" ->
                                            LoggerFactory.getLogger(ConfigurationFileTest.class);
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
