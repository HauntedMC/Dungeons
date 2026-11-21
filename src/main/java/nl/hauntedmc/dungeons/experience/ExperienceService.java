package nl.hauntedmc.dungeons.experience;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import nl.hauntedmc.dungeons.api.DungeonExperienceApi;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.menu.MenuFrame;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.presentation.DungeonUi;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.featureframework.hud.HudCoordinator;
import nl.hauntedmc.featureframework.hud.HudFrame;
import nl.hauntedmc.featureframework.hud.HudService;
import nl.hauntedmc.featureframework.lifecycle.CleanupSequence;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns the complete presentation lifetime; gameplay modules are instantiated only when configured.
 */
public final class ExperienceService implements DungeonExperienceApi, Listener, AutoCloseable {
    private static final Duration LEASE = Duration.ofSeconds(10);

    private static final class Participation {
        final PlayableInstance instance;
        final int entered;
        final EnumMap<HudService.Channel, HudService.Lease> channels =
                new EnumMap<>(HudService.Channel.class);
        final EnumMap<HudService.Channel, HudService.Lease> feedback =
                new EnumMap<>(HudService.Channel.class);
        final ArrayDeque<Component> history = new ArrayDeque<>();
        String pinned;
        String outcome;
        int lostLives;
        int kills;
        final List<Component> rewards = new ArrayList<>();
        int lastLives;
        int lastProgress;
        boolean hintOffered;
        long offlineAt;

        Participation(PlayableInstance instance) {
            this.instance = instance;
            entered = instance.getTimeElapsed();
            lastProgress = entered;
        }
    }

    private static final class Waiting {
        nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry queue;
        HudService.Lease sidebar;
        long messageBucket = -1;
        ExperiencePresentation policy = ExperiencePresentation.defaults();
        boolean invalidPolicy;

        Waiting(nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry queue) {
            this.queue = queue;
        }
    }

    private final Map<UUID, Waiting> waiting = new HashMap<>();

    private record Exit(Participation participation, String signature, long expires) {}

    private final DungeonsRuntime runtime;
    private final java.util.function.LongSupplier nanoTime;
    private final Map<PlayableInstance, ExperienceRun> runs = new LinkedHashMap<>();
    private final Map<PlayableInstance, ExperiencePresentation> presentations = new HashMap<>();
    private final java.util.Set<PlayableInstance> invalidPresentations = new java.util.HashSet<>();
    private final Map<UUID, Participation> players = new HashMap<>();
    private final Map<UUID, HudService.Lease> previews = new HashMap<>();
    private final Map<UUID, Exit> exits = new HashMap<>();
    private final ExperienceSubscriptions subscriptions =
            new ExperienceSubscriptions(ExperienceService::requireThread);
    private final HudCoordinator standalone =
            new HudCoordinator(
                    new StandaloneHudRenderer(),
                    System::nanoTime,
                    ExperienceService::requireThread);
    private HudService backend;
    private ExperienceEditor editor;
    private ObservationHud observers;
    private BukkitTask ticker;
    private int ticks;
    private boolean closed;
    private boolean started;
    private boolean warned;

    public ExperienceService(DungeonsRuntime runtime) {
        this(runtime, System::nanoTime);
    }

    ExperienceService(DungeonsRuntime runtime, java.util.function.LongSupplier nanoTime) {
        this.runtime = runtime;
        this.nanoTime = nanoTime;
    }

    public void start() {
        requireThread();
        if (closed) throw new IllegalStateException("Experience service is closed");
        if (started) return;
        started = true;
        try {
            runtime.resources().ownIngress(this, ExperienceService::close);
            Bukkit.getPluginManager().registerEvents(this, runtime.environment().plugin());
            selectBackend();
            editor = new ExperienceEditor(runtime);
            observers = new ObservationHud(runtime, () -> backend, this::presentation);
            ticker =
                    Bukkit.getScheduler()
                            .runTaskTimer(runtime.environment().plugin(), this::tick, 1, 5);
            Bukkit.getServicesManager()
                    .register(
                            DungeonExperienceApi.class,
                            this,
                            runtime.environment().plugin(),
                            ServicePriority.Normal);
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static void requireThread() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Dungeon experience requires server thread");
    }

    private void selectBackend() {
        HudService shared = Bukkit.getServicesManager().load(HudService.class);
        if (shared == null)
            shared =
                    SharedHudBackend.discover(
                            Bukkit.getPluginManager().getPlugin("ServerFeatures"), backend);
        HudService next =
                shared != null
                        ? shared
                        : Bukkit.getPluginManager().isPluginEnabled("ServerFeatures")
                                ? null
                                : standalone;
        if (next == backend && (next != null || warned)) return;
        for (var value : players.values()) release(value);
        for (var value : waiting.values())
            if (value.sidebar != null) {
                value.sidebar.close();
                value.sidebar = null;
            }
        previews.values().forEach(HudService.Lease::close);
        previews.clear();
        if (observers != null) observers.close();
        backend = next;
        for (var entry : players.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline())
                entry.getValue().instance.refreshDragonHudOwnership(player);
        }
        if (next == null && !warned) {
            warned = true;
            runtime.environment()
                    .plugin()
                    .getLogger()
                    .warning(
                            "ServerFeatures has no coordinated HUD service; dungeon chat/guide remain available. Update ServerFeatures to enable persistent dungeon HUDs.");
        }
    }

    private void tick() {
        if (closed) return;
        ticks += 5;
        if (ticks % 20 == 0) selectBackend();
        standalone.tick(ticks % 20 == 0);
        for (var instance : runtime.activeInstances().getActiveInstances()) {
            if (!(instance instanceof PlayableInstance playable)
                    || playable.getInstanceWorld() == null
                    || playable.hasLoadFailed()) continue;
            if (ticks % 20 == 0) refreshPresentation(playable);
            for (var session : new ArrayList<>(playable.getPlayers())) {
                Player player = session.getPlayer();
                if (player == null || !player.isOnline()) continue;
                if (!players.containsKey(player.getUniqueId())) joined(playable, session);
            }
        }
        for (var entry : new ArrayList<>(runs.entrySet())) {
            try {
                entry.getValue().tick();
            } catch (RuntimeException failure) {
                entry.getValue().close();
                runs.remove(entry.getKey());
                runtime.environment()
                        .logger()
                        .error(
                                "Dungeon experience mechanics stopped for '{}'",
                                entry.getKey().getDungeon().getWorldName(),
                                failure);
            }
        }
        for (var entry : new ArrayList<>(players.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            var session = player == null ? null : runtime.playerSessions().get(player);
            if (player == null
                    || !player.isOnline()
                    || session == null
                    || session.getInstance() != entry.getValue().instance) {
                release(entry.getValue());
                if (session != null && session.getInstance() == null)
                    players.remove(entry.getKey());
                continue;
            }
            try {
                render(player, session, entry.getValue());
            } catch (RuntimeException failure) {
                release(entry.getValue());
                runtime.environment()
                        .logger()
                        .warn(
                                "Could not render dungeon HUD for '{}'",
                                player.getUniqueId(),
                                failure);
            }
        }
        if (observers != null) observers.tick();
        if (ticks % 20 == 0) queueFeedback();
        previews.values().removeIf(lease -> !lease.active());
        exits.values().removeIf(exit -> exit.expires <= nanoTime.getAsLong());
    }

    public ExperienceRun mechanics(PlayableInstance instance) {
        if (!instance.experienceDefinition().hasMechanics()) return null;
        return runs.computeIfAbsent(instance, key -> new ExperienceRun(key, this));
    }

    public void joined(PlayableInstance instance, DungeonPlayerSession session) {
        requireThread();
        Player player = session.getPlayer();
        Participation existing = players.get(player.getUniqueId());
        if (existing != null && existing.instance == instance) return;
        if (existing != null) release(existing);
        exits.remove(player.getUniqueId());
        Participation value = new Participation(instance);
        value.lastLives = instance.getConfig().getInt("players.lives", 0);
        var queued = waiting.remove(player.getUniqueId());
        if (queued != null && queued.sidebar != null) queued.sidebar.close();
        players.put(player.getUniqueId(), value);
        var run = mechanics(instance);
        if (run != null) run.restore(session);
        Bukkit.getScheduler()
                .runTaskLater(
                        runtime.environment().plugin(),
                        () -> {
                            if (closed || session.getInstance() != instance || !player.isOnline())
                                return;
                            if (notification(value, "welcome", "enabled", false))
                                notify(
                                        player,
                                        "welcome",
                                        instance.getDungeon().getDisplayNameComponent());
                            if (incompatible(session, instance)) {
                                notify(player, "incompatible-save", null);
                                player.sendMessage(
                                        action(player, "fresh-start", "/dungeon objectives fresh"));
                            }
                            player.sendMessage(
                                    DungeonUi.detail(
                                            DungeonUi.join(
                                                    action(
                                                            player,
                                                            "ui-guide-action",
                                                            "/dungeon guide"),
                                                    action(
                                                            player,
                                                            "ui-status-action",
                                                            "/dungeon status"),
                                                    action(
                                                            player,
                                                            "ui-leave-short",
                                                            "/dungeon leave"))));
                        },
                        3);
    }

    private void refreshPresentation(PlayableInstance instance) {
        try {
            var next = ExperiencePresentation.resolve(instance.getConfig(), runtime.config());
            presentations.put(instance, next);
            invalidPresentations.remove(instance);
        } catch (IllegalArgumentException failure) {
            if (invalidPresentations.add(instance))
                runtime.environment()
                        .logger()
                        .warn(
                                "Invalid presentation for '{}'; retaining the last valid HUD: {}",
                                instance.getDungeon().getWorldName(),
                                failure.getMessage());
            presentations.putIfAbsent(instance, ExperiencePresentation.defaults());
        }
    }

    private ExperiencePresentation presentation(PlayableInstance instance) {
        if (!presentations.containsKey(instance)) refreshPresentation(instance);
        return presentations.get(instance);
    }

    private boolean enabled(PlayableInstance instance, String path, boolean fallback) {
        return presentation(instance).flags().getOrDefault(path, fallback);
    }

    private String setting(PlayableInstance instance, String path, String fallback) {
        return presentation(instance).text().getOrDefault(path, fallback);
    }

    private String profile(DungeonPlayerSession session, PlayableInstance instance) {
        return presentation(instance).profile(session.experiencePreference("profile", "inherit"));
    }

    private Component text(CommandSender player, String key, Object... values) {
        var replacements = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2)
            replacements.put(values[i].toString(), values[i + 1]);
        return LangUtils.component("experience." + key, player, false, replacements);
    }

