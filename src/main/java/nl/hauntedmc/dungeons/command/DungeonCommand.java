package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.command.CommandOutput.FilterPageQuery;
import nl.hauntedmc.dungeons.command.config.DungeonConfigCommandHandler;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.gui.loot.LootTableEditor;
import nl.hauntedmc.dungeons.model.dungeon.AccessKeyDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.registry.DungeonTypeRegistry;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeam;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;

/**
 * Central Bukkit command executor for the plugin's root command.
 *
 * <p>This class owns top-level command dispatch, permission gating, and tab completion for the
 * plugin's command tree. Focused handlers own gameplay, authoring and staff command branches.
 */
public final class DungeonCommand implements TabExecutor {
    private StaffCommandHandler staffCommandHandler;

    public void setStaffCommandHandler(StaffCommandHandler handler) {
        this.staffCommandHandler = handler;
    }

    private final DungeonsPlugin plugin;
    private final PlayerSessionRegistry playerManager;
    private final DungeonRepository dungeonManager;
    private final ActiveInstanceRegistry activeInstanceManager;
    private final DungeonQueueCoordinator dungeonQueueCoordinator;
    private final DungeonQueueRegistry queueManager;
    private final DungeonTeamService teamManager;
    private final LifecycleCoordinator lifecycleCoordinator;
    private final GuiService guiService;
    private final CommandOutput output;
    private final DungeonConfigCommandHandler configCommandHandler;
    private final CommandHandler teamCommandGroup;
    private final CommandHandler lootCommandGroup;
    private final CommandHandler dungeonCommandGroup;
    private final CommandHandler editCommandGroup;
    private final DebugCommandHandler debugCommandGroup;

