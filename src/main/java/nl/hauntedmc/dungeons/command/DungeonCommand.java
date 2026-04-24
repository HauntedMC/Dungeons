package nl.hauntedmc.dungeons.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.UUID;
import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.command.config.DungeonConfigCommandHandler;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
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
import nl.hauntedmc.dungeons.util.entity.PlayerUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.time.TimeUtils;
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

/**
 * Central Bukkit command executor for the plugin's root command.
 *
 * <p>This class owns top-level command dispatch, permission gating, and tab completion for the
 * plugin's command tree. The implementation is intentionally centralized because many commands need
 * access to the same runtime managers, while nested {@link RootCommandGroup} implementations keep
 * the second-level command branches from becoming completely flat.</p>
 */
public final class DungeonCommand implements TabExecutor {
    private final DungeonsPlugin plugin;
    private final PlayerSessionRegistry playerManager;
    private final DungeonRepository dungeonManager;
    private final ActiveInstanceRegistry activeInstanceManager;
    private final DungeonQueueCoordinator dungeonQueueCoordinator;
    private final DungeonQueueRegistry queueManager;
    private final DungeonTeamService teamManager;
    private final LootTableRepository lootTableManager;
    private final LifecycleCoordinator lifecycleCoordinator;
    private final GuiService guiService;
    private final DungeonConfigCommandHandler configCommandHandler;
    private final RootCommandGroup teamCommandGroup;
    private final RootCommandGroup lootCommandGroup;
    private final RootCommandGroup dungeonCommandGroup;
    private final RootCommandGroup editCommandGroup;
    private final RootCommandGroup playerCommandGroup;
    private final RootCommandGroup instanceCommandGroup;
    private final RootCommandGroup debugCommandGroup;

    /**
     * Creates the root command executor and binds it to the configured Bukkit command name.
     */
    public DungeonCommand(DungeonsPlugin plugin, PlayerSessionRegistry playerManager, DungeonRepository dungeonManager,
            ActiveInstanceRegistry activeInstanceManager, DungeonQueueCoordinator dungeonQueueCoordinator,
            DungeonQueueRegistry queueManager, DungeonTeamService teamManager, LootTableRepository lootTableManager,
            FunctionRegistry functionManager, LifecycleCoordinator lifecycleCoordinator, GuiService guiService,
            String command) {
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.dungeonManager = dungeonManager;
        this.activeInstanceManager = activeInstanceManager;
        this.dungeonQueueCoordinator = dungeonQueueCoordinator;
        this.queueManager = queueManager;
        this.teamManager = teamManager;
        this.lootTableManager = lootTableManager;
        this.lifecycleCoordinator = lifecycleCoordinator;
        this.guiService = guiService;
        this.configCommandHandler = new DungeonConfigCommandHandler(dungeonManager, playerManager,
                activeInstanceManager);
        this.teamCommandGroup = new TeamCommandGroup();
        this.lootCommandGroup = new LootCommandGroup();
        this.dungeonCommandGroup = new DungeonDefinitionCommandGroup();
        this.editCommandGroup = new EditCommandGroup();
        this.playerCommandGroup = new PlayerAdminCommandGroup();
        this.instanceCommandGroup = new InstanceAdminCommandGroup();
        this.debugCommandGroup = new DebugCommandGroup();
        PluginCommand pluginCommand = plugin.getServer().getPluginCommand(command);
        if (pluginCommand != null) {
            pluginCommand.setExecutor(this);
            pluginCommand.setTabCompleter(this);
        }
    }