    private Component action(Player player, String key, String command) {
        String label =
                switch (key) {
                    case "guide" -> "ui-guide-action";
                    case "leave" -> "ui-leave-short";
                    case "cancel" -> "ui-cancel-action";
                    case "dungeon-info" -> "ui-info-action";
                    case "play-again" -> "ui-replay-action";
                    default -> key;
                };
        NamedTextColor color =
                key.startsWith("leave") || key.equals("ui-leave-short") || key.equals("cancel")
                        ? NamedTextColor.GOLD
                        : NamedTextColor.AQUA;
        return DungeonUi.action(text(player, label).color(color), command, color);
    }

    private HudService.Lease channel(
            Player player, Participation state, HudService.Channel channel) {
        var lease = state.channels.get(channel);
        if (lease == null || !lease.active()) {
            lease =
                    backend.claim(
                            "Dungeons", player.getUniqueId(), channel, HudService.SESSION, LEASE);
            state.channels.put(channel, lease);
        }
        lease.renew(LEASE);
        return lease;
    }

    private void render(Player player, DungeonPlayerSession session, Participation state) {
        var instance = state.instance;
        if (backend == null || !enabled(instance, "enabled", true)) {
            release(state);
            return;
        }
        String profile = profile(session, instance);
        boolean balanced = profile.equals("balanced"), immersive = profile.equals("immersive");
        var run = runs.get(instance);
        if (enabled(instance, "channels.sidebar", true)) {
            if (!immersive) {
                var board =
                        new DungeonSidebar(
                                presentation(instance),
                                player,
                                instance.getDungeon().getDisplayNameComponent());
                String status = stateName(player, state);
                if (!status.equals("playing")) board.row("state", runState(player, state));
                board.placeholder("state", text(player, status));
                board.runDetails(instance);
                if (run != null
                        && (balanced
                                || setting(instance, "widgets.stage", "auto").equals("shown"))) {
                    for (var track : run.definition().tracks().values()) {
                        var stage = run.progress().currentStage(track, player.getUniqueId());
                        if (stage != null) {
                            board.row("stage", LangUtils.trusted(stage.title()));
                            break;
                        }
                    }
                }
                if (run != null
                        && (balanced
                                || setting(instance, "widgets.objective", "auto")
                                        .equals("shown"))) {
                    currentObjective(run, state, player)
                            .ifPresent(objective -> board.row("objective", objective));
                }
                if (instance.isLivesEnabled())
                    board.metric(
                            "lives",
                            instance.getPlayerLives()
                                    .getOrDefault(
                                            player.getUniqueId(),
                                            instance.getConfig().getInt("players.lives")));
                else board.metric("lives", "∞");
                var team =
                        runtime.hasTeamService()
                                ? runtime.teamService().getTeam(player.getUniqueId())
                                : null;
                if (team != null) board.metric("party", team.size());
                var respawn = session.getDungeonRespawn();
                String visibility = setting(instance, "widgets.respawn", "auto");
                if (respawn != null && !visibility.equals("hidden")) {
                    var start = instance.getStartLoc();
                    boolean checkpoint =
                            start != null
                                    && (!java.util.Objects.equals(
                                                    start.getWorld(), respawn.getWorld())
                                            || start.distanceSquared(respawn) >= 0.01);
                    if (visibility.equals("shown")
                            || presentation(instance).rows().isPresent()
                            || balanced && checkpoint)
                        board.metric("respawn", text(player, checkpoint ? "checkpoint" : "spawn"));
                }
                sidebarActions(board, presentation(instance), player);
                channel(player, state, HudService.Channel.SIDEBAR).update(board.frame());
            } else channel(player, state, HudService.Channel.SIDEBAR).update(null);
        } else closeChannel(state, HudService.Channel.SIDEBAR);
        if (enabled(instance, "channels.actionbar", true))
            channel(player, state, HudService.Channel.ACTION_BAR)
                    .update(navigation(player, session, state, run));
        else closeChannel(state, HudService.Channel.ACTION_BAR);
        if (enabled(instance, "channels.bossbar", true)) {
            var bars =
                    immersive
                            ? new ArrayList<HudFrame.Bar>()
                            : new ArrayList<>(run == null ? List.of() : run.bars());
            if (!immersive && bars.isEmpty()) bars.addAll(instance.dragonHudBars());
            if (!immersive
                    && instance.isStarted()
                    && instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0
                    && instance.getTimeLeft() <= 60
                    && instance.getTimeLeft() > 0) {
                bars.addFirst(
                        new HudFrame.Bar(
                                text(
                                        player,
                                        "remaining",
                                        "value",
                                        Component.text(
                                                        clock(instance.getTimeLeft()),
                                                        DungeonSidebar.countdownColor(
                                                                instance.getTimeLeft()))
                                                .decorate(TextDecoration.BOLD)),
                                instance.getTimeLeft() / 60f,
                                instance.getTimeLeft() <= 10
                                        ? BossBar.Color.RED
                                        : BossBar.Color.YELLOW,
                                BossBar.Overlay.NOTCHED_6));
            }
            channel(player, state, HudService.Channel.BOSS_BAR)
                    .update(
                            bars.isEmpty()
                                    ? null
                                    : new HudFrame.Bars(bars.stream().limit(2).toList()));
        } else closeChannel(state, HudService.Channel.BOSS_BAR);
        if (enabled(instance, "channels.titles", true))
            channel(player, state, HudService.Channel.TITLE).update(null);
        else closeChannel(state, HudService.Channel.TITLE);
        if (run != null
                && !state.hintOffered
                && instance.getTimeElapsed() - state.lastProgress >= 120
                && run.progress().views(player.getUniqueId()).stream()
                        .anyMatch(
                                goal ->
                                        !goal.completed()
                                                && !run.definition()
                                                        .objectives()
                                                        .get(goal.id())
                                                        .hint()
                                                        .isBlank())) {
            state.hintOffered = true;
            notify(player, "hint-available", null);
        }
    }

    private void sidebarActions(
            DungeonSidebar board, ExperiencePresentation policy, Player player) {
        if (policy.rows().isPresent() || policy.text().get("widgets.help").equals("shown"))
            board.row("help", text(player, "ui-hud-tip").color(NamedTextColor.GRAY));
        if (policy.rows().isPresent() || policy.text().get("widgets.leave").equals("shown"))
            board.row("leave", Component.text("/dungeon leave", NamedTextColor.GRAY));
    }

    private HudFrame.ActionBar navigation(
            Player player, DungeonPlayerSession session, Participation state, ExperienceRun run) {
        if (session.experiencePreference("navigation", "false").equals("true")
                && state.pinned != null
                && run != null) {
            var goal = run.definition().objectives().get(state.pinned);
            if (goal != null
                    && goal.point() != null
                    && run.progress().active(goal.id(), player.getUniqueId())
                    && run.progress().visible(goal.id(), player.getUniqueId())
                    && !run.progress().completed(goal.id(), player.getUniqueId())) {
                return new HudFrame.ActionBar(waypoint(player, goal));
            }
        }
        return null;
    }

    Component waypoint(Player player, ExperienceDefinition.Objective goal) {
        var point = goal.point();
        var from = player.getLocation();
        double dx = point.x() - from.getX(),
                dy = point.y() - from.getY(),
                dz = point.z() - from.getZ();
        double horizontal = Math.hypot(dx, dz);
        double distance = Math.hypot(horizontal, dy);
        Component title = LangUtils.trusted(goal.title());
        if (distance <= point.radius())
            return DungeonUi.join(
                    Component.text("◆ ", NamedTextColor.GREEN)
                            .append(title.colorIfAbsent(NamedTextColor.WHITE)),
                    text(player, "ui-waypoint-here").color(NamedTextColor.GREEN));
        double relative = Math.toDegrees(Math.atan2(-dx, dz)) - from.getYaw();
        String arrow =
                horizontal < 1
                        ? "◆"
                        : List.of("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")
                                .get(Math.floorMod((int) Math.round(relative / 45), 8));
        Component range = Component.text(Math.round(distance) + " m", NamedTextColor.WHITE);
        if (Math.abs(dy) >= 3 && Math.abs(dy) > horizontal / 2)
            range =
                    range.append(Component.space())
                            .append(
                                    text(player, dy > 0 ? "ui-waypoint-above" : "ui-waypoint-below")
                                            .color(NamedTextColor.AQUA));
        return DungeonUi.join(
                Component.text(arrow + " ", NamedTextColor.AQUA)
                        .append(title.colorIfAbsent(NamedTextColor.WHITE)),
                range);
    }

