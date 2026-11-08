package nl.hauntedmc.dungeons.gui.staff;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.command.StaffCommandHandler;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.staff.StaffPermissions;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.function.Consumer;

/** One listener owns staff views. Actions re-enter the same authorized command services. */
public final class StaffMenus implements Listener, AutoCloseable {
    private record Screen(String kind, String target, String filter, int page) {}

    private record Row(String label, List<String> information, Material icon, Runnable action) {}

    private record View(Inventory inventory, Screen screen, Map<Integer, Runnable> actions) {}

    private record Prompt(Player owner, long expires, Consumer<String> accept, Runnable cancel) {}

    private final DungeonsRuntime runtime;
    private final StaffCommandHandler commands;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<UUID, Prompt> prompts = new java.util.concurrent.ConcurrentHashMap<>();
    private BukkitTask refresh;
    private boolean closed;
    private final java.util.function.LongSupplier clock;

    public StaffMenus(DungeonsRuntime runtime, StaffCommandHandler commands) {
        this(runtime, commands, System::nanoTime);
    }

    StaffMenus(
            DungeonsRuntime runtime,
            StaffCommandHandler commands,
            java.util.function.LongSupplier clock) {
        this.clock = clock;
        this.runtime = runtime;
        this.commands = commands;
        runtime.staffOperations().onClose(this::close);
        Bukkit.getPluginManager().registerEvents(this, runtime.environment().plugin());
    }

    public void open(Player player, String kind, String target, String filter, int page) {
        try {
            if (closed) throw new IllegalStateException("Staff interfaces have shut down");
            render(player, new Screen(kind, target, filter, Math.max(0, page)), true);
        } catch (RuntimeException failure) {
            StaffCommandHandler.tell(player, failure.getMessage());
        }
    }

