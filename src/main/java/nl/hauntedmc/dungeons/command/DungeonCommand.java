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
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
            if (!nl.hauntedmc.dungeons.runtime.RuntimeContext.runtime().database().ready())
                throw new IllegalArgumentException("Dungeons database is unavailable");
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
            return List.of();
        }

        return this.completeCanonical(sender, args);
    }

    private boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            this.canonicalHelp(sender, new String[0]);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            this.canonicalHelp(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        for (var route : CommandRoutes.ALL) {
            var values = route.match(args);
            if (values == null) continue;
            if (values.containsKey("map") && !route.syntax().startsWith("map create ")
                    && !route.syntax().startsWith("map import ")) {
                var resolved = this.dungeonManager.get(values.get("map").getFirst());
                if (resolved != null) values.put("map", List.of(resolved.getWorldName()));
            }
            if (!this.allowed(sender, route, values)) {
                LangUtils.sendMessage(sender, "general.errors.no-permission");
                return true;
            }
            if (route.playerOnly() && sender instanceof Player player)
                nl.hauntedmc.dungeons.runtime.RuntimeContext.runtime().stateStore().requirePlayerReady(player.getUniqueId());
            String[] operation = route.arguments(values);
            return switch (route.handler()) {
                case GAMEPLAY -> this.gameplayCommands.handleCommand(sender, operation);
                case TEAM -> this.teamCommandGroup.handleCommand(sender, operation);
                case MAP -> this.dungeonCommandGroup.handleCommand(sender, operation);
                case SETTINGS -> this.configCommandHandler.handleCommand(sender, operation);
                case EDIT -> this.editCommandGroup.handleCommand(sender, operation);
                case DEBUG -> {
                    if (!(sender instanceof Player player)
                            || !nl.hauntedmc.dungeons.runtime.staff.StaffEditGuard.editor(
                                    player, this.playerManager.get(player)))
                        throw new IllegalArgumentException("Open an authorized editor first");
                    yield this.debugCommandGroup.handleCommand(sender, operation);
                }
                case LOOT -> this.lootCommandGroup.handleCommand(sender, operation);
                case STAFF -> {
                    if (this.staffCommandHandler == null) throw new IllegalStateException("Staff services unavailable");
                    yield this.staffCommandHandler.handleCommand(sender, operation);
                }
                case DISCOVERY -> operation[0].equals("info")
                        ? this.publicInfo(sender, operation[1]) : this.handleListCommand(sender, operation);
                case QUERY -> throw new IllegalStateException("Database query services unavailable");
            };
        }
        // A bare semantic group shows its own help. Removed groups have no executable route.
        if (CommandRoutes.ALL.stream().anyMatch(route -> route.syntax().startsWith(args[0].toLowerCase(Locale.ROOT) + " "))) {
            this.canonicalHelp(sender, args);
        } else {
            sender.sendMessage("[Dungeons] Unknown command. Use /dungeon help.");
        }
        return true;
    }

    private boolean allowed(CommandSender sender, CommandRoutes.Route route,
            java.util.Map<String, List<String>> values) {
        if (route.playerOnly() && !(sender instanceof Player)) return false;
        if (!route.permission().startsWith("staff.")) {
            return sender.hasPermission("dungeons." + route.permission());
        }
        String capability = route.permission().substring(6);
        var map = values.get("map");
        if (map != null) {
            var dungeon = this.dungeonManager.get(map.getFirst());
            String scope = dungeon == null ? map.getFirst() : dungeon.getWorldName();
            if (!scope.equals("all") && !scope.equals("current")) {
                return nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.allows(sender, capability, scope);
            }
        }
        if (nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.allows(sender, capability, null)) return true;
        if (route.syntax().startsWith("server") || route.syntax().startsWith("loot ")
                || route.syntax().startsWith("map archive ") || route.syntax().equals("instance list")
                || route.syntax().equals("queue list") || capability.equals("compensate")) return false;
        return this.dungeonManager.getLoadedDungeons().stream().anyMatch(dungeon ->
                nl.hauntedmc.dungeons.runtime.staff.StaffPermissions.allows(sender, capability, dungeon.getWorldName()));
    }

    private void canonicalHelp(CommandSender sender, String[] path) {
        String prefix = String.join(" ", path).toLowerCase(Locale.ROOT);
        sender.sendMessage("[Dungeons] " + (prefix.isBlank() ? "Commands" : prefix));
        var lines = new java.util.LinkedHashSet<String>();
        for (var route : CommandRoutes.ALL) {
            String[] pattern = route.syntax().split(" ");
            if (path.length > pattern.length) continue;
            var values = new java.util.LinkedHashMap<String, List<String>>();
            boolean matches = true;
            for (int index = 0; index < path.length; index++) {
                String part = pattern[index];
                if (part.startsWith("<")) values.put(part.substring(1, part.length() - 1), List.of(path[index]));
                else if (part.startsWith("*")) break;
                else if (!part.toLowerCase(Locale.ROOT).startsWith(path[index].toLowerCase(Locale.ROOT))) {
                    matches = false;
                    break;
                }
            }
            if (!matches || !this.allowed(sender, route, values)) continue;
            String usage = route.usage();
            for (var value : values.entrySet()) usage = usage.replace("<" + value.getKey() + ">", value.getValue().getFirst());
            if (prefix.isBlank()) {
                String root = usage.split(" ")[0];
                if (List.of("map", "edit", "loot", "player", "instance", "queue", "server", "team").contains(root))
                    usage = root + " … (use /dungeon help " + root + ")";
            }
            lines.add("/dungeon " + usage);
        }
        if (lines.isEmpty()) sender.sendMessage("No accessible commands match this path.");
        else lines.forEach(sender::sendMessage);
    }

    private List<String> completeCanonical(CommandSender sender, String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("help"))
            return this.completeCanonical(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
        var suggestions = new java.util.TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (args.length == 0) return List.of();
        if (args.length == 1) suggestions.add("help");
        for (var route : CommandRoutes.ALL) {
            String[] pattern = route.syntax().split(" ");
            if (args.length > pattern.length) continue;
            var values = new java.util.LinkedHashMap<String, List<String>>();
            boolean matches = true;
            for (int index = 0; index < args.length - 1; index++) {
                String part = pattern[index];
                if (part.startsWith("<")) values.put(part.substring(1, part.length() - 1), List.of(args[index]));
                else if (!part.equalsIgnoreCase(args[index])) { matches = false; break; }
            }
            if (!matches || !this.allowed(sender, route, values)) continue;
            String next = pattern[args.length - 1];
            if (!next.startsWith("<") && !next.startsWith("*")) suggestions.add(next);
            else if (next.equals("<map>")) {
                for (var dungeon : this.dungeonManager.getLoadedDungeons()) {
                    var candidate = new java.util.LinkedHashMap<>(values);
                    candidate.put("map", List.of(dungeon.getWorldName()));
                    if (this.allowed(sender, route, candidate)) suggestions.add(dungeon.getWorldName());
                }
            } else if (next.equals("<player>")) {
                org.bukkit.Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
            } else if (next.equals("<difficulty>") && values.containsKey("map")) {
                var dungeon = this.dungeonManager.get(values.get("map").getFirst());
                if (dungeon != null) suggestions.addAll(dungeon.getDifficultyLevels().keySet());
            } else if (next.equals("<setting>") && values.containsKey("map")) {
                var dungeon = this.dungeonManager.get(values.get("map").getFirst());
                if (dungeon != null) suggestions.addAll(nl.hauntedmc.dungeons.command.config.DungeonConfigRegistry.getKeys(dungeon, ""));
            } else if (next.equals("<table>")) {
                suggestions.addAll(this.lootCommandGroup.handleTabComplete(sender, new String[] {"loot", "edit", ""}));
            } else if (next.equals("<environment>")) suggestions.addAll(List.of("normal", "nether", "the_end"));
            else if (next.equals("<type>")) suggestions.addAll(List.of("static", "branching", "open"));
            else if (next.equals("<state>")) suggestions.addAll(List.of("on", "off"));
            else if (next.equals("<target>")) suggestions.addAll(List.of("config", "maps", "all"));
        }
        String query = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(query)).toList();
    }

    private boolean publicInfo(CommandSender sender, String name) {
        var dungeon = this.dungeonManager.get(name);
        if (dungeon == null) throw new IllegalArgumentException("Unknown dungeon: " + name);
        sender.sendMessage("[Dungeons] " + dungeon.getDisplayName() + " (" + dungeon.getWorldName() + ")");
        sender.sendMessage("Type: " + this.output.getDungeonTypeName(dungeon)
                + "; team limit: " + dungeon.getMaxTeamSize()
                + "; difficulties: " + this.output.formatDungeonDifficulties(dungeon));
        sender.sendMessage("Available: " + dungeon.isEnabled() + "; keys required: " + !dungeon.getAccessKeys().isEmpty());
        return true;
    }

    private String getPermissionNode() { return "dungeons.core"; }
    private boolean isConsoleFriendly() { return true; }
    private boolean canUseDiscoveryCommands(CommandSender sender) {
        return sender.hasPermission("dungeons.play") || sender.hasPermission("dungeons.play.send");
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
