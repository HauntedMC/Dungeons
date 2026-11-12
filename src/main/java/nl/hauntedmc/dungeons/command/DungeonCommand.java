package nl.hauntedmc.dungeons.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.command.CommandOutput.FilterPageQuery;
import nl.hauntedmc.dungeons.command.config.DungeonConfigCommandHandler;
import nl.hauntedmc.dungeons.command.config.DungeonConfigRegistry;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.registry.FunctionRegistry;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.ActiveInstanceRegistry;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueCoordinator;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.runtime.staff.StaffEditGuard;
import nl.hauntedmc.dungeons.runtime.staff.StaffPermissions;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
    private final DungeonQueueRegistry selfQueues;
    private final DungeonRepository dungeonManager;
    private final CommandOutput output;
    private final DungeonConfigCommandHandler configCommandHandler;
    private final CommandHandler teamCommandGroup;
    private final RepoCommandHandler repoCommands;

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
            FunctionRegistry functionManager,
            LifecycleCoordinator lifecycleCoordinator,
            GuiService guiService,
            String command) {
        this.gameplayCommands =
                new GameplayCommandHandler(
                        playerManager,
                        dungeonManager,
                        activeInstanceManager,
                        dungeonQueueCoordinator,
                        queueManager,
                        teamManager,
                        guiService);
        this.plugin = plugin;
        this.repoCommands = new RepoCommandHandler(RuntimeContext.runtime());
        this.playerManager = playerManager;
        this.selfQueues = queueManager;
        this.dungeonManager = dungeonManager;
        this.configCommandHandler =
                new DungeonConfigCommandHandler(
                        dungeonManager, playerManager, activeInstanceManager);
        this.output = new CommandOutput(plugin, dungeonManager);
        this.teamCommandGroup = new TeamCommandHandler(teamManager);
        this.dungeonCommandGroup =
                new DungeonDefinitionCommandHandler(
                        dungeonManager,
                        playerManager,
                        queueManager,
                        dungeonQueueCoordinator,
                        this.output,
                        new DungeonWorldCommandHandler(plugin, dungeonManager));
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
            @NotNull Command cmd,
            @NotNull String label,
            @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(this.getPermissionNode())
                && !(selfControl(args.length == 0 ? "guide" : args[0])
                        && activeSelfContext(sender))) {
            LangUtils.sendMessage(sender, "general.errors.no-permission");
            return true;
        }

        if (!this.isConsoleFriendly() && !(sender instanceof Player)) {
            LangUtils.sendMessage(sender, "general.errors.players-only");
            return true;
        }

        try {
            if (!RuntimeContext.runtime().database().ready())
                throw new IllegalArgumentException("Dungeons database is unavailable");
            return this.handleCommand(sender, args);
        } catch (IllegalArgumentException rejected) {
            LangUtils.sendRaw(sender, rejected.getMessage());
            return true;
        } catch (RuntimeException failure) {
            this.plugin
                    .getSLF4JLogger()
                    .error("Dungeon command failed for {}", sender.getName(), failure);
            LangUtils.sendRaw(sender, "Change failed; check the server log before retrying.");
            return true;
        }
    }

    /** Produces tab completions for the root command and its nested subcommands. */
    @Override
    public List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command cmd,
            @NotNull String label,
            @NotNull String @NotNull [] args) {
        if (!sender.hasPermission(this.getPermissionNode()) && !activeSelfContext(sender)) {
            return List.of();
        }

        return this.completeCanonical(sender, args);
    }

    private boolean handleCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player
                    && this.playerManager.runtimeExperience() != null
                    && this.playerManager
                            .runtimeExperience()
                            .view(player.getUniqueId())
                            .isPresent()) {
                this.playerManager.runtimeExperience().handle(player, new String[] {"guide"});
                return true;
            }
            this.canonicalHelp(sender, new String[0]);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            this.canonicalHelp(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        for (var route : CommandRoutes.ALL) {
            var values = route.match(args);
            if (values == null) continue;
            if (values.containsKey("map")
                    && !route.syntax().startsWith("map create ")
                    && !route.syntax().startsWith("map import ")) {
                var resolved = this.dungeonManager.get(values.get("map").getFirst());
                if (resolved != null) values.put("map", List.of(resolved.getWorldName()));
            }
            if (!this.allowed(sender, route, values)) {
                LangUtils.sendMessage(sender, "general.errors.no-permission");
                return true;
            }
            if (route.playerOnly() && sender instanceof Player player)
                RuntimeContext.runtime().stateStore().requirePlayerReady(player.getUniqueId());
            String[] operation = route.arguments(values);
            return switch (route.handler()) {
                case REPO -> this.repoCommands.handleCommand(sender, operation);
                case GAMEPLAY -> this.gameplayCommands.handleCommand(sender, operation);
                case TEAM -> this.teamCommandGroup.handleCommand(sender, operation);
                case MAP -> this.dungeonCommandGroup.handleCommand(sender, operation);
                case SETTINGS -> this.configCommandHandler.handleCommand(sender, operation);
                case EDIT -> this.editCommandGroup.handleCommand(sender, operation);
                case DEBUG -> {
                    if (!(sender instanceof Player player)
                            || !StaffEditGuard.editor(player, this.playerManager.get(player)))
                        throw new IllegalArgumentException("Open an authorized editor first");
                    yield this.debugCommandGroup.handleCommand(sender, operation);
                }
                case STAFF -> {
                    if (this.staffCommandHandler == null)
                        throw new IllegalStateException("Staff services unavailable");
                    yield this.staffCommandHandler.handleCommand(sender, operation);
                }
                case DISCOVERY ->
                        operation[0].equals("info")
                                ? this.publicInfo(sender, operation[1])
                                : this.handleListCommand(sender, operation);
                case QUERY ->
                        throw new IllegalStateException("Database query services unavailable");
            };
        }
        // A bare semantic group shows its own help. Removed groups have no executable route.
        if (CommandRoutes.ALL.stream()
                .anyMatch(
                        route ->
                                route.syntax()
                                        .startsWith(args[0].toLowerCase(Locale.ROOT) + " "))) {
            this.canonicalHelp(sender, args);
        } else {
            LangUtils.sendRaw(sender, "Unknown command. Use /dungeon help.");
        }
        return true;
    }

    private static boolean selfControl(String root) {
        return java.util.Set.of("guide", "status", "objectives", "hud", "spectate", "help", "leave")
                .contains(root.toLowerCase(Locale.ROOT));
    }

    private boolean activeSelfContext(CommandSender sender) {
        if (!(sender instanceof Player player)) return false;
        var session = playerManager.get(player);
        return session != null
                && (session.getInstance()
                                instanceof nl.hauntedmc.dungeons.runtime.instance.PlayableInstance
                        || session.isObserving()
                        || session.isAwaitingDungeon()
                        || selfQueues.getQueue(session) != null);
    }

    private boolean allowed(
            CommandSender sender, CommandRoutes.Route route, Map<String, List<String>> values) {
        if (route.playerOnly() && !(sender instanceof Player)) return false;
        if (selfControl(route.syntax().split(" ", 2)[0]) && activeSelfContext(sender)) return true;
        if (!route.permission().startsWith("staff.")) {
            return sender.hasPermission("dungeons." + route.permission());
        }
        String capability = route.permission().substring(6);
        var map = values.get("map");
        if (map != null) {
            var dungeon = this.dungeonManager.get(map.getFirst());
            String scope = dungeon == null ? map.getFirst() : dungeon.getWorldName();
            if (dungeon == null
                    && route.handler() == CommandRoutes.Handler.REPO
                    && RuntimeContext.runtime().contentRepository() != null)
                scope = RuntimeContext.runtime().contentRepository().cachedScope(scope);
            if (!scope.equals("all") && !scope.equals("current")) {
                return StaffPermissions.allows(sender, capability, scope);
            }
        }
        if (StaffPermissions.allows(sender, capability, null)) return true;
        if (route.handler() == CommandRoutes.Handler.REPO
                && !route.syntax().equals("repo fetch")
                && RuntimeContext.runtime().contentRepository() != null
                && !RuntimeContext.runtime()
                        .contentRepository()
                        .cachedNames(sender, capability.substring("repo.".length()))
                        .isEmpty()) return true;
        if (route.syntax().startsWith("server")
                || route.syntax().startsWith("map archive ")
                || route.syntax().equals("instance list")
                || route.syntax().equals("queue list")) return false;
        return this.dungeonManager.getLoadedDungeons().stream()
                .anyMatch(
                        dungeon ->
                                StaffPermissions.allows(
                                        sender, capability, dungeon.getWorldName()));
    }

    private void canonicalHelp(CommandSender sender, String[] path) {
        String prefix = String.join(" ", path).toLowerCase(Locale.ROOT);
        LangUtils.sendRaw(sender, prefix.isBlank() ? "Commands" : prefix);
        var lines = new LinkedHashSet<String>();
        for (var route : CommandRoutes.ALL) {
            String[] pattern = route.syntax().split(" ");
            if (path.length > pattern.length) continue;
            var values = new LinkedHashMap<String, List<String>>();
            boolean matches = true;
            for (int index = 0; index < path.length; index++) {
                String part = pattern[index];
                if (part.startsWith("<"))
                    values.put(part.substring(1, part.length() - 1), List.of(path[index]));
                else if (part.startsWith("*")) break;
                else if (!part.toLowerCase(Locale.ROOT)
                        .startsWith(path[index].toLowerCase(Locale.ROOT))) {
                    matches = false;
                    break;
                }
            }
            if (!matches || !this.allowed(sender, route, values)) continue;
            String usage = route.usage();
            for (var value : values.entrySet())
                usage = usage.replace("<" + value.getKey() + ">", value.getValue().getFirst());
            if (prefix.isBlank()) {
                String root = usage.split(" ")[0];
                if (List.of("map", "edit", "player", "instance", "queue", "server", "team", "repo")
                        .contains(root)) usage = root + " … (use /dungeon help " + root + ")";
            }
            lines.add("/dungeon " + usage);
        }
        if (lines.isEmpty()) LangUtils.sendRaw(sender, "No accessible commands match this path.");
        else lines.forEach(line -> LangUtils.sendRaw(sender, line));
    }

    private List<String> completeCanonical(CommandSender sender, String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("help"))
            return this.completeCanonical(sender, Arrays.copyOfRange(args, 1, args.length));
        var suggestions = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        if (args.length == 0) return List.of();
        if (args.length == 1) suggestions.add("help");
        for (var route : CommandRoutes.ALL) {
            String[] pattern = route.syntax().split(" ");
            if (args.length > pattern.length) continue;
            var values = new LinkedHashMap<String, List<String>>();
            boolean matches = true;
            for (int index = 0; index < args.length - 1; index++) {
                String part = pattern[index];
                if (part.startsWith("<"))
                    values.put(part.substring(1, part.length() - 1), List.of(args[index]));
                else if (!part.equalsIgnoreCase(args[index])) {
                    matches = false;
                    break;
                }
            }
            if (!matches || !this.allowed(sender, route, values)) continue;
            String next = pattern[args.length - 1];
            if (!next.startsWith("<") && !next.startsWith("*")) suggestions.add(next);
            else if (next.equals("<map>")) {
                for (var dungeon : this.dungeonManager.getLoadedDungeons()) {
                    var candidate = new LinkedHashMap<>(values);
                    candidate.put("map", List.of(dungeon.getWorldName()));
                    if (this.allowed(sender, route, candidate))
                        suggestions.add(dungeon.getWorldName());
                }
                if (route.handler() == CommandRoutes.Handler.REPO
                        && RuntimeContext.runtime().contentRepository() != null) {
                    String capability = route.permission().substring("staff.repo.".length());
                    suggestions.addAll(
                            RuntimeContext.runtime()
                                    .contentRepository()
                                    .cachedNames(sender, capability));
                }
            } else if (next.equals("<spawnpoint>") && sender instanceof Player player) {
                var session = this.playerManager.get(player);
                if (session != null && session.isEditMode() && session.getInstance() != null) {
                    var dungeon = session.getInstance().getDungeon();
                    if (StaffPermissions.allows(sender, "edit", dungeon.getWorldName()))
                        suggestions.addAll(dungeon.getStartSpawns().keySet());
                }
            } else if (next.equals("<bump>")) {
                suggestions.addAll(List.of("major", "minor", "patch"));
            } else if (next.equals("<player>")) {
                Bukkit.getOnlinePlayers().forEach(player -> suggestions.add(player.getName()));
            } else if (next.equals("<difficulty>") && values.containsKey("map")) {
                var dungeon = this.dungeonManager.get(values.get("map").getFirst());
                if (dungeon != null) suggestions.addAll(dungeon.getDifficultyLevels().keySet());
            } else if (next.equals("<setting>") && values.containsKey("map")) {
                var dungeon = this.dungeonManager.get(values.get("map").getFirst());
                if (dungeon != null) suggestions.addAll(DungeonConfigRegistry.getKeys(dungeon, ""));
            } else if (next.equals("<environment>"))
                suggestions.addAll(List.of("normal", "nether", "the_end"));
            else if (next.equals("<type>"))
                suggestions.addAll(List.of("static", "branching", "open"));
            else if (next.equals("<state>")) suggestions.addAll(List.of("on", "off"));
            else if (next.equals("<target>")) suggestions.addAll(List.of("config", "maps", "all"));
        }
        String query = args[args.length - 1].toLowerCase(Locale.ROOT);
        return suggestions.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(query))
                .toList();
    }

    private boolean publicInfo(CommandSender sender, String name) {
        var dungeon = this.dungeonManager.get(name);
        if (dungeon == null) throw new IllegalArgumentException("Unknown dungeon: " + name);
        LangUtils.sendRaw(
                sender,
                dungeon.getDisplayNameComponent()
                        .append(Component.text(" (" + dungeon.getWorldName() + ")")));
        LangUtils.sendRaw(
                sender,
                Component.text(
                                "Type: "
                                        + this.output.getDungeonTypeName(dungeon)
                                        + "; team limit: "
                                        + dungeon.getMaxTeamSize()
                                        + "; difficulties: ")
                        .append(this.output.formatDungeonDifficulties(sender, dungeon)));
        LangUtils.sendRaw(
                sender,
                "Available: "
                        + dungeon.isEnabled()
                        + "; keys required: "
                        + !dungeon.getAccessKeys().isEmpty());
        if (sender instanceof Player player && this.playerManager.runtimeExperience() != null)
            this.playerManager.runtimeExperience().briefing(player, dungeon);
        return true;
    }

    private String getPermissionNode() {
        return "dungeons.core";
    }

    private boolean isConsoleFriendly() {
        return true;
    }

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
        List<Component> lines = new ArrayList<>();
        for (DungeonDefinition dungeon : dungeons) {
            lines.add(
                    LangUtils.component(
                            "commands.list.line",
                            sender,
                            false,
                            LangUtils.placeholder("dungeon", dungeon.getWorldName()),
                            LangUtils.placeholder("display", dungeon.getDisplayNameComponent()),
                            LangUtils.placeholder(
                                    "team_size", String.valueOf(dungeon.getMaxTeamSize())),
                            LangUtils.placeholder(
                                    "difficulties",
                                    this.output.formatDungeonDifficulties(sender, dungeon))));
        }

        this.output.sendPaginatedLines(
                sender,
                LangUtils.component("commands.list.header", sender, false),
                lines,
                "commands.list.no-match",
                "commands.list.footer",
                query.page());
        return true;
    }
}
