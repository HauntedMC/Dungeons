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
import nl.hauntedmc.dungeons.api.DungeonExperienceApi;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.localization.LangUtils;
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

    private record Exit(
            Participation participation, String signature, String token, long expires) {}

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
    private final Map<UUID, List<Component>> reports = new HashMap<>();
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
                            notify(
                                    player,
                                    "welcome",
                                    instance.getDungeon().getDisplayNameComponent());
                            briefing(player, instance);
                            if (incompatible(session, instance)) {
                                notify(player, "incompatible-save", null);
                                player.sendMessage(
                                        action(player, "fresh-start", "/dungeon objectives fresh"));
                            }
                            player.sendMessage(
                                    action(player, "guide", "/dungeon guide")
                                            .append(Component.space())
                                            .append(action(player, "leave", "/dungeon leave")));
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

    private Component text(Player player, String key, Object... values) {
        var replacements = new LinkedHashMap<String, Object>();
        for (int i = 0; i + 1 < values.length; i += 2)
            replacements.put(values[i].toString(), values[i + 1]);
        return LangUtils.component("experience." + key, player, false, replacements);
    }

    private Component action(Player player, String key, String command) {
        return text(player, key).clickEvent(ClickEvent.runCommand(command));
    }

    private void line(Map<String, Component> widgets, Player player, String key, Object value) {
        Component row = text(player, key, "value", value);
        widgets.put(key, row);
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
            var widgetRows = new LinkedHashMap<String, Component>();
            if (!immersive) {
                line(widgetRows, player, "state", text(player, stateName(player, state)));
                if (instance.getDifficulty() != null)
                    line(widgetRows, player, "difficulty", instance.getDifficulty().getNamespace());
                if (run != null
                        && (balanced
                                || setting(instance, "widgets.stage", "auto").equals("shown"))) {
                    for (var track : run.definition().tracks().values()) {
                        var stage = run.progress().currentStage(track, player.getUniqueId());
                        if (stage != null) {
                            widgetRows.put("stage", LangUtils.trusted(stage.title()));
                            break;
                        }
                    }
                }
                if (run != null
                        && (balanced
                                || setting(instance, "widgets.objective", "auto")
                                        .equals("shown"))) {
                    var goals = run.progress().views(player.getUniqueId());
                    var goal =
                            goals.stream()
                                    .filter(value -> value.id().equals(state.pinned))
                                    .findFirst()
                                    .orElseGet(
                                            () ->
                                                    goals.stream()
                                                            .filter(value -> !value.completed())
                                                            .findFirst()
                                                            .orElse(null));
                    if (goal != null)
                        widgetRows.put(
                                "objective",
                                LangUtils.trusted(goal.title())
                                        .append(
                                                Component.text(
                                                        " "
                                                                + goal.progress()
                                                                + "/"
                                                                + goal.target())));
                }
                if (instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0
                        && !setting(instance, "widgets.remaining", "auto").equals("hidden"))
                    line(widgetRows, player, "remaining", clock(instance.getTimeLeft()));
                if (setting(instance, "widgets.elapsed", "auto").equals("shown"))
                    line(
                            widgetRows,
                            player,
                            "elapsed",
                            clock(instance.getTimeElapsed() - state.entered));
                Integer lives = instance.getPlayerLives().get(player.getUniqueId());
                if (lives != null && !setting(instance, "widgets.lives", "auto").equals("hidden"))
                    line(widgetRows, player, "lives", lives);
                else if (setting(instance, "widgets.lives", "auto").equals("shown"))
                    line(widgetRows, player, "lives", text(player, "unlimited"));
                long count =
                        instance.getPlayers().stream()
                                .filter(
                                        member ->
                                                member.getPlayer() == player
                                                        || player.canSee(member.getPlayer()))
                                .count();
                if ((count > 1 || setting(instance, "widgets.party", "auto").equals("shown"))
                        && !setting(instance, "widgets.party", "auto").equals("hidden"))
                    line(widgetRows, player, "party", count);
                if ((balanced || setting(instance, "widgets.respawn", "auto").equals("shown"))
                        && !setting(instance, "widgets.respawn", "auto").equals("hidden")
                        && session.getDungeonRespawn() != null)
                    line(
                            widgetRows,
                            player,
                            "respawn",
                            text(
                                    player,
                                    session.getDungeonRespawn()
                                                            .distanceSquared(instance.getStartLoc())
                                                    < 0.01
                                            ? "spawn"
                                            : "checkpoint"));
                widgetRows.put("help", Component.text("/dungeon guide", NamedTextColor.GRAY));
                widgetRows.put("leave", Component.text("/dungeon leave", NamedTextColor.GRAY));
            }
            Map<String, Object> substitutions = new LinkedHashMap<>();
            substitutions.put("dungeon", instance.getDungeon().getDisplayNameComponent());
            substitutions.put(
                    "difficulty",
                    instance.getDifficulty() == null
                            ? ""
                            : instance.getDifficulty().getNamespace());
            substitutions.put("state", text(player, stateName(player, state)));
            substitutions.put(
                    "time_left",
                    instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0
                            ? clock(instance.getTimeLeft())
                            : "");
            substitutions.put("elapsed", clock(instance.getTimeElapsed() - state.entered));
            substitutions.put(
                    "lives",
                    instance.getPlayerLives().containsKey(player.getUniqueId())
                            ? instance.getPlayerLives().get(player.getUniqueId())
                            : text(player, "unlimited"));
            for (String key : List.of("stage", "objective", "party", "respawn"))
                substitutions.put(key, widgetRows.getOrDefault(key, Component.empty()));
            String titleTemplate = setting(instance, "sidebar.title", "");
            Component sidebarTitle =
                    titleTemplate.isBlank()
                            ? instance.getDungeon().getDisplayNameComponent()
                            : LangUtils.trusted(titleTemplate, substitutions);
            channel(player, state, HudService.Channel.SIDEBAR)
                    .update(
                            immersive
                                    ? null
                                    : new HudFrame.Sidebar(
                                            sidebarTitle,
                                            presentation(instance)
                                                    .layout(widgetRows, substitutions)));
        } else closeChannel(state, HudService.Channel.SIDEBAR);
        if (enabled(instance, "channels.actionbar", true))
            channel(player, state, HudService.Channel.ACTION_BAR).update(null);
        else closeChannel(state, HudService.Channel.ACTION_BAR);
        if (enabled(instance, "channels.bossbar", true)) {
            var bars =
                    immersive
                            ? new ArrayList<HudFrame.Bar>()
                            : new ArrayList<>(run == null ? List.of() : run.bars());
            if (!immersive && bars.isEmpty()) bars.addAll(instance.dragonHudBars());
            if (!immersive
                    && instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0
                    && instance.getTimeLeft() <= 60
                    && instance.getTimeLeft() > 0) {
                bars.addFirst(
                        new HudFrame.Bar(
                                text(player, "remaining", "value", clock(instance.getTimeLeft())),
                                instance.getTimeLeft() / 60f,
                                BossBar.Color.RED,
                                BossBar.Overlay.PROGRESS));
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
        if (session.experiencePreference("navigation", "false").equals("true")
                && state.pinned != null
                && run != null) {
            var goal = run.definition().objectives().get(state.pinned);
            if (goal != null
                    && goal.point() != null
                    && run.progress().visible(goal.id(), player.getUniqueId())
                    && !run.progress().completed(goal.id(), player.getUniqueId())) {
                var point = goal.point();
                double dx = point.x() - player.getLocation().getX(),
                        dz = point.z() - player.getLocation().getZ();
                actionBar(
                        player,
                        text(
                                player,
                                "navigation",
                                "value",
                                Math.round(Math.sqrt(dx * dx + dz * dz)),
                                "bearing",
                                Math.round(Math.toDegrees(Math.atan2(-dx, dz)))),
                        2,
                        HudService.CONTEXT);
            }
        }
    }

    private static String clock(int seconds) {
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
                player, key, text(player, key, "value", value == null ? Component.empty() : value));
    }

    private void deliverNotification(Player player, String key, Component message) {
        var state = players.get(player.getUniqueId());
        if (state != null && !notification(state, key, "enabled", true)) return;
        if (state == null
                || notification(
                        state,
                        key,
                        "chat",
                        !java.util.Set.of("objective-progress", "wave-started", "boss-phase")
                                .contains(key))) player.sendMessage(LangUtils.notice(message));
        if (state != null) {
            if (notification(
                    state,
                    key,
                    "actionbar",
                    java.util.Set.of("objective-progress", "wave-started", "boss-phase")
                            .contains(key)))
                actionBar(
                        player,
                        message,
                        key.equals("objective-progress") ? 2 : 3,
                        HudService.CONTEXT);
            if (notification(
                    state,
                    key,
                    "title",
                    java.util.Set.of("stage-started", "encounter-started").contains(key)))
                title(
                        player,
                        new HudFrame.Title(
                                message,
                                Component.empty(),
                                Duration.ofMillis(200),
                                Duration.ofSeconds(2),
                                Duration.ofMillis(300)));
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
                && (!key.equals("objective-progress") || notification(state, key, "chat", false))) {
            if (state.history.size() == 20) state.history.removeFirst();
            state.history.addLast(message);
        }
    }

    public void changed(PlayableInstance instance, ObjectiveProgress.Change change) {
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
            var goal = run == null ? null : run.definition().objectives().get(change.id());
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
                notify(player, change.kind(), label);
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
        UUID id = session.getPlayer().getUniqueId();
        var state = players.get(id);
        if (state == null || state.instance != instance) return;
        players.remove(id);
        release(state);
        exits.remove(id);
        var report = new ArrayList<Component>();
        report.add(instance.getDungeon().getDisplayNameComponent());
        report.add(
                text(
                        session.getPlayer(),
                        state.outcome == null ? "expedition-ended" : state.outcome));
        report.add(
                text(
                        session.getPlayer(),
                        "elapsed",
                        "value",
                        clock(instance.getTimeElapsed() - state.entered)));
        report.add(text(session.getPlayer(), "lives-lost", "value", state.lostLives));
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
                report.add(text(session.getPlayer(), "objectives-finished", "value", completed));
            if (state.kills > 0)
                report.add(text(session.getPlayer(), "tracked-kills", "value", state.kills));
        }
        report.addAll(state.rewards);
        var cooldown = instance.getDungeon().getAccessCooldown(session.getPlayer());
        if (cooldown != null)
            report.add(
                    text(
                            session.getPlayer(),
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
        reports.put(id, List.copyOf(report));
        if (session.getPlayer().isOnline()) {
            report.forEach(line -> session.getPlayer().sendMessage(LangUtils.notice(line)));
            session.getPlayer()
                    .sendMessage(
                            action(session.getPlayer(), "report-details", "/dungeon status")
                                    .append(Component.space())
                                    .append(
                                            action(
                                                    session.getPlayer(),
                                                    "dungeon-info",
                                                    "/dungeon info "
                                                            + instance.getDungeon().getWorldName()))
                                    .append(Component.space())
                                    .append(
                                            action(
                                                    session.getPlayer(),
                                                    "play-again",
                                                    "/dungeon play "
                                                            + instance.getDungeon().getWorldName()
                                                            + (instance.getDifficulty() == null
                                                                    ? ""
                                                                    : " "
                                                                            + instance.getDifficulty()
                                                                                    .getNamespace()))));
        }
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
        return session.incompatibleExperienceCheckpoint(
                instance.getDungeon().getDungeonId().toString(),
                instance.experienceDefinition(),
                instance.getDifficulty() == null ? "" : instance.getDifficulty().getNamespace());
    }

    public void savePoint(DungeonPlayerSession session) {
        var state = players.get(session.getPlayer().getUniqueId());
        if (state == null) return;
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
            Player player, nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition dungeon) {
        String description = dungeon.getConfig().getString("experience.description", "");
        if (!description.isBlank())
            player.sendMessage(LangUtils.notice(LangUtils.trusted(description)));
        int lives = dungeon.getConfig().getInt("players.lives", 0),
                minutes = dungeon.getConfig().getInt("runs.time_limit_minutes", 0);
        player.sendMessage(
                text(player, "lives", "value", lives == 0 ? text(player, "unlimited") : lives));
        player.sendMessage(
                text(player, minutes == 0 ? "untimed" : "remaining", "value", clock(minutes * 60)));
        player.sendMessage(text(player, "party-limit", "value", dungeon.getMaxTeamSize()));
        if (!dungeon.getAccessKeys().isEmpty())
            player.sendMessage(
                    text(
                            player,
                            dungeon.getConfig().getBoolean("access.keys.consume_on_entry", true)
                                    ? "entry-key-consumed"
                                    : "entry-key-required"));
        player.sendMessage(
                text(
                        player,
                        dungeon.getConfig().getBoolean("players.spectate_on_death", true)
                                ? "entry-spectator"
                                : "entry-eliminated"));
        player.sendMessage(
                text(
                        player,
                        dungeon.getConfig().getBoolean("players.keep_on_entry.inventory", true)
                                ? "entry-inventory-kept"
                                : "inventory-restored"));
        player.sendMessage(text(player, "spawn-explanation"));
        if (dungeon.isAccessCooldownEnabled()) player.sendMessage(text(player, "entry-cooldown"));
    }

    private void briefing(Player player, PlayableInstance instance) {
        String description = setting(instance, "description", "");
        if (!description.isBlank())
            player.sendMessage(LangUtils.notice(LangUtils.trusted(description)));
        if (instance.getConfig().getInt("runs.time_limit_minutes", 0) > 0)
            player.sendMessage(
                    text(
                            player,
                            "remaining",
                            "value",
                            clock(
                                    instance.getTimeLeft() > 0
                                            ? instance.getTimeLeft()
                                            : instance.getConfig().getInt("runs.time_limit_minutes")
                                                    * 60)));
        int lives =
                instance.getPlayerLives()
                        .getOrDefault(
                                player.getUniqueId(),
                                instance.getConfig().getInt("players.lives", 0));
        player.sendMessage(
                text(player, "lives", "value", lives == 0 ? text(player, "unlimited") : lives));
        player.sendMessage(text(player, "spawn-explanation"));
        if (!instance.getConfig().getBoolean("players.keep_on_entry.inventory", true))
            player.sendMessage(text(player, "inventory-restored"));
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
    public boolean requestExit(Player player, String token) {
        requireThread();
        var state = players.get(player.getUniqueId());
        var session = runtime.playerSessions().get(player);
        if (session == null || state != null && session.getInstance() != state.instance) {
            exits.remove(player.getUniqueId());
            return token == null && (session == null || session.getInstance() == null);
        }
        Exit pending = exits.remove(player.getUniqueId());
        if (state == null) return token == null;
        String signature = exitSignature(session, state);
        if (token != null
                && pending != null
                && pending.token.equals(token)
                && pending.participation == state
                && pending.signature.equals(signature)
                && pending.expires > nanoTime.getAsLong()) return true;
        if (token == null
                && (state.outcome != null || !enabled(state.instance, "confirm_exit", true)))
            return true;
        String next = UUID.randomUUID().toString();
        exits.put(
                player.getUniqueId(),
                new Exit(
                        state,
                        signature,
                        next,
                        nanoTime.getAsLong() + Duration.ofSeconds(30).toNanos()));
        notify(player, "exit-confirm", null);
        player.sendMessage(
                text(
                        player,
                        session.getDungeonSavePoint(state.instance.getDungeon().getWorldName())
                                        == null
                                ? "exit-unsaved"
                                : "exit-saved"));
        if (state.instance.getDungeon().isAccessCooldownEnabled()
                && state.instance.getDungeon().isCooldownOnLeave()
                && state.instance
                        .getDungeon()
                        .shouldApplyAccessCooldown(state.instance, player.getUniqueId()))
            player.sendMessage(text(player, "exit-cooldown"));
        if (!state.instance.getConfig().getList("access.keys.items", List.of()).isEmpty()
                && state.instance.getConfig().getBoolean("access.keys.consume_on_entry", true))
            player.sendMessage(text(player, "exit-key"));
        player.sendMessage(
                action(player, "confirm-leave", "/dungeon leave " + next)
                        .append(Component.space())
                        .append(action(player, "stay", "/dungeon guide")));
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
        lines.add(text(player, "playing"));
        int time = dungeon.getConfig().getInt("runs.time_limit_minutes", 0);
        int lives = dungeon.getConfig().getInt("players.lives", 0);
        if (time > 0) lines.add(text(player, "remaining", "value", clock(time * 60)));
        if (lives > 0) lines.add(text(player, "lives", "value", lives));
        lines.add(Component.text("/dungeon guide"));
        lines.add(Component.text("/dungeon leave"));
        var previous = previews.remove(player.getUniqueId());
        if (previous != null) previous.close();
        var lease =
                backend.claim(
                        "Dungeons",
                        player.getUniqueId(),
                        HudService.Channel.SIDEBAR,
                        HudService.CONTEXT,
                        Duration.ofSeconds(10));
        lease.update(new HudFrame.Sidebar(dungeon.getDisplayNameComponent(), lines));
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
                            .noneMatch(member -> member.getPlayer() == target))
                throw new IllegalArgumentException(
                        "Choose an online living teammate while spectating");
            player.setSpectatorTarget(target);
            return true;
        }
        if (state == null && command.equals("hud")) {
            if (args.length > 1
                    && List.of("balanced", "minimal", "immersive", "inherit").contains(args[1]))
                session.setExperiencePreference("profile", args[1]);
            else if (args.length == 3
                    && List.of("sounds", "titles", "navigation").contains(args[1])
                    && List.of("true", "false").contains(args[2]))
                session.setExperiencePreference(args[1], args[2]);
            else
                for (String profile : List.of("balanced", "minimal", "immersive", "inherit"))
                    player.sendMessage(
                            Component.text(profile)
                                    .clickEvent(ClickEvent.runCommand("/dungeon hud " + profile)));
            return true;
        }
        if (state == null
                && observers != null
                && observers.view(player.getUniqueId()).isPresent()) {
            var observed = observers.view(player.getUniqueId()).orElseThrow();
            player.sendMessage(
                    observed.title().append(Component.space()).append(text(player, "observing")));
            player.sendMessage(action(player, "leave", "/dungeon leave"));
            return true;
        }
        if (state == null) {
            var queue = runtime.queueRegistry().getQueue(session);
            if (queue != null) {
                player.sendMessage(
                        text(
                                        player,
                                        "queued",
                                        "value",
                                        queue.getDungeon().getDisplayNameComponent(),
                                        "time",
                                        clock((int) queue.getWaitingSeconds()))
                                .append(Component.space())
                                .append(action(player, "cancel", "/dungeon leave")));
                return true;
            }
            var report = reports.get(player.getUniqueId());
            if (report != null && command.equals("status")) report.forEach(player::sendMessage);
            else notify(player, "not-participating", null);
            return true;
        }
        var run = runs.get(state.instance);
        if (command.equals("hud") && args.length > 1) {
            String option = args[1].toLowerCase(Locale.ROOT);
            if (List.of("balanced", "minimal", "immersive", "inherit").contains(option))
                session.setExperiencePreference("profile", option);
            else if (List.of("sounds", "titles", "navigation").contains(option)
                    && args.length == 3
                    && List.of("true", "false").contains(args[2]))
                session.setExperiencePreference(option, args[2]);
            else
                throw new IllegalArgumentException(
                        "/dungeon hud balanced|minimal|immersive|inherit|sounds|titles|navigation [true|false]");
            notify(player, "settings-updated", null);
            return true;
        }
        if (command.equals("objectives")) {
            if (args.length == 2
                    && args[1].equals("fresh")
                    && incompatible(session, state.instance)) {
                session.discardExperienceCheckpoint(
                        state.instance.getDungeon().getWorldName(),
                        state.instance.getDungeon().getDungeonId().toString());
                if (run != null) run.progress().clearPlayer(player.getUniqueId());
                notify(player, "fresh-confirmed", null);
                return true;
            }
            if (run == null || run.progress().views(player.getUniqueId()).isEmpty()) {
                notify(player, "exploration", null);
                return true;
            }
            if (args.length > 2 && args[1].equals("pin")) {
                if (run.progress().views(player.getUniqueId()).stream()
                        .noneMatch(goal -> goal.id().equals(args[2])))
                    throw new IllegalArgumentException("Objective is not currently visible");
                state.pinned = args[2];
            }
            if (args.length > 1 && args[1].equals("hint")) {
                for (var view : run.progress().views(player.getUniqueId()))
                    if (!view.completed()) {
                        var goal = run.definition().objectives().get(view.id());
                        if (!goal.hint().isBlank())
                            player.sendMessage(LangUtils.notice(LangUtils.trusted(goal.hint())));
                    }
            } else
                for (var goal : run.progress().views(player.getUniqueId()))
                    player.sendMessage(
                            LangUtils.trusted(goal.title())
                                    .append(
                                            Component.text(
                                                    " " + goal.progress() + "/" + goal.target()))
                                    .clickEvent(
                                            ClickEvent.runCommand(
                                                    "/dungeon objectives pin " + goal.id())));
            return true;
        }
        if (command.equals("status")) {
            briefing(player, state.instance);
            state.history.forEach(player::sendMessage);
            return true;
        }
        guide(player, state, session);
        return true;
    }

    private void guide(Player player, Participation state, DungeonPlayerSession session) {
        String namespace = "experience_" + player.getUniqueId();
        var window =
                runtime.guiService()
                        .createWindow(
                                namespace,
                                27,
                                ComponentUtils.serialize(text(player, "guide-title")));
        window.setAccessCheck(
                viewer ->
                        viewer.getUniqueId().equals(player.getUniqueId())
                                && players.get(player.getUniqueId()) == state);
        addButton(
                window,
                10,
                player,
                "status",
                Material.COMPASS,
                () -> handle(player, new String[] {"status"}));
        if (state.instance.getPlayers().size() > 1)
            addButton(
                    window,
                    11,
                    player,
                    "party-guide",
                    Material.PLAYER_HEAD,
                    () -> party(player, state));
        addButton(
                window, 12, player, "rules", Material.BOOK, () -> briefing(player, state.instance));
        if (runs.containsKey(state.instance)
                && !runs.get(state.instance).definition().objectives().isEmpty())
            addButton(
                    window,
                    14,
                    player,
                    "objectives",
                    Material.WRITABLE_BOOK,
                    () -> handle(player, new String[] {"objectives"}));
        addButton(
                window,
                16,
                player,
                "leave",
                Material.OAK_DOOR,
                () -> player.performCommand("dungeon leave"));
        if (incompatible(session, state.instance))
            addButton(
                    window,
                    18,
                    player,
                    "fresh-start",
                    Material.SPONGE,
                    () -> handle(player, new String[] {"objectives", "fresh"}));
        addButton(
                window,
                20,
                player,
                "balanced",
                Material.PAINTING,
                () -> handle(player, new String[] {"hud", "balanced"}));
        addButton(
                window,
                21,
                player,
                "minimal",
                Material.PAPER,
                () -> handle(player, new String[] {"hud", "minimal"}));
        addButton(
                window,
                22,
                player,
                "immersive",
                Material.FEATHER,
                () -> handle(player, new String[] {"hud", "immersive"}));
        addButton(
                window,
                24,
                player,
                "history",
                Material.CLOCK,
                () -> state.history.forEach(player::sendMessage));
        window.open(player);
    }

    private void party(Player viewer, Participation state) {
        for (var member : state.instance.getPlayers()) {
            Player player = member.getPlayer();
            if (player != viewer && !viewer.canSee(player)) continue;
            var participation = players.get(player.getUniqueId());
            String status =
                    !player.isOnline()
                            ? "offline"
                            : player.getGameMode() == GameMode.SPECTATOR ? "spectating" : "playing";
            Component label = Component.text(player.getName() + " · ").append(text(viewer, status));
            if (viewer.getGameMode() == GameMode.SPECTATOR
                    && player.isOnline()
                    && state.instance.getLivingPlayers().contains(member))
                label =
                        label.clickEvent(
                                ClickEvent.runCommand("/dungeon spectate " + player.getName()));
            viewer.sendMessage(label);
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
            nl.hauntedmc.dungeons.gui.framework.window.GuiWindow window,
            int slot,
            Player player,
            String label,
            Material material,
            Runnable action) {
        var button = new Button(label, material, ComponentUtils.serialize(text(player, label)));
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
            List<Component> lines =
                    List.of(
                            text(player, preparing ? "preparing-label" : "queue-label"),
                            Component.text(state.queue.getDifficulty()),
                            text(
                                    player,
                                    "elapsed",
                                    "value",
                                    clock((int) state.queue.getWaitingSeconds())),
                            Component.empty(),
                            Component.text("/dungeon leave"));
            state.sidebar.update(
                    profile.equals("immersive")
                            ? null
                            : new HudFrame.Sidebar(dungeon.getDisplayNameComponent(), lines));
        }
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
            var queued = waiting.get(viewer);
            if (queued == null) return Optional.empty();
            return Optional.of(
                    new Snapshot(
                            queued.queue.getDungeon().getDungeonId(),
                            runtime.queueRegistry().getQueue(runtime.playerSessions().get(viewer))
                                            == null
                                    ? "preparing"
                                    : "queued",
                            queued.queue.getDungeon().getDisplayNameComponent(),
                            queued.queue.getDifficulty(),
                            (int) queued.queue.getWaitingSeconds(),
                            null,
                            null,
                            List.of()));
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
                if (session.getPlayer().isOnline()) notify(session.getPlayer(), "started", null);
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
        reports.remove(event.getPlayer().getUniqueId());
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
                                briefing(event.getPlayer(), state.instance);
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
        reports.clear();
        CleanupSequence.run(steps);
    }
}
