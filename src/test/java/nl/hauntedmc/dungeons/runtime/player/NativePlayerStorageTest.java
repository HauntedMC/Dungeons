package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

class NativePlayerStorageTest {
    @TempDir Path directory;

    @Test
    void disabledNativeSavingIsRejectedBeforeRecoveryCanBeAdmitted() {
        assertThrows(IOException.class, () -> NativePlayerStorage.requireSavingEnabled(DisabledSaving.class));
        assertDoesNotThrow(() -> NativePlayerStorage.requireSavingEnabled(EnabledSaving.class));
    }

    public static final class DisabledSaving {
        public static boolean disablePlayerDataSaving = true;
    }

    public static final class EnabledSaving {
        public static boolean disablePlayerDataSaving = false;
    }

    @Test
    void resolvesTheActiveStorageInsteadOfAssumingAnyWorldRelativePath() throws Exception {
        for (String layout : new String[] {"players/data", "playerdata", "custom-player-store"}) {
            var actual = directory.resolve(layout);
            assertEquals(actual.toAbsolutePath(), NativePlayerStorage.directory(new Server(actual)));
        }
    }

    @Test
    void unsupportedNativeStorageNeverFallsBackToAnUnrelatedExistingFile() {
        assertThrows(IOException.class, () -> NativePlayerStorage.directory(new Object()));
    }

    public static final class Server {
        private final Players players;
        Server(Path directory) { players = new Players(directory); }
        public Players getHandle() { return players; }
    }

    public static final class Players {
        public final Storage playerIo;
        Players(Path directory) { playerIo = new Storage(directory); }
    }

    public static final class Storage {
        private final Path directory;
        Storage(Path directory) { this.directory = directory; }
        public File getPlayerDir() { return directory.toFile(); }
    }
}
