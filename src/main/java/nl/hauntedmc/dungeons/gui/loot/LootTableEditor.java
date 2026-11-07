package nl.hauntedmc.dungeons.gui.loot;

import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.content.reward.LootTableItem;
import nl.hauntedmc.dungeons.gui.framework.GuiService;
import nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns loot editing leases, window transitions and persistence; content models contain no UI. */
public final class LootTableEditor implements Listener, AutoCloseable {
    private final LootTableRepository repository;
    private final GuiService gui;
    private final Runnable unsubscribe;
    private final Map<String, Windows> windows = new HashMap<>();
    private final Map<String, UUID> editors = new HashMap<>();
    private final Map<UUID, String> transitioning = new HashMap<>();
    private boolean closed;

    private static final class Windows {
        final LootTable table;
        final LootTableMenu main;
        LootItemMenu item;

        Windows(LootTableEditor owner, LootTable table) {
            this.table = table;
            this.main = new LootTableMenu(owner, table);
            this.main.initialize();
        }

        boolean visible(Player player) {
            return this.main.visible(player) || (this.item != null && this.item.visible(player));
        }

        void unregister() {
            this.main.close();
            if (this.item != null) this.item.close();
        }
    }

    public LootTableEditor(LootTableRepository repository, GuiService gui) {
        this.repository = repository;
        this.gui = gui;
        this.unsubscribe = repository.onTableReplacement(this::invalidateReplacedTables);
    }

    /** Builds windows lazily for the current model, never as part of loading persisted data. */
    public void initialize(LootTable table) {
        requireOwnerThread();
        if (this.closed || this.repository.get(table.getNamespace()) != table) {
            throw new IllegalStateException("Cannot initialize an inactive loot table");
        }
        invalidateReplacedTables();
        this.windows.computeIfAbsent(table.getNamespace(), key -> new Windows(this, table));
    }

    /** Resolves a UUID lease without retaining a disconnected Bukkit player. */
    public Player getEditor(LootTable table) {
        requireOwnerThread();
        UUID id = this.editors.get(table.getNamespace());
        if (id == null) return null;
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            this.editors.remove(table.getNamespace(), id);
            return null;
        }
        return player;
    }

    /** Acquires the table's exclusive editing lease and opens its main menu. */
    public boolean open(LootTable table, Player player) {
        requireOwnerThread();
        if (this.closed
                || !this.repository.isWritable()
                || this.repository.get(table.getNamespace()) != table) return false;
        Player existing = getEditor(table);
        if (existing != null && !existing.getUniqueId().equals(player.getUniqueId())) return false;
        initialize(table);
        this.editors.put(table.getNamespace(), player.getUniqueId());
        openWindow(table, player, windowName(table, false));
        return true;
    }

    boolean canEdit(LootTable table, Player player) {
        requireOwnerThread();
        return !this.closed
                && this.repository.isWritable()
                && this.repository.get(table.getNamespace()) == table
                && player.getUniqueId().equals(this.editors.get(table.getNamespace()));
    }

    void openItem(LootTable table, LootTableItem item, Player player) {
        if (!canEdit(table, player)) return;
        Windows current = this.windows.get(table.getNamespace());
        if (current.item != null) current.item.close();
        current.item = new LootItemMenu(this, table, item);
        current.item.initialize();
        openWindow(table, player, windowName(table, true));
    }

    static String windowName(LootTable table, boolean item) {
        // Length framing prevents a table namespace from colliding with another table's item menu.
        return "loot:"
                + table.getNamespace().length()
                + ":"
                + table.getNamespace()
                + (item ? ":item" : ":main");
    }

    private void openWindow(LootTable table, Player player, String namespace) {
        UUID id = player.getUniqueId();
        this.transitioning.put(id, table.getNamespace());
        try {
            this.gui.openGui(player, namespace);
        } catch (RuntimeException failure) {
            this.editors.remove(table.getNamespace(), id);
            throw failure;
        } finally {
            this.transitioning.remove(id, table.getNamespace());
        }
    }

    void closed(LootTable table, Player player) {
        try {
            if (!this.closed
                    && this.repository.get(table.getNamespace()) == table
                    && player.getUniqueId().equals(this.editors.get(table.getNamespace()))
                    && !this.repository.saveTablesConfig(table)) {
                LangUtils.sendMessage(player, "commands.loot.save-failed");
            }
        } finally {
            if (this.repository.get(table.getNamespace()) == table
                    && !table.getNamespace().equals(this.transitioning.get(player.getUniqueId()))) {
                this.editors.remove(table.getNamespace(), player.getUniqueId());
            }
        }
    }

    private void invalidateReplacedTables() {
        for (var entry : java.util.List.copyOf(this.windows.entrySet())) {
            Windows stale = entry.getValue();
            if (this.repository.get(entry.getKey()) == stale.table) continue;
            retire(entry.getKey(), stale);
        }
    }

    private void retire(String namespace, Windows stale) {
        UUID id = this.editors.remove(namespace);
        Player player = id == null ? null : Bukkit.getPlayer(id);
        try {
            if (player != null && stale.visible(player)) player.closeInventory();
        } finally {
            stale.unregister();
            this.windows.remove(namespace, stale);
        }
    }

    /** Quit also releases a lease if normal inventory-close handling was suppressed. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        try {
            for (var entry : java.util.List.copyOf(this.windows.entrySet())) {
                if (id.equals(this.editors.get(entry.getKey())))
                    closed(entry.getValue().table, event.getPlayer());
            }
        } finally {
            this.editors.values().removeIf(id::equals);
            this.transitioning.remove(id);
        }
    }

    /** Checkpoints edits and removes every window/listener registration during plugin shutdown. */
    @Override
    public void close() {
        requireOwnerThread();
        if (this.closed) return;
        try {
            if (!this.windows.isEmpty() && this.repository.isWritable()) {
                this.repository.saveTablesConfig();
            }
        } finally {
            this.closed = true;
            if (this.unsubscribe != null) this.unsubscribe.run();
            for (var entry : java.util.List.copyOf(this.windows.entrySet())) {
                retire(entry.getKey(), entry.getValue());
            }
            this.editors.clear();
            this.transitioning.clear();
        }
    }

    private static void requireOwnerThread() {
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Loot editing requires the server thread");
        }
    }
}
