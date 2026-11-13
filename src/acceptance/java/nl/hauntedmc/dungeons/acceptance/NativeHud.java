package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.featureframework.hud.HudFrame;
import nl.hauntedmc.featureframework.hud.HudService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scoreboard.DisplaySlot;

/**
 * Runs against the installed ServerFeatures coordinator, scoreboard renderer and native packets.
 */
final class NativeHud {
    private NativeHud() {}

    private static final Component SIDEBAR = Component.text("Ambient sidebar");
    private static final Component ACTION = Component.text("Ambient actionbar");
    private static final Component BOSS = Component.text("Ambient bossbar");

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        var registration = Bukkit.getServicesManager().getRegistration(HudService.class);
        require(
                registration != null && registration.getPlugin().getName().equals("ServerFeatures"),
                "Acceptance must use the actual ServerFeatures HUD provider");
        HudService hud = registration.getProvider();
        var dungeon = WorldFixtures.definition(test, "acceptance_coordinated_hud", "static");
        UUID id = UUID.randomUUID();
        test.seedPlayer(id);
        var probe = new NativeHudPlayer(id);
        var player = probe.player;
        var loader = registration.getPlugin().getClass().getClassLoader();
        var scoreboards =
                loader.loadClass(
                        "nl.hauntedmc.serverfeatures.platform.paper.ui.scoreboard.ScoreboardManager");
        scoreboards.getMethod("onPlayerJoin", org.bukkit.entity.Player.class).invoke(null, player);
        var session = test.runtime().playerSessions().put(player);
        var ambient = new ArrayList<HudService.Lease>();
        ambient.add(
                claim(
                        hud,
                        id,
                        HudService.Channel.SIDEBAR,
                        new HudFrame.Sidebar(SIDEBAR, List.of(Component.text("Ambient line")))));
        ambient.add(claim(hud, id, HudService.Channel.ACTION_BAR, new HudFrame.ActionBar(ACTION)));
        ambient.add(
                claim(
                        hud,
                        id,
                        HudService.Channel.BOSS_BAR,
                        new HudFrame.Bars(
                                List.of(
                                        new HudFrame.Bar(
                                                BOSS,
                                                .5f,
                                                BossBar.Color.GREEN,
                                                BossBar.Overlay.PROGRESS)))));
        var replacement = new Replacement(hud);
        var service = test.runtime().experience();
        return WorldFixtures.start(test, dungeon)
                .thenCompose(
                        run -> {
                            require(
                                    ambient(probe),
                                    "Ambient HUD fixture was not rendered by ServerFeatures");
                            // Exercise the same experience hooks as admission, with a native
                            // inventory and session.
                            session.setInstance(run);
                            run.getPlayers().add(session);
                            service.joined(run, session);
                            return test.await(
                                            "dungeon takes coordinated HUD",
                                            () -> {
                                                var sidebar =
                                                        player.getScoreboard()
                                                                .getObjective(DisplaySlot.SIDEBAR);
                                                return sidebar != null
                                                        && !sidebar.displayName().equals(SIDEBAR)
                                                        && !hasAmbientBoss(player)
                                                        && probe.actionBar().isEmpty();
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                Bukkit.getServicesManager()
                                                        .register(
                                                                HudService.class,
                                                                replacement,
                                                                test,
                                                                ServicePriority.Highest);
                                                return test.await(
                                                        "HUD replacement receives session claims",
                                                        () -> replacement.active() >= 4);
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                Bukkit.getServicesManager()
                                                        .unregister(HudService.class, replacement);
                                                require(
                                                        replacement.active() == 0,
                                                        "Old HUD-service leases survived replacement");
                                                service.departed(run, session);
                                                run.getPlayers().remove(session);
                                                session.setInstance(null);
                                                return test.await(
                                                        "ambient HUD after leaving",
                                                        () -> ambient(probe));
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                session.beginObservation(run);
                                                player.setGameMode(org.bukkit.GameMode.SPECTATOR);
                                                return test.await(
                                                        "observer sidebar",
                                                        () -> {
                                                            var sidebar =
                                                                    player.getScoreboard()
                                                                            .getObjective(
                                                                                    DisplaySlot
                                                                                            .SIDEBAR);
                                                            return sidebar != null
                                                                    && !sidebar.displayName()
                                                                            .equals(SIDEBAR);
                                                        });
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                require(
                                                        hasAmbientBoss(player)
                                                                && probe.actionBar()
                                                                        .equals(
                                                                                "Ambient actionbar"),
                                                        "Observation displaced ambient actionbar or bossbar");
                                                require(
                                                        session.stopObservation(),
                                                        "Observation recovery failed");
                                                return test.await(
                                                        "ambient HUD after spectating",
                                                        () -> ambient(probe));
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                service.preview(player, dungeon);
                                                require(
                                                        !player.getScoreboard()
                                                                .getObjective(DisplaySlot.SIDEBAR)
                                                                .displayName()
                                                                .equals(SIDEBAR),
                                                        "Editor preview did not take the sidebar");
                                                require(
                                                        hasAmbientBoss(player)
                                                                && probe.actionBar()
                                                                        .equals(
                                                                                "Ambient actionbar"),
                                                        "Editor preview displaced unrelated ambient channels");
                                                return test.await(
                                                        "ambient HUD after preview expires",
                                                        () -> ambient(probe));
                                            })
                                    .thenCompose(ignored -> WorldFixtures.dispose(test, run));
                        })
                .whenComplete(
                        (ignored, failure) -> {
                            Bukkit.getServicesManager().unregister(HudService.class, replacement);
                            service.observationEnded(id);
                            ambient.forEach(HudService.Lease::close);
                            hud.removePlayer(id);
                            try {
                                scoreboards
                                        .getMethod("onPlayerQuit", org.bukkit.entity.Player.class)
                                        .invoke(null, player);
                            } catch (ReflectiveOperationException cleanup) {
                                throw new IllegalStateException(cleanup);
                            } finally {
                                test.runtime().playerSessions().disconnected(player);
                                probe.close();
                            }
                        });
    }

    private static HudService.Lease claim(
            HudService hud, UUID id, HudService.Channel channel, HudFrame frame) {
        var lease =
                hud.claim(
                        "ServerFeatures:acceptance",
                        id,
                        channel,
                        HudService.AMBIENT,
                        Duration.ofMinutes(5));
        lease.update(frame);
        return lease;
    }

    private static boolean ambient(NativeHudPlayer probe) {
        var sidebar = probe.player.getScoreboard().getObjective(DisplaySlot.SIDEBAR);
        return sidebar != null
                && sidebar.displayName().equals(SIDEBAR)
                && hasAmbientBoss(probe.player)
                && probe.actionBar().equals("Ambient actionbar");
    }

    private static boolean hasAmbientBoss(org.bukkit.entity.Player player) {
        for (var bar : player.activeBossBars()) if (bar.name().equals(BOSS)) return true;
        return false;
    }

    /** Replacement publication delegates to the real renderer while exposing lease ownership. */
    private static final class Replacement implements HudService {
        private final HudService delegate;
        private final List<Lease> claims = new ArrayList<>();

        Replacement(HudService delegate) {
            this.delegate = delegate;
        }

        long active() {
            return claims.stream().filter(Lease::active).count();
        }

        @Override
        public Lease claim(
                String owner, UUID player, Channel channel, int priority, Duration lifetime) {
            var lease = delegate.claim(owner, player, channel, priority, lifetime);
            claims.add(lease);
            return lease;
        }

        @Override
        public void releaseOwner(String owner) {
            delegate.releaseOwner(owner);
        }

        @Override
        public void removePlayer(UUID player) {
            delegate.removePlayer(player);
        }
    }
}
