package nl.hauntedmc.dungeons.config;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class ConfigurationFileTest {
    @TempDir Path directory;

    @Test
    void disabledPluginSavesInlineAndCompletesTheFuture() throws Exception {
        ConfigurationFile config = new ConfigurationFile(disabledPlugin());
        config.set("value", "saved");
        Path file = directory.resolve("config.yml");
        assertTrue(config.saveAsync(file.toFile()).get(1, TimeUnit.SECONDS));
        assertEquals("value: saved\n", Files.readString(file));
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

    @Test
    void failedSavePreservesTheLastGoodFileAndItsLoadableValues() throws Exception {
        var fail = new AtomicBoolean();
        var config =
                new ConfigurationFile(disabledPlugin()) {
                    @Override
                    public String saveToString() {
                        if (fail.get()) throw new IllegalArgumentException("unserializable graph");
                        return super.saveToString();
                    }
                };
        var file = directory.resolve("durable.yml");
        config.set("progress.completed", 10);
        assertTrue(config.saveAsync(file.toFile()).join());
        byte[] previous = Files.readAllBytes(file);
        config.set("progress.completed", 20);
        fail.set(true);
        assertFalse(config.saveAsync(file.toFile()).join());
        assertArrayEquals(previous, Files.readAllBytes(file));
        var restored = new ConfigurationFile(disabledPlugin());
        restored.load(file.toFile());
        assertEquals(10, restored.getInt("progress.completed"));
    }

    @Test
    void ordinaryWriteFailuresCompleteAsFailedAndARepairedDestinationCanBeSavedAgain()
            throws Exception {
        var config = new ConfigurationFile(disabledPlugin());
        config.set("value", 23);
        var blocker = directory.resolve("blocked");
        Files.writeString(blocker, "existing file");
        var destination = blocker.resolve("config.yml");
        assertFalse(config.saveAsync(destination.toFile()).get(1, TimeUnit.SECONDS));
        assertEquals("existing file", Files.readString(blocker));
        Files.delete(blocker);
        Files.createDirectory(blocker);
        assertTrue(config.saveAsync(destination.toFile()).get(1, TimeUnit.SECONDS));
        var loaded = new ConfigurationFile(disabledPlugin());
        loaded.load(destination.toFile());
        assertEquals(23, loaded.getInt("value"));
        assertFalse(config.saveAsync(blocker.toFile()).get(1, TimeUnit.SECONDS));
        assertTrue(Files.isRegularFile(destination));
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