    static String clock(int seconds) {
        return "%d:%02d".formatted(Math.max(0, seconds) / 60, Math.max(0, seconds) % 60);
    }

    private String stateName(Player player, Participation state) {
        if (state.outcome != null) return state.outcome;
        if (player.getGameMode() == GameMode.SPECTATOR) return "spectating";
        return state.instance.isStarted() ? "playing" : "lobby";
    }

    private void closeChannel(Participation state, HudService.Channel channel) {
        var lease = state.channels.remove(channel);
        if (lease != null) lease.close();
    }

    private void release(Participation state) {
        var steps = new ArrayList<Runnable>();
        state.channels.values().forEach(lease -> steps.add(lease::close));
        state.feedback.values().forEach(lease -> steps.add(lease::close));
        state.channels.clear();
        state.feedback.clear();
        CleanupSequence.run(steps);
    }

    public boolean managesBossBars(Player player) {
        return backend != null && players.containsKey(player.getUniqueId());
    }

    public void actionBar(Player player, Component message, int seconds, int priority) {
        var state = players.get(player.getUniqueId());
        if (state != null
                && (!enabled(state.instance, "enabled", true)
                        || !enabled(state.instance, "channels.actionbar", true))) return;
        feedback(
                player,
                HudService.Channel.ACTION_BAR,
                new HudFrame.ActionBar(message),
                Duration.ofSeconds(Math.max(1, seconds)),
                priority);
    }

    public boolean title(Player player, HudFrame.Title title) {
        var state = players.get(player.getUniqueId());
        if (state == null) return false;
        var session = runtime.playerSessions().get(player);
        if (!enabled(state.instance, "enabled", true)
                || session.experiencePreference("titles", "true").equals("false")
                || !enabled(state.instance, "channels.titles", true)) return true;
        feedback(
                player,
                HudService.Channel.TITLE,
                title,
                title.fadeIn().plus(title.stay()).plus(title.fadeOut()).plusMillis(50),
                HudService.CONTEXT);
        return backend != null;
    }

    private void feedback(
            Player player,
            HudService.Channel channel,
            HudFrame frame,
            Duration lifetime,
            int priority) {
        var state = players.get(player.getUniqueId());
        if (state == null || backend == null) {
            if (frame instanceof HudFrame.ActionBar bar && state == null)
                player.sendActionBar(bar.text());
            return;
        }
        var old = state.feedback.remove(channel);
        if (old != null) old.close();
        var lease = backend.claim("Dungeons", player.getUniqueId(), channel, priority, lifetime);
        lease.update(frame);
        state.feedback.put(channel, lease);
    }

    private boolean notification(
            Participation state, String key, String channel, boolean fallback) {
        return enabled(state.instance, "notifications." + key + "." + channel, fallback);
    }

    public void record(Player player, Component message) {
        var state = players.get(player.getUniqueId());
        if (state != null) {
            if (state.history.size() == 20) state.history.removeFirst();
            state.history.addLast(message);
        }
    }

    public void notify(Player player, String key, Component value) {
        deliverNotification(
                player,
                key,
                text(player, key, "value", value == null ? Component.empty() : value),
                value);
    }

    private void deliverNotification(Player player, String key, Component message) {
        deliverNotification(player, key, message, null);
    }

    private void deliverNotification(
            Player player, String key, Component message, Component subject) {
        var state = players.get(player.getUniqueId());
        if (state != null && !notification(state, key, "enabled", true)) return;
        boolean transientNotice =
                java.util.Set.of("started", "objective-progress", "wave-started", "boss-phase")
                        .contains(key);
        if (state == null
                || notification(
                        state,
                        key,
                        "chat",
                        !transientNotice
                                || backend == null
                                || !enabled(state.instance, "enabled", true)
                                || !enabled(state.instance, "channels.actionbar", true)))
            player.sendMessage(LangUtils.notice(message));
        if (state != null) {
            if (notification(state, key, "actionbar", transientNotice))
                actionBar(
                        player,
                        message,
                        key.equals("objective-progress") ? 2 : 3,
                        HudService.CONTEXT);
            if (notification(
                    state,
                    key,
                    "title",
                    java.util.Set.of("stage-started", "encounter-started").contains(key))) {
                boolean named =
                        subject != null
                                && !subject.equals(Component.empty())
                                && (key.equals("stage-started") || key.equals("encounter-started"));
                title(
                        player,
                        new HudFrame.Title(
                                named ? subject.decorate(TextDecoration.BOLD) : message,
                                named
                                        ? text(player, "ui-title-" + key).color(NamedTextColor.GOLD)
                                        : Component.empty(),
                                Duration.ofMillis(200),
                                Duration.ofSeconds(2),
                                Duration.ofMillis(300)));
            }
            var session = runtime.playerSessions().get(player);
            if (session != null
                    && session.experiencePreference("sounds", "true").equals("true")
                    && notification(
                            state,
                            key,
                            "sound",
                            java.util.Set.of("objective-completed", "completed", "saved")
                                    .contains(key)))
                player.playSound(
                        player.getLocation(),
                        "minecraft:entity.experience_orb.pickup",
                        0.35f,
                        1.2f);
        }
        if (state != null
                && !key.equals("settings-updated")
                && (!key.equals("objective-progress") || notification(state, key, "chat", false))) {
            record(player, message);
        }
    }

    public void changed(PlayableInstance instance, ObjectiveProgress.Change change) {
        changed(instance, change, null);
    }

    void changed(PlayableInstance instance, ObjectiveProgress.Change change, Component eventTitle) {
        for (var session : new ArrayList<>(instance.getPlayers())) {
            Player player = session.getPlayer();
            UUID id = player.getUniqueId();
            if (!player.isOnline() || change.player() != null && !change.player().equals(id))
                continue;
            var state = players.get(id);
            if (state == null) continue;
            state.lastProgress = instance.getTimeElapsed();
            if (change.kind().equals("stage-started")) state.hintOffered = false;
            var run = runs.get(instance);
            var goal =
                    run != null && change.kind().startsWith("objective-")
                            ? run.definition().objectives().get(change.id())
                            : null;
            if (goal != null && !run.progress().visible(goal.id(), id)) continue;
            Component label =
                    goal == null ? Component.text(change.id()) : LangUtils.trusted(goal.title());
            if (goal == null && run != null && change.id().contains("/")) {
                String[] parts = change.id().split("/", 2);
                var track = run.definition().tracks().get(parts[0]);
                if (track != null)
                    for (var stage : track.stages())
                        if (stage.id().equals(parts[1])) label = LangUtils.trusted(stage.title());
            }
            if (change.kind().equals("objective-progress") && goal != null && run != null)
                deliverNotification(
                        player,
                        change.kind(),
                        text(
                                player,
                                "progress",
                                "value",
                                label,
                                "count",
                                change.value(),
                                "target",
                                run.progress().target(goal.id())));
            else if (!change.kind().equals("objective-progress"))
                notify(player, change.kind(), eventTitle == null ? label : eventTitle);
            subscriptions.publish(
                    id,
                    new Change(
                            instance.getDungeon().getDungeonId(),
                            change.kind(),
                            change.id(),
                            change.value()),
                    failure ->
                            runtime.environment()
                                    .logger()
                                    .warn("Dungeon experience subscriber failed", failure));
        }
    }