    private List<Row> rows(Player p, Screen screen) {
        var rows = new ArrayList<Row>();
        var ops = runtime.staffOperations();
        String scope =
                screen.target.isBlank() || screen.target.equals("all") ? null : screen.target;
        if (screen.kind.equals("instance"))
            scope = ops.instance(screen.target).getDungeon().getWorldName();
        if (screen.kind.equals("player"))
            scope =
                    commands.playerScope(
                            runtime.playerSessions().get(UUID.fromString(screen.target)));
        if (screen.kind.equals("operation")) scope = ops.operation(screen.target).target;
        StaffPermissions.require(p, "inspect", scope);
        switch (screen.kind) {
            case "overview" -> {
                rows.add(
                        row(
                                "Dungeons",
                                List.of(ops.impact("all")),
                                Material.SPAWNER,
                                () -> open(p, "dungeons", "", "", 0)));
                rows.add(
                        row(
                                "Active instances",
                                List.of("Participants, timers and lifecycle"),
                                Material.COMPASS,
                                () -> open(p, "instances", "all", "", 0)));
                rows.add(
                        row(
                                "Player support",
                                List.of("Search exact name or UUID"),
                                Material.PLAYER_HEAD,
                                () ->
                                        prompt(
                                                p,
                                                "Player name or UUID",
                                                value -> execute(p, "player", value),
                                                () -> reopen(p, screen))));
                rows.add(
                        row(
                                "Queued starts",
                                commands.queueLines(),
                                Material.CLOCK,
                                () -> open(p, "queues", "", "", 0)));
                rows.add(
                        row(
                                "Operations",
                                List.of("Waiting, running and completed work"),
                                Material.WRITABLE_BOOK,
                                () -> open(p, "operations", "", "", 0)));
                rows.add(
                        row(
                                "Loot tables",
                                List.of("Manage reward content"),
                                Material.CHEST,
                                () -> execute(p, "loot")));
                rows.add(
                        row(
                                "Operational problems",
                                List.of(
                                        "Failed loads: "
                                                + runtime.dungeonCatalog().loadFailures().size(),
                                        "Recent failures: " + ops.problems().size()),
                                Material.REDSTONE_TORCH,
                                () -> execute(p, "problems")));
            }
            case "dungeons" -> {
                for (var d : commands.sortedDungeons()) {
                    if (!StaffPermissions.allows(p, "inspect", d.getWorldName())) continue;
                    rows.add(
                            row(
                                    d.getWorldName(),
                                    List.of(commands.describe(d)),
                                    d.isEnabled() ? Material.MOSSY_COBBLESTONE : Material.BARRIER,
                                    () -> open(p, "dungeon", d.getWorldName(), "", 0)));
                }
                runtime.dungeonCatalog()
                        .loadFailures()
                        .forEach(
                                (name, failure) ->
                                        rows.add(
                                                row(
                                                        name + " (load failed)",
                                                        List.of(
                                                                failure,
                                                                "Repair files, then reload in"
                                                                        + " maintenance"),
                                                        Material.BARRIER,
                                                        () -> execute(p, "problems"))));
            }
            case "dungeon" -> {
                var d = commands.dungeon(screen.target);
                String name = d.getWorldName();
                rows.add(
                        row(
                                "Readiness and details",
                                commands.validation(d),
                                Material.BOOK,
                                () -> execute(p, "dungeon", name, "validate")));
                rows.add(
                        row(
                                "Instances",
                                List.of(ops.impact(name)),
                                Material.COMPASS,
                                () -> open(p, "instances", name, "", 0)));
                action(
                        rows,
                        p,
                        "maintenance",
                        name,
                        "Enable maintenance",
                        "Stops entries; existing runs finish",
                        Material.REDSTONE_BLOCK,
                        () ->
                                reason(
                                        p,
                                        "Maintenance reason",
                                        value -> execute(p, "maintenance", name, "on", value),
                                        screen));
                action(
                        rows,
                        p,
                        "maintenance",
                        name,
                        "Resume admissions",
                        "End maintenance after checking readiness",
                        Material.EMERALD_BLOCK,
                        () ->
                                reason(
                                        p,
                                        "Reason to resume",
                                        value -> execute(p, "maintenance", name, "off", value),
                                        screen));
                action(
                        rows,
                        p,
                        "reload",
                        name,
                        "Reload after drain",
                        "Maintenance remains enabled when finished",
                        Material.REPEATER,
                        () ->
                                reason(
                                        p,
                                        "Reload reason",
                                        value -> execute(p, "reload", name, value),
                                        screen));
                action(
                        rows,
                        p,
                        "edit",
                        name,
                        "Open editor",
                        "Requires maintenance and drained play worlds",
                        Material.FEATHER,
                        () -> {
                            p.closeInventory();
                            p.performCommand("dungeon edit open " + name);
                        });
                action(
                        rows,
                        p,
                        "edit",
                        name,
                        "Dungeon settings",
                        "Browse validated settings",
                        Material.COMPARATOR,
                        () -> open(p, "settings", name, "", 0));
                action(
                        rows,
                        p,
                        "backup",
                        name,
                        "Create backup",
                        "Keeps ten copies; excludes progression",
                        Material.CHEST,
                        () ->
                                reason(
                                        p,
                                        "Backup reason",
                                        value -> execute(p, "backup", name, value),
                                        screen));
                action(
                        rows,
                        p,
                        "restore",
                        name,
                        "Restore backup",
                        "List copies and supply an ID",
                        Material.ENDER_CHEST,
                        () -> {
                            execute(p, "backups", name);
                            prompt(
                                    p,
                                    "Backup ID",
                                    id ->
                                            reason(
                                                    p,
                                                    "Restore reason",
                                                    value -> execute(p, "restore", name, id, value),
                                                    screen),
                                    () -> reopen(p, screen));
                        });
                action(
                        rows,
                        p,
                        "delete",
                        name,
                        "Archive dungeon",
                        "Recoverable until explicitly purged",
                        Material.BARRIER,
                        () ->
                                reason(
                                        p,
                                        "Archive reason",
                                        value -> execute(p, "archive", name, value),
                                        screen));
            }
            case "loot" -> {
                StaffPermissions.require(p, "loot", null);
                for (var table : runtime.lootTableRepository().getTables())
                    rows.add(
                            row(
                                    table.getNamespace(),
                                    List.of("Click to edit"),
                                    Material.CHEST,
                                    () -> execute(p, "loot", table.getNamespace())));
            }
            case "settings" -> {
                var d = commands.dungeon(screen.target);
                StaffPermissions.require(p, "edit", d.getWorldName());
                for (var entry :
                        nl.hauntedmc.dungeons.command.config.DungeonConfigRegistry.getEntries(d)) {
                    String value = Objects.toString(d.getConfig().get(entry.getPath()), "unset");
                    String source =
                            entry.getPath().startsWith("rules.")
                                            && !d.getConfig()
                                                    .getValues(true)
                                                    .containsKey(entry.getPath())
                                    ? "inherited globally"
                                    : "dungeon override";
                    rows.add(
                            row(
                                    entry.getKey(),
                                    List.of(
                                            entry.getDescription(),
                                            "Current: " + value,
                                            "Source: " + source,
                                            "Gameplay changes require drained maintenance"),
                                    Material.PAPER,
                                    () ->
                                            prompt(
                                                    p,
                                                    entry.getKey() + " (current: " + value + ")",
                                                    input -> {
                                                        if (runtime.dungeonCatalog()
                                                                        .get(d.getWorldName())
                                                                != d)
                                                            throw new IllegalArgumentException(
                                                                    "Dungeon was replaced; reopen"
                                                                            + " settings");
                                                        p.performCommand(
                                                                "dungeon config "
                                                                        + d.getWorldName()
                                                                        + " "
                                                                        + entry.getKey()
                                                                        + " "
                                                                        + input);
                                                        reopen(p, screen);
                                                    },
                                                    () -> reopen(p, screen))));
                }
            }
            case "instances" -> {
                for (var i : ops.instances(screen.target.isBlank() ? "all" : screen.target)) {
                    if (!StaffPermissions.allows(p, "inspect", i.getDungeon().getWorldName()))
                        continue;
                    rows.add(
                            row(
                                    i.getInstanceId().toString().substring(0, 8),
                                    List.of(
                                            commands.describe(i),
                                            "World: "
                                                    + Objects.toString(
                                                            i.getInstanceWorldName(),
                                                            "not loaded")),
                                    Material.COMPASS,
                                    () ->
                                            open(
                                                    p,
                                                    "instance",
                                                    i.getInstanceId().toString(),
                                                    "",
                                                    0)));
                }
            }
            case "instance" -> {
                var i = ops.instance(screen.target);
                String id = i.getInstanceId().toString();
                String name = i.getDungeon().getWorldName();
                rows.add(
                        row(
                                "Instance details",
                                List.of(commands.describe(i)),
                                Material.BOOK,
                                () -> execute(p, "instance", id)));
                for (var session : i.getPlayers()) {
                    UUID playerId = session.getPlayer().getUniqueId();
                    rows.add(
                            row(
                                    session.getPlayer().getName(),
                                    List.of("Player support"),
                                    Material.PLAYER_HEAD,
                                    () -> execute(p, "player", playerId.toString())));
                }
                action(
                        rows,
                        p,
                        "observe",
                        name,
                        "Observe run",
                        "Spectator presence; does not join gameplay",
                        Material.ENDER_EYE,
                        () -> {
                            p.closeInventory();
                            execute(p, "instance", id, "observe");
                        });
                action(
                        rows,
                        p,
                        "recovery",
                        name,
                        "Retry disposal",
                        "Refuses while participants remain",
                        Material.REPEATER,
                        () ->
                                reason(
                                        p,
                                        "Disposal retry reason",
                                        value -> execute(p, "instance", id, "retry", value),
                                        screen));
                action(
                        rows,
                        p,
                        "rescue",
                        name,
                        "Evacuate run",
                        "Confirmed removal without new access penalties",
                        Material.BARRIER,
                        () ->
                                reason(
                                        p,
                                        "Evacuation reason",
                                        value -> execute(p, "instance", id, "evacuate", value),
                                        screen));
                action(
                        rows,
                        p,
                        "timer",
                        name,
                        "Extend time",
                        "Add seconds to a running timed instance",
                        Material.CLOCK,
                        () ->
                                prompt(
                                        p,
                                        "Seconds to add",
                                        seconds ->
                                                reason(
                                                        p,
                                                        "Extension reason",
                                                        value ->
                                                                execute(
                                                                        p,
                                                                        "instance",
                                                                        id,
                                                                        "extend",
                                                                        seconds,
                                                                        value),
                                                        screen),
                                        () -> reopen(p, screen)));
            }
            case "player" -> {
                UUID id = UUID.fromString(screen.target);
                rows.add(
                        row(
                                "Player details",
                                commands.playerLines(p, id),
                                Material.BOOK,
                                () -> execute(p, "player", id.toString())));
                var session = runtime.playerSessions().get(id);
                String name = commands.playerScope(session);
                for (String support :
                        List.of("rescue", "remove", "queue-clear", "recover", "revive")) {
                    String cap =
                            switch (support) {
                                case "queue-clear" -> "queue";
                                case "recover" -> "recovery";
                                case "revive" -> "lives";
                                default -> support;
                            };
                    action(
                            rows,
                            p,
                            cap,
                            name,
                            support,
                            "Requires a reason; state is checked again at execution",
                            Material.PAPER,
                            () ->
                                    reason(
                                            p,
                                            support + " reason",
                                            value ->
                                                    execute(
                                                            p,
                                                            "player",
                                                            id.toString(),
                                                            support,
                                                            value),
                                            screen));
                }
                action(
                        rows,
                        p,
                        "lives",
                        name,
                        "Set lives",
                        "Set a living participant's life count",
                        Material.TOTEM_OF_UNDYING,
                        () ->
                                prompt(
                                        p,
                                        "Lives (1–10000)",
                                        count ->
                                                reason(
                                                        p,
                                                        "Lives correction reason",
                                                        value ->
                                                                execute(
                                                                        p,
                                                                        "player",
                                                                        id.toString(),
                                                                        "lives",
                                                                        count,
                                                                        value),
                                                        screen),
                                        () -> reopen(p, screen)));
                for (String correction :
                        List.of("cooldown", "loot-cooldown", "completion", "restrict")) {
                    String capability =
                            correction.equals("completion")
                                    ? "progression"
                                    : correction.equals("restrict") ? "restrict" : "cooldown";
                    action(
                            rows,
                            p,
                            capability,
                            name,
                            correction,
                            "Correct persisted player state for one dungeon",
                            Material.WRITABLE_BOOK,
                            () ->
                                    prompt(
                                            p,
                                            "Dungeon name"
                                                    + (correction.equals("restrict")
                                                            ? " or all"
                                                            : ""),
                                            dungeon ->
                                                    prompt(
                                                            p,
                                                            correction.equals("completion")
                                                                    ? "true or false"
                                                                    : correction.equals(
                                                                                    "loot-cooldown")
                                                                            ? "clear"
                                                                            : correction.equals(
                                                                                            "restrict")
                                                                                    ? "Duration,"
                                                                                          + " forever"
                                                                                          + " or clear"
                                                                                    : "Duration or"
                                                                                          + " clear",
                                                            amount ->
                                                                    reason(
                                                                            p,
                                                                            "Correction reason",
                                                                            value ->
                                                                                    execute(
                                                                                            p,
                                                                                            correction,
                                                                                            id
                                                                                                    .toString(),
                                                                                            dungeon,
                                                                                            amount,
                                                                                            value),
                                                                            screen),
                                                            () -> reopen(p, screen)),
                                            () -> reopen(p, screen)));
                }
                action(
                        rows,
                        p,
                        "compensate",
                        null,
                        "Compensate",
                        "Queue an item stack for durable reward delivery",
                        Material.CHEST,
                        () ->
                                prompt(
                                        p,
                                        "Material name",
                                        material ->
                                                prompt(
                                                        p,
                                                        "Amount",
                                                        amount ->
                                                                reason(
                                                                        p,
                                                                        "Compensation reason",
                                                                        value ->
                                                                                execute(
                                                                                        p,
                                                                                        "compensate",
                                                                                        id
                                                                                                .toString(),
                                                                                        material,
                                                                                        amount,
                                                                                        value),
                                                                        screen),
                                                        () -> reopen(p, screen)),
                                        () -> reopen(p, screen)));
            }
            case "queues" -> {
                for (var q : runtime.queueRegistry().snapshotQueues()) {
                    UUID leader = q.getLeaderId();
                    rows.add(
                            row(
                                    q.getDungeon().getWorldName(),
                                    List.of(
                                            "Players: " + q.getPlayers().size(),
                                            "Leader: " + leader,
                                            "Waiting: " + q.getWaitingSeconds() + "s",
                                            "Click to inspect and cancel pending starts"),
                                    Material.CLOCK,
                                    () -> execute(p, "player", leader.toString())));
                }
            }
            case "operations" -> {
                for (var op : ops.operations())
                    rows.add(
                            row(
                                    op.shortId() + " " + op.state,
                                    List.of(
                                            op.action + " " + op.target,
                                            op.detail,
                                            "Actor: " + op.actor,
                                            "Reason: " + op.reason),
                                    op.state
                                                    == nl.hauntedmc.dungeons.runtime.staff
                                                            .StaffOperations.State.FAILED
                                            ? Material.BARRIER
                                            : Material.PAPER,
                                    () -> open(p, "operation", op.shortId(), "", 0)));
            }
            case "operation" -> {
                var op = ops.operation(screen.target);
                rows.add(
                        row(
                                op.shortId() + " " + op.state,
                                List.of(op.action + " " + op.target, op.detail, op.reason),
                                Material.BOOK,
                                () -> execute(p, "operation", op.shortId())));
                action(
                        rows,
                        p,
                        "maintenance",
                        op.target,
                        "Cancel waiting operation",
                        "Maintenance remains enabled",
                        Material.BARRIER,
                        () ->
                                reason(
                                        p,
                                        "Cancellation reason",
                                        value -> execute(p, "cancel", op.shortId(), value),
                                        screen));
            }
            default -> throw new IllegalArgumentException("Unknown staff screen");
        }
        return rows;
    }

