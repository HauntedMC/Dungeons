package nl.hauntedmc.dungeons.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import nl.hauntedmc.dungeons.bootstrap.LifecycleCoordinator;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.instance.*;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.player.*;
import nl.hauntedmc.dungeons.runtime.staff.*;

import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Staff commands and inventory actions invoke the same capability-checked operations. */
public final class StaffCommandHandler implements CommandHandler {
    private record Syntax(String command, String capability, String usage) {}

    private static final List<Syntax> SYNTAX =
            List.of(
                    new Syntax("edit", "edit", "edit <dungeon> <reason>"),
                    new Syntax("overview", "inspect", "overview"),
                    new Syntax("dungeons", "inspect", "dungeons [filter]"),
                    new Syntax("dungeon", "inspect", "dungeon <name> [validate]"),
                    new Syntax("instances", "inspect", "instances [dungeon]"),
                    new Syntax(
                            "instance",
                            "inspect",
                            "instance <id> [observe|retry|evacuate|extend <seconds>] [reason]"),
                    new Syntax(
                            "player",
                            "inspect",
                            "player <name|uuid> [rescue|remove|queue-clear|recover|revive|lives"
                                    + " <count>] <reason>"),
                    new Syntax(
                            "cooldown",
                            "cooldown",
                            "cooldown <name|uuid> <dungeon> <clear|duration> <reason>"),
                    new Syntax(
                            "loot-cooldown",
                            "cooldown",
                            "loot-cooldown <name|uuid> <dungeon> clear <reason>"),
                    new Syntax(
                            "completion",
                            "progression",
                            "completion <name|uuid> <dungeon> <true|false> <reason>"),
                    new Syntax(
                            "compensate",
                            "compensate",
                            "compensate <name|uuid> <material> <amount> <reason>"),
                    new Syntax(
                            "restrict",
                            "restrict",
                            "restrict <name|uuid> <dungeon|all> <duration|forever|clear> <reason>"),
                    new Syntax("loot", "loot", "loot [table]"),
                    new Syntax("loot-remove", "loot", "loot-remove <table> <reason>"),
                    new Syntax("keys", "keys", "keys <dungeon> [give <player> <key-id> <reason>]"),
                    new Syntax("queues", "inspect", "queues"),
                    new Syntax(
                            "maintenance",
                            "maintenance",
                            "maintenance <dungeon|all> <on|off> <reason>"),
                    new Syntax("reload", "reload", "reload <dungeon|all|config> <reason>"),
                    new Syntax("archive", "delete", "archive <dungeon> <reason>"),
                    new Syntax("backup", "backup", "backup <dungeon> <reason>"),
                    new Syntax("backups", "inspect", "backups <dungeon>"),
                    new Syntax("archives", "inspect", "archives"),
                    new Syntax("restore", "restore", "restore <dungeon> <backup-id> <reason>"),
                    new Syntax("unarchive", "restore", "unarchive <archive-id> <reason>"),
                    new Syntax("purge", "purge", "purge <archive-id> <reason>"),
                    new Syntax("operations", "inspect", "operations"),
                    new Syntax("operation", "inspect", "operation <id>"),
                    new Syntax("cancel", "maintenance", "cancel <operation-id> <reason>"),
                    new Syntax("problems", "diagnostics", "problems"),
                    new Syntax("stop-observing", "observe", "stop-observing"));
    private final DungeonsRuntime runtime;
    private final LifecycleCoordinator lifecycle;
    private final StaffOperations ops;
    private final StaffContentStorage content;

    public StaffCommandHandler(DungeonsRuntime runtime, LifecycleCoordinator lifecycle) {
        this.runtime = runtime;
        this.lifecycle = lifecycle;
        this.ops = runtime.staffOperations();
        this.content = new StaffContentStorage(runtime);
        this.ops.start();
    }

    private CompletableFuture<String> openEditor(Player player, DungeonDefinition dungeon) {
        String name = dungeon.getWorldName();
        StaffPermissions.require(player, "edit", name);
        if (!player.isOnline()) throw new IllegalStateException("Editor disconnected");
        return content.backup(dungeon)
                .thenCompose(
                        backup ->
                                nl.hauntedmc.dungeons.config.ConfigurationFile.onOwnerThread(
                                                runtime.environment().plugin(),
                                                () -> {
                                                    StaffPermissions.require(player, "edit", name);
                                                    if (!player.isOnline())
                                                        throw new IllegalStateException(
                                                                "Editor disconnected");
                                                    return dungeon.editAsync(player.getUniqueId());
                                                })
                                        .thenApply(
                                                opened -> {
                                                    if (!opened)
                                                        throw new IllegalStateException(
                                                                "Editor opening failed; backup "
                                                                        + backup);
                                                    return "Editor opened; backup " + backup;
                                                }));
    }