    public void failed(PlayableInstance instance, String outcome) {
        for (var entry : players.entrySet())
            if (entry.getValue().instance == instance && entry.getValue().outcome == null) {
                entry.getValue().outcome = outcome;
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) notify(player, outcome, null);
            }
    }

    public void observationEnded(UUID player) {
        if (observers != null) observers.release(player);
    }

    public void waitingChanged() {
        queueFeedback();
    }

    public boolean isParticipating(Player player, PlayableInstance run) {
        var participation = players.get(player.getUniqueId());
        return participation != null && participation.instance == run;
    }

    public boolean completed(UUID player, PlayableInstance instance) {
        var state = players.get(player);
        return state != null && state.instance == instance && "completed".equals(state.outcome);
    }

    public void reward(Player player, Component name, int amount) {
        var state = players.get(player.getUniqueId());
        if (state != null && state.rewards.size() < 50)
            state.rewards.add(text(player, "reward", "value", name, "amount", amount));
    }

    public void finished(PlayableInstance instance, DungeonPlayerSession session) {
        var state = players.get(session.getPlayer().getUniqueId());
        if (state == null || state.instance != instance || state.outcome != null) return;
        state.outcome = "completed";
        notify(session.getPlayer(), "completed", instance.getDungeon().getDisplayNameComponent());
        title(
                session.getPlayer(),
                new HudFrame.Title(
                        text(session.getPlayer(), "victory"),
                        instance.getDungeon().getDisplayNameComponent(),
                        Duration.ofMillis(250),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(500)));
    }

    public void departed(PlayableInstance instance, DungeonPlayerSession session) {
        Player player = session.getPlayer();
        UUID id = player.getUniqueId();
        var state = players.get(id);
        if (state == null || state.instance != instance) return;
        players.remove(id);
        release(state);
        exits.remove(id);
        if (!player.isOnline()) return;

        var report = new ArrayList<Component>();
        report.add(
                DungeonUi.heading(
                        DungeonUi.join(
                                instance.getDungeon().getDisplayNameComponent(),
                                text(player, "ui-run-summary").color(NamedTextColor.GRAY))));
        if (state.outcome != null) report.add(runState(player, state));
        report.add(
                DungeonUi.join(
                        stat(
                                player,
                                "elapsed",
                                Component.text(clock(instance.getTimeElapsed() - state.entered))),
                        stat(player, "deaths", Component.text(state.lostLives))));
        var mechanics = runs.get(instance);
        if (mechanics != null) {
            long completed =
                    mechanics.definition().objectives().values().stream()
                            .filter(
                                    goal ->
                                            mechanics.progress().visible(goal.id(), id)
                                                    && mechanics
                                                            .progress()
                                                            .completed(goal.id(), id))
                            .count();
            if (!mechanics.definition().objectives().isEmpty())
                report.add(text(player, "objectives-finished", "value", completed));
            if (state.kills > 0) report.add(text(player, "tracked-kills", "value", state.kills));
        }
        report.addAll(state.rewards);
        var cooldown = instance.getDungeon().getAccessCooldown(player);
        if (cooldown != null)
            report.add(
                    text(
                            player,
                            "next-entry",
                            "value",
                            clock(
                                    (int)
                                            Math.min(
                                                    Integer.MAX_VALUE,
                                                    Math.max(
                                                                    0,
                                                                    cooldown.getTime()
                                                                            - System
                                                                                    .currentTimeMillis())
                                                            / 1000))));
        player.sendMessage(report.getFirst());
        report.stream().skip(1).forEach(line -> player.sendMessage(DungeonUi.detail(line)));

        String map = instance.getDungeon().getWorldName();
        String difficulty =
                instance.getDifficulty() == null
                                || !player.hasPermission("dungeons.play.difficulty")
                        ? ""
                        : " " + instance.getDifficulty().getNamespace();
        player.sendMessage(
                DungeonUi.detail(
                        DungeonUi.join(
                                action(player, "dungeon-info", "/dungeon info " + map),
                                action(
                                        player,
                                        "play-again",
                                        "/dungeon play " + map + difficulty))));
    }

    public void dispose(PlayableInstance instance) {
        presentations.remove(instance);
        invalidPresentations.remove(instance);
        var run = runs.remove(instance);
        if (run != null) run.close();
        for (var entry : new ArrayList<>(players.entrySet()))
            if (entry.getValue().instance == instance) {
                release(entry.getValue());
                players.remove(entry.getKey());
                exits.remove(entry.getKey());
            }
    }

    private boolean incompatible(DungeonPlayerSession session, PlayableInstance instance) {
        return !instance.isTestPlay()
                && session.incompatibleExperienceCheckpoint(
                        instance.getDungeon().getDungeonId().toString(),
                        instance.experienceDefinition(),
                        instance.getDifficulty() == null
                                ? ""
                                : instance.getDifficulty().getNamespace());
    }

    public void savePoint(DungeonPlayerSession session) {
        var state = players.get(session.getPlayer().getUniqueId());
        if (state == null || state.instance.isTestPlay()) return;
        if (incompatible(session, state.instance)) {
            notify(session.getPlayer(), "incompatible-save", null);
            return;
        }
        var run = runs.get(state.instance);
        session.setExperienceSavePoint(
                session.getDungeonRespawn(),
                state.instance.getDungeon().getWorldName(),
                state.instance.getDungeon().getDungeonId().toString(),
                state.instance.experienceDefinition().fingerprint(),
                state.instance.getDifficulty() == null
                        ? ""
                        : state.instance.getDifficulty().getNamespace(),
                run == null ? Map.of() : run.progress().save(session.getPlayer().getUniqueId()),
                run == null
                        ? List.of()
                        : run.progress().savedReveals(session.getPlayer().getUniqueId()),
                run == null
                        ? List.of()
                        : run.progress().savedTracks(session.getPlayer().getUniqueId()));
        notify(session.getPlayer(), "saved", null);
    }

    public void briefing(
            CommandSender viewer, nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition dungeon) {
        boolean available = dungeon.isEnabled();
        viewer.sendMessage(
                DungeonUi.join(
                        DungeonUi.heading(dungeon.getDisplayNameComponent()),
                        runIndicator(
                                text(viewer, available ? "ui-available" : "ui-unavailable"),
                                available ? NamedTextColor.GREEN : NamedTextColor.RED)));
        String description = dungeon.getConfig().getString("experience.description", "");
        if (!description.isBlank())
            viewer.sendMessage(DungeonUi.detail(LangUtils.trusted(description)));
        int lives = dungeon.getConfig().getInt("players.lives", 0);
        int minutes = dungeon.getConfig().getInt("runs.time_limit_minutes", 0);
        var limits = new ArrayList<Component>();
        limits.add(stat(viewer, "party", Component.text(dungeon.getMaxTeamSize())));
        if (lives > 0) limits.add(stat(viewer, "lives", Component.text(lives)));
        if (minutes > 0) limits.add(stat(viewer, "time", Component.text(clock(minutes * 60))));
        if (lives == 0 && minutes == 0)
            limits.add(text(viewer, "ui-unlimited-run").color(NamedTextColor.GRAY));
        viewer.sendMessage(DungeonUi.detail(DungeonUi.join(limits)));
        if (!dungeon.getAccessKeys().isEmpty())
            viewer.sendMessage(
                    DungeonUi.detail(
                            text(
                                            viewer,
                                            dungeon.getConfig()
                                                            .getBoolean(
                                                                    "access.keys.consume_on_entry",
                                                                    true)
                                                    ? "ui-key-spent"
                                                    : "ui-key-kept")
                                    .color(NamedTextColor.GOLD)));
        if (lives > 0)
            viewer.sendMessage(
                    DungeonUi.detail(
                            text(
                                    viewer,
                                    dungeon.getConfig()
                                                    .getBoolean("players.spectate_on_death", true)
                                            ? "entry-spectator"
                                            : "entry-eliminated")));
        if (!dungeon.getConfig().getBoolean("players.keep_on_entry.inventory", true))
            viewer.sendMessage(DungeonUi.detail(text(viewer, "inventory-restored")));
        if (dungeon.isAccessCooldownEnabled())
            viewer.sendMessage(DungeonUi.detail(text(viewer, "entry-cooldown")));
        if (available && viewer.hasPermission("dungeons.play"))
            viewer.sendMessage(
                    DungeonUi.detail(
                            DungeonUi.action(
                                    text(viewer, "ui-enter-action").color(NamedTextColor.AQUA),
                                    "/dungeon play " + dungeon.getWorldName(),
                                    NamedTextColor.AQUA)));
    }

    private Component stat(CommandSender viewer, String key, Component value) {
        return DungeonUi.metric(
                text(viewer, "ui-" + key + "-label").color(NamedTextColor.GRAY),
                value.color(NamedTextColor.WHITE));
    }

    private void runStatus(Player player, Participation state) {
        var instance = state.instance;
        player.sendMessage(DungeonUi.heading(instance.getDungeon().getDisplayNameComponent()));
        var facts = runFacts(player, state);
        for (int offset = 0; offset < facts.size(); offset += 3)
            player.sendMessage(
                    DungeonUi.detail(
                            DungeonUi.join(
                                    facts.subList(offset, Math.min(offset + 3, facts.size())))));
        currentObjective(runs.get(instance), state, player)
                .ifPresent(objective -> player.sendMessage(DungeonUi.detail(objective)));
        player.sendMessage(
                DungeonUi.detail(
                        DungeonUi.join(
                                action(player, "ui-guide-action", "/dungeon guide"),
                                action(player, "ui-leave-short", "/dungeon leave"))));
    }

    private List<Component> runFacts(Player player, Participation state) {
        var instance = state.instance;
        var facts = new ArrayList<Component>();
        if (instance.getDifficulty() != null)
            facts.add(instance.getDifficulty().getDisplayNameComponent());
        if (!stateName(player, state).equals("playing")) facts.add(runState(player, state));
        if (instance.isStarted())
            facts.add(text(player, "ui-hud-run-time", "value", clock(instance.getTimeElapsed())));
        if (instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0)
            facts.add(text(player, "ui-hud-remaining", "value", clock(instance.getTimeLeft())));
        if (instance.isLivesEnabled())
            facts.add(
                    stat(
                            player,
                            "lives",
                            Component.text(
                                    instance.getPlayerLives()
                                            .getOrDefault(
                                                    player.getUniqueId(),
                                                    instance.getConfig()
                                                            .getInt("players.lives")))));
        var team =
                runtime.hasTeamService()
                        ? runtime.teamService().getTeam(player.getUniqueId())
                        : null;
        if (team != null) facts.add(text(player, "ui-hud-party", "value", team.size()));
        return facts;
    }

    private Optional<Component> currentObjective(
            ExperienceRun run, Participation state, Player player) {
        if (run == null || state.outcome != null) return Optional.empty();
        ObjectiveProgress.View next = null;
        for (var goal : run.progress().views(player.getUniqueId())) {
            if (goal.completed()) continue;
            if (next == null) next = goal;
            if (goal.id().equals(state.pinned)) {
                next = goal;
                break;
            }
        }
        if (next == null) return Optional.empty();
        Component row =
                Component.text(next.id().equals(state.pinned) ? "◆ " : "◇ ", NamedTextColor.AQUA)
                        .append(LangUtils.trusted(next.title()));
        if (next.target() > 1)
            row =
                    row.append(
                            Component.text(
                                    "  " + next.progress() + "/" + next.target(),
                                    NamedTextColor.GOLD));
        return Optional.of(row);
    }

    private Component runState(Player player, Participation state) {
        String key = stateName(player, state);
        NamedTextColor color =
                switch (key) {
                    case "playing", "completed" -> NamedTextColor.GREEN;
                    case "lobby", "spectating" -> NamedTextColor.GOLD;
                    default -> NamedTextColor.RED;
                };
        return runIndicator(text(player, key), color);
    }

    private static Component runIndicator(Component state, NamedTextColor color) {
        return Component.text("● ", color).append(state.color(color));
    }

    private void runRules(Player player, PlayableInstance instance) {
        player.sendMessage(DungeonUi.heading(text(player, "ui-rules-heading")));
        player.sendMessage(DungeonUi.detail(text(player, "spawn-explanation")));
        if (!instance.getConfig().getBoolean("players.keep_on_entry.inventory", true))
            player.sendMessage(DungeonUi.detail(text(player, "inventory-restored")));
    }

    private String exitSignature(DungeonPlayerSession session, Participation state) {
        var dungeon = state.instance.getDungeon();
        return state.instance.getInstanceId()
                + ":"
                + state.outcome
                + ":"
                + state.instance.getPlayerLives().get(session.getPlayer().getUniqueId())
                + ":"
                + dungeon.isCooldownOnLeave()
                + ":"
                + dungeon.isAccessCooldownEnabled()
                + ":"
                + dungeon.shouldApplyAccessCooldown(
                        state.instance, session.getPlayer().getUniqueId())
                + ":"
                + state.instance.getConfig().getBoolean("access.keys.consume_on_entry", true)
                + ":"
                + state.instance.getConfig().getList("access.keys.items", List.of())
                + ":"
                + session.getDungeonSavePoint(dungeon.getWorldName());
    }

    /**
     * Returns true only for an immediate exit or a matching, unexpired consequence confirmation.
     */
    public boolean requestExit(Player player) {
        requireThread();
        UUID id = player.getUniqueId();
        var state = players.get(id);
        var session = runtime.playerSessions().get(player);
        Exit pending = exits.remove(id);
        if (session == null || state == null) return true;
        var instance = state.instance;
        if (session.getInstance() != instance) return session.getInstance() == null;
        if (instance.isTestPlay()
                || state.outcome != null
                || !enabled(instance, "confirm_exit", true)) return true;

        var dungeon = instance.getDungeon();
        boolean cooldown =
                dungeon.isAccessCooldownEnabled()
                        && dungeon.isCooldownOnLeave()
                        && dungeon.shouldApplyAccessCooldown(instance, id);
        boolean consumesKey =
                !instance.getConfig().getList("access.keys.items", List.of()).isEmpty()
                        && instance.getConfig().getBoolean("access.keys.consume_on_entry", true);
        if (!instance.isStarted() && !cooldown && !consumesKey) return true;

        String signature = exitSignature(session, state);
        long now = nanoTime.getAsLong();
        if (pending != null
                && pending.participation == state
                && pending.signature.equals(signature)
                && pending.expires - now > 0) return true;
        exits.put(id, new Exit(state, signature, now + Duration.ofSeconds(10).toNanos()));
        player.sendMessage(
                DungeonUi.heading(text(player, "ui-leave-heading").color(NamedTextColor.GOLD)));
        player.sendMessage(
                DungeonUi.detail(
                        text(
                                player,
                                session.getDungeonSavePoint(dungeon.getWorldName()) == null
                                        ? "ui-leave-unsaved"
                                        : "ui-leave-progress")));
        var costs = new ArrayList<Component>();
        if (cooldown) costs.add(text(player, "ui-leave-cooldown").color(NamedTextColor.GOLD));
        if (consumesKey) costs.add(text(player, "ui-leave-key").color(NamedTextColor.GOLD));
        if (!costs.isEmpty()) player.sendMessage(DungeonUi.detail(DungeonUi.join(costs)));
        player.sendMessage(
                DungeonUi.detail(
                        DungeonUi.join(
                                DungeonUi.action(
                                        text(player, "ui-leave-action").color(NamedTextColor.GOLD),
                                        "/dungeon leave",
                                        NamedTextColor.GOLD),
                                text(player, "ui-leave-repeat").color(NamedTextColor.GRAY))));
        return false;
    }

    public void saveDraft(Player player) {
        editor.saveDraft(player);
    }

    public boolean edit(Player player, String[] args) {
        return editor.handle(player, args);
    }

    public void preview(
            Player player, nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition dungeon) {
        if (backend == null) {
            notify(player, "editor-preview", null);
            return;
        }
        var lines = new ArrayList<Component>();
        lines.add(text(player, "ui-hud-run-time", "value", clock(0)));
        int time = dungeon.getConfig().getInt("runs.time_limit_minutes", 0);
        int lives = dungeon.getConfig().getInt("players.lives", 0);
        if (time > 0) lines.add(text(player, "ui-hud-remaining", "value", clock(time * 60)));
        if (lives > 0) lines.add(text(player, "ui-hud-lives", "value", lives));
        lines.add(Component.empty());
        lines.add(text(player, "ui-hud-tip").color(NamedTextColor.GRAY));
        var previous = previews.remove(player.getUniqueId());
        if (previous != null) previous.close();
        var lease =
                backend.claim(
                        "Dungeons",
                        player.getUniqueId(),
                        HudService.Channel.SIDEBAR,
                        HudService.CONTEXT,
                        Duration.ofSeconds(10));
        lease.update(
                new HudFrame.Sidebar(DungeonUi.heading(dungeon.getDisplayNameComponent()), lines));
        previews.put(player.getUniqueId(), lease);
        notify(player, "editor-preview", null);
    }

    public boolean handle(Player player, String[] args) {
        if (args.length == 0) return false;
        String command = args[0].toLowerCase(Locale.ROOT);
        if (!List.of("guide", "status", "objectives", "hud", "spectate").contains(command))
            return false;
        var state = players.get(player.getUniqueId());
        var session = runtime.playerSessions().get(player);
        if (session == null) {
            notify(player, "not-participating", null);
            return true;
        }
        if (command.equals("spectate")) {
            var instance =
                    state != null
                            ? state.instance
                            : session.getObservingInstance() instanceof PlayableInstance observed
                                    ? observed
                                    : null;
            Player target = args.length == 2 ? Bukkit.getPlayerExact(args[1]) : null;
            if (instance == null
                    || player.getGameMode() != GameMode.SPECTATOR
                    || target == null
                    || !player.canSee(target)
                    || instance.getLivingPlayers().stream()
                            .noneMatch(member -> member.getPlayer() == target)) {
                LangUtils.sendMessage(player, "experience.spectate-invalid-target");
                return true;
            }
            player.setSpectatorTarget(target);
            return true;
        }
        if (command.equals("hud")) {
            if (args.length == 1) {
                player.sendMessage(DungeonUi.heading(text(player, "ui-hud-heading")));
                String selected = session.experiencePreference("profile", "inherit");
                for (String profile : List.of("balanced", "minimal", "immersive", "inherit")) {
                    boolean active = profile.equals(selected);
                    Component label =
                            text(player, profile.equals("inherit") ? "ui-inherit" : profile);
                    if (active) label = label.append(Component.text(" ✓"));
                    player.sendMessage(
                            DungeonUi.detail(
                                    DungeonUi.join(
                                            DungeonUi.action(
                                                    label,
                                                    "/dungeon hud " + profile,
                                                    active
                                                            ? NamedTextColor.GREEN
                                                            : NamedTextColor.AQUA),
                                            text(player, "ui-menu-" + profile)
                                                    .color(NamedTextColor.GRAY))));
                }
                var toggles = new ArrayList<Component>();
                for (String toggle : List.of("sounds", "titles", "navigation")) {
                    boolean on =
                            session.experiencePreference(
                                            toggle, toggle.equals("navigation") ? "false" : "true")
                                    .equals("true");
                    Component label =
                            text(player, "ui-toggle-" + toggle)
                                    .append(Component.text(on ? " ✓" : " ×"));
                    toggles.add(
                            DungeonUi.action(
                                    label,
                                    "/dungeon hud " + toggle + " " + !on,
                                    on ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                }
                player.sendMessage(DungeonUi.detail(DungeonUi.join(toggles)));
                return true;
            }
            String option = args[1].toLowerCase(Locale.ROOT);
            if (args.length == 2
                    && List.of("balanced", "minimal", "immersive", "inherit").contains(option)) {
                session.setExperiencePreference("profile", option);
            } else if (args.length == 3
                    && List.of("sounds", "titles", "navigation").contains(option)
                    && List.of("true", "false").contains(args[2].toLowerCase(Locale.ROOT))) {
                session.setExperiencePreference(option, args[2].toLowerCase(Locale.ROOT));
            } else {
                LangUtils.sendMessage(player, "experience.hud-invalid-option");
                return true;
            }
            Component saved = text(player, "ui-settings-saved").color(NamedTextColor.GREEN);
            if (state != null
                    && backend != null
                    && !notification(state, "settings-updated", "chat", false)) {
                if (notification(state, "settings-updated", "enabled", true)
                        && notification(state, "settings-updated", "actionbar", true))
                    actionBar(player, saved, 2, HudService.CONTEXT);
            } else deliverNotification(player, "settings-updated", saved);
            return true;
        }
        if (state == null
                && observers != null
                && observers.view(player.getUniqueId()).isPresent()) {
            var observed = observers.view(player.getUniqueId()).orElseThrow();
            player.sendMessage(DungeonUi.heading(observed.title()));
            player.sendMessage(
                    DungeonUi.detail(
                            DungeonUi.join(
                                    runIndicator(text(player, "observing"), NamedTextColor.AQUA),
                                    action(player, "leave", "/dungeon leave"))));
            return true;
        }
        if (state == null) {
            var pending = waitingSnapshot(session);
            if (pending.isPresent()) {
                var snapshot = pending.get();
                player.sendMessage(DungeonUi.heading(snapshot.title()));
                var details = new ArrayList<Component>();
                details.add(
                        runIndicator(
                                text(
                                        player,
                                        snapshot.state().equals("preparing")
                                                ? "preparing-label"
                                                : "queue-label"),
                                NamedTextColor.GOLD));
                if (snapshot.elapsedSeconds() > 0)
                    details.add(
                            text(
                                    player,
                                    "ui-hud-elapsed",
                                    "value",
                                    clock(snapshot.elapsedSeconds())));
                details.add(action(player, "cancel", "/dungeon leave"));
                player.sendMessage(DungeonUi.detail(DungeonUi.join(details)));
                return true;
            }
            notify(player, "not-participating", null);
            return true;
        }
        var run = runs.get(state.instance);
        if (command.equals("objectives")) {
            if (args.length == 2 && args[1].equals("fresh")) {
                if (!incompatible(session, state.instance)) {
                    LangUtils.sendMessage(player, "experience.no-incompatible-save");
                    return true;
                }
                session.discardExperienceCheckpoint(
                        state.instance.getDungeon().getWorldName(),
                        state.instance.getDungeon().getDungeonId().toString());
                if (run != null) run.progress().clearPlayer(player.getUniqueId());
                notify(player, "fresh-confirmed", null);
                return true;
            }
            var views =
                    run == null
                            ? List.<ObjectiveProgress.View>of()
                            : run.progress().views(player.getUniqueId());
            if (args.length == 3 && args[1].equals("pin")) {
                var goal = views.stream().filter(view -> view.id().equals(args[2])).findFirst();
                if (goal.isEmpty()) {
                    LangUtils.sendMessage(player, "experience.objective-not-visible");
                } else if (goal.get().completed()) {
                    LangUtils.sendMessage(player, "experience.ui-already-complete");
                } else {
                    state.pinned = args[2];
                    Component confirmation =
                            text(
                                    player,
                                    "ui-pinned",
                                    "value",
                                    LangUtils.trusted(goal.get().title()));
                    if (run.definition().objectives().get(args[2]).point() != null
                            && backend != null
                            && enabled(state.instance, "enabled", true)
                            && enabled(state.instance, "channels.actionbar", true)
                            && !session.experiencePreference("navigation", "false").equals("true"))
                        confirmation =
                                DungeonUi.join(
                                        confirmation,
                                        DungeonUi.action(
                                                text(player, "ui-enable-navigation"),
                                                "/dungeon hud navigation true",
                                                NamedTextColor.AQUA));
                    player.sendMessage(DungeonUi.detail(confirmation));
                }
                return true;
            }
            boolean hintsOnly = args.length == 2 && args[1].equals("hint");
            if (views.isEmpty() && !hintsOnly) {
                notify(player, "exploration", null);
                return true;
            }
            boolean hasHints = false;
            if (!hintsOnly) player.sendMessage(DungeonUi.heading(text(player, "objectives")));
            for (var goal : views) {
                var definition = run.definition().objectives().get(goal.id());
                if (!goal.completed() && !definition.hint().isBlank()) {
                    hasHints = true;
                    if (hintsOnly) {
                        player.sendMessage(DungeonUi.heading(LangUtils.trusted(goal.title())));
                        player.sendMessage(DungeonUi.detail(LangUtils.trusted(definition.hint())));
                    }
                }
                if (!hintsOnly)
                    player.sendMessage(
                            DungeonUi.detail(
                                    objectiveRow(
                                            player,
                                            goal,
                                            definition,
                                            goal.id().equals(state.pinned))));
            }
            if (hintsOnly && !hasHints)
                player.sendMessage(DungeonUi.detail(text(player, "ui-no-hints")));
            if (!hintsOnly && hasHints)
                player.sendMessage(
                        DungeonUi.detail(
                                action(player, "ui-view-hints", "/dungeon objectives hint")));
            return true;
        }
        if (command.equals("status")) {
            runStatus(player, state);
            return true;
        }
        guide(player, state, session);
        return true;
    }

    private Component objectiveRow(
            Player player,
            ObjectiveProgress.View goal,
            ExperienceDefinition.Objective definition,
            boolean pinned) {
        Component title = LangUtils.trusted(goal.title());
        NamedTextColor color = goal.completed() ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        Component row =
                Component.text(goal.completed() ? "✓ " : pinned ? "◆ " : "◇ ", color).append(title);
        if (goal.target() > 1)
            row =
                    row.append(
                            Component.text(
                                    "  " + goal.progress() + "/" + goal.target(),
                                    NamedTextColor.GOLD));
        Component hover =
                definition.description().isBlank()
                        ? Component.empty()
                        : LangUtils.trusted(definition.description());
        if (!goal.completed() && !pinned) {
            Component instruction = text(player, "ui-click-pin").color(NamedTextColor.AQUA);
            hover =
                    hover.equals(Component.empty())
                            ? instruction
                            : hover.append(Component.newline()).append(instruction);
            row = row.clickEvent(ClickEvent.runCommand("/dungeon objectives pin " + goal.id()));
        }
        if (!hover.equals(Component.empty())) row = row.hoverEvent(hover);
        return row;
    }

    private void guide(Player player, Participation state, DungeonPlayerSession session) {
        showGuidePage(player, state, session, false);
    }

    private void showGuidePage(
            Player player, Participation state, DungeonPlayerSession session, boolean settings) {
        var window =
                runtime.guiService()
                        .createWindow(
                                "experience_" + player.getUniqueId(),
                                18,
                                ComponentUtils.serialize(
                                        text(player, settings ? "ui-hud-heading" : "guide-title")));
        window.setAccessCheck(
                viewer -> viewer == player && players.get(player.getUniqueId()) == state);
        MenuFrame.divideNavigation(window);
        var instance = state.instance;
        var overview =
                new Button(
                        "overview",
                        Material.RECOVERY_COMPASS,
                        ComponentUtils.serialize(
                                instance.getDungeon()
                                        .getDisplayNameComponent()
                                        .decorate(TextDecoration.BOLD)));
        runFacts(player, state).forEach(fact -> overview.addLore(ComponentUtils.serialize(fact)));
        currentObjective(runs.get(instance), state, player)
                .ifPresent(objective -> overview.addLore(ComponentUtils.serialize(objective)));
        overview.addLore(
                ComponentUtils.serialize(
                        text(player, "ui-open-status").color(NamedTextColor.AQUA)));
        overview.addAction(
                "status",
                event -> {
                    player.closeInventory();
                    runStatus(player, state);
                });
        window.addButton(0, overview);
        if (settings) {
            String selected = session.experiencePreference("profile", "inherit");
            var profiles = List.of("balanced", "minimal", "immersive", "inherit");
            var materials =
                    List.of(
                            Material.MAP,
                            Material.FEATHER,
                            Material.ENDER_EYE,
                            Material.COMPARATOR);
            for (int i = 0; i < profiles.size(); i++) {
                String profile = profiles.get(i);
                var button =
                        guideButton(
                                player,
                                profile.equals("inherit") ? "ui-inherit" : profile,
                                materials.get(i),
                                "ui-menu-" + profile,
                                profile.equals(selected)
                                        ? NamedTextColor.GREEN
                                        : NamedTextColor.WHITE);
                if (profile.equals("inherit"))
                    button.addLore(
                            ComponentUtils.serialize(
                                    text(
                                            player,
                                            "ui-effective-profile",
                                            "value",
                                            text(
                                                    player,
                                                    presentation(instance).profile("inherit")))));
                if (profile.equals(selected)) {
                    button.setEnchanted(true);
                    button.addLore(
                            ComponentUtils.serialize(
                                    text(player, "ui-active").color(NamedTextColor.GREEN)));
                }
                button.addAction(
                        profile,
                        event -> {
                            session.setExperiencePreference("profile", profile);
                            turnPage(player, state, session, true);
                        });
                window.addButton(2 + i * 2, button);
            }
            var toggles = List.of("sounds", "titles", "navigation");
            var icons = List.of(Material.NOTE_BLOCK, Material.NAME_TAG, Material.COMPASS);
            for (int i = 0; i < toggles.size(); i++) {
                String toggle = toggles.get(i);
                boolean on =
                        session.experiencePreference(
                                        toggle, toggle.equals("navigation") ? "false" : "true")
                                .equals("true");
                var button =
                        guideButton(
                                player,
                                "ui-toggle-" + toggle,
                                icons.get(i),
                                "ui-about-" + toggle,
                                NamedTextColor.WHITE);
                button.addLore(
                        ComponentUtils.serialize(
                                text(player, on ? "ui-switch-on" : "ui-switch-off")
                                        .color(on ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
                button.setEnchanted(on);
                button.addAction(
                        toggle,
                        event -> {
                            session.setExperiencePreference(toggle, String.valueOf(!on));
                            turnPage(player, state, session, true);
                        });
                window.addButton(12 + i * 2, button);
            }
            var back =
                    guideButton(
                            player,
                            "ui-guide-action",
                            Material.ARROW,
                            "ui-guide-back",
                            NamedTextColor.AQUA);
            back.addAction("back", event -> turnPage(player, state, session, false));
            window.addButton(9, back);
        } else {
            boolean party =
                    instance.getPlayers().stream()
                            .map(DungeonPlayerSession::getPlayer)
                            .anyMatch(member -> member != player && player.canSee(member));
            boolean objectives =
                    runs.containsKey(instance)
                            && !runs.get(instance).definition().objectives().isEmpty();
            int slot = 5 - (party ? 1 : 0) - (objectives ? 1 : 0);
            if (objectives) {
                addButton(
                        window,
                        slot,
                        player,
                        "objectives",
                        Material.TARGET,
                        () -> handle(player, new String[] {"objectives"}));
                slot += 2;
            }
            addButton(
                    window,
                    slot,
                    player,
                    "rules",
                    Material.WRITTEN_BOOK,
                    () -> runRules(player, instance));
            var history =
                    new Button(
                            "history",
                            Material.CLOCK,
                            ComponentUtils.serialize(
                                    text(player, "history")
                                            .color(NamedTextColor.WHITE)
                                            .decorate(TextDecoration.BOLD)));
            if (state.history.isEmpty()) {
                history.addLore(
                        ComponentUtils.serialize(
                                text(player, "ui-no-history").color(NamedTextColor.GRAY)));
            } else {
                history.addLore(ComponentUtils.serialize(state.history.getLast()));
                history.addLore(
                        ComponentUtils.serialize(
                                text(player, "ui-open-history", "count", state.history.size())
                                        .color(NamedTextColor.AQUA)));
                history.addAction(
                        "history",
                        event -> {
                            player.closeInventory();
                            player.sendMessage(DungeonUi.heading(text(player, "history")));
                            state.history.forEach(
                                    line -> player.sendMessage(DungeonUi.detail(line)));
                        });
            }
            window.addButton(12, history);
            if (party)
                addButton(
                        window,
                        slot + 2,
                        player,
                        "run-roster",
                        Material.PLAYER_HEAD,
                        () -> party(player, state));
            var hud =
                    guideButton(
                            player,
                            "ui-hud-heading",
                            Material.COMPARATOR,
                            "ui-guide-hud",
                            NamedTextColor.AQUA);
            hud.addAction("hud", event -> turnPage(player, state, session, true));
            window.addButton(9, hud);
            if (incompatible(session, instance))
                addButton(
                        window,
                        14,
                        player,
                        "fresh-start",
                        Material.SPONGE,
                        () -> handle(player, new String[] {"objectives", "fresh"}));
            addButton(
                    window,
                    16,
                    player,
                    "leave",
                    Material.DARK_OAK_DOOR,
                    () -> player.performCommand("dungeon leave"));
        }
        window.open(player);
    }

    private void turnPage(
            Player player, Participation state, DungeonPlayerSession session, boolean settings) {
        var source = player.getOpenInventory().getTopInventory();
        Bukkit.getScheduler()
                .runTask(
                        runtime.environment().plugin(),
                        () -> {
                            if (!closed
                                    && player.isOnline()
                                    && players.get(player.getUniqueId()) == state
                                    && player.getOpenInventory().getTopInventory() == source)
                                showGuidePage(player, state, session, settings);
                        });
    }

    private Button guideButton(
            Player player,
            String label,
            Material material,
            String description,
            NamedTextColor color) {
        var button =
                new Button(
                        label,
                        material,
                        ComponentUtils.serialize(
                                text(player, label).color(color).decorate(TextDecoration.BOLD)));
        button.addLore(
                ComponentUtils.serialize(text(player, description).color(NamedTextColor.GRAY)));
        return button;
    }

    private void party(Player viewer, Participation state) {
        viewer.sendMessage(DungeonUi.heading(text(viewer, "run-roster")));
        for (var member : state.instance.getPlayers()) {
            Player player = member.getPlayer();
            if (player != viewer && !viewer.canSee(player)) continue;
            var participation = players.get(player.getUniqueId());
            String status =
                    !player.isOnline()
                            ? "offline"
                            : player.getGameMode() == GameMode.SPECTATOR ? "spectating" : "playing";
            NamedTextColor color =
                    status.equals("offline")
                            ? NamedTextColor.GRAY
                            : status.equals("spectating")
                                    ? NamedTextColor.GOLD
                                    : NamedTextColor.GREEN;
            Component label =
                    DungeonUi.join(
                            Component.text("● ", color)
                                    .append(Component.text(player.getName(), NamedTextColor.WHITE)),
                            text(viewer, status).color(color));
            if (viewer.getGameMode() == GameMode.SPECTATOR
                    && player.isOnline()
                    && state.instance.getLivingPlayers().contains(member))
                label =
                        label.clickEvent(
                                ClickEvent.runCommand("/dungeon spectate " + player.getName()));
            viewer.sendMessage(DungeonUi.detail(label));
            if (!player.isOnline()
                    && participation != null
                    && state.instance
                            .getConfig()
                            .getBoolean("players.offline_kick.enabled", true)) {
                long remaining =
                        Math.max(
                                0,
                                state.instance
                                                .getConfig()
                                                .getInt("players.offline_kick.delay_seconds", 300)
                                        - (System.nanoTime() - participation.offlineAt)
                                                / 1_000_000_000L);
                viewer.sendMessage(
                        text(viewer, "reconnect-deadline", "value", clock((int) remaining)));
            }
        }
    }

    private void addButton(
            GuiWindow window,
            int slot,
            Player player,
            String label,
            Material material,
            Runnable action) {
        NamedTextColor accent =
                label.equals("leave") || label.equals("fresh-start")
                        ? NamedTextColor.GOLD
                        : NamedTextColor.WHITE;
        var button =
                guideButton(
                        player,
                        label.equals("leave") ? "ui-leave-short" : label,
                        material,
                        "ui-menu-" + label,
                        accent);
        button.addAction(
                label,
                event -> {
                    player.closeInventory();
                    action.run();
                });
        window.addButton(slot, button);
    }

    private void queueFeedback() {
        for (var queue : runtime.queueRegistry().snapshotQueues())
            for (UUID id : queue.getPlayers()) {
                Waiting state = waiting.get(id);
                if (state == null || !state.queue.getAdmissionId().equals(queue.getAdmissionId())) {
                    if (state != null && state.sidebar != null) state.sidebar.close();
                    waiting.put(id, new Waiting(queue));
                }
            }
        for (var entry : new ArrayList<>(waiting.entrySet())) {
            UUID id = entry.getKey();
            Waiting state = entry.getValue();
            Player player = Bukkit.getPlayer(id);
            var session = runtime.playerSessions().get(id);
            var queue = session == null ? null : runtime.queueRegistry().getQueue(session);
            if (player == null
                    || !player.isOnline()
                    || session == null
                    || players.containsKey(id)
                    || queue == null && !session.isAwaitingDungeon()) {
                if (state.sidebar != null) state.sidebar.close();
                waiting.remove(id);
                continue;
            }
            boolean preparing = queue == null;
            long bucket = state.queue.getWaitingSeconds() / 30;
            if (bucket != state.messageBucket) {
                state.messageBucket = bucket;
                player.sendMessage(
                        text(
                                        player,
                                        preparing ? "preparing" : "queued",
                                        "value",
                                        state.queue.getDungeon().getDisplayNameComponent(),
                                        "time",
                                        clock((int) state.queue.getWaitingSeconds()))
                                .append(Component.space())
                                .append(action(player, "cancel", "/dungeon leave")));
            }
            var dungeon = state.queue.getDungeon();
            ExperiencePresentation policy;
            try {
                policy = ExperiencePresentation.resolve(dungeon.getConfig(), runtime.config());
                state.policy = policy;
                state.invalidPolicy = false;
            } catch (IllegalArgumentException invalid) {
                policy = state.policy;
                if (!state.invalidPolicy)
                    runtime.environment()
                            .logger()
                            .warn(
                                    "Invalid queue presentation for '{}'; retaining the last valid HUD: {}",
                                    dungeon.getWorldName(),
                                    invalid.getMessage());
                state.invalidPolicy = true;
            }
            boolean enabled =
                    policy.flags().get("enabled") && policy.flags().get("channels.sidebar");
            if (backend == null || !enabled) {
                if (state.sidebar != null) {
                    state.sidebar.close();
                    state.sidebar = null;
                }
                continue;
            }
            if (state.sidebar == null || !state.sidebar.active())
                state.sidebar =
                        backend.claim(
                                "Dungeons",
                                id,
                                HudService.Channel.SIDEBAR,
                                HudService.SESSION,
                                LEASE);
            state.sidebar.renew(LEASE);
            String profile = policy.profile(session.experiencePreference("profile", "inherit"));
            var lines = new ArrayList<Component>();
            lines.add(
                    text(player, preparing ? "preparing-label" : "queue-label")
                            .color(NamedTextColor.GOLD));
            if (!state.queue.getDifficulty().isBlank())
                lines.add(text(player, "ui-hud-difficulty", "value", state.queue.getDifficulty()));
            lines.add(
                    text(
                            player,
                            "ui-hud-elapsed",
                            "value",
                            clock((int) state.queue.getWaitingSeconds())));
            lines.add(Component.empty());
            lines.add(Component.text("/dungeon leave", NamedTextColor.GRAY));
            state.sidebar.update(
                    profile.equals("immersive")
                            ? null
                            : new HudFrame.Sidebar(
                                    DungeonUi.heading(dungeon.getDisplayNameComponent()), lines));
        }
    }

    private Optional<Snapshot> waitingSnapshot(DungeonPlayerSession session) {
        if (session == null) return Optional.empty();
        var queue = runtime.queueRegistry().getQueue(session);
        boolean preparing = queue == null;
        var pending = preparing ? runtime.preparations().pendingDungeon(session) : null;
        if (preparing) {
            var previous = waiting.get(session.getPlayer().getUniqueId());
            if (previous != null
                    && session.isAwaitingDungeon()
                    && (pending == null || previous.queue.getDungeon() == pending))
                queue = previous.queue;
        }
        var dungeon = queue == null ? pending : queue.getDungeon();
        if (dungeon == null) return Optional.empty();
        return Optional.of(
                new Snapshot(
                        dungeon.getDungeonId(),
                        preparing ? "preparing" : "queued",
                        dungeon.getDisplayNameComponent(),
                        queue == null ? "" : queue.getDifficulty(),
                        queue == null ? 0 : (int) queue.getWaitingSeconds(),
                        null,
                        null,
                        List.of()));
    }

    @Override
    public Optional<Snapshot> view(UUID viewer) {
        requireThread();
        var state = players.get(viewer);
        var player = Bukkit.getPlayer(viewer);
        if (closed || player == null || !player.isOnline()) return Optional.empty();
        if (state == null) {
            var observation =
                    observers == null ? Optional.<Snapshot>empty() : observers.view(viewer);
            if (observation.isPresent()) return observation;
            return waitingSnapshot(runtime.playerSessions().get(viewer));
        }
        var instance = state.instance;
        var run = runs.get(instance);
        return Optional.of(
                new Snapshot(
                        instance.getDungeon().getDungeonId(),
                        stateName(player, state),
                        instance.getDungeon().getDisplayNameComponent(),
                        instance.getDifficulty() == null
                                ? ""
                                : instance.getDifficulty().getNamespace(),
                        instance.getTimeElapsed() - state.entered,
                        instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0
                                ? instance.getTimeLeft()
                                : null,
                        instance.getPlayerLives().get(viewer),
                        run == null
                                ? List.of()
                                : run.progress().views(viewer).stream()
                                        .map(
                                                goal ->
                                                        new Goal(
                                                                goal.id(),
                                                                LangUtils.trusted(goal.title()),
                                                                goal.progress(),
                                                                goal.target(),
                                                                goal.completed(),
                                                                goal.optional()))
                                        .toList()));
    }

    @Override
    public AutoCloseable subscribe(UUID viewer, Consumer<Change> listener) {
        requireThread();
        if (closed) throw new IllegalStateException("Experience service closed");
        return subscriptions.subscribe(viewer, listener);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void started(DungeonStartEvent event) {
        if (event.getInstance() instanceof PlayableInstance instance) {
            mechanics(instance);
            for (var session : instance.getPlayers())
                if (session.getPlayer().isOnline())
                    deliverNotification(
                            session.getPlayer(),
                            "started",
                            text(session.getPlayer(), "ui-started").color(NamedTextColor.GREEN));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void died(EntityDeathEvent event) {
        for (var run : runs.entrySet())
            if (run.getKey().getInstanceWorld() == event.getEntity().getWorld()) {
                var killer = event.getEntity().getKiller();
                run.getValue()
                        .death(
                                event.getEntity(),
                                killer == null ? null : killer.getUniqueId(),
                                false);
                if (killer != null
                        && players.containsKey(killer.getUniqueId())
                        && event.getEntity()
                                .getPersistentDataContainer()
                                .has(
                                        new org.bukkit.NamespacedKey(
                                                runtime.environment().plugin(),
                                                "experience_instance")))
                    players.get(killer.getUniqueId()).kills++;
            }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void removed(org.bukkit.event.entity.EntityRemoveEvent event) {
        for (var entry : new ArrayList<>(runs.entrySet()))
            if (entry.getKey().getInstanceWorld() == event.getEntity().getWorld())
                entry.getValue().removed(event.getEntity(), event.getCause());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void crystalDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal) crystal(crystal);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void crystalExplosion(EntityExplodeEvent event) {
        if (event.getEntity() instanceof EnderCrystal crystal) crystal(crystal);
    }

    private void crystal(EnderCrystal crystal) {
        Bukkit.getScheduler()
                .runTaskLater(
                        runtime.environment().plugin(),
                        () -> {
                            if (closed || !crystal.isDead()) return;
                            for (var entry : runs.entrySet())
                                if (entry.getKey().getInstanceWorld() == crystal.getWorld())
                                    entry.getValue().death(crystal, null, true);
                        },
                        1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void interacted(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        var state = players.get(event.getPlayer().getUniqueId());
        if (state == null
                || !state.instance
                        .getLivingPlayers()
                        .contains(runtime.playerSessions().get(event.getPlayer()))) return;
        var run = runs.get(state.instance);
        if (run == null) return;
        var block = event.getClickedBlock();
        run.signal(
                block.getX() + "," + block.getY() + "," + block.getZ(),
                event.getPlayer().getUniqueId(),
                ExperienceDefinition.Source.INTERACT,
                "interact:" + block.getX() + "," + block.getY() + "," + block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerDeath(PlayerDeathEvent event) {
        var state = players.get(event.getEntity().getUniqueId());
        if (state == null) return;
        state.lostLives++;
        Bukkit.getScheduler()
                .runTaskLater(
                        runtime.environment().plugin(),
                        () -> {
                            if (closed || !event.getEntity().isOnline()) return;
                            Integer lives =
                                    state.instance
                                            .getPlayerLives()
                                            .get(event.getEntity().getUniqueId());
                            notify(
                                    event.getEntity(),
                                    lives != null && lives <= 0
                                            ? state.instance
                                                            .getConfig()
                                                            .getBoolean(
                                                                    "players.spectate_on_death",
                                                                    true)
                                                    ? "death-spectator"
                                                    : "death-eliminated"
                                            : "death",
                                    null);
                            if (lives != null)
                                event.getEntity()
                                        .sendMessage(
                                                text(
                                                        event.getEntity(),
                                                        "lives",
                                                        "value",
                                                        Math.max(0, lives)));
                            var session = runtime.playerSessions().get(event.getEntity());
                            if (session != null
                                    && session.getDungeonRespawn() != null
                                    && lives != null
                                    && lives > 0)
                                event.getEntity()
                                        .sendMessage(
                                                text(
                                                        event.getEntity(),
                                                        "respawn",
                                                        "value",
                                                        text(
                                                                event.getEntity(),
                                                                session.getDungeonRespawn()
                                                                                        .distanceSquared(
                                                                                                state
                                                                                                        .instance
                                                                                                        .getStartLoc())
                                                                                < 0.01
                                                                        ? "spawn"
                                                                        : "checkpoint")));
                        },
                        1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        var state = players.get(event.getPlayer().getUniqueId());
        if (state != null) {
            release(state);
            state.offlineAt = System.nanoTime();
            for (var member : state.instance.getPlayers())
                if (member.getPlayer().isOnline() && member.getPlayer().canSee(event.getPlayer()))
                    notify(
                            member.getPlayer(),
                            "teammate-offline",
                            Component.text(event.getPlayer().getName()));
        }
        var preview = previews.remove(event.getPlayer().getUniqueId());
        if (preview != null) preview.close();
        exits.remove(event.getPlayer().getUniqueId());
        var window =
                runtime.guiService().getWindow("experience_" + event.getPlayer().getUniqueId());
        if (window != null) window.unregister();
    }

    @EventHandler
    public void hudRegistered(org.bukkit.event.server.ServiceRegisterEvent event) {
        if (event.getProvider().getService().getName().equals(HudService.class.getName()))
            selectBackend();
    }

    @EventHandler
    public void hudUnregistered(org.bukkit.event.server.ServiceUnregisterEvent event) {
        if (event.getProvider().getService().getName().equals(HudService.class.getName()))
            selectBackend();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void rejoined(PlayerJoinEvent event) {
        Bukkit.getScheduler()
                .runTaskLater(
                        runtime.environment().plugin(),
                        () -> {
                            var state = players.get(event.getPlayer().getUniqueId());
                            if (!closed && state != null) {
                                notify(event.getPlayer(), "reconnected", null);
                                runStatus(event.getPlayer(), state);
                            }
                        },
                        5);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        var steps = new ArrayList<Runnable>();
        if (editor != null) steps.add(editor::close);
        if (observers != null) steps.add(observers::close);
        if (ticker != null) steps.add(ticker::cancel);
        players.values().forEach(value -> steps.add(() -> release(value)));
        runs.values().forEach(run -> steps.add(run::close));
        previews.values().forEach(lease -> steps.add(lease::close));
        waiting.values()
                .forEach(
                        value -> {
                            if (value.sidebar != null) steps.add(value.sidebar::close);
                        });
        steps.add(standalone::close);
        steps.add(subscriptions::close);
        steps.add(() -> HandlerList.unregisterAll(this));
        steps.add(() -> Bukkit.getServicesManager().unregister(DungeonExperienceApi.class, this));
        players.clear();
        runs.clear();
        presentations.clear();
        invalidPresentations.clear();
        previews.clear();
        waiting.clear();
        exits.clear();
        CleanupSequence.run(steps);
    }
}