    /**
     * Handles Bukkit command execution after the server has resolved the root command.
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command cmd,
            @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(this.getPermissionNode())) {
            LangUtils.sendMessage(sender, "general.errors.no-permission");
            return true;
        }

        if (!this.isConsoleFriendly() && !(sender instanceof Player)) {
            LangUtils.sendMessage(sender, "general.errors.players-only");
            return true;
        }

        return this.handleCommand(sender, args);
    }

    /**
     * Produces tab completions for the root command and its nested subcommands.
     */
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command cmd,
            @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(this.getPermissionNode())) {
            return null;
        }

        List<String> tabCompletes = this.handleTabComplete(sender, args);
        if (tabCompletes == null && args.length == 1) {
            return new ArrayList<>();
        }

        return tabCompletes;
    }

    /**
     * Runs handle command.
     */
    private boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            this.sendRootHelp(sender);
            return true;
        }

        // Keep the first-level command dispatch in one switch so permissions, help behavior, and
        // root subcommand names stay easy to audit from a single place.
        return switch (args[0].toLowerCase(Locale.ROOT)) {
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
            case "player" -> this.playerCommandGroup.handleCommand(sender, args);
            case "instance" -> this.instanceCommandGroup.handleCommand(sender, args);
            case "debug" -> this.debugCommandGroup.handleCommand(sender, args);
            case "join" -> this.handleJoinCommand(sender, args);
            case "kick" -> this.handleKickCommand(sender, args);
            case "status" -> this.handleStatusCommand(sender, args);
            case "reload" -> this.handleReloadCommand(sender, args);
            case "create" -> this.handleCreateCommand(sender, args);
            case "delete" -> this.handleDeleteCommand(sender, args);
            case "addkey" -> this.handleAddKeyCommand(sender, args);
            case "removekey" -> this.handleRemoveKeyCommand(sender, args);
            case "clearkeys" -> this.handleRemoveAllkeysCommand(sender, args);
            case "setlobby" -> this.handleSetLobbyCommand(sender, args);
            case "setspawn" -> this.handleSetSpawnCommand(sender, args);
            case "setexit" -> this.handleSetExitCommand(sender, args);
            case "banitem" -> this.handleBanItemCommand(sender, args);
            case "unbanitem" -> this.handleUnbanItemCommand(sender, args);
            case "dungeonitem" -> this.handleDungeonItemCommand(sender, args);
            case "functiontool" -> this.handleFunctionToolCommand(sender);
            case "roomtool" -> this.handleRoomToolCommand(sender);
            case "save" -> this.handleSaveCommand(sender);
            case "import" -> this.handleImportCommand(sender, args);
            case "cleansigns" -> this.handleCleanSignsCommand(sender, args);
            case "setcooldown" -> this.handleSetCooldownCommand(sender, args);
            case "testdoor" -> this.handleTestDoorCommand(sender, args);
            case "testalldoors" -> this.handleTestAllDoorsCommand(sender, args);
            case "getmap" -> this.handleMapCommand(sender, args);
            default -> {
                this.sendRootHelp(sender);
                yield true;
            }
        };
    }

    /**
     * Runs handle tab complete.
     */
    private List<String> handleTabComplete(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("config")) {
            return this.configCommandHandler.handleTabComplete(sender, args);
        }

        boolean playerSender = sender instanceof Player;
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            String query = args[0];
            boolean canPlayFromHere = playerSender
                    ? CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                            || CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")
                    : CommandUtils.hasPermissionSilent(sender, "dungeons.play.send");
            if (this.canUseDiscoveryCommands(sender)) {
                this.addMatchingLiteralOptions(options, query, "list");
            }
            if (canPlayFromHere) {
                this.addMatchingLiteralOptions(options, query, "play");
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.play")) {
                this.addMatchingLiteralOptions(options, query, "team");
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.stuck")) {
                this.addMatchingLiteralOptions(options, query, "stuck");
            }

            this.addMatchingLiteralOptions(options, query, "help");
            if (playerSender) {
                this.addMatchingLiteralOptions(options, query, "leave");
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.lives")) {
                this.addMatchingLiteralOptions(options, query, "lives");
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.rewards")) {
                this.addMatchingLiteralOptions(options, query, "rewards");
            }

            if (CommandUtils.hasAnyDungeonEditAccess(sender)) {
                this.addMatchingLiteralOptions(options, query, "config");
                if (playerSender) {
                    this.addMatchingLiteralOptions(options, query, "edit");
                }
            }

            if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.loottables")) {
                this.addMatchingLiteralOptions(options, query, "loot");
            }

            if (CommandUtils.hasPermissionSilent(sender, "dungeons.admin")) {
                this.addMatchingLiteralOptions(options, query, "dungeon", "player");
                if (playerSender) {
                    this.addMatchingLiteralOptions(options, query, "instance", "debug");
                }
            }

            return options;
        }

        String root = args[0].toLowerCase(Locale.ROOT);
        // Nested command groups own their second-level completions so each branch can evolve without
        // making the root completion logic unreadable.
        return switch (root) {
            case "help" -> args.length == 2 ? List.of("<page>") : List.of();
            case "list" -> this.canUseDiscoveryCommands(sender) ? this.completeListCommand(args) : List.of();
            case "play" ->
                this.canTabCompletePlay(sender, playerSender) ? this.completePlayCommand(sender, args) : List.of();
            case "team" -> playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                    ? this.teamCommandGroup.handleTabComplete(sender, args)
                    : List.of();
            case "loot" -> playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.loottables")
                    ? this.lootCommandGroup.handleTabComplete(sender, args)
                    : List.of();
            case "dungeon" -> CommandUtils.hasPermissionSilent(sender, "dungeons.admin")
                    ? this.dungeonCommandGroup.handleTabComplete(sender, args)
                    : List.of();
            case "edit" -> playerSender && CommandUtils.hasAnyDungeonEditAccess(sender)
                    ? this.editCommandGroup.handleTabComplete(sender, args)
                    : List.of();
            case "player" -> CommandUtils.hasPermissionSilent(sender, "dungeons.admin")
                    ? this.playerCommandGroup.handleTabComplete(sender, args)
                    : List.of();
            case "instance" -> playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.admin")
                    ? this.instanceCommandGroup.handleTabComplete(sender, args)
                    : List.of();
            case "debug" -> playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.admin")
                    ? this.debugCommandGroup.handleTabComplete(sender, args)
                    : List.of();
            default -> List.of();
        };
    }

    /**
     * Adds matching dungeon names.
     */
    private void addMatchingDungeonNames(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (DungeonDefinition dungeon : this.dungeonManager.getLoadedDungeons()) {
            String namespace = dungeon.getWorldName().toLowerCase(Locale.ROOT);
            if (namespace.contains(lowerQuery)) {
                options.add(dungeon.getWorldName());
            }
        }
    }

    /**
     * Adds matching editable dungeon names.
     */
    private void addMatchingEditableDungeonNames(CommandSender sender, List<String> options, String query) {
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

    /**
     * Adds matching loot tables.
     */
    private void addMatchingLootTables(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (LootTable table : this.lootTableManager.getTables()) {
            String namespace = table.getNamespace().toLowerCase(Locale.ROOT);
            if (namespace.contains(lowerQuery)) {
                options.add(table.getNamespace());
            }
        }
    }

    /**
     * Adds matching players.
     */
    private void addMatchingPlayers(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getName().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                options.add(target.getName());
            }
        }
    }

    /**
     * Adds matching dungeon type names.
     */
    private void addMatchingDungeonTypeNames(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        List<Class<?>> found = new ArrayList<>();
        for (Entry<String, Class<? extends DungeonDefinition>> pair : DungeonTypeRegistry.getDungeonTypes().entrySet()) {
            String dungeonType = pair.getKey();
            if (!found.contains(pair.getValue()) && dungeonType.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                found.add(pair.getValue());
                options.add(dungeonType);
            }
        }
    }

    /**
     * Adds matching literal options.
     */
    private void addMatchingLiteralOptions(List<String> options, String query, String... literals) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (String literal : literals) {
            if (literal.toLowerCase(Locale.ROOT).contains(lowerQuery) && !options.contains(literal)) {
                options.add(literal);
            }
        }
    }

    /**
     * Adds matching play difficulty options.
     */
    private void addMatchingPlayDifficultyOptions(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (DungeonDefinition dungeon : this.dungeonManager.getLoadedDungeons()) {
            if (!dungeon.isUseDifficultyLevels()) {
                continue;
            }

            for (String difficulty : dungeon.getDifficultyLevels().keySet()) {
                String option = dungeon.getWorldName() + ":" + difficulty;
                if (option.toLowerCase(Locale.ROOT).contains(lowerQuery) && !options.contains(option)) {
                    options.add(option);
                }
            }
        }
    }

    /**
     * Runs complete play command.
     */
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
            this.addMatchingPlayers(options, args[2]);
        }

        return options;
    }

    /**
     * Runs complete list command.
     */
    private List<String> completeListCommand(String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 2) {
            this.addMatchingDungeonNames(options, args[1]);
            this.addMatchingLiteralOptions(options, args[1], "1", "2", "3");
        } else if (args.length == 3) {
            this.addMatchingLiteralOptions(options, args[2], "1", "2", "3", "4", "5");
        }

        return options;
    }

    /**
     * Returns whether it can tab complete play.
     */
    private boolean canTabCompletePlay(CommandSender sender, boolean playerSender) {
        return playerSender
                ? CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                        || CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")
                : CommandUtils.hasPermissionSilent(sender, "dungeons.play.send");
    }

    /**
     * Returns whether it can use discovery commands.
     */
    private boolean canUseDiscoveryCommands(CommandSender sender) {
        if (CommandUtils.hasPermissionSilent(sender, "dungeons.admin")) {
            return true;
        }

        return sender instanceof Player
                ? CommandUtils.hasPermissionSilent(sender, "dungeons.play")
                        || CommandUtils.hasPermissionSilent(sender, "dungeons.play.send")
                : CommandUtils.hasPermissionSilent(sender, "dungeons.play.send");
    }

    /**
     * Runs require dungeon edit access.
     */
    private boolean requireDungeonEditAccess(Player player, DungeonDefinition dungeon) {
        if (dungeon != null && CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            return true;
        }

        LangUtils.sendMessage(player, "general.errors.no-permission");
        return false;
    }

    /**
     * Runs shift args.
     */
    private String[] shiftArgs(String[] args) {
        return this.shiftArgs(args, 1);
    }

    /**
     * Runs shift args.
     */
    private String[] shiftArgs(String[] args, int amount) {
        if (args.length <= amount) {
            return new String[0];
        }

        String[] shiftedArgs = new String[args.length - amount];
        System.arraycopy(args, amount, shiftedArgs, 0, shiftedArgs.length);
        return shiftedArgs;
    }

    /**
     * Runs send usage list.
     */
    private void sendUsageList(CommandSender sender, String key) {
        LangUtils.sendMessageList(sender, key);
    }

    /**
     * Runs send root help.
     */
    private void sendRootHelp(CommandSender sender) {
        boolean playerSender = sender instanceof Player;
        sender.sendMessage(LangUtils.getMessage("commands.root.header", false,
                LangUtils.placeholder("version", this.plugin.getPluginMeta().getVersion().split("-")[0])));
        LangUtils.sendMessage(sender, "commands.root.help");
        boolean canPlayFromHere = playerSender
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
        if (playerSender && CommandUtils.hasPermissionSilent(sender, "dungeons.loottables")) {
            LangUtils.sendMessage(sender, "commands.root.loot");
        }
        if (CommandUtils.hasPermissionSilent(sender, "dungeons.admin")) {
            LangUtils.sendMessage(sender, "commands.root.dungeon");
            LangUtils.sendMessage(sender, "commands.root.player");
            if (playerSender) {
                LangUtils.sendMessage(sender, "commands.root.instance");
                LangUtils.sendMessage(sender, "commands.root.debug");
            }
        }
    }

    /**
     * Returns the permission node.
     */
    private String getPermissionNode() {
        return "dungeons.core";
    }

    /**
     * Returns whether console friendly.
     */
    private boolean isConsoleFriendly() {
        return true;
    }

    /**
     * Runs handle rewards command.
     */
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

    /**
     * Runs handle set cooldown command.
     */
    private boolean handleSetCooldownCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.setcooldown")) {
            return false;
        }

        if (args.length < 3) {
            LangUtils.sendMessage(sender, "commands.player.cooldown.set.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(sender, "commands.player.cooldown.set.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[2]);
        if (targetPlayer == null) {
            LangUtils.sendMessage(sender, "commands.player.cooldown.set.player-not-found",
                    LangUtils.placeholder("player", args[2]));
            return true;
        }

        if (args.length == 3) {
            dungeon.addAccessCooldown(targetPlayer);
            LangUtils.sendMessage(sender, "commands.player.cooldown.set.success",
                    LangUtils.placeholder("player", PlayerUtils.playerDisplayName(targetPlayer)),
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            LangUtils.sendMessage(sender, "commands.player.cooldown.set.cooldown-time",
                    LangUtils.placeholder("time", TimeUtils.formatDate(dungeon.getNextUnlockTime())));
            return true;
        }

        String duration = args[3];
        Date resetTime = TimeUtils.convertDurationString(duration);
        if (resetTime == null) {
            LangUtils.sendMessage(sender, "commands.player.cooldown.set.invalid-duration");
            LangUtils.sendMessage(sender, "commands.player.cooldown.set.invalid-duration-format");
            return true;
        }

        dungeon.addAccessCooldown(targetPlayer, resetTime);
        LangUtils.sendMessage(sender, "commands.player.cooldown.set.success",
                LangUtils.placeholder("player", PlayerUtils.playerDisplayName(targetPlayer)),
                LangUtils.placeholder("dungeon", dungeon.getWorldName()));
        LangUtils.sendMessage(sender, "commands.player.cooldown.set.cooldown-time",
                LangUtils.placeholder("time", TimeUtils.formatDate(resetTime)));
        return true;
    }

    /**
     * Runs handle clean signs command.
     */
    private boolean handleCleanSignsCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.cleansigns")) {
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
        LangUtils.sendMessage(player, "commands.edit.signs.clean.success",
                LangUtils.placeholder("count", String.valueOf(cleanedSigns)));
        return true;
    }

    /**
     * Runs handle import command.
     */
    private boolean handleImportCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 2 && args.length != 3) {
            LangUtils.sendMessage(sender, "commands.dungeon.import.usage");
            return true;
        }

        String worldName = args[1].trim();
        if (!this.isSafeImportWorldName(worldName)) {
            LangUtils.sendMessage(sender, "commands.dungeon.import.world-not-found",
                    LangUtils.placeholder("world", args[1]));
            return true;
        }

        if (this.dungeonManager.get(worldName) != null) {
            LangUtils.sendMessage(sender, "commands.dungeon.import.already-exists",
                    LangUtils.placeholder("world", worldName));
            return true;
        }

        File importFolder;
        try {
            importFolder = this.resolveSafeChildDirectory(Bukkit.getWorldContainer(), worldName);
        } catch (IOException exception) {
            this.plugin.getSLF4JLogger().warn("Rejected unsafe import world path '{}'.", worldName, exception);
            LangUtils.sendMessage(sender, "commands.dungeon.import.world-not-found",
                    LangUtils.placeholder("world", args[1]));
            return true;
        }

        if (!importFolder.isDirectory()) {
            LangUtils.sendMessage(sender, "commands.dungeon.import.world-not-found",
                    LangUtils.placeholder("world", args[1]));
            return true;
        }

        if (Bukkit.getWorld(worldName) != null) {
            LangUtils.sendMessage(sender, "commands.dungeon.import.world-loaded",
                    LangUtils.placeholder("world", worldName));
            return true;
        }

        File saveFolder;
        try {
            saveFolder = this.resolveDungeonSaveFolder(worldName);
        } catch (IOException exception) {
            this.plugin.getSLF4JLogger().error("Failed to resolve safe dungeon folder for import '{}'.", worldName,
                    exception);
            this.runImportFailure(sender);
            return true;
        }

        if (saveFolder.exists()) {
            LangUtils.sendMessage(sender, "commands.dungeon.import.already-exists",
                    LangUtils.placeholder("world", worldName));
            return true;
        }

        WorldCreator importer = new WorldCreator(worldName);
        if (args.length == 3) {
            try {
                Environment dimension = Environment.valueOf(args[2].toUpperCase(Locale.ROOT));
                importer.environment(dimension);
            } catch (IllegalArgumentException exception) {
                LangUtils.sendMessage(sender, "commands.dungeon.import.invalid-dimension",
                        LangUtils.placeholder("dimension", args[2].toUpperCase(Locale.ROOT)));
                return true;
            }
        }

        importer.type(WorldType.FLAT);
        World importWorld = importer.createWorld();
        if (importWorld == null) {
            this.runImportFailure(sender);
            return true;
        }

        Location importSpawn = importWorld.getSpawnLocation();
        String dimension = importWorld.getEnvironment().toString();
        if (!Bukkit.unloadWorld(importWorld, false)) {
            this.plugin.getSLF4JLogger().error(
                    "Failed to unload imported source world '{}' before copying it into a dungeon folder.", worldName);
            this.runImportFailure(sender);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            if (saveFolder.exists()) {
                this.plugin.getSLF4JLogger().error(
                        "Refusing to import world '{}' because the target dungeon folder '{}' already exists.",
                        worldName, saveFolder.getAbsolutePath());
                this.runImportFailure(sender);
                return;
            }

            if (!saveFolder.mkdirs()) {
                this.plugin.getSLF4JLogger().error("Failed to create dungeon import folder '{}'.",
                        saveFolder.getAbsolutePath());
                this.runImportFailure(sender);
                return;
            }

            try {
                FileUtils.copyDirectory(importFolder, saveFolder);
            } catch (IOException exception) {
                this.cleanupDirectory(saveFolder, "failed import target");
                this.plugin.getSLF4JLogger().error("Failed to import world '{}' into dungeon folder '{}'.", worldName,
                        saveFolder.getAbsolutePath(), exception);
                this.runImportFailure(sender);
                return;
            }

            Bukkit.getScheduler().runTask(this.plugin, () -> {
                DungeonDefinition importDungeon = this.dungeonManager.loadDungeon(saveFolder);
                if (importDungeon == null) {
                    this.cleanupDirectoryAsync(saveFolder, "failed imported dungeon");
                    LangUtils.sendMessage(sender, "commands.dungeon.import.failed");
                    return;
                }

                importDungeon.setSaveConfig("dungeon.environment", dimension);
                importDungeon.setLobbySpawn(importSpawn);
                LangUtils.sendMessage(sender, "commands.dungeon.import.success",
                        LangUtils.placeholder("world", worldName));
            });
        });

        return true;
    }

    /**
     * Returns whether safe import world name.
     */
    private boolean isSafeImportWorldName(String worldName) {
        return !worldName.isBlank() && worldName.matches("[A-Za-z0-9_-]+");
    }

    /**
     * Resolves dungeon save folder.
     */
    private File resolveDungeonSaveFolder(String worldName) throws IOException {
        return this.resolveSafeChildDirectory(new File(this.plugin.getDataFolder(), "dungeons"), worldName);
    }

    /**
     * Runs world name conflicts with existing server world.
     */
    private boolean worldNameConflictsWithExistingServerWorld(String worldName) {
        if (Bukkit.getWorld(worldName) != null) {
            return true;
        }

        try {
            return this.resolveSafeChildDirectory(Bukkit.getWorldContainer(), worldName).exists();
        } catch (IOException exception) {
            this.plugin.getSLF4JLogger().warn("Failed to resolve world folder collision check for '{}'.", worldName,
                    exception);
            return true;
        }
    }

    /**
     * Creates temporary world name.
     */
    private String createTemporaryWorldName(String baseWorldName) {
        String compactBase = baseWorldName.toLowerCase(Locale.ROOT);
        if (compactBase.length() > 20) {
            compactBase = compactBase.substring(0, 20);
        }

        while (true) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String candidate = "dungeons_tmp_" + compactBase + "_" + suffix;
            File worldFolder = new File(Bukkit.getWorldContainer(), candidate);
            if (Bukkit.getWorld(candidate) == null && !worldFolder.exists()) {
                return candidate;
            }
        }
    }

    /**
     * Runs cleanup directory async.
     */
    private void cleanupDirectoryAsync(File directory, String description) {
        Runnable task = () -> this.cleanupDirectory(directory, description);
        if (this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, task);
        } else {
            task.run();
        }
    }

    /**
     * Runs cleanup directory.
     */
    private void cleanupDirectory(File directory, String description) {
        if (directory == null || !directory.exists()) {
            return;
        }

        try {
            if (directory.isDirectory()) {
                FileUtils.deleteDirectory(directory);
            } else if (!directory.delete()) {
                this.plugin.getSLF4JLogger().warn("Failed to delete {} '{}'.", description,
                        directory.getAbsolutePath());
            }
        } catch (IOException exception) {
            this.plugin.getSLF4JLogger().warn("Failed to clean up {} '{}'.", description, directory.getAbsolutePath(),
                    exception);
        }
    }

    /**
     * Resolves safe child directory.
     */
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

    /**
     * Runs run import failure.
     */
    private void runImportFailure(CommandSender sender) {
        Bukkit.getScheduler().runTask(this.plugin,
                () -> LangUtils.sendMessage(sender, "commands.dungeon.import.failed"));
    }

    /**
     * Runs handle save command.
     */
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
                .whenComplete((success, throwable) -> Bukkit.getScheduler().runTask(this.plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    if (throwable != null || !Boolean.TRUE.equals(success)) {
                        LangUtils.sendMessage(player, "commands.edit.save.failed",
                                LangUtils.placeholder("dungeon", instance.getDungeon().getWorldName()));
                        return;
                    }

                    LangUtils.sendMessage(player, "commands.edit.save.success",
                            LangUtils.placeholder("dungeon", instance.getDungeon().getWorldName()));
                }));
        return true;
    }

    /**
     * Runs handle function tool command.
     */
    private boolean handleFunctionToolCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.functioneditor")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.tools.function.not-in-dungeon");
            return true;
        }

        playerSession.restoreEditorTools();
        int functionSlot = PluginConfigView.getFunctionToolSlot(this.plugin.getConfig());
        player.getInventory().setHeldItemSlot(functionSlot);
        LangUtils.sendMessage(player, "commands.edit.tools.function.restored",
                LangUtils.placeholder("slot", String.valueOf(functionSlot + 1)));
        return true;
    }

    /**
     * Runs handle room tool command.
     */
    private boolean handleRoomToolCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.functioneditor")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (!playerSession.isEditMode()) {
            LangUtils.sendMessage(player, "commands.edit.tools.function.not-in-dungeon");
            return true;
        }

        if (playerSession.getInstance().as(BranchingEditableInstance.class) == null) {
            LangUtils.sendMessage(player, "commands.edit.tools.room.not-in-branching");
            return true;
        }

        playerSession.restoreEditorTools();
        int roomSlot = PluginConfigView.getRoomToolSlot(this.plugin.getConfig());
        player.getInventory().setHeldItemSlot(roomSlot);
        LangUtils.sendMessage(player, "commands.edit.tools.room.restored",
                LangUtils.placeholder("slot", String.valueOf(roomSlot + 1)));
        return true;
    }

    /**
     * Runs handle dungeon item command.
     */
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

    /**
     * Runs handle unban item command.
     */
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

    /**
     * Runs handle ban item command.
     */
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

    /**
     * Runs handle set exit command.
     */
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
            LangUtils.sendMessage(player, "commands.dungeon.exit.set.dungeon-not-found",
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
        LangUtils.sendMessage(player, "commands.dungeon.exit.set.success",
                LangUtils.placeholder("x", String.valueOf(exitX)), LangUtils.placeholder("y", String.valueOf(exitY)),
                LangUtils.placeholder("z", String.valueOf(exitZ)),
                LangUtils.placeholder("yaw", String.valueOf(exitYAW)));
        return true;
    }

    /**
     * Runs handle set dungeon enabled command.
     */
    private boolean handleSetDungeonEnabledCommand(CommandSender sender, String[] args, boolean enabled) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(sender, enabled ? "commands.dungeon.enable.usage" : "commands.dungeon.disable.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(sender, enabled ? "commands.dungeon.enable.dungeon-not-found"
                    : "commands.dungeon.disable.dungeon-not-found", LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (dungeon.isEnabled() == enabled) {
            LangUtils.sendMessage(sender, enabled ? "commands.dungeon.enable.already-enabled"
                    : "commands.dungeon.disable.already-disabled",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return true;
        }

        dungeon.setSaveConfig("dungeon.enabled", enabled);
        if (!enabled) {
            this.clearQueuedStartsForDisabledDungeon(dungeon);
        }

        LangUtils.sendMessage(sender, enabled ? "commands.dungeon.enable.success" : "commands.dungeon.disable.success",
                LangUtils.placeholder("dungeon", dungeon.getWorldName()));
        return true;
    }

    /**
     * Runs clear queued starts for disabled dungeon.
     */
    private void clearQueuedStartsForDisabledDungeon(DungeonDefinition dungeon) {
        for (DungeonQueueEntry queue : this.queueManager.snapshotQueues()) {
            if (queue.getDungeon() != dungeon) {
                continue;
            }

            for (UUID playerId : new ArrayList<>(queue.getPlayers())) {
                DungeonPlayerSession playerSession = this.playerManager.get(playerId);
                Player player = playerSession == null ? null : playerSession.getPlayer();
                if (player != null && player.isOnline()) {
                    LangUtils.sendMessage(player, "commands.play.disabled",
                            LangUtils.placeholder("dungeon", dungeon.getWorldName()));
                }
            }

            this.dungeonQueueCoordinator.discardQueue(queue);
        }
    }

    /**
     * Runs handle set spawn command.
     */
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
        LangUtils.sendMessage(player, "commands.edit.start.set.success",
                LangUtils.placeholder("x", String.valueOf(spawnX)), LangUtils.placeholder("y", String.valueOf(spawnY)),
                LangUtils.placeholder("z", String.valueOf(spawnZ)),
                LangUtils.placeholder("yaw", String.valueOf(spawnYAW)));
        return true;
    }

    /**
     * Runs handle set lobby command.
     */
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
        LangUtils.sendMessage(player, "commands.edit.lobby.set.success",
                LangUtils.placeholder("x", String.valueOf(lobbyX)), LangUtils.placeholder("y", String.valueOf(lobbyY)),
                LangUtils.placeholder("z", String.valueOf(lobbyZ)),
                LangUtils.placeholder("yaw", String.valueOf(lobbyYAW)));
        return true;
    }

    /**
     * Runs handle remove allkeys command.
     */
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
            LangUtils.sendMessage(player, "commands.dungeon.keys.clear.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.clear.no-permission");
            return true;
        }

        dungeon.removeAllAccessKeys();
        LangUtils.sendMessage(player, "commands.dungeon.keys.clear.success");
        return true;
    }

    /**
     * Runs handle remove key command.
     */
    private boolean handleRemoveKeyCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!CommandUtils.hasDungeonEditAccess(player, dungeon.getWorldName())) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-permission");
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem.getType() == Material.AIR) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-held-item");
            return true;
        }

        boolean keyFound = dungeon.removeAccessKey(heldItem);
        if (keyFound) {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.success");
        } else {
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-key-found");
            LangUtils.sendMessage(player, "commands.dungeon.keys.remove.no-key-found-detail");
        }

        return true;
    }

    /**
     * Runs handle add key command.
     */
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
            LangUtils.sendMessage(player, "commands.dungeon.keys.add.dungeon-not-found",
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

        dungeon.addAccessKey(heldItem);
        LangUtils.sendMessage(player, "commands.dungeon.keys.add.success");
        return true;
    }

    /**
     * Runs handle delete command.
     */
    private boolean handleDeleteCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(sender, "commands.dungeon.delete.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(sender, "commands.dungeon.delete.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (dungeon.getEditSession() != null) {
            LangUtils.sendMessage(sender, "commands.dungeon.delete.edit-in-progress");
            return true;
        }

        if (!dungeon.isMarkedForDelete()) {
            dungeon.setMarkedForDelete(true);
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> dungeon.setMarkedForDelete(false), 200L);
            LangUtils.sendMessage(sender, "commands.dungeon.delete.delete-warning");
            LangUtils.sendMessage(sender, "commands.dungeon.delete.delete-confirm");
            return true;
        }

        for (DungeonInstance instance : new ArrayList<>(this.activeInstanceManager.getActiveInstances())) {
            if (instance.getDungeon() == dungeon) {
                for (DungeonPlayerSession playerSession : new ArrayList<>(instance.getPlayers())) {
                    instance.removePlayer(playerSession);
                    LangUtils.sendMessage(playerSession.getPlayer(), "commands.dungeon.delete.notification");
                }
            }
        }

        this.dungeonManager.remove(dungeon);
        try {
            FileUtils.deleteDirectory(dungeon.getFolder());
        } catch (IOException exception) {
            this.plugin.getSLF4JLogger().error("Failed to delete dungeon folder '{}'.",
                    dungeon.getFolder().getAbsolutePath(), exception);
        }

        LangUtils.sendMessage(sender, "commands.dungeon.delete.success", LangUtils.placeholder("dungeon", args[1]));
        return true;
    }

    /**
     * Runs handle edit command.
     */
    private boolean handleEditCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.edit.open.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession.getInstance() != null) {
            LangUtils.sendMessage(player, "commands.edit.open.already-in-dungeon");
            return true;
        }

        if (this.dungeonManager.get(args[1]) == null) {
            LangUtils.sendMessage(player, "commands.edit.open.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!CommandUtils.hasDungeonEditAccess(player, args[1])) {
            LangUtils.sendMessage(player, "commands.edit.open.no-permission");
            return true;
        }

        LangUtils.sendMessage(player, "commands.edit.open.loading");
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin,
                () -> this.dungeonManager.editDungeon(args[1], playerId));
        return true;
    }

    /**
     * Runs handle create command.
     */
    private boolean handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
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
            this.plugin.getSLF4JLogger().error("Failed to resolve safe dungeon folder for creation '{}'.", worldName,
                    exception);
            LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
            return true;
        }

        if (this.dungeonManager.get(worldName) != null || saveFolder.exists()) {
            LangUtils.sendMessage(player, "commands.dungeon.create.already-exists");
            return true;
        }

        if (this.worldNameConflictsWithExistingServerWorld(worldName)) {
            LangUtils.sendMessage(player, "commands.dungeon.create.world-conflict",
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
        World world = loader.createWorld();
        if (world == null) {
            LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
            return true;
        }

        Location spawnpoint = world.getSpawnLocation();
        File worldFolder = world.getWorldFolder();
        if (!Bukkit.unloadWorld(world, true)) {
            this.plugin.getSLF4JLogger().error("Failed to unload temporary world '{}' while creating dungeon '{}'.",
                    tempWorldName, worldName);
            LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            if (saveFolder.exists()) {
                this.plugin.getSLF4JLogger().error(
                        "Refusing to create dungeon '{}' because the target folder '{}' already exists.", worldName,
                        saveFolder.getAbsolutePath());
                this.cleanupDirectory(worldFolder, "temporary world folder");
                Bukkit.getScheduler().runTask(this.plugin,
                        () -> LangUtils.sendMessage(player, "commands.dungeon.create.already-exists"));
                return;
            }

            if (!saveFolder.mkdirs()) {
                this.plugin.getSLF4JLogger().error("Failed to create dungeon folder '{}'.",
                        saveFolder.getAbsolutePath());
                this.cleanupDirectory(worldFolder, "temporary world folder");
                Bukkit.getScheduler().runTask(this.plugin,
                        () -> LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create"));
                return;
            }

            try {
                FileUtils.copyDirectory(worldFolder, saveFolder);
            } catch (IOException exception) {
                this.cleanupDirectory(saveFolder, "failed creation target");
                this.cleanupDirectory(worldFolder, "temporary world folder");
                this.plugin.getSLF4JLogger().error("Failed to create dungeon '{}' from world folder '{}'.", worldName,
                        worldFolder.getAbsolutePath(), exception);
                Bukkit.getScheduler().runTask(this.plugin,
                        () -> LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create"));
                return;
            }

            this.cleanupDirectory(worldFolder, "temporary world folder");

            Bukkit.getScheduler().runTask(this.plugin, () -> {
                DungeonDefinition newDungeon = this.dungeonManager.loadDungeon(saveFolder, dungeonType, generatorType);
                if (newDungeon == null) {
                    this.cleanupDirectoryAsync(saveFolder, "failed created dungeon");
                    LangUtils.sendMessage(player, "commands.dungeon.create.failed-to-create");
                    this.plugin.getSLF4JLogger().warn("Dungeon creation failed for '{}': unknown dungeon type '{}'.",
                            worldName, dungeonType);
                    return;
                }

                newDungeon.setLobbySpawn(spawnpoint);
                if (!dungeonType.isEmpty()) {
                    newDungeon.getConfig().set("dungeon.type", dungeonType);
                }

                newDungeon.saveConfig();
                LangUtils.sendMessage(player, "commands.dungeon.create.success",
                        LangUtils.placeholder("dungeon", worldName));
            });
        });

        return true;
    }

    /**
     * Runs handle reload command.
     */
    private boolean handleReloadCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 1 && args.length != 2) {
            LangUtils.sendMessage(sender, "commands.dungeon.reload.usage");
            return true;
        }

        if (!(sender instanceof Player player)) {
            if (args.length == 1) {
                LangUtils.sendMessage(sender, "commands.dungeon.reload.config-reloading");
                this.lifecycleCoordinator.reloadConfigs();
                LangUtils.sendMessage(sender, "commands.dungeon.reload.config-reloaded");
                return true;
            }

            if (args[1].equalsIgnoreCase("all")) {
                LangUtils.sendMessage(sender, "commands.dungeon.reload.all-dungeons-reloading");
                this.lifecycleCoordinator.reloadAllDungeons();
                LangUtils.sendMessage(sender, "commands.dungeon.reload.all-dungeons-reloaded");
                return true;
            }

            DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
            if (dungeon == null) {
                LangUtils.sendMessage(sender, "commands.dungeon.reload.dungeon-not-found",
                        LangUtils.placeholder("dungeon", args[1]));
                return true;
            }

            LangUtils.sendMessage(sender, "commands.dungeon.reload.dungeon-reloading",
                    LangUtils.placeholder("dungeon", args[1]));
            this.lifecycleCoordinator.reloadDungeon(dungeon);
            LangUtils.sendMessage(sender, "commands.dungeon.reload.dungeon-reloaded",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (args.length == 1) {
            LangUtils.sendMessage(sender, "commands.dungeon.reload.config-reloading");
            this.lifecycleCoordinator.reloadConfigs();
            LangUtils.sendMessage(sender, "commands.dungeon.reload.config-reloaded");
            return true;
        }

        if (args[1].equalsIgnoreCase("all")) {
            if (playerSession.isReloadQueued() || this.activeInstanceManager.getActiveInstances().isEmpty()) {
                LangUtils.sendMessage(sender, "commands.dungeon.reload.all-dungeons-reloading");
                this.lifecycleCoordinator.reloadAllDungeons();
                playerSession.unqueueReload();
                LangUtils.sendMessage(sender, "commands.dungeon.reload.all-dungeons-reloaded");
                return true;
            }
        } else {
            DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
            if (dungeon == null) {
                LangUtils.sendMessage(sender, "commands.dungeon.reload.dungeon-not-found",
                        LangUtils.placeholder("dungeon", args[1]));
                return true;
            }

            if (playerSession.isReloadQueued() || dungeon.getInstances().isEmpty()) {
                LangUtils.sendMessage(sender, "commands.dungeon.reload.dungeon-reloading",
                        LangUtils.placeholder("dungeon", args[1]));
                this.lifecycleCoordinator.reloadDungeon(dungeon);
                playerSession.unqueueReload();
                LangUtils.sendMessage(sender, "commands.dungeon.reload.dungeon-reloaded",
                        LangUtils.placeholder("dungeon", args[1]));
                return true;
            }
        }

        playerSession.queueReload();
        LangUtils.sendMessage(sender, "commands.dungeon.reload.reload-dungeon-warning",
                LangUtils.placeholder("dungeon", args[1]));
        LangUtils.sendMessage(sender, "commands.dungeon.reload.reload-dungeon-confirm",
                LangUtils.placeholder("dungeon", args[1]));
        return true;
    }

    /**
     * Runs handle status command.
     */
    private boolean handleStatusCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length == 1) {
            player.sendMessage(LangUtils.getMessage("commands.dungeon.status.global-header", false,
                    LangUtils.placeholder("version", this.plugin.getPluginMeta().getVersion().split("-")[0])));
            LangUtils.sendMessage(player, "commands.dungeon.status.total-instances", LangUtils.placeholder("count",
                    String.valueOf(this.activeInstanceManager.getActiveInstances().size())));

            List<DungeonPlayerSession> players = new ArrayList<>();
            for (DungeonInstance instance : this.activeInstanceManager.getActiveInstances()) {
                players.addAll(instance.getPlayers());
            }

            LangUtils.sendMessage(player, "commands.dungeon.status.total-players",
                    LangUtils.placeholder("count", String.valueOf(players.size())));

            for (DungeonDefinition dungeonInfo : this.dungeonManager.getLoadedDungeons()) {
                player.sendMessage(LangUtils.getMessage("commands.dungeon.status.dungeon-section-header", false,
                        LangUtils.placeholder("dungeon", dungeonInfo.getWorldName())));
                LangUtils.sendMessage(player, "commands.dungeon.status.dungeon-instances",
                        LangUtils.placeholder("count", String.valueOf(dungeonInfo.getInstances().size())));

                players = new ArrayList<>();
                for (DungeonInstance instance : dungeonInfo.getInstances()) {
                    players.addAll(instance.getPlayers());
                }

                LangUtils.sendMessage(player, "commands.dungeon.status.dungeon-players",
                        LangUtils.placeholder("count", String.valueOf(players.size())));
            }

            return true;
        }

        if (args.length == 2) {
            DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
            if (dungeon == null) {
                LangUtils.sendMessage(player, "commands.dungeon.status.dungeon-not-found",
                        LangUtils.placeholder("dungeon", args[1]));
                return true;
            }

            player.sendMessage(LangUtils.getMessage("commands.dungeon.status.dungeon-header", false,
                    LangUtils.placeholder("dungeon", dungeon.getWorldName())));
            if (dungeon.getInstances().isEmpty()) {
                LangUtils.sendMessage(player, "commands.dungeon.status.none-loaded");
            }

            for (DungeonInstance instance : dungeon.getInstances()) {
                player.sendMessage(LangUtils.getMessage("commands.dungeon.status.instance-line", false,
                        LangUtils.placeholder("instance", instance.getInstanceWorld().getName())));
                if (instance.isEditInstance()) {
                    player.sendMessage(LangUtils.getMessage("commands.dungeon.status.edit-session-label", false));
                }

                for (DungeonPlayerSession dPlayer : instance.getPlayers()) {
                    player.sendMessage(LangUtils.getMessage("commands.dungeon.status.player-line", false,
                            LangUtils.placeholder("player", dPlayer.getPlayer().getName())));
                }
            }

            return true;
        }

        LangUtils.sendMessage(player, "commands.dungeon.status.usage");
        return true;
    }

    /**
     * Runs handle kick command.
     */
    private boolean handleKickCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(sender, "commands.instance.kick-player.usage");
            return true;
        }

        Player kickPlayer = Bukkit.getPlayer(args[1]);
        if (kickPlayer == null) {
            LangUtils.sendMessage(sender, "commands.play.player-not-found", LangUtils.placeholder("player", args[1]));
            return true;
        }

        DungeonPlayerSession kickAPlayer = this.playerManager.get(kickPlayer);
        if (kickAPlayer.getInstance() == null) {
            LangUtils.sendMessage(sender, "commands.instance.kick-player.not-in-dungeon",
                    LangUtils.placeholder("player", kickPlayer.getName()));
            return true;
        }

        LangUtils.sendMessage(kickPlayer, "commands.instance.kick-player.kick-alert");
        kickAPlayer.getInstance().removePlayer(kickAPlayer);
        LangUtils.sendMessage(sender, "commands.instance.kick-player.success",
                LangUtils.placeholder("player", kickPlayer.getName()));
        return true;
    }

    /**
     * Runs handle leave command.
     */
    private boolean handleLeaveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
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

            DungeonInstance instance = this.activeInstanceManager.getDungeonInstance(player.getWorld().getName());
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
        LangUtils.sendMessage(player, "commands.leave.left-dungeon", LangUtils.placeholder("dungeon", dungeonName));
        return true;
    }

    /**
     * Runs handle join command.
     */
    private boolean handleJoinCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(player, "commands.instance.join-player.usage");
            return true;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        Player targetPlayer = Bukkit.getPlayer(args[1]);
        if (targetPlayer == null) {
            LangUtils.sendMessage(player, "commands.play.player-not-found", LangUtils.placeholder("player", args[1]));
            return true;
        }

        DungeonPlayerSession targetAPlayer = this.playerManager.get(targetPlayer);
        if (targetAPlayer.getInstance() == null) {
            LangUtils.sendMessage(player, "commands.instance.join-player.not-in-dungeon");
            return true;
        }

        targetAPlayer.getInstance().addPlayer(playerSession);
        if (playerSession.getInstance() == targetAPlayer.getInstance()) {
            LangUtils.sendMessage(player, "commands.instance.join-player.success",
                    LangUtils.placeholder("player", targetPlayer.getName()),
                    LangUtils.placeholder("dungeon", targetAPlayer.getInstance().getDungeon().getDisplayName()));
        }
        return true;
    }

    /**
     * Runs handle lives command.
     */
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
                LangUtils.sendMessage(player, "commands.lives.lives-remaining",
                        LangUtils.placeholder("lives", String.valueOf(livesRemaining)));
            }
        }

        return true;
    }

    /**
     * Runs handle stuck command.
     */
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

    /**
     * Runs handle play command.
     */
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
                LangUtils.sendMessage(sender, "commands.play.player-not-found",
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
            LangUtils.sendMessage(sender, "commands.play.dungeon-not-found", LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        if (!dungeon.isEnabled()) {
            LangUtils.sendMessage(sender, "commands.play.disabled",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            if (!sender.equals(targetPlayer)) {
                LangUtils.sendMessage(targetPlayer, "commands.play.disabled",
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

    /**
     * Runs handle team command.
     */
    private boolean handleTeamCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.play")) {
            return false;
        }

        if (args.length < 2) {
            LangUtils.sendMessageList(player, "commands.team.help.lines");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> this.teamManager.handleCreateTeam(player);
            case "invite" -> this.handleTeamInviteCommand(player, args);
            case "kick" -> this.handleTeamKickCommand(player, args);
            case "revoke" -> this.handleTeamRevokeCommand(player, args);
            case "accept" -> this.teamManager.handleAcceptInvite(player);
            case "deny" -> this.teamManager.handleDenyInvite(player);
            case "leave" -> this.teamManager.handleLeaveTeam(player);
            case "delete" -> this.teamManager.handleDeleteTeam(player);
            case "info" -> this.teamManager.handleInfo(player);
            default -> {
                LangUtils.sendMessage(player, "commands.team.unknown-subcommand");
                yield true;
            }
        };
    }

    /**
     * Runs handle team invite command.
     */
    private boolean handleTeamInviteCommand(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.team.invite.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            LangUtils.sendMessage(player, "commands.play.player-not-found", LangUtils.placeholder("player", args[2]));
            return true;
        }

        return this.teamManager.handleInvite(player, target);
    }

    /**
     * Runs handle team kick command.
     */
    private boolean handleTeamKickCommand(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.team.kick.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            LangUtils.sendMessage(player, "commands.play.player-not-found", LangUtils.placeholder("player", args[2]));
            return true;
        }

        return this.teamManager.handleKickMember(player, target);
    }

    /**
     * Runs handle team revoke command.
     */
    private boolean handleTeamRevokeCommand(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.team.revoke.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            LangUtils.sendMessage(player, "commands.play.player-not-found", LangUtils.placeholder("player", args[2]));
            return true;
        }

        return this.teamManager.handleRevokeInvite(player, target);
    }

    /**
     * Runs handle loot command.
     */
    private boolean handleLootCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.loottables")) {
            return true;
        }

        if (args.length < 2) {
            LangUtils.sendMessage(player, "commands.loot.usage");
            return true;
        }

        return switch (args[1].toLowerCase(Locale.ROOT)) {
            case "create" -> this.handleLootCreate(player, args);
            case "remove" -> this.handleLootRemove(player, args);
            case "edit" -> this.handleLootEdit(player, args);
            default -> false;
        };
    }

    /**
     * Runs handle loot create.
     */
    private boolean handleLootCreate(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.loot.create.usage");
            return true;
        }

        LootTable table = this.lootTableManager.get(args[2]);
        if (table != null) {
            LangUtils.sendMessage(player, "commands.loot.create.already-exists",
                    LangUtils.placeholder("loot_table", args[2]));
            return true;
        }

        this.lootTableManager.put(new LootTable(args[2]));
        LangUtils.sendMessage(player, "commands.loot.create.success", LangUtils.placeholder("loot_table", args[2]));
        this.guiService.openGui(player, "loottable_" + args[2]);
        return true;
    }

    /**
     * Runs handle loot remove.
     */
    private boolean handleLootRemove(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.loot.remove.usage");
            return true;
        }

        this.lootTableManager.remove(args[2]);
        LangUtils.sendMessage(player, "commands.loot.remove.success", LangUtils.placeholder("loot_table", args[2]));
        return true;
    }

    /**
     * Runs handle loot edit.
     */
    private boolean handleLootEdit(Player player, String[] args) {
        if (args.length != 3) {
            LangUtils.sendMessage(player, "commands.loot.edit.usage");
            return true;
        }

        LootTable table = this.lootTableManager.get(args[2]);
        if (table == null) {
            LangUtils.sendMessage(player, "commands.loot.edit.not-found", LangUtils.placeholder("loot_table", args[2]));
            return true;
        }

        if (table.getEditor() != null) {
            LangUtils.sendMessage(player, "commands.loot.edit.already-editing");
            return true;
        }

        table.setEditor(player);
        this.guiService.openGui(player, "loottable_" + args[2]);
        return true;
    }

    /**
     * Runs handle help command.
     */
    private boolean handleHelpCommand(CommandSender sender, String[] args) {
        if (args.length == 2) {
            Optional<Integer> helpPage = InputUtils.readIntegerInput(sender, args[1]);
            CommandUtils.displayHelpMenu(sender, helpPage.orElse(1));
        } else {
            CommandUtils.displayHelpMenu(sender, 1);
        }

        return true;
    }

    /**
     * Runs handle list command.
     */
    private boolean handleListCommand(CommandSender sender, String[] args) {
        if (!this.canUseDiscoveryCommands(sender)) {
            LangUtils.sendMessage(sender, "general.errors.no-permission");
            return true;
        }

        FilterPageQuery query = this.parseFilterPageQuery(sender, args, 1, "commands.list.usage");
        if (query == null) {
            return true;
        }

        List<DungeonDefinition> dungeons = this.getFilteredDungeons(query.filter(), false);
        List<String> lines = new ArrayList<>();
        for (DungeonDefinition dungeon : dungeons) {
            lines.add(LangUtils.getMessage("commands.list.line", false,
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()),
                    LangUtils.placeholder("display", dungeon.getDisplayName()),
                    LangUtils.placeholder("team_size", String.valueOf(dungeon.getMaxTeamSize())),
                    LangUtils.placeholder("difficulties", this.formatDungeonDifficulties(dungeon))));
        }

        this.sendPaginatedLines(sender, LangUtils.getMessage("commands.list.header", false), lines,
                "commands.list.no-match", "commands.list.footer", query.page());
        return true;
    }

    /**
     * Runs handle dungeon list command.
     */
    private boolean handleDungeonListCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        FilterPageQuery query = this.parseFilterPageQuery(sender, args, 1, "commands.dungeon.list.usage");
        if (query == null) {
            return true;
        }

        List<DungeonDefinition> dungeons = this.getFilteredDungeons(query.filter(), true);
        List<String> lines = new ArrayList<>();
        for (DungeonDefinition dungeon : dungeons) {
            lines.add(LangUtils.getMessage("commands.dungeon.list.line", false,
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()),
                    LangUtils.placeholder("display", dungeon.getDisplayName()),
                    LangUtils.placeholder("type", this.getDungeonTypeName(dungeon)),
                    LangUtils.placeholder("instances", String.valueOf(dungeon.getInstances().size())),
                    LangUtils.placeholder("team_size", String.valueOf(dungeon.getMaxTeamSize())),
                    LangUtils.placeholder("difficulties", this.formatDungeonDifficulties(dungeon))));
        }

        this.sendPaginatedLines(sender, LangUtils.getMessage("commands.dungeon.list.header", false), lines,
                "commands.dungeon.list.no-match", "commands.dungeon.list.footer", query.page());
        return true;
    }

    /**
     * Runs handle dungeon info command.
     */
    private boolean handleDungeonInfoCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(sender, "commands.dungeon.info.usage");
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[1]);
        if (dungeon == null) {
            LangUtils.sendMessage(sender, "commands.dungeon.status.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[1]));
            return true;
        }

        sender.sendMessage(LangUtils.getMessage("commands.dungeon.info.header", false,
                LangUtils.placeholder("dungeon", dungeon.getWorldName())));
        LangUtils.sendMessage(sender, "commands.dungeon.info.display",
                LangUtils.placeholder("display", dungeon.getDisplayName()));
        LangUtils.sendMessage(sender, "commands.dungeon.info.type",
                LangUtils.placeholder("type", this.getDungeonTypeName(dungeon)));
        LangUtils.sendMessage(sender, "commands.dungeon.info.team-size",
                LangUtils.placeholder("team_size", String.valueOf(dungeon.getMaxTeamSize())));
        LangUtils.sendMessage(sender, "commands.dungeon.info.instances",
                LangUtils.placeholder("count", String.valueOf(dungeon.getInstances().size())));
        LangUtils.sendMessage(sender, "commands.dungeon.info.locations",
                LangUtils.placeholder("lobby", this.formatConfiguredState(dungeon.getLobbySpawn())),
                LangUtils.placeholder("start", this.formatConfiguredState(dungeon.getStartSpawn())),
                LangUtils.placeholder("exit", this.formatConfiguredState(dungeon.getExitLoc())));
        LangUtils.sendMessage(sender, "commands.dungeon.info.difficulties",
                LangUtils.placeholder("difficulties", this.formatDungeonDifficulties(dungeon)));
        LangUtils.sendMessage(sender, "commands.dungeon.info.keys",
                LangUtils.placeholder("count", String.valueOf(dungeon.getValidKeys().size())));
        LangUtils.sendMessage(sender, "commands.dungeon.info.enabled",
                LangUtils.placeholder("state", this.formatEnabledState(dungeon.isEnabled())));
        LangUtils.sendMessage(sender, "commands.dungeon.info.access-cooldown",
                LangUtils.placeholder("state", this.formatEnabledState(dungeon.isAccessCooldownEnabled())));
        LangUtils.sendMessage(sender, "commands.dungeon.info.edit-session",
                LangUtils.placeholder("state", this.formatYesNoState(dungeon.getEditSession() != null)));
        return true;
    }

    /**
     * Runs handle player status command.
     */
    private boolean handlePlayerStatusCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 2) {
            LangUtils.sendMessage(sender, "commands.player.status.usage");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            LangUtils.sendMessage(sender, "commands.play.player-not-found", LangUtils.placeholder("player", args[0]));
            return true;
        }

        DungeonPlayerSession targetSession = this.playerManager.get(targetPlayer);
        DungeonQueueEntry queue = targetSession == null ? null : this.queueManager.getQueue(targetSession);
        DungeonTeam team = this.teamManager.getTeam(targetPlayer.getUniqueId());

        sender.sendMessage(LangUtils.getMessage("commands.player.status.header", false,
                LangUtils.placeholder("player", PlayerUtils.playerDisplayName(targetPlayer))));

        String instanceName = targetSession != null && targetSession.getInstance() != null
                && targetSession.getInstance().getInstanceWorld() != null
                        ? targetSession.getInstance().getInstanceWorld().getName()
                        : LangUtils.getMessage("commands.player.status.labels.none", false);
        LangUtils.sendMessage(sender, "commands.player.status.instance", LangUtils.placeholder("value", instanceName));
        LangUtils.sendMessage(sender, "commands.player.status.queue.label",
                LangUtils.placeholder("value", this.formatQueueStatus(targetPlayer, queue)));
        LangUtils.sendMessage(sender, "commands.player.status.awaiting", LangUtils.placeholder("value",
                this.formatYesNoState(targetSession != null && targetSession.isAwaitingDungeon())));
        LangUtils.sendMessage(sender, "commands.player.status.team.label",
                LangUtils.placeholder("value", this.formatTeamStatus(targetPlayer, team)));
        LangUtils.sendMessage(sender, "commands.player.status.reserved-key",
                LangUtils.placeholder("value", this.formatReservedKeyStatus(targetSession)));

        List<String> cooldownLines = new ArrayList<>();
        List<DungeonDefinition> loadedDungeons = this.getFilteredDungeons("", true);
        for (DungeonDefinition dungeon : loadedDungeons) {
            if (dungeon.hasAccessCooldown(targetPlayer)) {
                cooldownLines.add(LangUtils.getMessage("commands.player.status.cooldown-line", false,
                        LangUtils.placeholder("dungeon", dungeon.getWorldName()),
                        LangUtils.placeholder("time", TimeUtils.formatDate(dungeon.getAccessCooldown(targetPlayer)))));
            }
        }

        if (cooldownLines.isEmpty()) {
            LangUtils.sendMessage(sender, "commands.player.status.cooldowns-none");
        } else {
            LangUtils.sendMessage(sender, "commands.player.status.cooldowns-header");
            for (String line : cooldownLines) {
                sender.sendMessage(line);
            }
        }

        return true;
    }

    /**
     * Runs handle clear cooldown command.
     */
    private boolean handleClearCooldownCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 4) {
            LangUtils.sendMessage(sender, "commands.player.cooldown.clear.usage");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            LangUtils.sendMessage(sender, "commands.play.player-not-found", LangUtils.placeholder("player", args[0]));
            return true;
        }

        DungeonDefinition dungeon = this.dungeonManager.get(args[3]);
        if (dungeon == null) {
            LangUtils.sendMessage(sender, "commands.player.cooldown.set.dungeon-not-found",
                    LangUtils.placeholder("dungeon", args[3]));
            return true;
        }

        if (!dungeon.clearAccessCooldown(targetPlayer)) {
            LangUtils.sendMessage(sender, "commands.player.cooldown.clear.none",
                    LangUtils.placeholder("player", PlayerUtils.playerDisplayName(targetPlayer)),
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
            return true;
        }

        dungeon.saveCooldowns(targetPlayer);
        LangUtils.sendMessage(sender, "commands.player.cooldown.clear.success",
                LangUtils.placeholder("player", PlayerUtils.playerDisplayName(targetPlayer)),
                LangUtils.placeholder("dungeon", dungeon.getWorldName()));
        if (!sender.equals(targetPlayer)) {
            LangUtils.sendMessage(targetPlayer, "commands.player.cooldown.clear.target",
                    LangUtils.placeholder("dungeon", dungeon.getWorldName()));
        }

        return true;
    }

    /**
     * Runs handle clear queue command.
     */
    private boolean handleClearQueueCommand(CommandSender sender, String[] args) {
        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        if (args.length != 3) {
            LangUtils.sendMessage(sender, "commands.player.queue.clear.usage");
            return true;
        }

        Player targetPlayer = Bukkit.getPlayer(args[0]);
        if (targetPlayer == null) {
            LangUtils.sendMessage(sender, "commands.play.player-not-found", LangUtils.placeholder("player", args[0]));
            return true;
        }

        DungeonPlayerSession targetSession = this.playerManager.get(targetPlayer);
        DungeonQueueEntry queue = targetSession == null ? null : this.queueManager.getQueue(targetSession);
        DungeonTeam team = this.teamManager.getTeam(targetPlayer.getUniqueId());
        boolean changed = false;
        boolean notifiedTarget = false;

        if (queue != null) {
            changed = true;
            if (queue.isTeamQueue() && team != null) {
                if (team.isLeader(targetPlayer.getUniqueId())) {
                    this.queueManager.unqueue(queue);
                    this.teamManager.clearQueued(team);
                } else {
                    notifiedTarget = this.teamManager.adminRemoveMember(targetPlayer.getUniqueId(),
                            "commands.player.queue.cleared.target", "commands.player.queue.cleared.team",
                            targetPlayer.getName());
                    if (!notifiedTarget) {
                        this.queueManager.removePlayer(targetPlayer.getUniqueId());
                    }
                }
            } else if (queue.isLeader(targetPlayer.getUniqueId())) {
                this.queueManager.unqueue(queue);
            } else {
                this.queueManager.removePlayer(targetPlayer.getUniqueId());
            }
        }

        if (team != null && team.isQueued() && !team.isStarted() && queue == null) {
            if (team.isLeader(targetPlayer.getUniqueId())) {
                this.teamManager.clearQueued(team);
                changed = true;
            } else {
                notifiedTarget = this.teamManager.adminRemoveMember(targetPlayer.getUniqueId(),
                        "commands.player.queue.cleared.target", "commands.player.queue.cleared.team",
                        targetPlayer.getName());
                changed = changed || notifiedTarget;
            }
        }

        if (targetSession != null && targetSession.isAwaitingDungeon()) {
            targetSession.setAwaitingDungeon(false);
            changed = true;
        }

        if (targetSession != null && targetSession.hasReservedAccessKey()) {
            targetSession.refundReservedAccessKey();
            changed = true;
        }

        if (!changed) {
            LangUtils.sendMessage(sender, "commands.player.queue.clear.none",
                    LangUtils.placeholder("player", PlayerUtils.playerDisplayName(targetPlayer)));
            return true;
        }

        LangUtils.sendMessage(sender, "commands.player.queue.clear.success",
                LangUtils.placeholder("player", PlayerUtils.playerDisplayName(targetPlayer)));
        if (!notifiedTarget && !sender.equals(targetPlayer)) {
            LangUtils.sendMessage(targetPlayer, "commands.player.queue.clear.target");
        }

        return true;
    }

    /**
     * Runs handle loot list.
     */
    private boolean handleLootList(Player player, String[] args) {
        FilterPageQuery query = this.parseFilterPageQuery(player, args, 1, "commands.loot.list.usage");
        if (query == null) {
            return true;
        }

        List<LootTable> tables = new ArrayList<>(this.lootTableManager.getTables());
        tables.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getNamespace(), right.getNamespace()));

        List<String> lines = new ArrayList<>();
        String filter = query.filter().toLowerCase(Locale.ROOT);
        for (LootTable table : tables) {
            if (!filter.isBlank() && !table.getNamespace().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }

            String editor = table.getEditor() == null
                    ? LangUtils.getMessage("commands.loot.list.no-editor", false)
                    : table.getEditor().getName();
            lines.add(LangUtils.getMessage("commands.loot.list.line", false,
                    LangUtils.placeholder("loot_table", table.getNamespace()),
                    LangUtils.placeholder("min", String.valueOf(table.getMinItems())),
                    LangUtils.placeholder("max", String.valueOf(table.getMaxItems())),
                    LangUtils.placeholder("duplicates", this.formatYesNoState(table.isAllowDuplicates())),
                    LangUtils.placeholder("editor", editor)));
        }

        this.sendPaginatedLines(player, LangUtils.getMessage("commands.loot.list.header", false), lines,
                "commands.loot.list.no-match", "commands.loot.list.footer", query.page());
        return true;
    }

    /**
     * Runs handle edit close command.
     */
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
        LangUtils.sendMessage(player, "commands.edit.close.success", LangUtils.placeholder("dungeon", dungeonName));
        return true;
    }

    /**
     * Returns the filtered dungeons.
     */
    private List<DungeonDefinition> getFilteredDungeons(String filter, boolean includeType) {
        List<DungeonDefinition> dungeons = new ArrayList<>();
        for (DungeonDefinition dungeon : this.dungeonManager.getLoadedDungeons()) {
            if (this.matchesDungeonFilter(dungeon, filter, includeType)) {
                dungeons.add(dungeon);
            }
        }

        dungeons.sort(
                (left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getWorldName(), right.getWorldName()));
        return dungeons;
    }

    /**
     * Runs matches dungeon filter.
     */
    private boolean matchesDungeonFilter(DungeonDefinition dungeon, String filter, boolean includeType) {
        if (filter == null || filter.isBlank()) {
            return true;
        }

        String lowerFilter = filter.toLowerCase(Locale.ROOT);
        if (dungeon.getWorldName().toLowerCase(Locale.ROOT).contains(lowerFilter)) {
            return true;
        }

        String displayName = dungeon.getDisplayName();
        if (displayName != null && displayName.toLowerCase(Locale.ROOT).contains(lowerFilter)) {
            return true;
        }

        return includeType && this.getDungeonTypeName(dungeon).toLowerCase(Locale.ROOT).contains(lowerFilter);
    }

    /**
     * Returns the dungeon type name.
     */
    private String getDungeonTypeName(DungeonDefinition dungeon) {
        return dungeon.getConfig().getString("dungeon.type", dungeon.getClass().getSimpleName());
    }

    /**
     * Runs format dungeon difficulties.
     */
    private String formatDungeonDifficulties(DungeonDefinition dungeon) {
        if (!dungeon.isUseDifficultyLevels() || dungeon.getDifficultyLevels().isEmpty()) {
            return LangUtils.getMessage("commands.list.default-difficulty", false);
        }

        List<String> difficultyNames = new ArrayList<>(dungeon.getDifficultyLevels().keySet());
        difficultyNames.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", difficultyNames);
    }

    /**
     * Runs format configured state.
     */
    private String formatConfiguredState(Location location) {
        return LangUtils.getMessage(
                location == null ? "commands.dungeon.info.labels.not-set" : "commands.dungeon.info.labels.set", false);
    }

    /**
     * Runs format enabled state.
     */
    private String formatEnabledState(boolean enabled) {
        return LangUtils.getMessage(
                enabled ? "commands.dungeon.info.labels.enabled" : "commands.dungeon.info.labels.disabled", false);
    }

    /**
     * Runs format yes no state.
     */
    private String formatYesNoState(boolean value) {
        return LangUtils.getMessage(value ? "commands.player.status.labels.yes" : "commands.player.status.labels.no",
                false);
    }

    /**
     * Runs format queue status.
     */
    private String formatQueueStatus(Player targetPlayer, DungeonQueueEntry queue) {
        if (queue == null) {
            return LangUtils.getMessage("commands.player.status.labels.none", false);
        }

        String modeKey;
        if (!queue.isTeamQueue()) {
            modeKey = "commands.player.status.queue.modes.solo";
        } else if (queue.isLeader(targetPlayer.getUniqueId())) {
            modeKey = "commands.player.status.queue.modes.team-leader";
        } else {
            modeKey = "commands.player.status.queue.modes.team-member";
        }

        return LangUtils.getMessage("commands.player.status.queue.entry", false,
                LangUtils.placeholder("dungeon", queue.getDungeon().getWorldName()),
                LangUtils.placeholder("difficulty", this.formatDifficultyValue(queue.getDifficulty())),
                LangUtils.placeholder("mode", LangUtils.getMessage(modeKey, false)));
    }

    /**
     * Runs format team status.
     */
    private String formatTeamStatus(Player targetPlayer, DungeonTeam team) {
        if (team == null) {
            return LangUtils.getMessage("commands.player.status.labels.none", false);
        }

        String stateKey = team.isStarted()
                ? "commands.player.status.team.states.running"
                : team.isQueued()
                        ? "commands.player.status.team.states.queued"
                        : "commands.player.status.team.states.idle";
        String state = LangUtils.getMessage(stateKey, false);
        if (team.isLeader(targetPlayer.getUniqueId())) {
            return LangUtils.getMessage("commands.player.status.team.as-leader", false,
                    LangUtils.placeholder("count", String.valueOf(team.size())), LangUtils.placeholder("state", state));
        }

        Player leader = Bukkit.getPlayer(team.getLeaderId());
        String leaderName = leader == null ? team.getLeaderId().toString() : leader.getName();
        return LangUtils.getMessage("commands.player.status.team.as-member", false,
                LangUtils.placeholder("leader", leaderName),
                LangUtils.placeholder("count", String.valueOf(team.size())), LangUtils.placeholder("state", state));
    }

    /**
     * Runs format reserved key status.
     */
    private String formatReservedKeyStatus(DungeonPlayerSession targetSession) {
        if (targetSession == null || !targetSession.hasReservedAccessKey()) {
            return LangUtils.getMessage("commands.player.status.labels.none", false);
        }

        String reservedDungeon = targetSession.getReservedAccessKeyDungeon();
        return reservedDungeon == null || reservedDungeon.isBlank()
                ? LangUtils.getMessage("commands.player.status.labels.none", false)
                : reservedDungeon;
    }

    /**
     * Runs format difficulty value.
     */
    private String formatDifficultyValue(String difficulty) {
        return difficulty == null || difficulty.isBlank()
                ? LangUtils.getMessage("commands.player.status.labels.default-difficulty", false)
                : difficulty;
    }

    /**
     * Runs parse filter page query.
     */
    private FilterPageQuery parseFilterPageQuery(CommandSender sender, String[] args, int startIndex, String usageKey) {
        int remainingArgs = args.length - startIndex;
        if (remainingArgs < 0 || remainingArgs > 2) {
            LangUtils.sendMessage(sender, usageKey);
            return null;
        }

        String filter = "";
        int page = 1;

        if (remainingArgs >= 1) {
            String firstArg = args[startIndex];
            if (remainingArgs == 1 && this.isInteger(firstArg)) {
                Optional<Integer> parsed = InputUtils.readIntegerInput(sender, firstArg);
                if (parsed.isEmpty()) {
                    return null;
                }

                page = Math.max(1, parsed.get());
            } else {
                filter = firstArg;
            }
        }

        if (remainingArgs == 2) {
            Optional<Integer> parsed = InputUtils.readIntegerInput(sender, args[startIndex + 1]);
            if (parsed.isEmpty()) {
                return null;
            }

            page = Math.max(1, parsed.get());
        }

        return new FilterPageQuery(filter, page);
    }

    /**
     * Returns whether integer.
     */
    private boolean isInteger(String value) {
        return value != null && value.matches("-?\\d+");
    }

    /**
     * Runs send paginated lines.
     */
    private void sendPaginatedLines(CommandSender sender, String headerLine, List<String> lines, String noMatchKey,
            String footerKey, int requestedPage) {
        sender.sendMessage(headerLine);
        if (lines.isEmpty()) {
            LangUtils.sendMessage(sender, noMatchKey);
            return;
        }

        int pageSize = PluginConfigView.getCommandListPageSize(this.plugin.getConfig());
        int totalPages = Math.max(1, (int) Math.ceil(lines.size() / (double) pageSize));
        int page = Math.min(Math.max(requestedPage, 1), totalPages);
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, lines.size());
        for (int i = startIndex; i < endIndex; i++) {
            sender.sendMessage(lines.get(i));
        }

        sender.sendMessage(LangUtils.getMessage(footerKey, false, LangUtils.placeholder("page", String.valueOf(page)),
                LangUtils.placeholder("total_pages", String.valueOf(totalPages))));
    }

    /**
     * Runs filter page query.
     */
    private record FilterPageQuery(String filter, int page) {
    }

    /**
     * Runs handle test door command.
     */
    private boolean handleTestDoorCommand(CommandSender sender, String[] args) {
        if (args.length != 2) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonInstance instance = playerSession.getInstance();
        if (instance != null) {
            BranchingInstance branchingInstance = instance.as(BranchingInstance.class);
            if (branchingInstance != null) {
                InstanceRoom room = branchingInstance.getRoom(player.getLocation());
                if (room != null) {
                    room.toggleDoor(args[1]);
                }
            }
        }

        return true;
    }

    /**
     * Runs handle test all doors command.
     */
    private boolean handleTestAllDoorsCommand(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return false;
        }

        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        DungeonInstance instance = playerSession.getInstance();
        if (instance != null) {
            BranchingInstance branchingInstance = instance.as(BranchingInstance.class);
            if (branchingInstance != null) {
                InstanceRoom room = branchingInstance.getRoom(player.getLocation());
                if (room != null) {
                    room.toggleValidDoors(true);
                }
            }
        }

        return true;
    }

    /**
     * Runs handle map command.
     */
    private boolean handleMapCommand(CommandSender sender, String[] args) {
        if (args.length != 1) {
            if (sender instanceof Player player) {
                LangUtils.sendMessage(player, "commands.instance.map.usage");
                return true;
            }

            return false;
        }

        if (!(sender instanceof Player player)) {
            return false;
        }

        if (!CommandUtils.hasPermission(sender, "dungeons.admin")) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        if (playerSession == null) {
            LangUtils.sendMessage(player, "commands.instance.map.not-in-dungeon");
            return true;
        }

        DungeonInstance abs = playerSession.getInstance();
        if (abs == null) {
            LangUtils.sendMessage(player, "commands.instance.map.not-in-dungeon");
            return true;
        }

        PlayableInstance instance = abs.as(PlayableInstance.class);
        if (instance == null) {
            LangUtils.sendMessage(player, "commands.instance.map.not-in-dungeon");
            return true;
        }

        instance.giveDungeonMap(player);
        LangUtils.sendMessage(player, "commands.instance.map.success");
        return true;
    }

    /**
     * Small abstraction used by the nested second-level command branches inside the root executor.
     */
    private interface RootCommandGroup {
        /**
         * Handles a branch-specific command invocation.
         */
        boolean handleCommand(CommandSender sender, String[] args);

        /**
         * Produces branch-specific tab completions.
         */
        List<String> handleTabComplete(CommandSender sender, String[] args);
    }

    /**
     * Handles team-related root subcommands.
     */
    private final class TeamCommandGroup implements RootCommandGroup {
        /**
         * Runs handle command.
         */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            return DungeonCommand.this.handleTeamCommand(sender, args);
        }

        /**
         * Runs handle tab complete.
         */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[1], "create", "invite", "kick", "revoke",
                        "accept", "deny", "leave", "delete", "info");
                return options;
            }

            if (args.length == 3 && (args[1].equalsIgnoreCase("invite") || args[1].equalsIgnoreCase("kick")
                    || args[1].equalsIgnoreCase("revoke"))) {
                DungeonCommand.this.addMatchingPlayers(options, args[2]);
            }

            return options;
        }
    }

    /**
     * Handles loot-table management subcommands.
     */
    private final class LootCommandGroup implements RootCommandGroup {
        /**
         * Runs handle command.
         */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            if (!(sender instanceof Player player)) {
                return true;
            }

            if (!CommandUtils.hasPermission(sender, "dungeons.loottables")) {
                return true;
            }

            if (args.length < 2) {
                LangUtils.sendMessage(player, "commands.loot.usage");
                return true;
            }

            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "create" -> DungeonCommand.this.handleLootCreate(player, args);
                case "remove" -> DungeonCommand.this.handleLootRemove(player, args);
                case "edit" -> DungeonCommand.this.handleLootEdit(player, args);
                case "list" -> DungeonCommand.this.handleLootList(player, DungeonCommand.this.shiftArgs(args));
                default -> {
                    LangUtils.sendMessage(player, "commands.loot.usage");
                    yield true;
                }
            };
        }

        /**
         * Runs handle tab complete.
         */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[1], "create", "edit", "remove", "list");
                return options;
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("list")) {
                DungeonCommand.this.addMatchingLootTables(options, args[2]);
            } else if (args.length == 3 && !args[1].equalsIgnoreCase("create")) {
                DungeonCommand.this.addMatchingLootTables(options, args[2]);
            } else if (args.length == 4 && args[1].equalsIgnoreCase("list")) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[3], "1", "2", "3", "4", "5");
            }

            return options;
        }
    }

    /**
     * Handles dungeon definition administration subcommands.
     */
    private final class DungeonDefinitionCommandGroup implements RootCommandGroup {
        /**
         * Runs handle command.
         */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            if (args.length < 2) {
                DungeonCommand.this.sendUsageList(sender, "commands.dungeon.help.lines");
                return true;
            }

            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "create" -> DungeonCommand.this.handleCreateCommand(sender, DungeonCommand.this.shiftArgs(args));
                case "import" -> DungeonCommand.this.handleImportCommand(sender, DungeonCommand.this.shiftArgs(args));
                case "delete" -> DungeonCommand.this.handleDeleteCommand(sender, DungeonCommand.this.shiftArgs(args));
                case "reload" -> DungeonCommand.this.handleReloadCommand(sender, DungeonCommand.this.shiftArgs(args));
                case "list" ->
                    DungeonCommand.this.handleDungeonListCommand(sender, DungeonCommand.this.shiftArgs(args));
                case "info" ->
                    DungeonCommand.this.handleDungeonInfoCommand(sender, DungeonCommand.this.shiftArgs(args));
                case "status" -> DungeonCommand.this.handleStatusCommand(sender, DungeonCommand.this.shiftArgs(args));
                case "enable" ->
                    DungeonCommand.this.handleSetDungeonEnabledCommand(sender, DungeonCommand.this.shiftArgs(args), true);
                case "disable" ->
                    DungeonCommand.this.handleSetDungeonEnabledCommand(sender, DungeonCommand.this.shiftArgs(args),
                            false);
                case "keys" -> this.handleKeysCommand(sender, args);
                case "exit" -> this.handleExitCommand(sender, args);
                default -> {
                    DungeonCommand.this.sendUsageList(sender, "commands.dungeon.help.lines");
                    yield true;
                }
            };
        }

        /**
         * Runs handle tab complete.
         */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[1], "create", "import", "delete", "reload",
                        "list", "info", "status", "enable", "disable", "keys", "exit");
                return options;
            }

            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "delete", "info", "status", "enable", "disable" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingDungeonNames(options, args[2]);
                    }
                }
                case "list" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingDungeonNames(options, args[2]);
                    } else if (args.length == 4) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[3], "1", "2", "3", "4", "5");
                    }
                }
                case "reload" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "all");
                        DungeonCommand.this.addMatchingDungeonNames(options, args[2]);
                    }
                }
                case "import" -> {
                    if (args.length == 4) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[3], "NORMAL", "NETHER", "THE_END");
                    }
                }
                case "create" -> {
                    if (args.length == 4) {
                        DungeonCommand.this.addMatchingDungeonTypeNames(options, args[3]);
                    }
                }
                case "keys" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "add", "remove", "clear");
                    } else if (args.length == 4) {
                        DungeonCommand.this.addMatchingDungeonNames(options, args[3]);
                    }
                }
                case "exit" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "set");
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

        /**
         * Runs handle keys command.
         */
        private boolean handleKeysCommand(CommandSender sender, String[] args) {
            if (args.length != 4) {
                DungeonCommand.this.sendUsageList(sender, "commands.dungeon.help.lines");
                return true;
            }

            String dungeonName = args[3];
            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "add" -> DungeonCommand.this.handleAddKeyCommand(sender, new String[]{"addkey", dungeonName});
                case "remove" ->
                    DungeonCommand.this.handleRemoveKeyCommand(sender, new String[]{"removekey", dungeonName});
                case "clear" ->
                    DungeonCommand.this.handleRemoveAllkeysCommand(sender, new String[]{"clearkeys", dungeonName});
                default -> {
                    DungeonCommand.this.sendUsageList(sender, "commands.dungeon.help.lines");
                    yield true;
                }
            };
        }

        /**
         * Runs handle exit command.
         */
        private boolean handleExitCommand(CommandSender sender, String[] args) {
            if (args.length == 4 && args[2].equalsIgnoreCase("set")) {
                return DungeonCommand.this.handleSetExitCommand(sender, new String[]{"setexit", args[3]});
            }

            DungeonCommand.this.sendUsageList(sender, "commands.dungeon.help.lines");
            return true;
        }
    }

    /**
     * Handles edit-mode and builder workflow subcommands.
     */
    private final class EditCommandGroup implements RootCommandGroup {
        /**
         * Runs handle command.
         */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            if (args.length < 2) {
                DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            if (args.length == 2 && !this.isNamedSubcommand(args[1])) {
                return DungeonCommand.this.handleEditCommand(sender, new String[]{"edit", args[1]});
            }

            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "open" -> this.handleOpenCommand(sender, args);
                case "close" -> DungeonCommand.this.handleEditCloseCommand(sender, args);
                case "save" -> this.handleSaveCommand(sender, args);
                case "lobby" -> this.handleLobbyCommand(sender, args);
                case "start" -> this.handleStartCommand(sender, args);
                case "items" -> this.handleItemsCommand(sender, args);
                case "tools" -> this.handleToolsCommand(sender, args);
                case "signs" -> this.handleSignsCommand(sender, args);
                default -> {
                    DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
                    yield true;
                }
            };
        }

        /**
         * Runs handle tab complete.
         */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[1], "open", "close", "save", "lobby",
                        "start", "items", "tools", "signs");
                DungeonCommand.this.addMatchingEditableDungeonNames(sender, options, args[1]);
                return options;
            }

            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "open" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingEditableDungeonNames(sender, options, args[2]);
                    }
                }
                case "lobby", "start" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "set");
                    }
                }
                case "items" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "ban", "unban", "mark-dungeon");
                    }
                }
                case "tools" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "function", "room");
                    }
                }
                case "signs" -> {
                    if (args.length == 3) {
                        DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "clean");
                    }
                }
                default -> {
                    return List.of();
                }
            }

            return options;
        }

        /**
         * Returns whether named subcommand.
         */
        private boolean isNamedSubcommand(String value) {
            return value.equalsIgnoreCase("open") || value.equalsIgnoreCase("close") || value.equalsIgnoreCase("save")
                    || value.equalsIgnoreCase("lobby") || value.equalsIgnoreCase("start")
                    || value.equalsIgnoreCase("items") || value.equalsIgnoreCase("tools")
                    || value.equalsIgnoreCase("signs");
        }

        /**
         * Runs handle open command.
         */
        private boolean handleOpenCommand(CommandSender sender, String[] args) {
            if (args.length != 3) {
                DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            return DungeonCommand.this.handleEditCommand(sender, new String[]{"edit", args[2]});
        }

        /**
         * Runs handle save command.
         */
        private boolean handleSaveCommand(CommandSender sender, String[] args) {
            if (args.length != 2) {
                DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            return DungeonCommand.this.handleSaveCommand(sender);
        }

        /**
         * Runs handle lobby command.
         */
        private boolean handleLobbyCommand(CommandSender sender, String[] args) {
            if (args.length == 3 && args[2].equalsIgnoreCase("set")) {
                return DungeonCommand.this.handleSetLobbyCommand(sender, new String[]{"setlobby"});
            }

            DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }

        /**
         * Runs handle start command.
         */
        private boolean handleStartCommand(CommandSender sender, String[] args) {
            if (args.length == 3 && args[2].equalsIgnoreCase("set")) {
                return DungeonCommand.this.handleSetSpawnCommand(sender, new String[]{"setspawn"});
            }

            DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }

        /**
         * Runs handle items command.
         */
        private boolean handleItemsCommand(CommandSender sender, String[] args) {
            if (args.length != 3) {
                DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "ban" -> DungeonCommand.this.handleBanItemCommand(sender, new String[]{"banitem"});
                case "unban" -> DungeonCommand.this.handleUnbanItemCommand(sender, new String[]{"unbanitem"});
                case "mark-dungeon" ->
                    DungeonCommand.this.handleDungeonItemCommand(sender, new String[]{"dungeonitem"});
                default -> {
                    DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
                    yield true;
                }
            };
        }

        /**
         * Runs handle tools command.
         */
        private boolean handleToolsCommand(CommandSender sender, String[] args) {
            if (args.length != 3) {
                DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
                return true;
            }

            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "function" -> DungeonCommand.this.handleFunctionToolCommand(sender);
                case "room" -> DungeonCommand.this.handleRoomToolCommand(sender);
                default -> {
                    DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
                    yield true;
                }
            };
        }

        /**
         * Runs handle signs command.
         */
        private boolean handleSignsCommand(CommandSender sender, String[] args) {
            if (args.length == 3 && args[2].equalsIgnoreCase("clean")) {
                return DungeonCommand.this.handleCleanSignsCommand(sender, new String[]{"cleansigns"});
            }

            DungeonCommand.this.sendUsageList(sender, "commands.edit.help.lines");
            return true;
        }
    }

    /**
     * Handles player-focused administrative subcommands.
     */
    private final class PlayerAdminCommandGroup implements RootCommandGroup {
        /**
         * Runs handle command.
         */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            if (args.length < 3) {
                DungeonCommand.this.sendUsageList(sender, "commands.player.help.lines");
                return true;
            }

            return switch (args[2].toLowerCase(Locale.ROOT)) {
                case "status" ->
                    DungeonCommand.this.handlePlayerStatusCommand(sender, DungeonCommand.this.shiftArgs(args));
                case "cooldown" -> this.handleCooldownCommand(sender, args);
                case "queue" -> this.handleQueueCommand(sender, args);
                default -> {
                    DungeonCommand.this.sendUsageList(sender, "commands.player.help.lines");
                    yield true;
                }
            };
        }

        /**
         * Runs handle tab complete.
         */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                DungeonCommand.this.addMatchingPlayers(options, args[1]);
                return options;
            }

            if (args.length == 3) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "status", "cooldown", "queue");
                return options;
            }

            if (args[2].equalsIgnoreCase("queue")) {
                if (args.length == 4) {
                    DungeonCommand.this.addMatchingLiteralOptions(options, args[3], "clear");
                }
                return options;
            }

            if (!args[2].equalsIgnoreCase("cooldown")) {
                return options;
            }

            if (args.length == 4) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[3], "set", "clear");
            } else if (args.length == 5 && args[3].equalsIgnoreCase("set")) {
                DungeonCommand.this.addMatchingDungeonNames(options, args[4]);
            } else if (args.length == 5 && args[3].equalsIgnoreCase("clear")) {
                DungeonCommand.this.addMatchingDungeonNames(options, args[4]);
            } else if (args.length == 6 && args[3].equalsIgnoreCase("set")) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[5], "30m", "1h", "12h", "1d", "7d");
            }

            return options;
        }

        /**
         * Runs handle cooldown command.
         */
        private boolean handleCooldownCommand(CommandSender sender, String[] args) {
            if (args.length < 4) {
                DungeonCommand.this.sendUsageList(sender, "commands.player.help.lines");
                return true;
            }

            if (!args[3].equalsIgnoreCase("set")) {
                if (args[3].equalsIgnoreCase("clear")) {
                    return DungeonCommand.this.handleClearCooldownCommand(sender, DungeonCommand.this.shiftArgs(args));
                }

                DungeonCommand.this.sendUsageList(sender, "commands.player.help.lines");
                return true;
            }

            if (args.length == 5) {
                return DungeonCommand.this.handleSetCooldownCommand(sender,
                        new String[]{"setcooldown", args[4], args[1]});
            }

            if (args.length == 6) {
                return DungeonCommand.this.handleSetCooldownCommand(sender,
                        new String[]{"setcooldown", args[4], args[1], args[5]});
            }

            DungeonCommand.this.sendUsageList(sender, "commands.player.help.lines");
            return true;
        }

        /**
         * Runs handle queue command.
         */
        private boolean handleQueueCommand(CommandSender sender, String[] args) {
            if (args.length == 4 && args[3].equalsIgnoreCase("clear")) {
                return DungeonCommand.this.handleClearQueueCommand(sender, DungeonCommand.this.shiftArgs(args));
            }

            DungeonCommand.this.sendUsageList(sender, "commands.player.help.lines");
            return true;
        }
    }

    /**
     * Handles active-instance administration subcommands.
     */
    private final class InstanceAdminCommandGroup implements RootCommandGroup {
        /**
         * Runs handle command.
         */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            if (args.length < 2) {
                DungeonCommand.this.sendUsageList(sender, "commands.instance.help.lines");
                return true;
            }

            return switch (args[1].toLowerCase(Locale.ROOT)) {
                case "join-player" -> this.handleJoinPlayerCommand(sender, args);
                case "kick-player" -> this.handleKickPlayerCommand(sender, args);
                case "map" -> this.handleMapCommand(sender, args);
                default -> {
                    DungeonCommand.this.sendUsageList(sender, "commands.instance.help.lines");
                    yield true;
                }
            };
        }

        /**
         * Runs handle tab complete.
         */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[1], "join-player", "kick-player", "map");
                return options;
            }

            if (args.length == 3
                    && (args[1].equalsIgnoreCase("join-player") || args[1].equalsIgnoreCase("kick-player"))) {
                DungeonCommand.this.addMatchingPlayers(options, args[2]);
            }

            return options;
        }

        /**
         * Runs handle join player command.
         */
        private boolean handleJoinPlayerCommand(CommandSender sender, String[] args) {
            if (args.length != 3) {
                DungeonCommand.this.sendUsageList(sender, "commands.instance.help.lines");
                return true;
            }

            return DungeonCommand.this.handleJoinCommand(sender, new String[]{"join", args[2]});
        }

        /**
         * Runs handle kick player command.
         */
        private boolean handleKickPlayerCommand(CommandSender sender, String[] args) {
            if (args.length != 3) {
                DungeonCommand.this.sendUsageList(sender, "commands.instance.help.lines");
                return true;
            }

            return DungeonCommand.this.handleKickCommand(sender, new String[]{"kick", args[2]});
        }

        /**
         * Runs handle map command.
         */
        private boolean handleMapCommand(CommandSender sender, String[] args) {
            if (args.length != 2) {
                DungeonCommand.this.sendUsageList(sender, "commands.instance.help.lines");
                return true;
            }

            return DungeonCommand.this.handleMapCommand(sender, new String[]{"getmap"});
        }
    }

    /**
     * Handles debug and diagnostic subcommands.
     */
    private final class DebugCommandGroup implements RootCommandGroup {
        /**
         * Runs handle command.
         */
        @Override
        public boolean handleCommand(CommandSender sender, String[] args) {
            if (args.length < 2 || !args[1].equalsIgnoreCase("doors")) {
                DungeonCommand.this.sendUsageList(sender, "commands.debug.help.lines");
                return true;
            }

            if (args.length == 3 && args[2].equalsIgnoreCase("test-all")) {
                return DungeonCommand.this.handleTestAllDoorsCommand(sender, new String[]{"testalldoors"});
            }

            if (args.length == 4 && args[2].equalsIgnoreCase("test")) {
                return DungeonCommand.this.handleTestDoorCommand(sender, new String[]{"testdoor", args[3]});
            }

            DungeonCommand.this.sendUsageList(sender, "commands.debug.help.lines");
            return true;
        }

        /**
         * Runs handle tab complete.
         */
        @Override
        public List<String> handleTabComplete(CommandSender sender, String[] args) {
            List<String> options = new ArrayList<>();
            if (args.length == 2) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[1], "doors");
                return options;
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("doors")) {
                DungeonCommand.this.addMatchingLiteralOptions(options, args[2], "test", "test-all");
            }

            return options;
        }
    }
}
