package nl.hauntedmc.dungeons.runtime.player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/** Paper adapter for the same PlayerDataStorage used by CraftPlayer.saveData(). */
final class NativePlayerStorage {
    private NativePlayerStorage() {}

    static void requireSavingEnabled() throws IOException {
        try {
            requireSavingEnabled(Class.forName("org.spigotmc.SpigotConfig"));
        } catch (ClassNotFoundException failure) {
            throw new IOException("Cannot verify native player saving configuration", failure);
        }
    }

    static void requireSavingEnabled(Class<?> configuration) throws IOException {
        try {
            if (configuration.getField("disablePlayerDataSaving").getBoolean(null))
                throw new IOException("Native player saving is disabled in Spigot configuration");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IOException("Cannot verify that native player saving is enabled", failure);
        }
    }

    static Path directory(Object server) throws IOException {
        try {
            Object players = server.getClass().getMethod("getHandle").invoke(server);
            Object storage = players.getClass().getField("playerIo").get(players);
            Object directory = storage.getClass().getMethod("getPlayerDir").invoke(storage);
            if (!(directory instanceof File folder))
                throw new IOException("Native player storage returned an unsupported directory");
            return folder.toPath().toAbsolutePath().normalize();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IOException(
                    "Cannot resolve the server's active native player storage", failure);
        }
    }
}
