package nl.hauntedmc.dungeons.runtime.player;

import de.tr7zw.changeme.nbtapi.NBT;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Verifies the native player-data save before the plugin journal may acknowledge a transaction. */
public final class PlayerDataCheckpoint {
    private PlayerDataCheckpoint() {}

    public static void verify(Player player, String transaction) {
        try {
            Path file = playerDataDirectory().resolve(player.getUniqueId() + ".dat");
            if (!Files.isRegularFile(file))
                throw new IOException("Native player data was not written: " + file);
            if (!transaction.equals(readMarker(file))) {
                throw new IOException(
                        "Native player data does not contain transaction "
                                + transaction
                                + ": "
                                + file);
            }
            // saveData has no success result. Read back its marker, then flush before removing WAL.
            try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot verify player-data checkpoint", failure);
        }
    }

    static Path playerDataDirectory() throws IOException {
        return NativePlayerStorage.directory(Bukkit.getServer());
    }

    /** Check the native storage contract before admitting any player-state transactions. */
    public static void requireStorage() {
        try {
            NativePlayerStorage.requireSavingEnabled();
            Path directory = playerDataDirectory();
            if (!Files.isDirectory(directory) || !Files.isWritable(directory))
                throw new IOException("Native player storage is unavailable or unwritable: " + directory);
        } catch (IOException failure) {
            throw new UncheckedIOException("Native player storage is required for safe recovery", failure);
        }
    }

    static String readMarker(Path file) throws IOException {
        var bukkit = NBT.readFile(file.toFile()).getCompound("BukkitValues");
        return bukkit == null ? null : bukkit.getString("dungeons:player_transaction");
    }
}
