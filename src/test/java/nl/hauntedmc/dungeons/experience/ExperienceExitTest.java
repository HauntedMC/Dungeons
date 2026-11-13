package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

class ExperienceExitTest {
    private static final class Fixture implements AutoCloseable {
        final PaperBoundary paper = new PaperBoundary();
        final org.mockito.MockedStatic<LangUtils> language = mockStatic(LangUtils.class);
        final AtomicLong clock = new AtomicLong();
        final Player player = mock(Player.class);
        final DungeonPlayerSession session = mock(DungeonPlayerSession.class);
        final DungeonDefinition dungeon = mock(DungeonDefinition.class);
        final YamlConfiguration config = new YamlConfiguration();
        final ExperienceService service = new ExperienceService(paper.runtime, clock::get);
        final PlayableInstance first;
        Component confirmation;

        Fixture() {
            new CapturedScheduler(paper);
            var sessions = mock(PlayerSessionRegistry.class);
            paper.runtime.setPlayerSessions(sessions);
            when(sessions.get(player)).thenReturn(session);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(session.getPlayer()).thenReturn(player);
            when(session.experiencePreference(anyString(), anyString()))
                    .thenAnswer(call -> call.getArgument(1));
            when(dungeon.getWorldName()).thenReturn("crypt");
            when(dungeon.getDungeonId()).thenReturn(UUID.randomUUID());
            when(dungeon.isAccessCooldownEnabled()).thenReturn(true);
            when(dungeon.isCooldownOnLeave()).thenReturn(true);
            when(dungeon.shouldApplyAccessCooldown(any(), any())).thenReturn(true);
            language.when(() -> LangUtils.component(anyString(), eq(player), eq(false), anyMap()))
                    .thenAnswer(call -> Component.text((String) call.getArgument(0)));
            language.when(() -> LangUtils.notice(any(Component.class)))
                    .thenAnswer(call -> call.getArgument(0));
            doAnswer(
                            call -> {
                                confirmation = call.getArgument(0);
                                return null;
                            })
                    .when(player)
                    .sendMessage(any(Component.class));
            first = run();
            join(first);
        }

        PlayableInstance run() {
            var run = mock(PlayableInstance.class);
            when(run.getDungeon()).thenReturn(dungeon);
            when(run.getConfig()).thenReturn(config);
            when(run.experienceDefinition()).thenReturn(ExperienceDefinition.empty());
            when(run.getInstanceId()).thenReturn(UUID.randomUUID());
            when(run.getPlayerLives()).thenReturn(new java.util.HashMap<>());
            return run;
        }

        void join(PlayableInstance run) {
            when(session.getInstance()).thenReturn(run);
            service.joined(run, session);
        }

        String request() {
            assertFalse(service.requestExit(player, null));
            assertNotNull(confirmation.clickEvent());
            return ((net.kyori.adventure.text.event.ClickEvent.Payload.Text)
                            confirmation.clickEvent().payload())
                    .value()
                    .substring("/dungeon leave ".length());
        }

        @Override
        public void close() {
            language.close();
            paper.close();
        }
    }

    @Test
    void matchingConfirmationWorksOnceAndExpiresAtDeadline() {
        try (var f = new Fixture()) {
            String token = f.request();
            assertTrue(f.service.requestExit(f.player, token));
            assertFalse(f.service.requestExit(f.player, token));
            token = f.request();
            f.clock.set(Duration.ofSeconds(30).toNanos());
            assertFalse(f.service.requestExit(f.player, token));
            assertTrue(f.service.requestExit(f.player, f.request()));
        }
    }

    @Test
    void reconnectInvalidatesConfirmationEvenInSameRun() {
        try (var f = new Fixture()) {
            String token = f.request();
            f.service.quit(new PlayerQuitEvent(f.player, Component.empty()));
            f.join(f.first);
            assertFalse(f.service.requestExit(f.player, token));
            assertTrue(f.service.requestExit(f.player, f.request()));
        }
    }

    @Test
    void switchingRunsAndLateDepartureCannotRemoveNewParticipation() {
        try (var f = new Fixture()) {
            String token = f.request();
            var second = f.run();
            f.join(second);
            f.service.departed(f.first, f.session);
            assertFalse(f.service.requestExit(f.player, token));
            assertTrue(f.service.requestExit(f.player, f.request()));
            f.join(f.first);
            assertFalse(f.service.requestExit(f.player, token));
        }
    }

    @Test
    void staleParticipationCannotAuthorizeDepartureFromCurrentSessionRun() {
        try (var f = new Fixture()) {
            String token = f.request();
            var second = f.run();
            when(f.session.getInstance()).thenReturn(second);
            assertFalse(f.service.requestExit(f.player, token));
        }
    }

    @Test
    void changedLivesSavepointCooldownAndKeyConsequencesRequireNewConfirmation() {
        try (var f = new Fixture()) {
            String token = f.request();
            f.first.getPlayerLives().put(f.player.getUniqueId(), 1);
            assertFalse(f.service.requestExit(f.player, token));
            token = f.request();
            when(f.session.getDungeonSavePoint("crypt")).thenReturn(new Location(null, 1, 70, 2));
            assertFalse(f.service.requestExit(f.player, token));
            token = f.request();
            when(f.dungeon.shouldApplyAccessCooldown(f.first, f.player.getUniqueId()))
                    .thenReturn(false);
            assertFalse(f.service.requestExit(f.player, token));
            token = f.request();
            f.config.set("access.keys.items", List.of("new-key"));
            assertFalse(f.service.requestExit(f.player, token));
            token = f.request();
            f.config.set("access.keys.consume_on_entry", false);
            assertFalse(f.service.requestExit(f.player, token));
        }
    }
}
