package nl.hauntedmc.dungeons.runtime.player;

import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.lang.LangUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Server-thread session ownership with bounded retention for inactive disconnected players. */
public final class PlayerSessionRegistry implements Listener {
    private final DungeonsPlugin plugin;
    private static final int MAX_EXPIRATIONS_PER_SWEEP = 8;
    private final LinkedHashMap<UUID, DungeonPlayerSession> players = new LinkedHashMap<>();
    private final HashMap<UUID, Long> disconnected = new HashMap<>();
    private final LongSupplier clock;
    private Predicate<DungeonPlayerSession> retained = session -> false;
    private Consumer<UUID> releaseCachedState = ignored -> {};
    private BukkitTask sweepTask;
    private boolean closed;

    public PlayerSessionRegistry(DungeonsPlugin plugin) {
        this(plugin, System::nanoTime);
    }

    PlayerSessionRegistry(DungeonsPlugin plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    /** Starts one bounded sweep after the runtime's queue and preparation services exist. */
    public void start(DungeonsRuntime runtime) {
        requireOwnerThread();
        if (closed) throw new IllegalStateException("Session registry has shut down");
        if (sweepTask != null) return;
        releaseCachedState = id -> {
            if (runtime.hasDungeonCatalog())
                for (var dungeon : runtime.dungeonCatalog().getLoadedDungeons()) dungeon.playerDataStore().evictPlayer(id);
        };
        retained =
                session ->
                        runtime.preparations().contains(session)
                                || runtime.queueRegistry().getQueue(session) != null;
        sweepTask =
                Bukkit.getScheduler().runTaskTimer(plugin, this::expireOfflineSessions, 20L, 20L);
    }

    /**
     * Creates a fresh session or rebinds a still-owned session during the reconnect grace period.
     */
    public DungeonPlayerSession put(Player player) {
        requireOwnerThread();
        if (closed) throw new IllegalStateException("Session registry has shut down");
        try {
            DungeonPlayerSession session = players.get(player.getUniqueId());
            if (session == null) {
                session = new DungeonPlayerSession(plugin, player);
                players.put(player.getUniqueId(), session);
            } else {
                session.setPlayer(player);
            }
            disconnected.remove(player.getUniqueId());
            return session;
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger()
                    .error(
                            "Cannot load durable player state for '{}'.",
                            player.getUniqueId(),
                            failure);
            player.kick(
                    LangUtils.notice(
                            "Your player data could not be loaded safely. Please contact an"
                                + " administrator."));
            throw failure;
        }
    }

    /** Bind retained runs before normal-priority reconnect listeners inspect membership. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        if (players.containsKey(event.getPlayer().getUniqueId())) {
            try {
                put(event.getPlayer());
            } catch (RuntimeException ignored) {
                // put logged the failure and disconnected the unsafe player.
            }
        }
    }

    /** Records departure after queue/run listeners have handled their disconnect transitions. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        disconnected(event.getPlayer());
    }

    public void disconnected(Player player) {
        requireOwnerThread();
        if (get(player) != null) disconnected.putIfAbsent(player.getUniqueId(), clock.getAsLong());
    }

    /** Expires only offline sessions whose runtime ownership has ended and checkpoint succeeds. */
    public int expireOfflineSessions() {
        requireOwnerThread();
        long now = clock.getAsLong();
        long grace =
                TimeUnit.SECONDS.toNanos(
                        PluginConfigView.getOfflineSessionRetentionSeconds(plugin.getConfig()));
        int removed = 0;
        int attempted = 0;
        for (var entry : List.copyOf(players.entrySet())) {
            UUID id = entry.getKey();
            DungeonPlayerSession session = entry.getValue();
            if (session.getPlayer().isOnline()) {
                disconnected.remove(id);
                continue;
            }
            long since = disconnected.computeIfAbsent(id, ignored -> now);
            if (now - since >= grace) {
                // Bound synchronous checkpoint work and rotate pinned/failed entries so they do
                // not starve other disconnected players on subsequent sweeps.
                players.remove(id);
                players.put(id, session);
                if (expire(id, session, false)) removed++;
                if (++attempted >= MAX_EXPIRATIONS_PER_SWEEP) break;
            }
        }
        return removed;
    }

    /** Stops expiry scheduling and releases checkpointed sessions after run/queue shutdown. */
    public void shutdown() {
        requireOwnerThread();
        closed = true;
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        for (var entry : List.copyOf(players.entrySet()))
            expire(entry.getKey(), entry.getValue(), true);
    }

    private boolean expire(UUID id, DungeonPlayerSession session, boolean shutdown) {
        if (retained.test(session)) return false;
        try {
            if (!session.expire(shutdown)) return false;
            players.remove(id, session);
            disconnected.remove(id);
            try { releaseCachedState.accept(id); }
            catch (RuntimeException failure) {
                plugin.getSLF4JLogger().error("Could not release cached dungeon records for expired player {}", id, failure);
            }
            return true;
        } catch (RuntimeException failure) {
            plugin.getSLF4JLogger()
                    .error(
                            "Cannot expire session '{}'; retaining it until its durable checkpoint"
                                + " succeeds.",
                            id,
                            failure);
            return false;
        }
    }

    public List<DungeonPlayerSession> snapshot() {
        return List.copyOf(players.values());
    }

    public int size() {
        return players.size();
    }

    public boolean contains(Player player) {
        return get(player) != null;
    }

    /** A late event from an old connection must not operate on a reconnected player's session. */
    public DungeonPlayerSession get(Player player) {
        DungeonPlayerSession session = players.get(player.getUniqueId());
        return session != null && session.getPlayer() == player ? session : null;
    }

    public DungeonPlayerSession get(UUID uuid) {
        return players.get(uuid);
    }

    private static void requireOwnerThread() {
        if (Bukkit.getServer() != null && !Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Player session lifecycle requires the server thread");
        }
    }
}