    @Override
    public boolean handleCommand(CommandSender actor, String[] raw) {
        try {
            executeChecked(actor, raw);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            tell(actor, failure.getMessage());
        } catch (RuntimeException failure) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            runtime.environment().logger().error("Staff operation {} failed", id, failure);
            ops.problem(id + ": " + failure.getClass().getSimpleName());
            tell(
                    actor,
                    "Operation failed ("
                            + id
                            + "); retained data is available for retry. Check the server log.");
        }
        return true;
    }

    /** Executes a staff operation, preserving validation errors for the caller. */
    public void executeChecked(CommandSender actor, String[] args) {
        execute(actor, new ArrayList<>(Arrays.asList(args)));
    }

    private void execute(CommandSender actor, List<String> input) {
        if (!input.isEmpty() && input.getFirst().equalsIgnoreCase("admin")) input.removeFirst();
        String token = null;
        int flag = input.indexOf("--confirm");
        if (flag >= 0) {
            if (flag != input.size() - 2)
                throw new IllegalArgumentException("Use --confirm <token> at the end");
            token = input.removeLast();
            input.removeLast();
        }
        if (input.isEmpty()) {
            overview(actor);
            return;
        }
        String action = input.getFirst().toLowerCase(Locale.ROOT);
        input.set(0, action);
        if (List.of("instance", "player").contains(action) && input.size() > 2)
            input.set(2, input.get(2).toLowerCase(Locale.ROOT));
        String[] a = input.toArray(String[]::new);
        if (action.equals("help")) {
            for (var route : CommandRoutes.ALL) {
                if (route.permission().startsWith("staff.")
                        && allowsAnyScope(actor, route.permission().substring(6)))
                    tell(actor, "/dungeon " + route.usage());
            }
            return;
        }
        switch (action) {
            case "edit" -> {
                need(a, 3, "edit <dungeon> <reason>");
                if (!(actor instanceof Player p))
                    throw new IllegalArgumentException("Players only");
                var definition = dungeon(a[1]);
                if (definition.getEditSession() != null)
                    throw new IllegalArgumentException("Close the existing editor first");
                var operation =
                        ops.drain(
                                actor,
                                "edit",
                                "open-editor",
                                a[1],
                                reason(a, 2),
                                () -> openEditor(p, definition));
                tell(actor, "Editor operation " + operation.shortId() + ": " + operation.detail);
            }
            case "dashboard" -> {
                StaffPermissions.require(actor, "inspect", null);
                overview(actor);
            }
            case "overview" -> overview(actor);
            case "dungeons" -> {
                StaffPermissions.require(actor, "inspect", null);
                for (var d : sortedDungeons())
                    if (a.length == 1 || d.getWorldName().toLowerCase(Locale.ROOT)
                            .contains(a[1].toLowerCase(Locale.ROOT))) tell(actor, describe(d));
            }
            case "dungeon" -> {
                need(a, 2, "dungeon <name> [validate]");
                var d = dungeon(a[1]);
                StaffPermissions.require(actor, "inspect", d.getWorldName());
                if (a.length > 2 && a[2].equalsIgnoreCase("validate"))
                    validation(d).forEach(line -> tell(actor, line));
                else {
                    tell(actor, describe(d));
                    validation(d).forEach(line -> tell(actor, line));
                }
            }
            case "instances" -> {
                String target = a.length > 1 ? a[1] : "all";
                StaffPermissions.require(actor, "inspect", target.equals("all") ? null : target);
                ops.instances(target).forEach(i -> tell(actor, describe(i)));
            }
            case "instance" -> instance(actor, a, input, token);
            case "player" -> player(actor, a, input, token);
            case "cooldown", "loot-cooldown", "completion" -> correct(actor, a, input, token);
            case "compensate" -> compensate(actor, a, input, token);
            case "restrict" -> {
                need(a, 5, "restrict <player> <dungeon|all> <duration|forever|clear> <reason>");
                UUID id = playerId(a[1]);
                if (!a[2].equals("all")) dungeon(a[2]);
                long until =
                        a[3].equals("clear")
                                ? -1
                                : a[3].equals("forever")
                                        ? 0
                                        : Math.addExact(System.currentTimeMillis(), duration(a[3]));
                ops.restrict(actor, id, a[2], until, reason(a, 4));
                tell(actor, "Entry restriction updated for " + id);
            }
            case "loot" -> {
                StaffPermissions.require(actor, "loot", null);
                if (a.length == 1) {
                    runtime.lootTableRepository()
                            .getTables()
                            .forEach(table -> tell(actor, table.getNamespace()));
                } else if (actor instanceof Player p) {
                    var table = runtime.lootTableRepository().get(a[1]);
                    if (table == null || !runtime.lootTableEditor().open(table, p))
                        throw new IllegalArgumentException(
                                "Table unavailable or already being edited");
                } else throw new IllegalArgumentException("Item editing requires a player");
            }
            case "loot-remove" -> {
                need(a, 3, "loot-remove <table> <reason>");
                StaffPermissions.require(actor, "loot", null);
                var table = runtime.lootTableRepository().get(a[1]);
                if (table == null) throw new IllegalArgumentException("Table not found");
                var references = new ArrayList<String>();
                for (var dungeon : sortedDungeons())
                    ContentReferences.dungeon(
                            dungeon,
                            value -> {
                                if (value
                                                instanceof
                                                nl.hauntedmc.dungeons.content.function.reward
                                                                .LootTableRewardsFunction
                                                        reward
                                        && reward.getLootTableName().equals(a[1]))
                                    references.add(dungeon.getWorldName());
                            });
                if (!references.isEmpty())
                    throw new IllegalArgumentException(
                            "Referenced by dungeons: " + new LinkedHashSet<>(references));
                String removalReason = reason(a, 2);
                if (!confirmed(
                        actor, input, token, "loot-remove", a[1], table.serialize().toString()))
                    return;
                if (!runtime.lootTableRepository().remove(a[1]))
                    throw new IllegalStateException("Loot save failed; table retained");
                ops.audit(actor, "loot-remove", a[1], removalReason, "removed");
                tell(actor, "Loot table removed");
            }
            case "keys" -> {
                need(a, 2, "keys <dungeon> [give <player> <key-id> <reason>]");
                var dungeon = dungeon(a[1]);
                StaffPermissions.require(actor, "keys", a[1]);
                if (a.length == 2) {
                    for (int key = 0; key < dungeon.getAccessKeys().size(); key++)
                        tell(
                                actor,
                                "Key "
                                        + (key + 1)
                                        + ": "
                                        + dungeon.getAccessKeys().get(key).getItem().getType()
                                        + " id=" + dungeon.getAccessKeys().get(key).getKeyId());
                } else {
                    need(a, 6, "keys <dungeon> give <player> <key-id> <reason>");
                    if (!a[2].equals("give")) throw new IllegalArgumentException("Use give");
                    nl.hauntedmc.dungeons.model.dungeon.AccessKeyDefinition key = dungeon.getAccessKeys().stream()
                            .filter(definition -> a[4].equals(definition.getKeyId())).findFirst().orElse(null);
                    if (key == null) {
                        try { key = dungeon.getAccessKey(Integer.parseInt(a[4])); }
                        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Unknown key ID"); }
                    }
                    if (key == null) throw new IllegalArgumentException("Unknown key ID");
                    UUID player = playerId(a[3]);
                    String issuanceReason = reason(a, 5);
                    ops.requireContentWritable(a[1]);
                    if (!confirmed(actor, input, token, "key-give", a[1], key.toString())) return;
                    ItemStack issued = dungeon.issueAccessKey(key);
                    if (issued == null)
                        throw new IllegalStateException("Key ledger could not be saved");
                    StaffPlayerRecords.compensate(runtime, player, UUID.fromString(token), issued);
                    ops.audit(
                            actor,
                            "key-give",
                            player.toString(),
                            issuanceReason,
                            "issued for " + a[1]);
                    tell(actor, "Key queued for durable delivery through /dungeon rewards");
                }
            }
            case "queues" -> {
                String scope = a.length > 1 ? dungeon(a[1]).getWorldName() : null;
                StaffPermissions.require(actor, "inspect", scope);
                runtime.queueRegistry().snapshotQueues().stream()
                        .filter(queue -> scope == null || queue.getDungeon().getWorldName().equals(scope))
                        .forEach(queue -> tell(actor, describeQueue(queue)));
            }
            case "queue-info", "queue-cancel" -> {
                need(a, action.equals("queue-cancel") ? 3 : 2, "queue <id> [reason]");
                var matches = runtime.queueRegistry().snapshotQueues().stream()
                        .filter(queue -> queue.getAdmissionId().toString().startsWith(a[1])).toList();
                if (matches.size() != 1) throw new IllegalArgumentException("Queue ID is unknown or ambiguous");
                var queue = matches.getFirst();
                StaffPermissions.require(actor, action.equals("queue-cancel") ? "queue" : "inspect", queue.getDungeon().getWorldName());
                if (action.equals("queue-info")) tell(actor, describeQueue(queue));
                else {
                    String reason = reason(a, 2);
                    if (!confirmed(actor, input, token, "queue-cancel", queue.getAdmissionId().toString(), queue.getPlayers().toString())) return;
                    runtime.dungeonQueueCoordinator().discardQueue(queue);
                    ops.audit(actor, "queue-cancel", queue.getAdmissionId().toString(), reason, "cancelled");
                    tell(actor, "Queue cancelled; uncommitted keys returned through recovery");
                }
            }
            case "maintenance" -> {
                need(a, 4, "maintenance <dungeon|all> <on|off> <reason>");
                if (!a[1].equals("all")) dungeon(a[1]);
                if (!List.of("on", "off").contains(a[2]))
                    throw new IllegalArgumentException("Use on or off");
                ops.maintenance(actor, a[1], a[2].equals("on"), reason(a, 3));
                tell(actor, "Maintenance " + a[2] + " for " + a[1] + ": " + ops.impact(a[1]));
            }
            case "reload", "reload-all", "archive", "backup", "restore" ->
                    contentOperation(actor, a, input, token);
            case "archive-info" -> {
                need(a, 2, "map archive info <id>");
                StaffPermissions.require(actor, "inspect", null);
                tell(actor, "Archive " + a[1] + "; map=" + content.archivedDungeonName(a[1])
                        + "; revision=" + content.archiveRevision(a[1]));
            }
            case "archives" -> {
                StaffPermissions.require(actor, "inspect", null);
                content.archives().forEach(line -> tell(actor, line));
            }
            case "backups" -> {
                need(a, 2, "backups <dungeon>");
                StaffPermissions.require(actor, "inspect", a[1]);
                content.backups(a[1]).forEach(line -> tell(actor, line));
            }
            case "unarchive", "purge" -> {
                need(a, 3, action + " <archive-id> <reason>");
                String capability = action.equals("purge") ? "purge" : "restore";
                StaffPermissions.require(actor, capability, null);
                String revision = content.archiveRevision(a[1]);
                if (!confirmed(actor, input, token, action, a[1], revision)) return;
                String name = content.archivedDungeonName(a[1]);
                var operation =
                        ops.drain(
                                actor,
                                capability,
                                action,
                                name,
                                reason(a, 2),
                                () -> content.archiveAction(a[1], action.equals("purge")));
                tell(actor, "Operation " + operation.shortId() + ": " + operation.detail);
            }
            case "operations" -> {
                StaffPermissions.require(actor, "inspect", null);
                ops.operations()
                        .forEach(
                                o ->
                                        tell(
                                                actor,
                                                o.shortId()
                                                        + " "
                                                        + o.action
                                                        + " "
                                                        + o.target
                                                        + " "
                                                        + o.state
                                                        + ": "
                                                        + o.detail));
            }
            case "operation" -> {
                need(a, 2, "operation <id>");
                var op = ops.operation(a[1]);
                StaffPermissions.require(actor, "inspect", op.target);
                tell(
                        actor,
                        op.shortId()
                                + " "
                                + op.state
                                + ": "
                                + op.detail
                                + " ("
                                + op.actor
                                + ", "
                                + op.reason
                                + ")");
            }
            case "cancel" -> {
                need(a, 3, "cancel <operation-id> <reason>");
                ops.cancel(actor, a[1], reason(a, 2));
                tell(actor, "Operation cancelled; maintenance remains enabled");
            }
            case "problems" -> {
                StaffPermissions.require(actor, "diagnostics", null);
                var problems = new ArrayList<>(ops.problems());
                if (!runtime.lootTableRepository().isWritable())
                    problems.add(
                            "Loot editing is blocked after a failed load; repair loottables.yml and"
                                    + " reload configuration");
                runtime.dungeonCatalog()
                        .loadFailures()
                        .forEach((name, problem) -> problems.add(name + ": " + problem));
                if (problems.isEmpty()) tell(actor, "No recorded operational problems");
                else problems.forEach(line -> tell(actor, line));
            }
            case "stop-observing" -> {
                if (!(actor instanceof Player p))
                    throw new IllegalArgumentException("Players only");
                var session = runtime.playerSessions().get(p);
                if (session == null || !session.isObserving())
                    throw new IllegalArgumentException("You are not observing");
                if (!session.stopObservation())
                    throw new IllegalStateException(
                            "Restoration incomplete; recovery record retained");
                tell(actor, "Observation ended; original player state restored");
            }
            default ->
                    throw new IllegalArgumentException(
                            "Unknown staff action. Use /dungeon help");
        }
    }

    private void contentOperation(
            CommandSender actor, String[] a, List<String> input, String token) {
        need(
                a,
                a[0].equals("restore") ? 4 : 3,
                a[0] + " <dungeon> " + (a[0].equals("restore") ? "<backup-id> " : "") + "<reason>");
        String target = a[1];
        String capability = a[0].equals("archive") ? "delete" : a[0].equals("reload-all") ? "reload" : a[0];
        boolean global = target.equals("all") || target.equals("config");
        if (global && !List.of("reload", "reload-all").contains(a[0]))
            throw new IllegalArgumentException("Select one dungeon");
        var d = global ? null : dungeon(target);
        StaffPermissions.require(actor, capability, global ? null : target);
        String reason = reason(a, a[0].equals("restore") ? 3 : 2);
        if (List.of("archive", "restore").contains(a[0]) && d.getEditSession() != null)
            throw new IllegalArgumentException(
                    "Close the editor before requesting a destructive operation");
        if (List.of("archive", "restore").contains(a[0])
                && !confirmed(
                        actor,
                        input,
                        token,
                        a[0],
                        target,
                        d.getConfig().saveToString() + ops.impact(target))) return;
        var operation =
                ops.drain(
                        actor,
                        capability,
                        a[0],
                        global ? "all" : target,
                        reason,
                        () -> {
                            if (d != null && runtime.dungeonCatalog().get(target) != d)
                                throw new IllegalArgumentException(
                                        "Dungeon was replaced while waiting; request the operation"
                                                + " again");
                            return switch (a[0]) {
                                case "reload", "reload-all" -> {
                                    if (a[0].equals("reload-all")) lifecycle.reloadConfigs();
                                    if (target.equals("config")) {
                                        lifecycle.reloadConfigs();
                                        yield CompletableFuture.completedFuture(
                                                "Configuration reloaded");
                                    }
                                    if (target.equals("all"))
                                        yield runtime.dungeonCatalog()
                                                .reloadAllAsync()
                                                .thenApply(
                                                        report -> {
                                                            if (!report.isEmpty())
                                                                throw new IllegalStateException(
                                                                        "Failed loads: " + report);
                                                            return "Dungeon catalog reloaded";
                                                        });
                                    if (runtime.dungeonCatalog().loadDungeon(d.getFolder()) == null)
                                        throw new IllegalStateException(
                                                "Replacement failed validation; last working"
                                                        + " definition retained");
                                    yield CompletableFuture.completedFuture("Dungeon reloaded");
                                }
                                case "archive" -> ops.archive(d);
                                case "backup" -> content.backup(d).thenApply(id -> "Backup " + id);
                                case "restore" -> content.restore(d, a[2]);
                                default ->
                                        throw new IllegalArgumentException(
                                                "Unknown content action");
                            };
                        });
        tell(
                actor,
                "Operation "
                        + operation.shortId()
                        + ": "
                        + operation.state
                        + " — "
                        + operation.detail);
    }

    private void instance(CommandSender actor, String[] a, List<String> input, String token) {
        need(a, 2, "instance <id> [observe|retry|evacuate|extend <seconds>] [reason]");
        var instance = ops.instance(a[1]);
        String name = instance.getDungeon().getWorldName();
        if (a.length == 2) {
            StaffPermissions.require(actor, "inspect", name);
            tell(actor, describe(instance));
            return;
        }
        switch (a[2]) {
            case "observe" -> {
                if (!(actor instanceof Player p))
                    throw new IllegalArgumentException("Players only");
                ops.observe(p, instance);
                tell(actor, "Observing. Return with /dungeon instance stop-observing");
            }
            case "retry" -> {
                StaffPermissions.require(actor, "recovery", name);
                need(a, 4, "instance <id> retry <reason>");
                if (!instance.getPlayers().isEmpty())
                    throw new IllegalArgumentException("World still has participants");
                String reason = reason(a, 3);
                instance.disposeAsync()
                        .whenComplete(
                                (ok, failure) ->
                                        Bukkit.getScheduler()
                                                .runTask(
                                                        runtime.environment().plugin(),
                                                        () -> {
                                                            ops.audit(
                                                                    actor,
                                                                    "retry-disposal",
                                                                    a[1],
                                                                    reason,
                                                                    failure == null
                                                                                    && Boolean.TRUE
                                                                                            .equals(
                                                                                                    ok)
                                                                            ? "succeeded"
                                                                            : "failed");
                                                            tell(
                                                                    actor,
                                                                    Boolean.TRUE.equals(ok)
                                                                            ? "World disposed"
                                                                            : "Disposal failed;"
                                                                                    + " world"
                                                                                    + " retained");
                                                        }));
                tell(actor, "Disposal retry requested");
            }
            case "evacuate" -> {
                StaffPermissions.require(actor, "rescue", name);
                need(a, 4, "instance <id> evacuate <reason>");
                String evacuationReason = reason(a, 3);
                String roster =
                        instance.getInstanceId()
                                + "/"
                                + instance.getDisposalState()
                                + "/"
                                + instance.getPlayers().stream()
                                        .map(
                                                session ->
                                                        session.getPlayer()
                                                                .getUniqueId()
                                                                .toString())
                                        .sorted()
                                        .toList();
                if (!confirmed(actor, input, token, "evacuate", a[1], roster)) return;
                var failures = new ArrayList<String>();
                int pendingRecovery = 0;
                for (var session : List.copyOf(instance.getPlayers())) {
                    try {
                        instance.removePlayer(session, DepartureCause.MAINTENANCE);
                        if (session.hasUnresolvedRecovery()) pendingRecovery++;
                    } catch (RuntimeException failure) {
                        failures.add(session.getPlayer().getUniqueId().toString());
                        runtime.environment()
                                .logger()
                                .error(
                                        "Evacuation failed for {}",
                                        session.getPlayer().getUniqueId(),
                                        failure);
                    }
                }
                String outcome =
                        "Remaining participants="
                                + instance.getPlayers().size()
                                + ", pending recovery="
                                + pendingRecovery
                                + ", failed departures="
                                + failures.size();
                ops.audit(actor, "evacuate", a[1], evacuationReason, outcome);
                tell(
                        actor,
                        outcome
                                + "; earned rewards retained. Inspect pending recovery before"
                                + " retrying.");
            }
            case "extend" -> {
                StaffPermissions.require(actor, "timer", name);
                need(a, 5, "instance <id> extend <seconds> <reason>");
                if (instance.asPlayInstance() == null)
                    throw new IllegalArgumentException("Not a playable run");
                String extensionReason = reason(a, 4);
                int before = instance.asPlayInstance().getTimeLeft();
                instance.asPlayInstance().extendTime(Integer.parseInt(a[3]));
                ops.audit(
                        actor,
                        "extend-time",
                        a[1],
                        extensionReason,
                        before + " -> " + instance.asPlayInstance().getTimeLeft());
                tell(actor, "Time limit extended");
            }
            default -> throw new IllegalArgumentException("Unknown instance action");
        }
    }

    private void player(CommandSender actor, String[] a, List<String> input, String token) {
        need(a, 2, "player <name|uuid> [action] [reason]");
        UUID id = playerId(a[1]);
        var session = runtime.playerSessions().get(id);
        String scope = playerScope(session);
        if (a.length == 2) {
            StaffPermissions.require(actor, "inspect", scope);
            playerLines(actor, id).forEach(line -> tell(actor, line));
            return;
        }
        String capability =
                switch (a[2]) {
                    case "remove" -> "remove";
                    case "queue-clear" -> "queue";
                    case "recover" -> "recovery";
                    case "revive", "lives" -> "lives";
                    case "rescue" -> "rescue";
                    default -> throw new IllegalArgumentException("Unknown player action");
                };
        StaffPermissions.require(actor, capability, scope);
        need(a, a[2].equals("lives") ? 5 : 4, "player <name|uuid> " + a[2] + " <reason>");
        String reason = reason(a, a[2].equals("lives") ? 4 : 3);
        if (session == null)
            throw new IllegalArgumentException(
                    "This action requires an online or retained session");
        switch (a[2]) {
            case "queue-clear" -> ops.cancelStart(session);
            case "recover" -> {
                if (!session.getPlayer().isOnline())
                    throw new IllegalArgumentException("Player must reconnect for restoration");
                if (session.getInstance() != null || session.isObserving())
                    throw new IllegalArgumentException("Use rescue or end observation first");
                if (!session.recoverPendingState())
                    throw new IllegalStateException(
                            "Recovery incomplete; original record retained");
            }
            case "rescue", "remove" -> {
                if (session.isObserving()) session.stopObservation();
                else if (session.getInstance() != null)
                    session.getInstance()
                            .removePlayer(
                                    session,
                                    a[2].equals("rescue")
                                            ? DepartureCause.RESCUE
                                            : DepartureCause.STAFF_REMOVE);
                else {
                    ops.cancelStart(session);
                    if (session.getPlayer().isOnline()) session.recoverPendingState();
                }
            }
            case "revive", "lives" -> {
                var play =
                        session.getInstance() == null
                                ? null
                                : session.getInstance().asPlayInstance();
                if (play == null
                        || !play.getPlayers().contains(session)
                        || !session.getPlayer().isOnline())
                    throw new IllegalArgumentException("Player is not in an active playable run");
                int lives = a[2].equals("revive") ? 1 : Integer.parseInt(a[3]);
                if (lives < 1 || lives > 10000)
                    throw new IllegalArgumentException("Use 1–10000 lives");
                Player player = session.getPlayer();
                if (player.isDead())
                    throw new IllegalArgumentException("Player must respawn first");
                GameMode mode;
                try {
                    mode =
                            GameMode.valueOf(
                                    play.getConfig()
                                            .getString("players.gamemode", "ADVENTURE")
                                            .toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException invalid) {
                    mode = GameMode.ADVENTURE;
                }
                if (a[2].equals("revive") && play.getLivingPlayers().contains(session))
                    throw new IllegalArgumentException(
                            "Player is already alive; use lives to adjust their count");
                if (a[2].equals("revive")
                        && !player.teleport(
                                Objects.requireNonNullElse(
                                        session.getDungeonRespawn(), play.getStartLoc())))
                    throw new IllegalStateException("Revival teleport rejected");
                player.setGameMode(mode);
                if (!play.getLivingPlayers().contains(session))
                    play.getLivingPlayers().add(session);
                play.getPlayerLives().put(id, lives);
                session.setDead(false);
            }
            default -> throw new IllegalArgumentException("Unknown support action");
        }
        if (List.of("rescue", "remove").contains(a[2]) && session.hasUnresolvedRecovery()) {
            ops.audit(actor, a[2], id.toString(), reason, "player detached; recovery retained");
            tell(
                    actor,
                    "Player detached; original state is retained for recovery on reconnect/respawn."
                            + " Inspect recovery status.");
            return;
        }
        ops.audit(actor, a[2], id.toString(), reason, "completed");
        if (session.getPlayer().isOnline() && !actor.equals(session.getPlayer()))
            tell(session.getPlayer(), "Staff action " + a[2] + ": " + reason);
        tell(actor, "Player action completed");
    }

    public String playerScope(DungeonPlayerSession session) {
        if (session == null) return null;
        if (session.getInstance() != null) return session.getInstance().getDungeon().getWorldName();
        if (session.isObserving())
            return session.getObservingInstance().getDungeon().getWorldName();
        var queue = runtime.queueRegistry().getQueue(session);
        if (queue != null) return queue.getDungeon().getWorldName();
        if (session.hasReservedAccessKey()) return session.getReservedAccessKeyDungeon();
        return runtime.preparations().dungeonName(session);
    }

    private void correct(CommandSender actor, String[] a, List<String> input, String token) {
        need(a, 5, a[0] + " <player> <dungeon> <value> <reason>");
        UUID id = playerId(a[1]);
        var dungeon = dungeon(a[2]);
        StaffPermissions.require(
                actor, a[0].equals("completion") ? "progression" : "cooldown", a[2]);
        String reason = reason(a, 4);
        var before = dungeon.playerDataStore().inspect(id);
        if (a[0].equals("completion") && !List.of("true", "false").contains(a[3]))
            throw new IllegalArgumentException("Use true or false");
        if (a[0].equals("loot-cooldown") && !a[3].equals("clear"))
            throw new IllegalArgumentException("Use clear for reward cooldowns");
        Long until =
                a[0].equals("cooldown") && !a[3].equals("clear")
                        ? Math.addExact(System.currentTimeMillis(), duration(a[3]))
                        : null;
        ops.requireContentWritable(a[2]);
        if (!confirmed(actor, input, token, a[0], id + "/" + a[2], before.toString())) return;
        dungeon.playerDataStore()
                .correct(
                        id,
                        record -> {
                            switch (a[0]) {
                                case "completion" ->
                                        record.set("Finished", Boolean.parseBoolean(a[3]));
                                case "loot-cooldown" -> record.set("RewardsInfo", null);
                                case "cooldown" -> record.set("AccessCooldown", until);
                                default -> throw new IllegalArgumentException("Unknown correction");
                            }
                        });
        ops.audit(
                actor,
                a[0],
                id + "/" + a[2],
                reason,
                before + " -> " + dungeon.playerDataStore().inspect(id));
        Player online = Bukkit.getPlayer(id);
        if (online != null && !actor.equals(online))
            tell(online, "Staff corrected " + a[0] + " for " + a[2] + ": " + reason);
        tell(actor, "Correction persisted");
    }

    private void compensate(CommandSender actor, String[] a, List<String> input, String token) {
        need(a, 5, "compensate <player> <material> <amount> <reason>");
        StaffPermissions.require(actor, "compensate", null);
        UUID id = playerId(a[1]);
        Material material = Material.matchMaterial(a[2]);
        int amount = Integer.parseInt(a[3]);
        if (material == null
                || !material.isItem()
                || material.isAir()
                || amount < 1
                || amount > material.getMaxStackSize())
            throw new IllegalArgumentException(
                    "Choose an item material and one valid stack amount");
        String reason = reason(a, 4);
        if (!confirmed(actor, input, token, "compensate", id.toString(), a[2] + "/" + amount))
            return;
        UUID receipt = UUID.fromString(token);
        boolean added =
                StaffPlayerRecords.compensate(
                        runtime, id, receipt, new ItemStack(material, amount));
        ops.audit(
                actor,
                "compensate",
                id.toString(),
                reason,
                receipt + " " + (added ? "queued " + amount + " " + material : "already applied"));
        tell(actor, "Compensation is durably claimable through /dungeon rewards");
    }

    private boolean confirmed(
            CommandSender actor,
            List<String> input,
            String token,
            String action,
            String target,
            String revision) {
        UUID next = ops.confirm(actor, action, target, revision, String.join(" ", input), token);
        if (next == null) return true;
        String command = CommandRoutes.staffCommand(input) + " --confirm " + next;
        tell(actor, "Confirm " + action + " for " + target + ". Impact: " + ops.impact(target));
        actor.sendMessage(
                Component.text("[Confirm within 30 seconds]")
                        .clickEvent(ClickEvent.suggestCommand(command)));
        tell(actor, command);
        return false;
    }

    public void overview(CommandSender actor) {
        StaffPermissions.require(actor, "inspect", null);
        tell(
                actor,
                "Dungeons: "
                        + runtime.dungeonCatalog().getLoadedDungeons().size()
                        + "; "
                        + ops.impact("all"));
        tell(
                actor,
                "Pending preparations: "
                        + runtime.preparations().count(null)
                        + "; load problems: "
                        + runtime.dungeonCatalog().loadFailures().size());
        var database = runtime.database();
        tell(actor, "Database: " + database.state() + "; ready=" + database.ready()
                + (database.problem().isBlank() ? "" : "; " + database.problem()));
        tell(actor, "Use /dungeon help for actions");
    }

    public List<DungeonDefinition> sortedDungeons() {
        return runtime.dungeonCatalog().getLoadedDungeons().stream()
                .sorted(Comparator.comparing(DungeonDefinition::getWorldName))
                .toList();
    }

    public List<String> validation(DungeonDefinition d) {
        var result = new ArrayList<String>();
        if (d.getStartSpawn() == null) result.add("ERROR: Start location is missing");
        if (d.getConfig().contains("locations.exit") && d.getExitLoc() == null)
            result.add("ERROR: Exit world is missing/unloaded; set the external exit again");
        if (d.getFunctions().isEmpty()) result.add("WARNING: No functions are configured");
        ContentReferences.dungeon(
                d,
                value -> {
                    if (value
                                    instanceof
                                    nl.hauntedmc.dungeons.model.element.DungeonFunction function
                            && function.getTrigger() == null
                            && function.getParentFunction() == null)
                        result.add("WARNING: Function has no trigger: " + function.getNamespace());
                    if (value
                                    instanceof
                                    nl.hauntedmc.dungeons.content.function.reward
                                                    .LootTableRewardsFunction
                                            reward
                            && runtime.lootTableRepository().get(reward.getLootTableName()) == null)
                        result.add("ERROR: Missing loot table: " + reward.getLootTableName());
                });
        for (String command : d.getJoinCommands()) {
            String label = command.strip().replaceFirst("^/+", "").split("\\s+", 2)[0];
            if (!label.isBlank() && Bukkit.getServer().getPluginCommand(label) == null)
                result.add("WARNING: Verify join-command dependency: " + label);
        }
        if (d.isSaving()) result.add("BUSY: Editor save in progress");
        if (d.getEditSession() != null) result.add("BUSY: Editor session owns this content");
        if (ops.maintenance(d.getWorldName()))
            result.add("MAINTENANCE: " + ops.maintenanceReason(d.getWorldName()));
        result.add("Instances: " + ops.impact(d.getWorldName()));
        if (result.size() == 1) result.add("No readiness problems found");
        return result;
    }

    private String describeQueue(nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry queue) {
        return queue.getAdmissionId() + " " + queue.getDungeon().getWorldName() + " " + queue.getDifficulty()
                + "; players=" + queue.getPlayers().size() + "; waiting=" + queue.getWaitingSeconds() + "s";
    }

    public List<String> queueLines() {
        var lines = new ArrayList<String>();
        int position = 0;
        for (var q : runtime.queueRegistry().snapshotQueues()) {
            String blocker = String.join(", ", runtime.dungeonQueueCoordinator().explain(q));
            lines.add(
                    "#"
                            + ++position
                            + " "
                            + q.getDungeon().getWorldName()
                            + ": "
                            + q.getPlayers().size()
                            + " players; waiting="
                            + q.getWaitingSeconds()
                            + "s; difficulty="
                            + q.getDifficulty()
                            + "; "
                            + blocker);
        }
        if (lines.isEmpty()) lines.add("No queued starts");
        return lines;
    }

    public List<String> playerLines(CommandSender actor, UUID id) {
        var lines = new ArrayList<String>();
        lines.add("Player " + id);
        var session = runtime.playerSessions().get(id);
        StaffPermissions.require(actor, "inspect", playerScope(session));
        if (session != null) {
            lines.add(
                    "Online: "
                            + session.getPlayer().isOnline()
                            + "; awaiting="
                            + session.isAwaitingDungeon());
            if (session.getInstance() != null) lines.add(describe(session.getInstance()));
            if (session.isObserving())
                lines.add("Observing " + session.getObservingInstance().getInstanceId());
            var team = runtime.teamService().getTeam(id);
            lines.add("Team: " + (team == null ? "none" : team.getLeaderId()));
            var queue = runtime.queueRegistry().getQueue(session);
            lines.add("Queue: " + (queue == null ? "none" : queue.getDungeon().getWorldName()));
        }
        if (StaffPermissions.allows(actor, "inspect", null)) {
            try {
                lines.add(StaffPlayerRecords.inspect(runtime, id));
            } catch (RuntimeException protectedRecord) {
                lines.add(
                        "RECOVERY RECORD PROTECTED: " + protectedRecord.getClass().getSimpleName());
            }
        }
        for (var d : sortedDungeons())
            if (StaffPermissions.allows(actor, "inspect", d.getWorldName()))
                lines.add(d.getWorldName() + ": " + d.playerDataStore().inspect(id));
        return lines;
    }

    public String describe(DungeonDefinition d) {
        return d.getWorldName()
                + " — "
                + (ops.maintenance(d.getWorldName())
                        ? "MAINTENANCE"
                        : d.isEnabled() ? "enabled" : "disabled")
                + "; "
                + ops.impact(d.getWorldName());
    }

    public String describe(DungeonInstance i) {
        return i.getInstanceId().toString().substring(0, 8)
                + " "
                + i.getDungeon().getWorldName()
                + " ["
                + i.getDisposalState()
                + "] "
                + i.getPlayers().size()
                + " participants"
                + (i.asPlayInstance() == null
                        ? " editor"
                        : "; elapsed="
                                + i.asPlayInstance().getTimeElapsed()
                                + "s, left="
                                + i.asPlayInstance().getTimeLeft()
                                + "s")
                + (i.getDisposalFailure() == null
                        ? ""
                        : "; failure=" + i.getDisposalFailure().getClass().getSimpleName());
    }

    public DungeonDefinition dungeon(String name) {
        var dungeon = runtime.dungeonCatalog().get(name);
        if (dungeon == null)
            throw new IllegalArgumentException(
                    "Dungeon not loaded: " + name + ". Inspect /dungeon server problems");
        return dungeon;
    }

    public UUID playerId(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            Player online = Bukkit.getPlayerExact(input);
            if (online != null) return online.getUniqueId();
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
            if (cached != null) return cached.getUniqueId();
            throw new IllegalArgumentException("Player not known locally; supply their UUID");
        }
    }

    public static long duration(String input) {
        var matcher =
                java.util.regex.Pattern.compile("(?i)([1-9][0-9]{0,8})(s|m|h|d|w)").matcher(input);
        if (!matcher.matches())
            throw new IllegalArgumentException("Use a positive duration, e.g. 30m, 2h, 7d");
        long unit =
                switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                    case "s" -> 1000L;
                    case "m" -> 60000L;
                    case "h" -> 3600000L;
                    case "d" -> 86400000L;
                    default -> 604800000L;
                };
        long value = Math.multiplyExact(Long.parseLong(matcher.group(1)), unit);
        if (value > 3650L * 86400000L)
            throw new IllegalArgumentException("Duration must be at most 10 years");
        return value;
    }

    private static String reason(String[] a, int offset) {
        String reason = String.join(" ", Arrays.copyOfRange(a, offset, a.length));
        StaffOperations.requireReason(reason);
        return reason;
    }

    private static void need(String[] a, int min, String usage) {
        if (a.length < min) throw new IllegalArgumentException("Invalid arguments. Use /dungeon help for command syntax.");
    }

    public static void tell(CommandSender actor, String text) {
        actor.sendMessage(Component.text("[Dungeons] " + text));
    }

    @Override
    public List<String> handleTabComplete(CommandSender actor, String[] args) {
        if (args.length == 2)
            return SYNTAX.stream()
                    .filter(s -> allowsAnyScope(actor, s.capability))
                    .map(Syntax::command)
                    .filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        if (args.length == 3) {
            var syntax =
                    SYNTAX.stream().filter(s -> s.command.equalsIgnoreCase(args[1])).findFirst();
            if (syntax.isEmpty()) return List.of();
            String capability = syntax.get().capability;
            boolean anyScope = allowsAnyScope(actor, capability);
            if (!anyScope) return List.of();
            List<String> options =
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "dungeon",
                                "maintenance",
                                "reload",
                                "archive",
                                "backup",
                                "backups",
                                "keys",
                                "restore" ->
                                sortedDungeons().stream()
                                        .filter(
                                                d ->
                                                        StaffPermissions.allows(
                                                                actor,
                                                                capability,
                                                                d.getWorldName()))
                                        .map(DungeonDefinition::getWorldName)
                                        .toList();
                        case "instance" ->
                                ops.instances("all").stream()
                                        .filter(
                                                i ->
                                                        StaffPermissions.allows(
                                                                actor,
                                                                capability,
                                                                i.getDungeon().getWorldName()))
                                        .map(i -> i.getInstanceId().toString().substring(0, 8))
                                        .toList();
                        case "player",
                                "cooldown",
                                "loot-cooldown",
                                "completion",
                                "compensate",
                                "restrict" ->
                                Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                        case "operation", "cancel" ->
                                ops.operations().stream()
                                        .filter(
                                                o ->
                                                        StaffPermissions.allows(
                                                                actor,
                                                                capability,
                                                                o.target.equals("all")
                                                                        ? null
                                                                        : o.target))
                                        .map(StaffOperations.Operation::shortId)
                                        .toList();
                        case "unarchive", "purge" -> content.archives();
                        default -> List.of();
                    };
            return options.stream()
                    .filter(
                            s ->
                                    s.toLowerCase(Locale.ROOT)
                                            .startsWith(args[2].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }

    private boolean allowsAnyScope(CommandSender actor, String capability) {
        return StaffPermissions.allows(actor, capability, null)
                || sortedDungeons().stream()
                        .anyMatch(
                                d -> StaffPermissions.allows(actor, capability, d.getWorldName()));
    }
}
