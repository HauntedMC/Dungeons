package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.command.CommandOutput.FilterPageQuery;
import nl.hauntedmc.dungeons.command.config.DungeonConfigCommandHandler;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.gui.loot.LootTableEditor;
import nl.hauntedmc.dungeons.model.dungeon.AccessKeyDefinition;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.DungeonInstance;
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
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

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
    private final DungeonWorldCommandHandler worldCommands;
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
        this.worldCommands = new DungeonWorldCommandHandler(plugin, dungeonManager);
        this.dungeonCommandGroup = new DungeonDefinitionCommandGroup();
        this.editCommandGroup = new EditCommandHandler(plugin, playerManager, dungeonManager);
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
                        DungeonCommand.this.worldCommands.handleCreateCommand(
                                sender, CommandArguments.shiftArgs(args));
                case "import" ->
                        DungeonCommand.this.worldCommands.handleImportCommand(
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

}
