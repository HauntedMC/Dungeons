package nl.hauntedmc.dungeons.gui.staff;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.command.StaffCommandHandler;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.gui.staff.StaffScreenRows.Row;
import nl.hauntedmc.dungeons.gui.staff.StaffScreenRows.Screen;
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
public final class StaffMenus implements Listener, AutoCloseable, StaffScreenRows.Navigation {

    private record View(Inventory inventory, Screen screen, Map<Integer, Runnable> actions) {}

    private record Prompt(Player owner, long expires, Consumer<String> accept, Runnable cancel) {}

    private final DungeonsRuntime runtime;
    private final StaffCommandHandler commands;
    private final StaffScreenRows screenRows;
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
        this.screenRows = new StaffScreenRows(runtime, commands, this);
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

    private void render(Player player, Screen screen, boolean open) {
        List<Row> rows =
                screenRows.rows(player, screen).stream()
                        .filter(
                                r ->
                                        screen.filter().isBlank()
                                                || (r.label() + " " + r.information())
                                                        .toLowerCase(Locale.ROOT)
                                                        .contains(
                                                                screen.filter().toLowerCase(
                                                                        Locale.ROOT)))
                        .toList();
        int pages = Math.max(1, (rows.size() + 44) / 45);
        int page = Math.min(screen.page(), pages - 1);
        var actual = new Screen(screen.kind(), screen.target(), screen.filter(), page);
        View existing = views.get(player.getUniqueId());
        Inventory inv =
                open || existing == null
                        ? Bukkit.createInventory(
                                null, 54, Component.text("Dungeons • " + screen.kind()))
                        : existing.inventory;
        inv.clear();
        var actions = new HashMap<Integer, Runnable>();
        for (int slot = 0; slot < 45 && page * 45 + slot < rows.size(); slot++) {
            Row row = rows.get(page * 45 + slot);
            put(inv, actions, slot, row.label(), row.information(), row.icon(), row.action());
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
                List.of("Current: " + screen.filter()),
                Material.NAME_TAG,
                () ->
                        prompt(
                                player,
                                "Search (type clear to remove filter)",
                                value ->
                                        open(
                                                player,
                                                screen.kind(),
                                                screen.target(),
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
                    () -> open(player, screen.kind(), screen.target(), screen.filter(), page - 1));
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
                    () -> open(player, screen.kind(), screen.target(), screen.filter(), page + 1));
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

    private void reopen(Player player, Screen screen) {
        open(player, screen.kind(), screen.target(), screen.filter(), screen.page());
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
                                                .contains(entry.getValue().screen.kind())) {
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
                                    if (!view.screen.kind().equals("confirmation"))
                                        screenRows.rows((Player) event.getWhoClicked(), view.screen);
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
