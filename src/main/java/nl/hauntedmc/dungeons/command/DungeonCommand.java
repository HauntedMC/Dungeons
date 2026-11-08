package nl.hauntedmc.dungeons.command;

import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.command.CommandOutput.FilterPageQuery;
import nl.hauntedmc.dungeons.command.config.DungeonConfigCommandHandler;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.gui.loot.LootTableEditor;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.util.command.CommandUtils;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    private final CommandOutput output;
    private final DungeonConfigCommandHandler configCommandHandler;
    private final CommandHandler teamCommandGroup;
    private final CommandHandler lootCommandGroup;
    private final CommandHandler dungeonCommandGroup;
    private final CommandHandler editCommandGroup;
    private final DebugCommandHandler debugCommandGroup;
    private final GameplayCommandHandler gameplayCommands;

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
        this.gameplayCommands = new GameplayCommandHandler(playerManager, dungeonManager,
                activeInstanceManager, dungeonQueueCoordinator, queueManager, teamManager, guiService);
        this.plugin = plugin;
        this.playerManager = playerManager;
        this.dungeonManager = dungeonManager;
        this.configCommandHandler =
                new DungeonConfigCommandHandler(
                        dungeonManager, playerManager, activeInstanceManager);
        this.output = new CommandOutput(plugin, dungeonManager);
        this.teamCommandGroup = new TeamCommandHandler(teamManager);
        this.lootCommandGroup = new LootCommandHandler(lootTableManager, lootEditor, this.output);
        this.dungeonCommandGroup = new DungeonDefinitionCommandHandler(
                        dungeonManager, playerManager, queueManager, dungeonQueueCoordinator,
                        this.output, new DungeonWorldCommandHandler(plugin, dungeonManager));
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
            case "rewards", "play", "stuck", "lives", "leave" ->
                    this.gameplayCommands.handleCommand(sender, args);
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
                            ? this.gameplayCommands.handleTabComplete(sender, args)
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

}
