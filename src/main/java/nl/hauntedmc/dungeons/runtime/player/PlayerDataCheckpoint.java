package nl.hauntedmc.dungeons.runtime.player;

import de.tr7zw.changeme.nbtapi.NBT;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/** Verifies the native player-data save before the plugin journal may acknowledge a transaction. */
public final class PlayerDataCheckpoint {
    private PlayerDataCheckpoint() {}

    public static void verify(Player player, String transaction) {
        var file =
                Bukkit.getServer()
                        .getLevelDirectory()
                        .resolve("playerdata")
                        .resolve(player.getUniqueId() + ".dat");
        try {
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

    static String readMarker(java.nio.file.Path file) throws IOException {
        var bukkit = NBT.readFile(file.toFile()).getCompound("BukkitValues");
        return bukkit == null ? null : bukkit.getString("dungeons:player_transaction");
    }
}