    /** Creates the root command executor and binds it to the configured Bukkit command name. */
    public DungeonCommand(
            DungeonsPlugin plugin,
            PlayerSessionRegistry playerManager,
            DungeonRepository dungeonManager,
            ActiveInstanceRegistry activeInstanceManager,
            DungeonQueueCoordinator dungeonQueueCoordinator,
            DungeonQueueRegistry queueManager,
            DungeonTeamService teamManager,
            LootTableRepository lootTableManager,
            LootTableEditor lootEditor,
            FunctionRegistry functionManager,
            LifecycleCoordinator lifecycleCoordinator,
            GuiService guiService,
            String command) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.dungeonManager = dungeonManager;
        this.activeInstanceManager = activeInstanceManager;
        this.dungeonQueueCoordinator = dungeonQueueCoordinator;
        this.queueManager = queueManager;
        this.teamManager = teamManager;
        this.lifecycleCoordinator = lifecycleCoordinator;
        this.guiService = guiService;
        this.configCommandHandler =
                new DungeonConfigCommandHandler(
                        dungeonManager, playerManager, activeInstanceManager);
        this.output = new CommandOutput(plugin, dungeonManager);
        this.teamCommandGroup = new TeamCommandHandler(teamManager);
        this.lootCommandGroup = new LootCommandHandler(lootTableManager, lootEditor, this.output);
        this.dungeonCommandGroup = new DungeonDefinitionCommandGroup();
        this.editCommandGroup = new EditCommandGroup();
        this.debugCommandGroup = new DebugCommandHandler(playerManager);
        PluginCommand pluginCommand = plugin.getServer().getPluginCommand(command);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(this);
            pluginCommand.setTabCompleter(this);
        }
    }

    /** Handles Bukkit command execution after the server has resolved the root command. */
    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull org.bukkit.command.Command cmd,
            @NotNull String label,
            @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(this.getPermissionNode())) {
            LangUtils.sendMessage(sender, "general.errors.no-permission");
            return true;
        }

        if (!this.isConsoleFriendly() && !(sender instanceof Player)) {
            LangUtils.sendMessage(sender, "general.errors.players-only");
            return true;
        }

        try {
            return this.handleCommand(sender, args);
        } catch (IllegalArgumentException rejected) {
            sender.sendMessage(
                    net.kyori.adventure.text.Component.text("[Dungeons] " + rejected.getMessage()));
            return true;
        } catch (RuntimeException failure) {
            this.plugin
                    .getSLF4JLogger()
                    .error("Dungeon command failed for {}", sender.getName(), failure);
            sender.sendMessage("[Dungeons] Change failed; check the server log before retrying.");
            return true;
        }
    }

    /** Produces tab completions for the root command and its nested subcommands. */
    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull org.bukkit.command.Command cmd,
            @NotNull String label,
            @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(this.getPermissionNode())) {
            return null;
        }

        if (this.staffCommandHandler != null
                && args.length > 1
                && args[0].equalsIgnoreCase("admin"))
            return this.staffCommandHandler.handleTabComplete(sender, args);
        List<String> tabCompletes = this.handleTabComplete(sender, args);
        if (tabCompletes == null && args.length == 1) {
            return new ArrayList<>();
        }

        return tabCompletes;
    }

    /** Runs handle command. */
    private boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            this.sendRootHelp(sender);
            if (this.staffCommandHandler != null
                    && nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.allows(
                            sender, "inspect", null))
                sender.sendMessage(
                        net.kyori.adventure.text.Component.text("[Staff dashboard: /dungeon admin]")
                                .clickEvent(
                                        net.kyori.adventure.text.event.ClickEvent.runCommand(
                                                "/dungeon admin")));
            return true;
        }

        // Keep the first-level command dispatch in one switch so permissions, help behavior, and
        // root subcommand names stay easy to audit from a single place.
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "admin" ->
                    this.staffCommandHandler == null
                            || this.staffCommandHandler.handleCommand(sender, args);
            case "help" -> this.handleHelpCommand(sender, args);
            case "list" -> this.handleListCommand(sender, args);
            case "rewards" -> this.handleRewardsCommand(sender, args);
            case "play" -> this.handlePlayCommand(sender, args);
            case "stuck" -> this.handleStuckCommand(sender, args);
            case "lives" -> this.handleLivesCommand(sender);
            case "leave" -> this.handleLeaveCommand(sender);
            case "team" -> this.teamCommandGroup.handleCommand(sender, args);
            case "loot" -> this.lootCommandGroup.handleCommand(sender, args);
            case "config" -> this.configCommandHandler.handleCommand(sender, args);
            case "dungeon" -> this.dungeonCommandGroup.handleCommand(sender, args);
            case "edit" -> this.editCommandGroup.handleCommand(sender, args);
            case "debug" -> {
                if (this.staffCommandHandler != null
                        && (!(sender instanceof Player p)
                                || !nl.hauntedmc.dungeons.runtime.staff.StaffEditGuard.editor(
                                        p, this.playerManager.get(p))))
                    throw new IllegalArgumentException(
                            "Diagnostics require an authorized editor session");
                yield this.debugCommandGroup.handleCommand(sender, args);
            }
            default -> {
                this.sendRootHelp(sender);
                yield true;
            }
        };
    }

    /** Runs handle tab complete. */
    private List<String> handleTabComplete(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
            return this.configCommandHandler.handleTabComplete(sender, args);
        }

        boolean playerSender = sender instanceof Player;
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            String query = args[0];
            boolean canPlayFromHere =
                    playerSender
                            ? CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                                    || CommandUtils.hasPermissionSilent(
                                            sender, "dungeons.play.send")
                            : CommandUtils.hasPermissionSilent(sender, "dungeons.play.send");
            if (this.canUseDiscoveryCommands(sender)) {
                CommandArguments.addMatchingLiteralOptions(options, query, "list");
            }
            if (canPlayFromHere) {
                CommandArguments.addMatchingLiteralOptions(options, query, "play");
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.play")) {
                CommandArguments.addMatchingLiteralOptions(options, query, "team");
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.stuck")) {
                CommandArguments.addMatchingLiteralOptions(options, query, "stuck");
            }

            CommandArguments.addMatchingLiteralOptions(options, query, "help");
            if (nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.allows(
                    sender, "inspect", null))
                CommandArguments.addMatchingLiteralOptions(options, query, "admin");
            if (playerSender) {
                CommandArguments.addMatchingLiteralOptions(options, query, "leave");
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.lives")) {
                CommandArguments.addMatchingLiteralOptions(options, query, "lives");
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.rewards")) {
                CommandArguments.addMatchingLiteralOptions(options, query, "rewards");
            }

            if (CommandUtils.hasAnyDungeonEditAccess(sender)) {
                CommandArguments.addMatchingLiteralOptions(options, query, "config");
                if (playerSender) {
                    CommandArguments.addMatchingLiteralOptions(options, query, "edit");
                }
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.staff.loot")) {
                CommandArguments.addMatchingLiteralOptions(options, query, "loot");
            }

            if (CommandUtils.hasPermissionSilent(sender, "dungeons.role.operator")) {
                CommandArguments.addMatchingLiteralOptions(options, query, "dungeon");
                if (playerSender) {
                    CommandArguments.addMatchingLiteralOptions(options, query, "debug");
                }
            }

            return options;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        // Nested command groups own their second-level completions so each branch can evolve
        // without
        // making the root completion logic unreadable.
        return switch (root) {
            case "help" -> args.length == 2 ? List.of("<page>") : List.of();
            case "list" ->
                    this.canUseDiscoveryCommands(sender)
                            ? this.completeListCommand(args)
                            : List.of();
            case "play" ->
                    this.canTabCompletePlay(sender, playerSender)
                            ? this.completePlayCommand(sender, args)
                            : List.of();
            case "team" ->
                    playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                            ? this.teamCommandGroup.handleTabComplete(sender, args)
                            : List.of();
            case "loot" ->
                    playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.staff.loot")
                            ? this.lootCommandGroup.handleTabComplete(sender, args)
                            : List.of();
            case "dungeon" ->
                    CommandUtils.hasPermissionSilent(sender, "dungeons.role.operator")
                            ? this.dungeonCommandGroup.handleTabComplete(sender, args)
                            : List.of();
            case "edit" ->
                    playerSender && CommandUtils.hasAnyDungeonEditAccess(sender)
                            ? this.editCommandGroup.handleTabComplete(sender, args)
                            : List.of();
            case "debug" ->
                    playerSender
                                    && CommandUtils.hasPermissionSilent(
                                            sender, "dungeons.role.operator")
                            ? this.debugCommandGroup.handleTabComplete(sender, args)
                            : List.of();
            default -> List.of();
        };
    }

    /** Adds matching dungeon names. */
    private void addMatchingDungeonNames(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (DungeonDefinition dungeon : this.dungeonManager.getLoadedDungeons()) {
            String namespace = dungeon.getWorldName().toLowerCase(Locale.ROOT);
            if (namespace.contains(lowerQuery)) {
                options.add(dungeon.getWorldName());
            }
        }
    }

    /** Adds matching editable dungeon names. */
    private void addMatchingEditableDungeonNames(
            CommandSender sender, List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (DungeonDefinition dungeon : this.dungeonManager.getLoadedDungeons()) {
            if (!CommandUtils.hasDungeonEditAccess(sender, dungeon.getWorldName())) {
                continue;
            }

            String namespace = dungeon.getWorldName().toLowerCase(Locale.ROOT);
            if (namespace.contains(lowerQuery)) {
                options.add(dungeon.getWorldName());
            }
        }
    }

    /** Adds matching dungeon type names. */
    private void addMatchingDungeonTypeNames(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<Class<?>> found = new ArrayList<>();
        for (Entry<String, Class<? extends DungeonDefinition>> pair :
                DungeonTypeRegistry.getDungeonTypes().entrySet()) {
            String dungeonType = pair.getKey();
            if (!found.contains(pair.getValue())
                    && dungeonType.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                found.add(pair.getValue());
                options.add(dungeonType);
            }
        }
    }

    /** Adds matching play difficulty options. */
    private void addMatchingPlayDifficultyOptions(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (DungeonDefinition dungeon : this.dungeonManager.getLoadedDungeons()) {
            if (!dungeon.isUseDifficultyLevels()) {
                continue;
            }

            for (String difficulty : dungeon.getDifficultyLevels().keySet()) {
                String option = dungeon.getWorldName() + ":" + difficulty;
                if (option.toLowerCase(Locale.ROOT).contains(lowerQuery)
                        && !options.contains(option)) {
                    options.add(option);
                }
            }
        }
    }

    /** Runs complete play command. */
    private List<String> completePlayCommand(CommandSender sender, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            String query = args[1];
            if (query.contains(":")) {
                if (CommandUtils.hasPermissionSilent(sender, "dungeons.play.difficulty")) {
                    this.addMatchingPlayDifficultyOptions(options, query);
                }
            } else {
                this.addMatchingDungeonNames(options, query);
                if (CommandUtils.hasPermissionSilent(sender, "dungeons.play.difficulty")) {
                    this.addMatchingPlayDifficultyOptions(options, query);
                }
            }

            return options;
        }

        if (args.length == 3 && CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")) {
            CommandArguments.addMatchingPlayers(options, args[2]);
        }

        return options;
    }

    /** Runs complete list command. */
    private List<String> completeListCommand(String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            this.addMatchingDungeonNames(options, args[1]);
            CommandArguments.addMatchingLiteralOptions(options, args[1], "1", "2", "3");
        } else if (args.length == 3) {
            CommandArguments.addMatchingLiteralOptions(options, args[2], "1", "2", "3", "4", "5");
        }

        return options;
    }

    /** Returns whether it can tab complete play. */
    private boolean canTabCompletePlay(CommandSender sender, boolean playerSender) {
        return playerSender
                ? CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                        || CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")
                : CommandUtils.hasPermissionSilent(sender, "dungeons.play.send");
    }

    /** Returns whether it can use discovery commands. */
    private boolean canUseDiscoveryCommands(CommandSender sender) {
        if (CommandUtils.hasPermissionSilent(sender, "dungeons.role.operator")) {
            return true;
        }

        return sender instanceof Player
                ? CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                        || CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")
                : CommandUtils.hasPermissionSilent(sender, "dungeons.play.send");
    }

    /** Runs require dungeon edit access. */
    private boolean requireDungeonEditAccess(Player player, DungeonDefinition dungeon) {
        if (dungeon != null && CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            nl.hauntedmc.dungeons.runtime.staff.StaffEditGuard.configuration(
                    player, dungeon, "editor");
            return true;
        }

        LangUtils.sendMessage(player, "general.errors.no-permission");
        return false;
    }

    /** Runs send root help. */
    private void sendRootHelp(CommandSender sender) {
        boolean playerSender = sender instanceof Player;
        sender.sendMessage(
                LangUtils.getMessage(
                        "commands.root.header",
                        false,
                        LangUtils.placeholder(
                                "version",
                                this.plugin.getPluginMeta().getVersion().split("-")[0])));
        LangUtils.sendMessage(sender, "commands.root.help");
        boolean canPlayFromHere =
                playerSender
                        ? CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                                || CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")
                        : CommandUtils.hasPermissionSilent(sender, "dungeons.play.send");
        if (this.canUseDiscoveryCommands(sender)) {
            LangUtils.sendMessage(sender, "commands.root.list");
        }
        if (canPlayFromHere) {
            LangUtils.sendMessage(sender, "commands.root.play");
        }
        if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.play")) {
            LangUtils.sendMessage(sender, "commands.root.team");
        }
        if (playerSender) {
            LangUtils.sendMessage(sender, "commands.root.leave");
        }
        if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.stuck")) {
            LangUtils.sendMessage(sender, "commands.root.stuck");
        }
        if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.lives")) {
            LangUtils.sendMessage(sender, "commands.root.lives");
        }
        if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.rewards")) {
            LangUtils.sendMessage(sender, "commands.root.rewards");
        }
        if (CommandUtils.hasAnyDungeonEditAccess(sender)) {
            LangUtils.sendMessage(sender, "commands.root.config");
            if (playerSender) {
                LangUtils.sendMessage(sender, "commands.root.edit");
            }
        }
        if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.staff.loot")) {
            LangUtils.sendMessage(sender, "commands.root.loot");
        }
        if (CommandUtils.hasPermissionSilent(sender, "dungeons.role.operator")) {
            LangUtils.sendMessage(sender, "commands.root.dungeon");
            if (playerSender) {
                LangUtils.sendMessage(sender, "commands.root.debug");
            }
        }
    }

    /** Returns the permission node. */
    private String getPermissionNode() {
        return "dungeons.core";
    }

    /** Returns whether console friendly. */
    private boolean isConsoleFriendly() {
        return true;
    }

    /** Runs handle rewards command. */
    private boolean handleRewardsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.rewards")) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.rewards.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null || playerSession.getRewardItems().isEmpty()) {
            LangUtils.sendMessage(player, "commands.rewards.empty");
            return true;
        }

        this.guiService.openGui(player, "rewards");
        return true;
    }

    /** Runs handle clean signs command. */
    private boolean handleCleanSignsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.staff.edit")) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.signs.clean.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.signs.clean.not-in-dungeon");
            return true;
        }

        int cleanedSigns = ((EditableInstance) playerSession.getInstance()).cleanSigns();
        LangUtils.sendMessage(
                player,
                "commands.edit.signs.clean.success",
                LangUtils.placeholder("count", String.valueOf(cleanedSigns)));
        return true;
    }

    /** Runs handle import command. */
    private boolean handleImportCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.role.operator")) {
            return false;
        }

        if (args.length != 2 && args.length != 3) {
            LangUtils.sendMessage(sender, "commands.dungeon.import.usage");
            return true;
        }

        String worldName = args[1].trim();
        if (!this.isSafeImportWorldName(worldName)) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.import.world-not-found",
                    LangUtils.placeholder("world", args[1]));
            return true;
        }

        if (this.dungeonManager.get(worldName) != null) {
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
            saveFolder = this.resolveDungeonSaveFolder(worldName);
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
        if (args.length == 3) {
            try {
                Environment dimension = Environment.valueOf(args[2].toUpperCase(Locale.ROOT));
                importer.environment(dimension);
            } catch (IllegalArgumentException exception) {
                LangUtils.sendMessage(
                        sender,
                        "commands.dungeon.import.invalid-dimension",
                        LangUtils.placeholder("dimension", args[2].toUpperCase(Locale.ROOT)));
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
                        () -> {
                            if (saveFolder.exists()) {
                                this.plugin
                                        .getSLF4JLogger()
                                        .error(
                                                "Refusing to import world '{}' because the target"
                                                        + " dungeon folder '{}' already exists.",
                                                worldName,
                                                saveFolder.getAbsolutePath());
                                this.runImportFailure(sender);
                                return;
                            }

                            if (!saveFolder.mkdirs()) {
                                this.plugin
                                        .getSLF4JLogger()
                                        .error(
                                                "Failed to create dungeon import folder '{}'.",
                                                saveFolder.getAbsolutePath());
                                this.runImportFailure(sender);
                                return;
                            }

                            try {
                                FileUtils.copyDirectory(importedWorldFolder, saveFolder);
                            } catch (IOException exception) {
                                this.cleanupDirectory(saveFolder, "failed import target");
                                this.plugin
                                        .getSLF4JLogger()
                                        .error(
                                                "Failed to import world '{}' into dungeon folder"
                                                        + " '{}'.",
                                                worldName,
                                                saveFolder.getAbsolutePath(),
                                                exception);
                                this.runImportFailure(sender);
                                return;
                            }

                            Bukkit.getScheduler()
                                    .runTask(
                                            this.plugin,
                                            () -> {
                                                DungeonDefinition importDungeon =
                                                        this.dungeonManager.loadDungeon(saveFolder);
                                                if (importDungeon == null) {
                                                    this.cleanupDirectoryAsync(
                                                            saveFolder, "failed imported dungeon");
                                                    LangUtils.sendMessage(
                                                            sender,
                                                            "commands.dungeon.import.failed");
                                                    return;
                                                }

                                                importDungeon.setSaveConfig(
                                                        "dungeon.environment", dimension);
                                                importDungeon.setLobbySpawn(importSpawn);
                                                LangUtils.sendMessage(
                                                        sender,
                                                        "commands.dungeon.import.success",
                                                        LangUtils.placeholder("world", worldName));
                                            });
                        });

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

    /** Runs handle save command. */
    private boolean handleSaveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonInstance instance = playerSession.getInstance();
        if (instance == null) {
            LangUtils.sendMessage(player, "commands.edit.save.not-in-dungeon");
            return true;
        }

        EditableInstance editable = instance.asEditInstance();
        if (editable == null) {
            LangUtils.sendMessage(player, "commands.edit.save.not-in-dungeon");
            return true;
        }

        if (!this.requireDungeonEditAccess(player, instance.getDungeon())) {
            return false;
        }

        if (instance.getDungeon().isSaving()) {
            LangUtils.sendMessage(player, "instance.lifecycle.is-saving");
            return true;
        }

        LangUtils.sendMessage(player, "commands.edit.save.saving");
        editable.saveWorldAsync()
                .whenComplete(
                        (success, throwable) ->
                                Bukkit.getScheduler()
                                        .runTask(
                                                this.plugin,
                                                () -> {
                                                    if (!player.isOnline()) {
                                                        return;
                                                    }

                                                    if (throwable != null
                                                            || !Boolean.TRUE.equals(success)) {
                                                        LangUtils.sendMessage(
                                                                player,
                                                                "commands.edit.save.failed",
                                                                LangUtils.placeholder(
                                                                        "dungeon",
                                                                        instance.getDungeon()
                                                                                .getWorldName()));
                                                        return;
                                                    }

                                                    LangUtils.sendMessage(
                                                            player,
                                                            "commands.edit.save.success",
                                                            LangUtils.placeholder(
                                                                    "dungeon",
                                                                    instance.getDungeon()
                                                                            .getWorldName()));
                                                }));
        return true;
    }

    /** Runs handle function tool command. */
    private boolean handleFunctionToolCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.staff.edit")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null || !playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.tools.function.not-in-dungeon");
            return true;
        }

        playerSession.restoreEditorTools();
        int functionSlot = PluginConfigView.getFunctionToolSlot(this.plugin.getConfig());
        player.getInventory().setHeldItemSlot(functionSlot);
        LangUtils.sendMessage(
                player,
                "commands.edit.tools.function.restored",
                LangUtils.placeholder("slot", String.valueOf(functionSlot + 1)));
        return true;
    }

    /** Runs handle room tool command. */
    private boolean handleRoomToolCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.staff.edit")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null || !playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.tools.room.not-in-dungeon");
            return true;
        }

        if (playerSession.getInstance().as(BranchingEditableInstance.class) == null) {
            LangUtils.sendMessage(player, "commands.edit.tools.room.not-in-branching");
            return true;
        }

        playerSession.restoreEditorTools();
        int roomSlot = PluginConfigView.getRoomToolSlot(this.plugin.getConfig());
        player.getInventory().setHeldItemSlot(roomSlot);
        LangUtils.sendMessage(
                player,
                "commands.edit.tools.room.restored",
                LangUtils.placeholder("slot", String.valueOf(roomSlot + 1)));
        return true;
    }

    /** Runs handle dungeon item command. */
    private boolean handleDungeonItemCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.items.mark-dungeon.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.items.mark-dungeon.not-in-dungeon");
            return true;
        }

        if (!this.requireDungeonEditAccess(player, playerSession.getInstance().getDungeon())) {
            return false;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.add.no-held-item");
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(new NamespacedKey(this.plugin, "DungeonItem"), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        LangUtils.sendMessage(player, "commands.edit.items.mark-dungeon.success");
        return true;
    }

    /** Runs handle unban item command. */
    private boolean handleUnbanItemCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.items.unban.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.items.unban.not-in-dungeon");
            return true;
        }

        DungeonDefinition dungeon = playerSession.getInstance().getDungeon();
        if (!this.requireDungeonEditAccess(player, dungeon)) {
            return false;
        }

        boolean unbanSuccess = dungeon.unbanItem(player.getInventory().getItemInMainHand());
        if (unbanSuccess) {
            LangUtils.sendMessage(player, "commands.edit.items.unban.success");
        } else {
            LangUtils.sendMessage(player, "commands.edit.items.unban.not-banned");
        }

        return true;
    }

    /** Runs handle ban item command. */
    private boolean handleBanItemCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.items.ban.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.items.ban.not-in-dungeon");
            return true;
        }

        DungeonDefinition dungeon = playerSession.getInstance().getDungeon();
        if (!this.requireDungeonEditAccess(player, dungeon)) {
            return false;
        }

        dungeon.banItem(player.getInventory().getItemInMainHand());
        LangUtils.sendMessage(player, "commands.edit.items.ban.success");
        return true;
    }

    /** Runs handle set exit command. */
    private boolean handleSetExitCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.dungeon.exit.set.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.exit.set.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            LangUtils.sendMessage(player, "commands.edit.open.no-permission");
            return true;
        }

        Location exitLoc = player.getLocation();
        dungeon.setExit(exitLoc);
        double exitX = MathUtils.round(exitLoc.getX(), 2);
        double exitY = MathUtils.round(exitLoc.getY(), 2);
        double exitZ = MathUtils.round(exitLoc.getZ(), 2);
        double exitYAW = MathUtils.round(exitLoc.getYaw(), 2);
        LangUtils.sendMessage(
                player,
                "commands.dungeon.exit.set.success",
                LangUtils.placeholder("x", String.valueOf(exitX)),
                LangUtils.placeholder("y", String.valueOf(exitY)),
                LangUtils.placeholder("z", String.valueOf(exitZ)),
                LangUtils.placeholder("yaw", String.valueOf(exitYAW)));
        return true;
    }

    /** Runs handle set dungeon enabled command. */
    private boolean handleSetDungeonEnabledCommand(
            CommandSender sender, String[] args, boolean enabled) {
        if (!CommandUtils.hasPermission(sender, "dungeons.role.operator")) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(
                    sender,
                    enabled ? "commands.dungeon.enable.usage" : "commands.dungeon.disable.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    sender,
                    enabled
                            ? "commands.dungeon.enable.dungeon-not-found"
                            : "commands.dungeon.disable.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (dungeon.isEnabled() == enabled) {
            LangUtils.sendMessage(
                    sender,
                    enabled
                            ? "commands.dungeon.enable.already-enabled"
                            : "commands.dungeon.disable.already-disabled",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return true;
        }

        dungeon.setSaveConfig("dungeon.enabled", enabled);
        if (!enabled) {
            this.clearQueuedStartsForDisabledDungeon(dungeon);
            if (dungeon.getRuntime().staffOperations() != null)
                dungeon.getRuntime().staffOperations().cancelStarts(dungeon.getWorldName());
        }

        LangUtils.sendMessage(
                sender,
                enabled ? "commands.dungeon.enable.success" : "commands.dungeon.disable.success",
                LangUtils.placeholder("dungeon", dungeon.getWorldName()));
        return true;
    }

    /** Runs clear queued starts for disabled dungeon. */
    private void clearQueuedStartsForDisabledDungeon(DungeonDefinition dungeon) {
        for (DungeonQueueEntry queue : this.queueManager.snapshotQueues()) {
            if (queue.getDungeon() != dungeon) {
                continue;
            }

            for (UUID playerId : new ArrayList<>(queue.getPlayers())) {
                DungeonPlayerSession playerSession = this.playerManager.get(playerId);
                Player player = playerSession == null ? null : playerSession.getPlayer();
                if (player != null && player.isOnline()) {
                    LangUtils.sendMessage(
                            player,
                            "commands.play.disabled",
                            LangUtils.placeholder("dungeon", dungeon.getWorldName()));
                }
            }

            this.dungeonQueueCoordinator.discardQueue(queue);
        }
    }

    /** Runs handle set spawn command. */
    private boolean handleSetSpawnCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.start.set.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.start.set.not-in-dungeon");
            LangUtils.sendMessage(player, "commands.edit.start.set.not-editing");
            return true;
        }

        DungeonDefinition dungeon = playerSession.getInstance().getDungeon();
        if (!this.requireDungeonEditAccess(player, dungeon)) {
            return false;
        }

        Location spawnLoc = player.getLocation();
        dungeon.setStartSpawn(spawnLoc);
        double spawnX = MathUtils.round(spawnLoc.getX(), 2);
        double spawnY = MathUtils.round(spawnLoc.getY(), 2);
        double spawnZ = MathUtils.round(spawnLoc.getZ(), 2);
        double spawnYAW = MathUtils.round(spawnLoc.getYaw(), 2);
        LangUtils.sendMessage(
                player,
                "commands.edit.start.set.success",
                LangUtils.placeholder("x", String.valueOf(spawnX)),
                LangUtils.placeholder("y", String.valueOf(spawnY)),
                LangUtils.placeholder("z", String.valueOf(spawnZ)),
                LangUtils.placeholder("yaw", String.valueOf(spawnYAW)));
        return true;
    }

    /** Runs handle set lobby command. */
    private boolean handleSetLobbyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.edit.lobby.set.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.lobby.set.not-in-dungeon");
            return true;
        }

        DungeonDefinition dungeon = playerSession.getInstance().getDungeon();
        if (!this.requireDungeonEditAccess(player, dungeon)) {
            return false;
        }

        Location lobbyLocation = player.getLocation();
        dungeon.setLobbySpawn(lobbyLocation);
        double lobbyX = MathUtils.round(lobbyLocation.getX(), 2);
        double lobbyY = MathUtils.round(lobbyLocation.getY(), 2);
        double lobbyZ = MathUtils.round(lobbyLocation.getZ(), 2);
        double lobbyYAW = MathUtils.round(lobbyLocation.getYaw(), 2);
        LangUtils.sendMessage(
                player,
                "commands.edit.lobby.set.success",
                LangUtils.placeholder("x", String.valueOf(lobbyX)),
                LangUtils.placeholder("y", String.valueOf(lobbyY)),
                LangUtils.placeholder("z", String.valueOf(lobbyZ)),
                LangUtils.placeholder("yaw", String.valueOf(lobbyYAW)));
        return true;
    }

    /** Runs handle remove allkeys command. */
    private boolean handleRemoveAllkeysCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.clear.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.keys.clear.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.clear.no-permission");
            return true;
        }

        int removedCount = dungeon.removeAllAccessKeys();
        LangUtils.sendMessage(
                player,
                "commands.dungeon.keys.clear.success",
                LangUtils.placeholder("count", String.valueOf(removedCount)));
        return true;
    }

    /** Runs handle remove key command. */
    private boolean handleRemoveKeyCommand(CommandSender sender, String[] args) {
        if (args.length != 2 && args.length != 3) {
            LangUtils.sendMessage(sender, "commands.dungeon.keys.remove.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.keys.remove.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!CommandUtils.hasDungeonEditAccess(sender, dungeon.getWorldName())) {
            LangUtils.sendMessage(sender, "commands.dungeon.keys.remove.no-permission");
            return true;
        }

        if (args.length == 3) {
            Optional<Integer> parsedKeyId = InputUtils.readIntegerInput(sender, args[2]);
            if (parsedKeyId.isEmpty()) {
                return true;
            }

            if (!dungeon.removeAccessKey(parsedKeyId.get())) {
                LangUtils.sendMessage(
                        sender,
                        "commands.dungeon.keys.remove.invalid-key",
                        LangUtils.placeholder("id", args[2]),
                        LangUtils.placeholder("dungeon", dungeon.getWorldName()));
                return true;
            }

            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.keys.remove.success",
                    LangUtils.placeholder("count", "1"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            LangUtils.sendMessage(sender, "commands.dungeon.keys.remove.no-held-item");
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-held-item");
            return true;
        }

        int removedCount = dungeon.removeAccessKey(heldItem);
        if (removedCount > 0) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.keys.remove.success",
                    LangUtils.placeholder("count", String.valueOf(removedCount)));
        } else {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-key-found");
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-key-found-detail");
        }

        return true;
    }

    /** Runs handle add key command. */
    private boolean handleAddKeyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.add.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.keys.add.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.add.no-permission");
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.add.no-held-item");
            return true;
        }

        AccessKeyDefinition addedKey = dungeon.addAccessKey(heldItem, player);
        ItemStack taggedKeyItem = dungeon.issueAccessKey(addedKey);
        int keyId = dungeon.getAccessKeys().indexOf(addedKey) + 1;
        if (taggedKeyItem == null || taggedKeyItem.getType() == Material.AIR) {
            LangUtils.sendMessage(
                    player,
                    "commands.dungeon.keys.add.issue-failed",
                    LangUtils.placeholder("id", String.valueOf(keyId)));
            return true;
        }

        player.getInventory().setItemInMainHand(taggedKeyItem);
        player.updateInventory();
        LangUtils.sendMessage(
                player,
                "commands.dungeon.keys.add.success",
                LangUtils.placeholder("id", String.valueOf(keyId)));
        return true;
    }

    private boolean handleInvalidDungeonKeysUsage(CommandSender sender) {
        CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
        return true;
    }

    /** Runs handle create command. */
    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.role.operator")) {
            return false;
        }

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
                        () -> {
                            if (saveFolder.exists()) {
                                this.plugin
                                        .getSLF4JLogger()
                                        .error(
                                                "Refusing to create dungeon '{}' because the target"
                                                        + " folder '{}' already exists.",
                                                worldName,
                                                saveFolder.getAbsolutePath());
                                this.cleanupDirectory(worldFolder, "temporary world folder");
                                this.cleanupDirectory(
                                        new File(Bukkit.getWorldContainer(), tempWorldName),
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

                            if (!saveFolder.mkdirs()) {
                                this.plugin
                                        .getSLF4JLogger()
                                        .error(
                                                "Failed to create dungeon folder '{}'.",
                                                saveFolder.getAbsolutePath());
                                this.cleanupDirectory(worldFolder, "temporary world folder");
                                this.cleanupDirectory(
                                        new File(Bukkit.getWorldContainer(), tempWorldName),
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
                                FileUtils.copyDirectory(worldFolder, saveFolder);
                            } catch (IOException exception) {
                                this.cleanupDirectory(saveFolder, "failed creation target");
                                this.cleanupDirectory(worldFolder, "temporary world folder");
                                this.cleanupDirectory(
                                        new File(Bukkit.getWorldContainer(), tempWorldName),
                                        "temporary world folder");
                                this.plugin
                                        .getSLF4JLogger()
                                        .error(
                                                "Failed to create dungeon '{}' from world folder"
                                                        + " '{}'.",
                                                worldName,
                                                worldFolder.getAbsolutePath(),
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

                            this.cleanupDirectory(worldFolder, "temporary world folder");
                            this.cleanupDirectory(
                                    new File(Bukkit.getWorldContainer(), tempWorldName),
                                    "temporary world folder");

                            Bukkit.getScheduler()
                                    .runTask(
                                            this.plugin,
                                            () -> {
                                                DungeonDefinition newDungeon =
                                                        this.dungeonManager.loadDungeon(
                                                                saveFolder,
                                                                dungeonType,
                                                                generatorType);
                                                if (newDungeon == null) {
                                                    this.cleanupDirectoryAsync(
                                                            saveFolder, "failed created dungeon");
                                                    LangUtils.sendMessage(
                                                            player,
                                                            "commands.dungeon.create.failed-to-create");
                                                    this.plugin
                                                            .getSLF4JLogger()
                                                            .warn(
                                                                    "Dungeon creation failed for"
                                                                        + " '{}': unknown dungeon"
                                                                        + " type '{}'.",
                                                                    worldName,
                                                                    dungeonType);
                                                    return;
                                                }

                                                newDungeon.setLobbySpawn(spawnpoint);
                                                if (!dungeonType.isEmpty()) {
                                                    newDungeon
                                                            .getConfig()
                                                            .set("dungeon.type", dungeonType);
                                                }

                                                newDungeon.saveConfig();
                                                LangUtils.sendMessage(
                                                        player,
                                                        "commands.dungeon.create.success",
                                                        LangUtils.placeholder(
                                                                "dungeon", worldName));
                                            });
                        });

        return true;
    }

    /** Runs handle leave command. */
    private boolean handleLeaveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession != null && playerSession.isObserving()) {
            playerSession.stopObservation();
            player.sendMessage("[Dungeons] Observation ended");
            return true;
        }
        if (playerSession.getInstance() == null) {
            DungeonQueueEntry queue = this.queueManager.getQueue(playerSession);
            if (queue != null) {
                DungeonTeam team = this.teamManager.getTeam(player.getUniqueId());
                if (queue.isTeamQueue() && team != null) {
                    if (team.isLeader(player.getUniqueId())) {
                        this.queueManager.unqueue(queue);
                        this.teamManager.clearQueued(team);
                    } else {
                        this.queueManager.removePlayer(player.getUniqueId());
                    }
                } else {
                    this.queueManager.unqueue(playerSession);
                }

                LangUtils.sendMessage(player, "commands.leave.left-queue");
                return true;
            }

            DungeonTeam team = this.teamManager.getTeam(player.getUniqueId());
            if (team != null) {
                LangUtils.sendMessage(player, "commands.leave.not-in-dungeon");
                return true;
            }

            DungeonInstance instance =
                    this.activeInstanceManager.getDungeonInstance(player.getWorld().getName());
            if (instance != null) {
                if (playerSession.getSavedPosition() != null) {
                    EntityUtils.forceTeleport(player, playerSession.getSavedPosition());
                } else {
                    EntityUtils.forceTeleport(player, player.getRespawnLocation());
                }
            }

            LangUtils.sendMessage(player, "commands.leave.not-in-dungeon");
            return true;
        }

        String dungeonName = playerSession.getInstance().getDungeon().getDisplayName();
        playerSession.getInstance().removePlayer(playerSession);
        LangUtils.sendMessage(
                player,
                "commands.leave.left-dungeon",
                LangUtils.placeholder("dungeon", dungeonName));
        return true;
    }

    /** Runs handle lives command. */
    private boolean handleLivesCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.lives")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonInstance instance = playerSession.getInstance();
        if (instance == null) {
            LangUtils.sendMessage(player, "commands.lives.not-in-dungeon");
            return true;
        }

        PlayableInstance playable = instance.asPlayInstance();
        if (playable != null) {
            Integer livesRemaining = playable.getPlayerLives().get(player.getUniqueId());
            if (livesRemaining == null) {
                LangUtils.sendMessage(player, "commands.lives.infinite-lives");
            } else {
                LangUtils.sendMessage(
                        player,
                        "commands.lives.lives-remaining",
                        LangUtils.placeholder("lives", String.valueOf(livesRemaining)));
            }
        }

        return true;
    }

    /** Runs handle stuck command. */
    private boolean handleStuckCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.stuck")) {
            return false;
        }

        if (args.length != 1) {
            LangUtils.sendMessage(player, "commands.stuck.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession.getInstance() == null) {
            LangUtils.sendMessage(player, "commands.stuck.not-in-dungeon");
            return true;
        }

        LangUtils.sendMessage(player, "commands.stuck.success");
        playerSession.sendToCheckpoint();
        return true;
    }

    /** Runs handle play command. */
    private boolean handlePlayCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.play")
                && !CommandUtils.hasPermission(sender, "dungeons.play.send")) {
            return true;
        }

        if (args.length < 2) {
            LangUtils.sendMessage(sender, "commands.play.usage");
            return true;
        }

        Player targetPlayer = null;

        if (args.length == 2) {
            if (!CommandUtils.hasPermission(sender, "dungeons.play")) {
                return true;
            }

            if (!(sender instanceof Player player)) {
                LangUtils.sendMessage(sender, "commands.play.console-needs-player");
                return true;
            }

            targetPlayer = player;
        } else if (args.length == 3) {
            if (!CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")) {
                return true;
            }

            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                LangUtils.sendMessage(
                        sender,
                        "commands.play.player-not-found",
                        LangUtils.placeholder("player", args[2]));
                return true;
            }
        }

        if (targetPlayer == null) {
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(targetPlayer);
        if (playerSession.isAwaitingDungeon()) {
            LangUtils.sendMessage(sender, "commands.play.already-in-queue");
            return true;
        }

        if (playerSession.getInstance() != null) {
            LangUtils.sendMessage(sender, "commands.play.already-in-dungeon");
            return true;
        }

        if (this.queueManager.getQueue(playerSession) != null) {
            LangUtils.sendMessage(sender, "commands.play.already-in-queue");
            return true;
        }

        String dungeonName = args[1];
        String difficulty = "";
        if (dungeonName.contains(":")) {
            String[] split = dungeonName.split(":", 2);
            dungeonName = split[0];
            difficulty = split[1];
        }

        DungeonDefinition dungeon = this.dungeonManager.get(dungeonName);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    sender,
                    "commands.play.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!dungeon.isEnabled()) {
            LangUtils.sendMessage(
                    sender,
                    "commands.play.disabled",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            if (!sender.equals(targetPlayer)) {
                LangUtils.sendMessage(
                        targetPlayer,
                        "commands.play.disabled",
                        LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            }
            return true;
        }

        boolean useDifficulty = dungeon.isUseDifficultyLevels();
        boolean showMenu = dungeon.isShowDifficultyMenu();
        if (useDifficulty) {
            if (difficulty.isEmpty()) {
                if (showMenu) {
                    this.guiService.openGui(targetPlayer, "difficulty_" + dungeon.getWorldName());
                    return true;
                }
            } else if (!CommandUtils.hasPermission(sender, "dungeons.play.difficulty")) {
                return true;
            }
        }

        this.dungeonQueueCoordinator.sendToDungeon(targetPlayer, dungeonName, difficulty);
        return true;
    }

    /** Runs handle help command. */
    private boolean handleHelpCommand(CommandSender sender, String[] args) {
        if (args.length == 2) {
            Optional<Integer> helpPage = InputUtils.readIntegerInput(sender, args[1]);
            CommandUtils.displayHelpMenu(sender, helpPage.orElse(1));
        } else {
            CommandUtils.displayHelpMenu(sender, 1);
        }

        return true;
    }

    /** Runs handle list command. */
    private boolean handleListCommand(CommandSender sender, String[] args) {
        if (!this.canUseDiscoveryCommands(sender)) {
            LangUtils.sendMessage(sender, "general.errors.no-permission");
            return true;
        }

        FilterPageQuery query =
                this.output.parseFilterPageQuery(sender, args, 1, "commands.list.usage");
        if (query == null) {
            return true;
        }

        List<DungeonDefinition> dungeons = this.output.getFilteredDungeons(query.filter(), false);
        List<String> lines = new ArrayList<>();
        for (DungeonDefinition dungeon : dungeons) {
            lines.add(
                    LangUtils.getMessage(
                            "commands.list.line",
                            false,
                            LangUtils.placeholder("dungeon", dungeon.getWorldName()),
                            LangUtils.placeholder("display", dungeon.getDisplayName()),
                            LangUtils.placeholder(
                                    "team_size", String.valueOf(dungeon.getMaxTeamSize())),
                            LangUtils.placeholder(
                                    "difficulties",
                                    this.output.formatDungeonDifficulties(dungeon))));
        }

        this.output.sendPaginatedLines(
                sender,
                LangUtils.getMessage("commands.list.header", false),
                lines,
                "commands.list.no-match",
                "commands.list.footer",
                query.page());
        return true;
    }

    /** Runs handle dungeon list command. */
    private boolean handleDungeonListCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.role.operator")) {
            return false;
        }

        FilterPageQuery query =
                this.output.parseFilterPageQuery(sender, args, 1, "commands.dungeon.list.usage");
        if (query == null) {
            return true;
        }

        List<DungeonDefinition> dungeons = this.output.getFilteredDungeons(query.filter(), true);
        List<String> lines = new ArrayList<>();
        for (DungeonDefinition dungeon : dungeons) {
            lines.add(
                    LangUtils.getMessage(
                            "commands.dungeon.list.line",
                            false,
                            LangUtils.placeholder("dungeon", dungeon.getWorldName()),
                            LangUtils.placeholder("display", dungeon.getDisplayName()),
                            LangUtils.placeholder("type", this.output.getDungeonTypeName(dungeon)),
                            LangUtils.placeholder(
                                    "instances", String.valueOf(dungeon.getInstances().size())),
                            LangUtils.placeholder(
                                    "team_size", String.valueOf(dungeon.getMaxTeamSize())),
                            LangUtils.placeholder(
                                    "difficulties",
                                    this.output.formatDungeonDifficulties(dungeon))));
        }

        this.output.sendPaginatedLines(
                sender,
                LangUtils.getMessage("commands.dungeon.list.header", false),
                lines,
                "commands.dungeon.list.no-match",
                "commands.dungeon.list.footer",
                query.page());
        return true;
    }

    /** Runs handle dungeon info command. */
    private boolean handleDungeonInfoCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.role.operator")) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(sender, "commands.dungeon.info.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(
                    sender,
                    "commands.dungeon.status.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        sender.sendMessage(
                LangUtils.getMessage(
                        "commands.dungeon.info.header",
                        false,
                        LangUtils.placeholder("dungeon", dungeon.getWorldName())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.display",
                LangUtils.placeholder("display", dungeon.getDisplayName()));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.type",
                LangUtils.placeholder("type", this.output.getDungeonTypeName(dungeon)));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.team-size",
                LangUtils.placeholder("team_size", String.valueOf(dungeon.getMaxTeamSize())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.instances",
                LangUtils.placeholder("count", String.valueOf(dungeon.getInstances().size())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.locations",
                LangUtils.placeholder(
                        "lobby", this.output.formatConfiguredState(dungeon.getLobbySpawn())),
                LangUtils.placeholder(
                        "start", this.output.formatConfiguredState(dungeon.getStartSpawn())),
                LangUtils.placeholder(
                        "exit", this.output.formatConfiguredState(dungeon.getExitLoc())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.difficulties",
                LangUtils.placeholder(
                        "difficulties", this.output.formatDungeonDifficulties(dungeon)));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.keys",
                LangUtils.placeholder("count", String.valueOf(dungeon.getValidKeys().size())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.enabled",
                LangUtils.placeholder(
                        "state", this.output.formatEnabledState(dungeon.isEnabled())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.access-cooldown",
                LangUtils.placeholder(
                        "state",
                        this.output.formatEnabledState(dungeon.isAccessCooldownEnabled())));
        LangUtils.sendMessage(
                sender,
                "commands.dungeon.info.edit-session",
                LangUtils.placeholder(
                        "state", this.output.formatYesNoState(dungeon.getEditSession() != null)));
        return true;
    }

    /** Runs handle edit close command. */
    private boolean handleEditCloseCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.edit.close.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null) {
            LangUtils.sendMessage(player, "commands.edit.close.not-in-editor");
            return true;
        }

        DungeonInstance instance = playerSession.getInstance();
        if (instance == null || instance.asEditInstance() == null || !playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.close.not-in-editor");
            return true;
        }

        if (!this.requireDungeonEditAccess(player, instance.getDungeon())) {
            return false;
        }

        String dungeonName = instance.getDungeon().getDisplayName();
        instance.removePlayer(playerSession);
        LangUtils.sendMessage(
                player,
                "commands.edit.close.success",
                LangUtils.placeholder("dungeon", dungeonName));
        return true;
    }

    /** Handles dungeon definition administration subcommands. */
    private final class DungeonDefinitionCommandGroup implements CommandHandler {
        /** Runs handle command. */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            if (args.length < 2) {
                CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
                return true;
            }

            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "create" ->
                        DungeonCommand.this.handleCreateCommand(
                                sender, CommandArguments.shiftArgs(args));
                case "import" ->
                        DungeonCommand.this.handleImportCommand(
                                sender, CommandArguments.shiftArgs(args));
                case "list" ->
                        DungeonCommand.this.handleDungeonListCommand(
                                sender, CommandArguments.shiftArgs(args));
                case "info" ->
                        DungeonCommand.this.handleDungeonInfoCommand(
                                sender, CommandArguments.shiftArgs(args));
                case "enable" ->
                        DungeonCommand.this.handleSetDungeonEnabledCommand(
                                sender, CommandArguments.shiftArgs(args), true);
                case "disable" ->
                        DungeonCommand.this.handleSetDungeonEnabledCommand(
                                sender, CommandArguments.shiftArgs(args), false);
                case "keys" -> this.handleKeysCommand(sender, args);
                case "exit" -> this.handleExitCommand(sender, args);
                default -> {
                    CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
                    yield true;
                }
            };
        }

        /** Runs handle tab complete. */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                CommandArguments.addMatchingLiteralOptions(
                        options, args[1], "create", "import", "list", "info", "enable", "disable",
                        "keys", "exit");
                return options;
            }

            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "info", "enable", "disable" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingDungeonNames(options, args[2]);
                    }
                }
                case "list" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingDungeonNames(options, args[2]);
                    } else if (args.length == 4) {
                        CommandArguments.addMatchingLiteralOptions(
                                options, args[3], "1", "2", "3", "4", "5");
                    }
                }

                case "import" -> {
                    if (args.length == 4) {
                        CommandArguments.addMatchingLiteralOptions(
                                options, args[3], "NORMAL", "NETHER", "THE_END");
                    }
                }
                case "create" -> {
                    if (args.length == 4) {
                        DungeonCommand.this.addMatchingDungeonTypeNames(options, args[3]);
                    }
                }
                case "keys" -> {
                    if (args.length == 3)
                        CommandArguments.addMatchingLiteralOptions(
                                options, args[2], "add", "remove", "clear");
                    else if (args.length == 4)
                        DungeonCommand.this.addMatchingEditableDungeonNames(
                                sender, options, args[3]);
                }
                case "exit" -> {
                    if (args.length == 3) {
                        CommandArguments.addMatchingLiteralOptions(options, args[2], "set");
                    } else if (args.length == 4 && args[2].equalsIgnoreCase("set")) {
                        DungeonCommand.this.addMatchingDungeonNames(options, args[3]);
                    }
                }
                default -> {
                    return List.of();
                }
            }

            return options;
        }

        /** Runs handle keys command. */
        private boolean handleKeysCommand(CommandSender sender, String[] args) {
            if (args.length > 3)
                nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.require(
                        sender, "keys", args[3]);
            if (args.length < 4) {
                CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
                return true;
            }

            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "add" ->
                        args.length == 4
                                ? DungeonCommand.this.handleAddKeyCommand(
                                        sender, new String[] {"addkey", args[3]})
                                : DungeonCommand.this.handleInvalidDungeonKeysUsage(sender);
                case "remove" ->
                        args.length == 4
                                ? DungeonCommand.this.handleRemoveKeyCommand(
                                        sender, new String[] {"removekey", args[3]})
                                : args.length == 5
                                        ? DungeonCommand.this.handleRemoveKeyCommand(
                                                sender,
                                                new String[] {"removekey", args[3], args[4]})
                                        : DungeonCommand.this.handleInvalidDungeonKeysUsage(sender);
                case "clear" ->
                        args.length == 4
                                ? DungeonCommand.this.handleRemoveAllkeysCommand(
                                        sender, new String[] {"clearkeys", args[3]})
                                : DungeonCommand.this.handleInvalidDungeonKeysUsage(sender);
                default -> {
                    CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
                    yield true;
                }
            };
        }

        /** Runs handle exit command. */
        private boolean handleExitCommand(CommandSender sender, String[] args) {
            if (args.length == 4 && args[2].equalsIgnoreCase("set")) {
                return DungeonCommand.this.handleSetExitCommand(
                        sender, new String[] {"setexit", args[3]});
            }

            CommandArguments.sendUsageList(sender, "commands.dungeon.help.lines");
            return true;
        }
    }

    /** Handles edit-mode and builder workflow subcommands. */
    private final class EditCommandGroup implements CommandHandler {
        /** Runs handle command. */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            if (args.length < 2) {
                CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "close" -> DungeonCommand.this.handleEditCloseCommand(sender, args);
                case "save" -> this.handleSaveCommand(sender, args);
                case "lobby" -> this.handleLobbyCommand(sender, args);
                case "start" -> this.handleStartCommand(sender, args);
                case "items" -> this.handleItemsCommand(sender, args);
                case "tools" -> this.handleToolsCommand(sender, args);
                case "signs" -> this.handleSignsCommand(sender, args);
                default -> {
                    CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                    yield true;
                }
            };
        }

        /** Runs handle tab complete. */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                CommandArguments.addMatchingLiteralOptions(
                        options, args[1], "close", "save", "lobby", "start", "items", "tools",
                        "signs");
                DungeonCommand.this.addMatchingEditableDungeonNames(sender, options, args[1]);
                return options;
            }

            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "lobby", "start" -> {
                    if (args.length == 3) {
                        CommandArguments.addMatchingLiteralOptions(options, args[2], "set");
                    }
                }
                case "items" -> {
                    if (args.length == 3) {
                        CommandArguments.addMatchingLiteralOptions(
                                options, args[2], "ban", "unban", "mark-dungeon");
                    }
                }
                case "tools" -> {
                    if (args.length == 3) {
                        CommandArguments.addMatchingLiteralOptions(
                                options, args[2], "function", "room");
                    }
                }
                case "signs" -> {
                    if (args.length == 3) {
                        CommandArguments.addMatchingLiteralOptions(options, args[2], "clean");
                    }
                }
                default -> {
                    return List.of();
                }
            }

            return options;
        }

        /** Returns whether named subcommand. */
        private boolean isNamedSubcommand(String value) {
            return value.equalsIgnoreCase("open")
                    || value.equalsIgnoreCase("close")
                    || value.equalsIgnoreCase("save")
                    || value.equalsIgnoreCase("lobby")
                    || value.equalsIgnoreCase("start")
                    || value.equalsIgnoreCase("items")
                    || value.equalsIgnoreCase("tools")
                    || value.equalsIgnoreCase("signs");
        }

        /** Runs handle save command. */
        private boolean handleSaveCommand(CommandSender sender, String[] args) {
            if (args.length != 2) {
                CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            return DungeonCommand.this.handleSaveCommand(sender);
        }

        /** Runs handle lobby command. */
        private boolean handleLobbyCommand(CommandSender sender, String[] args) {
            if (args.length == 3 && args[2].equalsIgnoreCase("set")) {
                return DungeonCommand.this.handleSetLobbyCommand(sender, new String[] {"setlobby"});
            }

            CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }

        /** Runs handle start command. */
        private boolean handleStartCommand(CommandSender sender, String[] args) {
            if (args.length == 3 && args[2].equalsIgnoreCase("set")) {
                return DungeonCommand.this.handleSetSpawnCommand(sender, new String[] {"setspawn"});
            }

            CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }

        /** Runs handle items command. */
        private boolean handleItemsCommand(CommandSender sender, String[] args) {
            if (args.length != 3) {
                CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "ban" ->
                        DungeonCommand.this.handleBanItemCommand(sender, new String[] {"banitem"});
                case "unban" ->
                        DungeonCommand.this.handleUnbanItemCommand(
                                sender, new String[] {"unbanitem"});
                case "mark-dungeon" ->
                        DungeonCommand.this.handleDungeonItemCommand(
                                sender, new String[] {"dungeonitem"});
                default -> {
                    CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                    yield true;
                }
            };
        }

        /** Runs handle tools command. */
        private boolean handleToolsCommand(CommandSender sender, String[] args) {
            if (args.length != 3) {
                CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "function" -> DungeonCommand.this.handleFunctionToolCommand(sender);
                case "room" -> DungeonCommand.this.handleRoomToolCommand(sender);
                default -> {
                    CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
                    yield true;
                }
            };
        }

        /** Runs handle signs command. */
        private boolean handleSignsCommand(CommandSender sender, String[] args) {
            if (args.length == 3 && args[2].equalsIgnoreCase("clean")) {
                return DungeonCommand.this.handleCleanSignsCommand(
                        sender, new String[] {"cleansigns"});
            }

            CommandArguments.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }
    }

    /** Handles active-instance administration subcommands. */
}
