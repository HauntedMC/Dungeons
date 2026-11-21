package nl.hauntedmc.dungeons.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoRecoveryTest {
    @TempDir Path root;

    @Test
    void interruptedSwapRestoresPriorDirectoryBeforeDiscovery() throws Exception {
        Path dungeons = Files.createDirectories(root.resolve("dungeons"));
        var files = new RepoFiles(root, dungeons);
        Path live = Files.createDirectories(dungeons.resolve("crypt"));
        Files.writeString(live.resolve("content"), "old");
        UUID id = UUID.randomUUID();
        Path stage = files.stage(id).resolve("install");
        Files.createDirectories(stage);
        Files.writeString(stage.resolve("content"), "new");
        files.install(id, "crypt", stage);
        assertEquals("new", Files.readString(live.resolve("content")));
        new RepoFiles(root, dungeons).recover(mock(RepoStore.class));
        assertEquals("old", Files.readString(live.resolve("content")));
    }

    @Test
    void restartAfterRestoringBackupButBeforeDeletingJournalCompletesRecovery() throws Exception {
        Path dungeons = Files.createDirectories(root.resolve("dungeons"));
        var files = new RepoFiles(root, dungeons);
        Path live = Files.createDirectories(dungeons.resolve("crypt"));
        Files.writeString(live.resolve("content"), "restored original");
        UUID id = UUID.randomUUID();
        Path journal = files.root().resolve("journals/" + id + ".yml");
        Files.writeString(
                journal,
                "kind: install\nname: crypt\nprevious: true\ncommitted: false\nphase: restoring\n");

        files.recover(mock(RepoStore.class));

        assertEquals("restored original", Files.readString(live.resolve("content")));
        assertFalse(Files.exists(journal));
    }

    @Test
    void committedInstallKeepsVersionAndSafetyBackup() throws Exception {
        Path dungeons = Files.createDirectories(root.resolve("dungeons"));
        var files = new RepoFiles(root, dungeons);
        Path live = Files.createDirectories(dungeons.resolve("crypt"));
        Files.writeString(live.resolve("content"), "old");
        UUID id = UUID.randomUUID();
        Path stage = Files.createDirectories(files.stage(id).resolve("install"));
        Files.writeString(stage.resolve("content"), "new");
        Path backup = files.install(id, "crypt", stage);
        files.commit(id, "crypt", true);
        new RepoFiles(root, dungeons).recover(mock(RepoStore.class));
        assertEquals("new", Files.readString(live.resolve("content")));
        assertEquals("old", Files.readString(backup.resolve("content")));
    }

    @Test
    void pruningKeepsTheCurrentSafetyBackupEvenIfItsTimestampIsOld() throws Exception {
        var files = new RepoFiles(root, Files.createDirectories(root.resolve("dungeons")));
        UUID installed = UUID.randomUUID();
        Path backups = Files.createDirectories(root.resolve("backups/crypt"));
        Path safety = Files.createDirectory(backups.resolve("repo-" + installed));
        for (int i = 0; i < 10; i++) Files.createDirectory(backups.resolve("backup-" + i));
        Files.setLastModifiedTime(safety, java.nio.file.attribute.FileTime.fromMillis(1));

        files.prune("crypt", installed);

        assertTrue(Files.isDirectory(safety));
        try (var remaining = Files.list(backups)) {
            assertEquals(10, remaining.count());
        }
    }

    @Test
    void interruptedFirstInstallIsQuarantinedAndUnsafeJournalsFailClosed() throws Exception {
        Path dungeons = Files.createDirectories(root.resolve("dungeons"));
        var files = new RepoFiles(root, dungeons);
        UUID id = UUID.randomUUID();
        Path stage = Files.createDirectories(files.stage(id).resolve("install"));
        Files.writeString(stage.resolve("content"), "new");
        files.install(id, "crypt", stage);
        files.recover(mock(RepoStore.class));
        assertFalse(Files.exists(dungeons.resolve("crypt")));
        UUID bad = UUID.randomUUID();
        Files.writeString(
                files.root().resolve("journals/" + bad + ".yml"),
                "kind: install\nname: '../outside'\nprevious: false\ncommitted: false\n");
        assertThrows(IllegalArgumentException.class, () -> files.recover(mock(RepoStore.class)));
    }

    @Test
    void legacyIdentityAloneNeverEnablesADungeon() throws Exception {
        try (var paper = new nl.hauntedmc.dungeons.support.PaperBoundary()) {
            Path folder = Files.createDirectories(root.resolve("legacy"));
            paper.bukkit.when(org.bukkit.Bukkit::getWorldContainer).thenReturn(root.toFile());
            paper.runtime.setDefaultDungeonConfig(
                    new org.bukkit.configuration.file.YamlConfiguration());
            var dungeon =
                    new nl.hauntedmc.dungeons.content.dungeon.StaticDungeon(
                            paper.runtime,
                            folder.toFile(),
                            new org.bukkit.configuration.file.YamlConfiguration());
            assertNotNull(dungeon.getDungeonId());
            assertFalse(dungeon.isEnabled());
            new RepoLocal(dungeon.getDungeonId(), null, RepoVersion.DRAFT, "a".repeat(64))
                    .write(folder);
            dungeon.refreshRepositoryState();
            assertTrue(dungeon.isEnabled());
            Files.delete(folder.resolve(RepoContent.MARKER));
            dungeon.refreshRepositoryState();
            assertFalse(dungeon.isEnabled());
        }
    }
}