    private void render(Player player, Screen screen, boolean open) {
        List<Row> rows =
                rows(player, screen).stream()
                        .filter(
                                r ->
                                        screen.filter.isBlank()
                                                || (r.label + " " + r.information)
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(
                                                                screen.filter.toLowerCase(
                                                                        Locale.ROOT)))
                        .toList();
        int pages = Math.max(1, (rows.size() + 44) / 45);
        int page = Math.min(screen.page, pages - 1);
        var actual = new Screen(screen.kind, screen.target, screen.filter, page);
        View existing = views.get(player.getUniqueId());
        Inventory inv =
                open || existing == null
                        ? Bukkit.createInventory(
                                null, 54, Component.text("Dungeons • " + screen.kind))
                        : existing.inventory;
        inv.clear();
        var actions = new HashMap<Integer, Runnable>();
        for (int slot = 0; slot < 45 && page * 45 + slot < rows.size(); slot++) {
            Row row = rows.get(page * 45 + slot);
            put(inv, actions, slot, row.label, row.information, row.icon, row.action);
        }
        put(
                inv,
                actions,
                45,
                "Back to dashboard",
                List.of(),
                Material.ARROW,
                () -> open(player, "overview", "", "", 0));
        put(
                inv,
                actions,
                47,
                "Search",
                List.of("Current: " + screen.filter),
                Material.NAME_TAG,
                () ->
                        prompt(
                                player,
                                "Search (type clear to remove filter)",
                                value ->
                                        open(
                                                player,
                                                screen.kind,
                                                screen.target,
                                                value.equalsIgnoreCase("clear") ? "" : value,
                                                0),
                                () -> reopen(player, actual)));
        if (page > 0)
            put(
                    inv,
                    actions,
                    48,
                    "Previous",
                    List.of(),
                    Material.ARROW,
                    () -> open(player, screen.kind, screen.target, screen.filter, page - 1));
        put(
                inv,
                actions,
                49,
                "Page " + (page + 1) + "/" + pages,
                List.of("Updates while open"),
                Material.CLOCK,
                () -> reopen(player, actual));
        if (page + 1 < pages)
            put(
                    inv,
                    actions,
                    50,
                    "Next",
                    List.of(),
                    Material.ARROW,
                    () -> open(player, screen.kind, screen.target, screen.filter, page + 1));
        put(inv, actions, 53, "Close", List.of(), Material.BARRIER, player::closeInventory);
        if (open) player.openInventory(inv);
        views.put(player.getUniqueId(), new View(inv, actual, actions));
        startRefresh();
    }

