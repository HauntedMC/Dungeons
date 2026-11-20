package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeam;
import nl.hauntedmc.dungeons.runtime.team.DungeonTeamService;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.RunFixture;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import nl.hauntedmc.featureframework.hud.HudFrame;
import nl.hauntedmc.featureframework.hud.HudService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunSidebarTest {
    @Test
    void lateJoinersSeeTheRunClockAndOnlyTheirOwnTeamAndLives(@TempDir Path directory)
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("runs.time_limit_minutes", 6);
        settings.set("players.lives", 3);
        try (var f = new RunFixture(directory, settings)) {
            var runtime = f.paper.runtime;
            var player = new PlayerState().player;
            var id = player.getUniqueId();
            when(player.locale()).thenReturn(Locale.ENGLISH);
            f.paper.bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            var session = runtime.playerSessions().put(player);
            var teams = mock(DungeonTeamService.class);
            runtime.setTeamService(teams);
            var services = mock(ServicesManager.class);
            var hud = mock(HudService.class);
            var sidebar = mock(HudService.Lease.class);
            when(sidebar.active()).thenReturn(true);
            var latest = new AtomicReference<HudFrame.Sidebar>();
            doAnswer(
                            call -> {
                                latest.set(call.getArgument(0));
                                return null;
                            })
                    .when(sidebar)
                    .update(any());
            when(hud.claim(anyString(), eq(id), any(), anyInt(), any()))
                    .thenReturn(mock(HudService.Lease.class));
            when(hud.claim(anyString(), eq(id), eq(HudService.Channel.SIDEBAR), anyInt(), any()))
                    .thenReturn(sidebar);
            when(services.load(HudService.class)).thenReturn(hud);
            f.paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            LangUtils.initialize();
            var service = new ExperienceService(runtime);
            service.start();
            var tick = f.scheduler.jobs.getLast();
            f.run.startGame();
            var clock =
                    f.scheduler.jobs.stream()
                            .filter(job -> job.period == 20)
                            .findFirst()
                            .orElseThrow();
            for (int i = 0; i < 83; i++) clock.run();
            session.setInstance(f.run);
            f.run.getPlayers().add(session);
            var stranger = new PlayerState();
            when(player.canSee(stranger.player)).thenReturn(true);
            f.run.getPlayers().add(f.session(stranger));
            service.joined(f.run, session);
            tick.run();
            var solo = lines(latest.get());
            assertFalse(
                    solo.stream().anyMatch(line -> line.contains("In progress")), solo.toString());
            assertTrue(
                    solo.stream().anyMatch(line -> line.contains("Run time  1:23")),
                    solo.toString());
            assertTrue(solo.stream().anyMatch(line -> line.contains("Time left  4:37")));
            assertFalse(
                    solo.stream()
                            .anyMatch(
                                    line -> line.contains("Party") || line.contains("Difficulty")));

            var team = new DungeonTeam(id);
            team.addMember(UUID.randomUUID());
            team.addMember(UUID.randomUUID());
            when(teams.getTeam(id)).thenReturn(team);
            var hard = mock(DungeonDifficulty.class);
            when(hard.getNamespace()).thenReturn("hard");
            when(hard.getDisplayNameComponent()).thenReturn(Component.text("Nightmare"));
            session.setExperiencePreference("profile", "minimal");
            f.run.setDifficulty(hard);
            clock.run();
            f.run.getPlayerLives().put(id, 1);
            tick.run();
            var grouped = lines(latest.get());
            assertTrue(grouped.getFirst().contains("Difficulty  Nightmare"), grouped.toString());
            assertTrue(grouped.stream().anyMatch(line -> line.contains("Party  3")));
            assertTrue(grouped.stream().anyMatch(line -> line.contains("Lives  1")));
            assertTrue(grouped.stream().anyMatch(line -> line.contains("Run time  1:24")));
            f.dungeon.getConfig().set("players.lives", 0);
            f.dungeon.getConfig().set("runs.time_limit_minutes", 0);
            f.dungeon.getConfig().set("experience.widgets.lives", "shown");
            var unlimited = new RunFixture.Run(f.dungeon, f.world);
            unlimited.startGame();
            unlimited.getPlayerLives().put(id, 3);
            session.setInstance(unlimited);
            service.joined(unlimited, session);
            tick.run();
            var untimed = lines(latest.get());
            assertTrue(untimed.stream().anyMatch(line -> line.contains("Run time")));
            assertFalse(
                    untimed.stream()
                            .anyMatch(
                                    line -> line.contains("Lives") || line.contains("Time left")));
            runtime.setQueueRegistry(
                    mock(nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry.class));
            when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SPECTATOR);
            tick.run();
            assertTrue(lines(latest.get()).stream().anyMatch(line -> line.contains("Spectat")));
            session.setExperiencePreference("profile", "immersive");
            tick.run();
            assertNull(latest.get(), "Immersive must still hide the scoreboard");
        } finally {
            LangUtils.clear();
        }
    }

    @Test
    void waypointSeparatesArrivalFromAnObjectiveOnAnotherFloor(@TempDir Path directory) throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            LangUtils.initialize();
            var player = new PlayerState().player;
            when(player.locale()).thenReturn(Locale.ENGLISH);
            var goal = mock(ExperienceDefinition.Objective.class);
            when(goal.title()).thenReturn("Vault");
            var from = player.getLocation();
            var service = new ExperienceService(fixture.paper.runtime);
            when(goal.point()).thenReturn(new ExperienceDefinition.Point(from.getX(), from.getY() + 12, from.getZ(), 2));
            assertTrue(ComponentUtils.plainText(service.waypoint(player, goal)).contains("12 m ▲ Above"));
            when(goal.point()).thenReturn(new ExperienceDefinition.Point(from.getX(), from.getY() + 1, from.getZ(), 2));
            assertTrue(ComponentUtils.plainText(service.waypoint(player, goal)).contains("Here"));
        } finally {
            LangUtils.clear();
        }
    }

    private static List<String> lines(HudFrame.Sidebar frame) {
        return frame.lines().stream().map(ComponentUtils::plainText).toList();
    }
}
