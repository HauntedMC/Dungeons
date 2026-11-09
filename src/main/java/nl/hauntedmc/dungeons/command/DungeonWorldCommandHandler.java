package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

/** Creates dungeon templates from imported or temporary Paper worlds. */
final class DungeonWorldCommandHandler {
    private final DungeonsPlugin plugin;
    private final DungeonRepository dungeonManager;

    DungeonWorldCommandHandler(DungeonsPlugin plugin, DungeonRepository dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
    }

    /** Runs handle import command. */
    boolean handleImportCommand(CommandSender sender, String[] args) {
        if (args.length != 3 && args.length != 4) {
            LangUtils.sendMessage(sender, "commands.dungeon.import.usage");
            return true;
        }

        String worldName = args[1].trim();
        String mapName = args[2].trim();
        nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(sender, "edit", mapName);
        if (!this.isSafeImportWorldName(mapName)) throw new IllegalArgumentException("Invalid dungeon name");
        if (!this.isSafeImportWorldName(worldName)) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.import.world-not-found",
                    LangUtils.placeholder("world", args[1]));
            return true;
        }

        if (this.dungeonManager.get(mapName) != null) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.import.already-exists",
                    LangUtils.placeholder("world", worldName));
            return true;
        }

        File importFolder = nl.hauntedmc.dungeons.util.world.WorldUtils.dimensionFolder(worldName);
        if (!importFolder.isDirectory()) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.import.world-not-found",
                    LangUtils.placeholder("world", args[1]));
            return true;
        }

        if (Bukkit.getWorld(worldName) != null) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.import.world-loaded",
                    LangUtils.placeholder("world", worldName));
            return true;
        }

        File saveFolder;
        try {
            saveFolder = this.resolveDungeonSaveFolder(mapName);
        } catch (IOException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to resolve safe dungeon folder for import '{}'.",
                            worldName,
                            exception);
            this.runImportFailure(sender);
            return true;
        }

        if (saveFolder.exists()) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.import.already-exists",
                    LangUtils.placeholder("world", worldName));
            return true;
        }

        WorldCreator importer = new WorldCreator(worldName);
        if (args.length == 4) {
            try {
                Environment dimension = Environment.valueOf(args[3].toUpperCase(Locale.ROOT));
                importer.environment(dimension);
            } catch (IllegalArgumentException exception) {
                LangUtils.sendMessage(
                        sender,
                        "commands.dungeon.import.invalid-dimension",
                        LangUtils.placeholder("dimension", args[3].toUpperCase(Locale.ROOT)));
                return true;
            }
        }

        importer.type(WorldType.FLAT);
        World importWorld;
        try {
            importWorld = importer.createWorld();
        } catch (RuntimeException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .error("Failed to load source world '{}' for import.", worldName, exception);
            this.runImportFailure(sender);
            return true;
        }
        if (importWorld == null) {
            this.runImportFailure(sender);
            return true;
        }

        // Copy the current Paper dimension after unloading it.
        File importedWorldFolder = importWorld.getWorldFolder();
        Location importSpawn = importWorld.getSpawnLocation();
        String dimension = importWorld.getEnvironment().toString();
        if (!Bukkit.unloadWorld(importWorld, false)) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to unload imported source world '{}' before copying it into a"
                                    + " dungeon folder.",
                            worldName);
            this.runImportFailure(sender);
            return true;
        }

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        this.plugin,
                        () -> this.copyImport(sender,
                                new ImportedWorld(worldName, importedWorldFolder, saveFolder, importSpawn, dimension)));

        return true;
    }

    /** Runs handle create command. */
    boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length > 1)
            nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(sender, "edit", args[1]);

        if (args.length < 2 || args.length > 4) {
            LangUtils.sendMessage(player, "commands.dungeon.create.usage");
            return true;
        }

        String worldName = args[1].trim();
        if (!this.isSafeImportWorldName(worldName)) {
            LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
            return true;
        }

        File saveFolder;
        try {
            saveFolder = this.resolveDungeonSaveFolder(worldName);
        } catch (IOException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to resolve safe dungeon folder for creation '{}'.",
                            worldName,
                            exception);
            LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
            return true;
        }

        if (this.dungeonManager.get(worldName) != null || saveFolder.exists()) {
            LangUtils.sendMessage(player, "commands.dungeon.create.already-exists");
            return true;
        }

        if (this.worldNameConflictsWithExistingServerWorld(worldName)) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.create.world-conflict",
                    LangUtils.placeholder("world", worldName));
            return true;
        }

        String dungeonType = args.length >= 3 ? args[2] : "";
        String generatorType = args.length == 4 ? args[3] : "";

        String tempWorldName = this.createTemporaryWorldName(worldName);
        WorldCreator loader = new WorldCreator(tempWorldName);
        if (!generatorType.isEmpty()) {
            loader.generator(generatorType);
        } else {
            loader.type(WorldType.FLAT);
        }

        loader.generateStructures(false);
        World world;
        try {
            world = loader.createWorld();
        } catch (RuntimeException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to create temporary world '{}' for dungeon '{}'.",
                            tempWorldName,
                            worldName,
                            exception);
            if (Bukkit.getWorld(tempWorldName) == null) {
                this.cleanupDirectoryAsync(
                        nl.hauntedmc.dungeons.util.world.WorldUtils.dimensionFolder(tempWorldName),
                        "failed temporary dimension");
                this.cleanupDirectoryAsync(
                        new File(Bukkit.getWorldContainer(), tempWorldName),
                        "failed temporary world");
            }
            LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
            return true;
        }
        if (world == null) {
            LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
            return true;
        }

        Location spawnpoint = world.getSpawnLocation();
        File worldFolder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, true)) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to unload temporary world '{}' while creating dungeon '{}'.",
                            tempWorldName,
                            worldName);
            LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
            return true;
        }

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        this.plugin,
                        () -> this.copyCreation(player,
                                new CreatedWorld(worldName, tempWorldName, worldFolder, saveFolder, spawnpoint, dungeonType, generatorType)));

        return true;
    }

    /** Returns whether safe import world name. */
    private boolean isSafeImportWorldName(String worldName) {
        return !worldName.isBlank() && worldName.matches("[A-Za-z0-9_-]+");
    }

    /** Resolves dungeon save folder. */
    private File resolveDungeonSaveFolder(String worldName) throws IOException {
        return this.resolveSafeChildDirectory(
                new File(this.plugin.getDataFolder(), "dungeons"), worldName);
    }

    /** Runs world name conflicts with existing server world. */
    private boolean worldNameConflictsWithExistingServerWorld(String worldName) {
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }

        try {
            return this.resolveSafeChildDirectory(Bukkit.getWorldContainer(), worldName).exists()
                    || nl.hauntedmc.dungeons.util.world.WorldUtils.dimensionFolder(worldName)
                            .exists();
        } catch (IOException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .warn(
                            "Failed to resolve world folder collision check for '{}'.",
                            worldName,
                            exception);
            return true;
        }
    }

    /** Creates temporary world name. */
    private String createTemporaryWorldName(String baseWorldName) {
        String compactBase = baseWorldName.toLowerCase(Locale.ROOT);
        if (compactBase.length() > 20) {
            compactBase = compactBase.substring(0, 20);
        }

        while (true) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String candidate = "dungeons_tmp_" + compactBase + "_" + suffix;
            File worldFolder = new File(Bukkit.getWorldContainer(), candidate);
            if (Bukkit.getWorld(candidate) == null
                    && !worldFolder.exists()
                    && !nl.hauntedmc.dungeons.util.world.WorldUtils.dimensionFolder(candidate)
                            .exists()) {
                return candidate;
            }
        }
    }

    /** Runs cleanup directory async. */
    private void cleanupDirectoryAsync(File directory, String description) {
        Runnable task = () -> this.cleanupDirectory(directory, description);
        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, task);
        } else {
            task.run();
        }
    }

    /** Runs cleanup directory. */
    private void cleanupDirectory(File directory, String description) {
        if (directory == null || !directory.exists()) {
            return;
        }

        try {
            if (directory.isDirectory()) {
                FileUtils.deleteDirectory(directory);
            } else if (!directory.delete()) {
                this.plugin
                        .getSLF4JLogger()
                        .warn(
                                "Failed to delete {} '{}'.",
                                description,
                                directory.getAbsolutePath());
            }
        } catch (IOException exception) {
            this.plugin
                    .getSLF4JLogger()
                    .warn(
                            "Failed to clean up {} '{}'.",
                            description,
                            directory.getAbsolutePath(),
                            exception);
        }
    }

    /** Resolves safe child directory. */
    private File resolveSafeChildDirectory(File parent, String childName) throws IOException {
        File canonicalParent = parent.getCanonicalFile();
        File canonicalChild = new File(canonicalParent, childName).getCanonicalFile();
        String parentPath = canonicalParent.getPath();
        String childPath = canonicalChild.getPath();
        if (!childPath.equals(parentPath + File.separator + childName)) {
            throw new IOException("Resolved path escaped parent directory");
        }
        return canonicalChild;
    }

    /** Runs run import failure. */
    private void runImportFailure(CommandSender sender) {
        Bukkit.getScheduler()
                .runTask(
                        this.plugin,
                        () -> LangUtils.sendMessage(sender, "commands.dungeon.import.failed"));
    }
    private void copyImport(CommandSender sender, ImportedWorld captured) {
        if (captured.destination().exists()) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Refusing to import world '{}' because the target"
                                    + " dungeon folder '{}' already exists.",
                            captured.name(),
                            captured.destination().getAbsolutePath());
            this.runImportFailure(sender);
            return;
        }

        if (!captured.destination().mkdirs()) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to create dungeon import folder '{}'.",
                            captured.destination().getAbsolutePath());
            this.runImportFailure(sender);
            return;
        }

        try {
            FileUtils.copyDirectory(captured.sourceFolder(), captured.destination());
        } catch (IOException exception) {
            this.cleanupDirectory(captured.destination(), "failed import target");
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to import world '{}' into dungeon folder"
                                    + " '{}'.",
                            captured.name(),
                            captured.destination().getAbsolutePath(),
                            exception);
            this.runImportFailure(sender);
            return;
        }

        Bukkit.getScheduler()
                .runTask(
                        this.plugin,
                        () -> this.finishImport(sender, captured));
    }

    private void finishImport(CommandSender sender, ImportedWorld captured) {
        DungeonDefinition importDungeon =
                this.dungeonManager.loadDungeon(captured.destination());
        if (importDungeon == null) {
            this.cleanupDirectoryAsync(
                    captured.destination(), "failed imported dungeon");
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.import.failed");
            return;
        }

        importDungeon.setSaveConfig(
                "dungeon.environment", captured.environment());
        importDungeon.setLobbySpawn(captured.spawn());
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.import.success",
                LangUtils.placeholder("world", captured.name()));
    }

    private record ImportedWorld(String name, File sourceFolder, File destination, Location spawn, String environment) {}

    private void copyCreation(Player player, CreatedWorld captured) {
        if (captured.destination().exists()) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Refusing to create dungeon '{}' because the target"
                                    + " folder '{}' already exists.",
                            captured.name(),
                            captured.destination().getAbsolutePath());
            this.cleanupDirectory(captured.sourceFolder(), "temporary world folder");
            this.cleanupDirectory(
                    new File(Bukkit.getWorldContainer(), captured.temporaryName()),
                    "temporary world folder");
            Bukkit.getScheduler()
                    .runTask(
                            this.plugin,
                            () ->
                                    LangUtils.sendMessage(
                                            player,
                                            "commands.dungeon.create.already-exists"));
            return;
        }

        if (!captured.destination().mkdirs()) {
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to create dungeon folder '{}'.",
                            captured.destination().getAbsolutePath());
            this.cleanupDirectory(captured.sourceFolder(), "temporary world folder");
            this.cleanupDirectory(
                    new File(Bukkit.getWorldContainer(), captured.temporaryName()),
                    "temporary world folder");
            Bukkit.getScheduler()
                    .runTask(
                            this.plugin,
                            () ->
                                    LangUtils.sendMessage(
                                            player,
                                            "commands.dungeon.create.failed-to-create"));
            return;
        }

        try {
            FileUtils.copyDirectory(captured.sourceFolder(), captured.destination());
        } catch (IOException exception) {
            this.cleanupDirectory(captured.destination(), "failed creation target");
            this.cleanupDirectory(captured.sourceFolder(), "temporary world folder");
            this.cleanupDirectory(
                    new File(Bukkit.getWorldContainer(), captured.temporaryName()),
                    "temporary world folder");
            this.plugin
                    .getSLF4JLogger()
                    .error(
                            "Failed to create dungeon '{}' from world folder"
                                    + " '{}'.",
                            captured.name(),
                            captured.sourceFolder().getAbsolutePath(),
                            exception);
            Bukkit.getScheduler()
                    .runTask(
                            this.plugin,
                            () ->
                                    LangUtils.sendMessage(
                                            player,
                                            "commands.dungeon.create.failed-to-create"));
            return;
        }

        this.cleanupDirectory(captured.sourceFolder(), "temporary world folder");
        this.cleanupDirectory(
                new File(Bukkit.getWorldContainer(), captured.temporaryName()),
                "temporary world folder");

        Bukkit.getScheduler()
                .runTask(
                        this.plugin,
                        () -> this.finishCreation(player, captured));
    }

    private void finishCreation(Player player, CreatedWorld captured) {
        DungeonDefinition newDungeon =
                this.dungeonManager.loadDungeon(
                        captured.destination(),
                        captured.dungeonType(),
                        captured.generatorType());
        if (newDungeon == null) {
            this.cleanupDirectoryAsync(
                    captured.destination(), "failed created dungeon");
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.create.failed-to-create");
            this.plugin
                    .getSLF4JLogger()
                    .warn(
                            "Dungeon creation failed for"
                                + " '{}': unknown dungeon"
                                + " type '{}'.",
                            captured.name(),
                            captured.dungeonType());
            return;
        }

        newDungeon.setLobbySpawn(captured.spawn());
        if (!captured.dungeonType().isEmpty()) {
            newDungeon
                    .getConfig()
                    .set("dungeon.type", captured.dungeonType());
        }

        newDungeon.saveConfig();
        LangUtils.sendMessage(
                player,
                "commands.dungeon.create.success",
                LangUtils.placeholder(
                        "dungeon", captured.name()));
    }

    private record CreatedWorld(String name, String temporaryName, File sourceFolder, File destination, Location spawn, String dungeonType, String generatorType) {}
}