    public void confirm(Player player, String command) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Confirm staff action"));
        var actions = new HashMap<Integer, Runnable>();
        put(
                inv,
                actions,
                11,
                "Confirm",
                List.of("Single use • expires in 30 seconds", "Impact is shown in chat"),
                Material.LIME_CONCRETE,
                () -> {
                    player.closeInventory();
                    commands.handleCommand(
                            player, command.substring("/dungeon ".length()).split(" "));
                });
        put(inv, actions, 15, "Cancel", List.of(), Material.RED_CONCRETE, player::closeInventory);
        player.openInventory(inv);
        views.put(
                player.getUniqueId(),
                new View(inv, new Screen("confirmation", "", "", 0), actions));
        startRefresh();
    }

    public void prompt(Player player, String question, Consumer<String> accept, Runnable cancel) {
        if (closed) throw new IllegalStateException("Staff interfaces have shut down");
        player.closeInventory();
        var session = runtime.playerSessions().get(player);
        if (session != null) {
            session.setChatListening(false);
            session.setAwaitingRoomName(false);
        }
        prompts.put(
                player.getUniqueId(),
                new Prompt(
                        player,
                        clock.getAsLong() + java.util.concurrent.TimeUnit.SECONDS.toNanos(60),
                        accept,
                        cancel));
        StaffCommandHandler.tell(player, question + ". Type cancel to return (60 seconds).");
        startRefresh();
    }

    private void reason(Player player, String question, Consumer<String> accept, Screen screen) {
        prompt(player, question, accept, () -> reopen(player, screen));
    }

    private void reopen(Player player, Screen screen) {
        open(player, screen.kind, screen.target, screen.filter, screen.page);
    }

    private void execute(Player player, String... args) {
        var command = new ArrayList<>(List.of("admin"));
        command.addAll(Arrays.asList(args));
        commands.executeChecked(player, command.toArray(String[]::new));
    }

    private static Row row(String name, List<String> lines, Material icon, Runnable action) {
        return new Row(name, lines, icon, action);
    }

    private static void action(
            List<Row> rows,
            Player player,
            String capability,
            String scope,
            String label,
            String detail,
            Material icon,
            Runnable callback) {
        boolean allowed = StaffPermissions.allows(player, capability, scope);
        rows.add(
                row(
                        label,
                        List.of(
                                detail,
                                allowed
                                        ? "Click to continue"
                                        : "Unavailable: missing " + capability + " permission"),
                        allowed ? icon : Material.GRAY_STAINED_GLASS_PANE,
                        () -> {
                            StaffPermissions.require(player, capability, scope);
                            callback.run();
                        }));
    }

    private static void put(
            Inventory inventory,
            Map<Integer, Runnable> actions,
            int slot,
            String label,
            List<String> lines,
            Material material,
            Runnable action) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(label));
        meta.lore(
                lines.stream()
                        .limit(18)
                        .map(
                                line ->
                                        Component.text(
                                                line.length() > 140
                                                        ? line.substring(0, 140) + "…"
                                                        : line))
                        .toList());
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        actions.put(slot, action);
    }

    private void startRefresh() {
        if (refresh != null) return;
        refresh =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                runtime.environment().plugin(),
                                () -> {
                                    for (var entry : List.copyOf(views.entrySet())) {
                                        Player player = Bukkit.getPlayer(entry.getKey());
                                        if (player == null
                                                || !player.isOnline()
                                                || player.getOpenInventory().getTopInventory()
                                                        != entry.getValue().inventory) {
                                            views.remove(entry.getKey());
                                            continue;
                                        }
                                        if (List.of(
                                                        "overview",
                                                        "instances",
                                                        "instance",
                                                        "queues",
                                                        "operations")
                                                .contains(entry.getValue().screen.kind)) {
                                            try {
                                                render(player, entry.getValue().screen, false);
                                            } catch (RuntimeException stale) {
                                                player.closeInventory();
                                                StaffCommandHandler.tell(
                                                        player,
                                                        "View changed; reopen the staff dashboard");
                                            }
                                        }
                                    }
                                    for (var entry : List.copyOf(prompts.entrySet()))
                                        if (entry.getValue().expires <= clock.getAsLong()) {
                                            prompts.remove(entry.getKey(), entry.getValue());
                                            Player p = Bukkit.getPlayer(entry.getKey());
                                            if (p != null
                                                    && p == entry.getValue().owner
                                                    && p.isOnline()) {
                                                StaffCommandHandler.tell(p, "Input timed out");
                                                entry.getValue().cancel.run();
                                            }
                                        }
                                    if (views.isEmpty() && prompts.isEmpty()) {
                                        refresh.cancel();
                                        refresh = null;
                                    }
                                },
                                20L,
                                20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        View view = views.get(event.getWhoClicked().getUniqueId());
        if (view == null || event.getView().getTopInventory() != view.inventory) return;
        event.setCancelled(true);
        if (event.getClick() != ClickType.LEFT || event.getRawSlot() >= view.inventory.getSize())
            return;
        Runnable action = view.actions.get(event.getRawSlot());
        if (action != null)
            Bukkit.getScheduler()
                    .runTask(
                            runtime.environment().plugin(),
                            () -> {
                                View current = views.get(event.getWhoClicked().getUniqueId());
                                if (closed
                                        || current == null
                                        || current.inventory != view.inventory
                                        || event.getWhoClicked()
                                                        .getOpenInventory()
                                                        .getTopInventory()
                                                != view.inventory) return;
                                try {
                                    if (!view.screen.kind.equals("confirmation"))
                                        rows((Player) event.getWhoClicked(), view.screen);
                                    action.run();
                                } catch (RuntimeException failure) {
                                    StaffCommandHandler.tell(
                                            event.getWhoClicked(), failure.getMessage());
                                }
                            });
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        View view = views.get(event.getWhoClicked().getUniqueId());
        if (view != null && event.getView().getTopInventory() == view.inventory)
            event.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        View view = views.get(event.getPlayer().getUniqueId());
        if (view != null && view.inventory == event.getInventory())
            views.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        views.remove(event.getPlayer().getUniqueId());
        prompts.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(AsyncChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Prompt prompt = prompts.get(id);
        if (prompt == null || prompt.owner != event.getPlayer() || closed) return;
        event.setCancelled(true);
        String value = ComponentUtils.plainText(event.originalMessage()).strip();
        Bukkit.getScheduler()
                .runTask(
                        runtime.environment().plugin(),
                        () -> {
                            if (closed
                                    || !prompts.remove(id, prompt)
                                    || !event.getPlayer().isOnline()
                                    || runtime.playerSessions().get(event.getPlayer()) == null)
                                return;
                            if (value.equalsIgnoreCase("cancel")) {
                                prompt.cancel.run();
                                return;
                            }
                            if (clock.getAsLong() >= prompt.expires) {
                                StaffCommandHandler.tell(event.getPlayer(), "Input timed out");
                                return;
                            }
                            try {
                                if (value.isBlank())
                                    throw new IllegalArgumentException("A value is required");
                                prompt.accept.accept(value);
                            } catch (RuntimeException failure) {
                                StaffCommandHandler.tell(event.getPlayer(), failure.getMessage());
                                prompts.putIfAbsent(
                                        id,
                                        new Prompt(
                                                prompt.owner,
                                                prompt.expires,
                                                prompt.accept,
                                                prompt.cancel));
                            }
                        });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (refresh != null) {
            refresh.cancel();
            refresh = null;
        }
        for (var entry : List.copyOf(views.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null
                    && player.getOpenInventory().getTopInventory() == entry.getValue().inventory)
                player.closeInventory();
        }
        views.clear();
        prompts.clear();
        HandlerList.unregisterAll(this);
    }
}
