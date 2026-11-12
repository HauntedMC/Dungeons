package nl.hauntedmc.dungeons.experience;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import nl.hauntedmc.featureframework.hud.HudCoordinator;
import nl.hauntedmc.featureframework.hud.HudFrame;
import nl.hauntedmc.featureframework.hud.HudService;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Used only without ServerFeatures. Removes only owned objectives and restores conditional
 * ownership.
 */
final class StandaloneHudRenderer implements HudCoordinator.Renderer {
    private record Board(Scoreboard previous, Scoreboard owned, Objective previousSidebar) {}

    private final Map<UUID, Board> boards = new HashMap<>();
    private final Map<UUID, List<BossBar>> bars = new HashMap<>();

    @Override
    public void render(UUID id, HudService.Channel channel, HudFrame frame) {
        var player = Bukkit.getPlayer(id);
        if (player == null) return;
        switch (channel) {
            case SIDEBAR -> {
                if (frame instanceof HudFrame.Sidebar sidebar) {
                    Board state =
                            boards.computeIfAbsent(
                                    id,
                                    ignored -> {
                                        Scoreboard previous = player.getScoreboard();
                                        Scoreboard board =
                                                previous
                                                                == Bukkit.getScoreboardManager()
                                                                        .getMainScoreboard()
                                                        ? Bukkit.getScoreboardManager()
                                                                .getNewScoreboard()
                                                        : previous;
                                        Objective prior = board.getObjective(DisplaySlot.SIDEBAR);
                                        if (board != previous) player.setScoreboard(board);
                                        return new Board(previous, board, prior);
                                    });
                    Objective objective = state.owned.getObjective("dng_hud");
                    if (objective == null)
                        objective =
                                state.owned.registerNewObjective(
                                        "dng_hud", Criteria.DUMMY, sidebar.title());
                    objective.displayName(sidebar.title());
                    objective.setDisplaySlot(DisplaySlot.SIDEBAR);
                    for (int i = 0; i < 15; i++) {
                        var score = objective.getScore("dng_line_" + i);
                        if (i < sidebar.lines().size()) {
                            score.setScore(15 - i);
                            score.customName(sidebar.lines().get(i));
                            score.numberFormat(NumberFormat.blank());
                        } else score.resetScore();
                    }
                } else {
                    Board state = boards.remove(id);
                    if (state != null) {
                        Objective objective = state.owned.getObjective("dng_hud");
                        boolean owner =
                                player.getScoreboard() == state.owned
                                        && state.owned.getObjective(DisplaySlot.SIDEBAR)
                                                == objective;
                        if (objective != null) objective.unregister();
                        if (owner) {
                            if (state.previous != state.owned) player.setScoreboard(state.previous);
                            else if (state.previousSidebar != null
                                    && state.previousSidebar.getScoreboard() != null)
                                state.previousSidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
                        }
                    }
                }
            }
            case ACTION_BAR ->
                    player.sendActionBar(
                            frame instanceof HudFrame.ActionBar value
                                    ? value.text()
                                    : Component.empty());
            case TITLE -> {
                if (frame instanceof HudFrame.Title value)
                    player.showTitle(
                            Title.title(
                                    value.title(),
                                    value.subtitle(),
                                    Title.Times.times(
                                            value.fadeIn(), value.stay(), value.fadeOut())));
                else player.clearTitle();
            }
            case BOSS_BAR -> {
                var active = bars.computeIfAbsent(id, ignored -> new ArrayList<>());
                var next =
                        frame instanceof HudFrame.Bars value
                                ? value.bars()
                                : List.<HudFrame.Bar>of();
                while (active.size() > next.size()) player.hideBossBar(active.removeLast());
                for (int i = 0; i < next.size(); i++) {
                    var value = next.get(i);
                    if (i == active.size()) {
                        var bar =
                                BossBar.bossBar(
                                        value.title(),
                                        value.progress(),
                                        value.color(),
                                        value.overlay());
                        active.add(bar);
                        player.showBossBar(bar);
                    } else
                        active.get(i)
                                .name(value.title())
                                .progress(value.progress())
                                .color(value.color())
                                .overlay(value.overlay());
                    active.get(i).flags(value.flags());
                }
                if (active.isEmpty()) bars.remove(id);
            }
        }
    }
}
