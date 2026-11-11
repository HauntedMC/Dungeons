package nl.hauntedmc.dungeons.util.world;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/** World-level helpers for dungeon runtime world tuning. */
public final class WorldUtils {
    /** Present throughout an editor lifetime so restart cleanup cannot erase unsaved edits. */
    public static final String EDITOR_RECOVERY_MARKER = ".dungeons-editor-recovery";

    private WorldUtils() {}

    /** Resolves the storage used by Paper 26.1+ for a custom world. */
    public static File dimensionFolder(String worldName) {
        return dimensionRoot().toPath().resolve(worldName.toLowerCase(Locale.ROOT)).toFile();
    }

    /** Directory containing custom Minecraft dimensions. */
    public static File dimensionRoot() {
        return Bukkit.getServer().getLevelDirectory().resolve("dimensions/minecraft").toFile();
    }

    private static final ReentrantReadWriteLock[] TEMPLATE_LOCKS = new ReentrantReadWriteLock[64];

    static {
        Arrays.setAll(TEMPLATE_LOCKS, ignored -> new ReentrantReadWriteLock(true));
    }

    private static ReentrantReadWriteLock templateLock(File template) throws IOException {
        int hash = template.getCanonicalPath().hashCode();
        return TEMPLATE_LOCKS[Math.floorMod(hash, TEMPLATE_LOCKS.length)];
    }

    /** Filesystem reader for a stable template generation. */
    @FunctionalInterface
    public interface TemplateReader {
        void read() throws IOException;
    }

    /** Prevents template readers from observing an incomplete publication or rollback. */
    public static void readTemplate(File template, TemplateReader reader) throws IOException {
        var lock = templateLock(template).readLock();
        lock.lock();
        try {
            reader.read();
        } finally {
            lock.unlock();
        }
    }

    // Publish only world-owned paths. Moving the template root would race independent writes to
    // functions.yml, gamerules.yml and rooms, or restore stale metadata from the live world.
    private static final List<String> WORLD_ENTRIES =
            List.of(
                    "region",
                    "entities",
                    "poi",
                    "data",
                    "DIM1",
                    "DIM-1",
                    "dimensions",
                    "players",
                    "playerdata",
                    "stats",
                    "advancements",
                    "datapacks",
                    "level.dat",
                    "level.dat_old",
                    "paper-world.yml",
                    "resources.zip",
                    "icon.png",
                    "uid.dat",
                    "session.lock");

    /**
     * Stages world data without copying the old template, then replaces its world-owned paths.
     * Callers must serialize commits per template and keep the flushed source loaded with autosave
     * disabled until this method finishes. No Bukkit/model state is accessed here.
     */
    public static void saveTemplate(File worldFolder, File template) throws IOException {
        var source = worldFolder.getCanonicalFile().toPath();
        var target = template.getCanonicalFile().toPath();
        if (!Files.isDirectory(source)
                || !Files.isDirectory(target)
                || source.startsWith(target)
                || target.startsWith(source)) {
            throw new IOException(
                    "Invalid world/template directories: " + source + " -> " + target);
        }
        var publication = templateLock(template).writeLock();
        var staging = Files.createTempDirectory(target.getParent().getParent(), ".dungeons-save-");
        var snapshot = staging.resolve("snapshot");
        var previous = staging.resolve("previous");
        boolean publishing = false;
        var backedUp = new ArrayList<String>();
        var installed = new ArrayList<String>();
        boolean retainBackup = false;
        Throwable primaryFailure = null;
        try {
            Files.createDirectory(snapshot);
            Files.createDirectory(previous);
            for (String entry : WORLD_ENTRIES) {
                if (entry.equals("uid.dat") || entry.equals("session.lock")) continue;
                var from = source.resolve(entry);
                var to = snapshot.resolve(entry);
                if (Files.isDirectory(from)) {
                    FileUtils.copyDirectory(from.toFile(), to.toFile());
                } else if (Files.exists(from)) {
                    Files.copy(from, to);
                }
            }
            publication.lock();
            publishing = true;
            // Rename old paths instead of copying a second full world. Metadata in the root stays
            // in place throughout publication. A failed publication rolls back every touched path.
            for (String entry : WORLD_ENTRIES) {
                var current = target.resolve(entry);
                if (Files.exists(current)) {
                    Files.move(current, previous.resolve(entry));
                    backedUp.add(entry);
                }
                if (Files.exists(snapshot.resolve(entry))) {
                    Files.move(snapshot.resolve(entry), current);
                    installed.add(entry);
                }
            }
        } catch (IOException | RuntimeException | Error failure) {
            primaryFailure = failure;
            for (String entry : installed.reversed()) {
                try {
                    Files.move(target.resolve(entry), snapshot.resolve(entry));
                } catch (IOException | RuntimeException rollback) {
                    failure.addSuppressed(rollback);
                    retainBackup = true;
                }
            }
            for (String entry : backedUp.reversed()) {
                try {
                    Files.move(previous.resolve(entry), target.resolve(entry));
                } catch (IOException | RuntimeException rollback) {
                    failure.addSuppressed(rollback);
                    retainBackup = true;
                }
            }
            if (retainBackup) {
                throw new IOException("Template backup retained at " + previous, failure);
            }
            throw failure;
        } finally {
            if (publishing) publication.unlock();
            if (!retainBackup) {
                try {
                    FileUtils.deleteDirectory(staging.toFile());
                } catch (IOException cleanup) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }

    /** Releases forced loading on the world spawn chunk. */
    public static void releaseSpawnChunk(World world) {
        if (world == null) return;
        Location spawn = world.getSpawnLocation();
        world.setChunkForceLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4, false);
    }
}
