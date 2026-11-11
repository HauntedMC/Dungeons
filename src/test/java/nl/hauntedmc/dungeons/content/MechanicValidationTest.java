package nl.hauntedmc.dungeons.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import nl.hauntedmc.dungeons.content.condition.*;
import nl.hauntedmc.dungeons.content.function.PlaySoundFunction;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.event.RemoteTriggerEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ValueMenuItem;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.runtime.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.RunFixture;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MechanicValidationTest {
    @TempDir Path directory;

    @Test
    void nonFiniteProximityRadiiFailWithoutCallingTheWorld() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            for (double radius :
                    new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                for (var condition :
                        List.of(
                                new PlayersWithinCondition(Map.of("radius", radius)),
                                new MobWithinCondition(Map.of("radius", radius)))) {
                    condition.initializeFields();
                    condition.setInstance(f.run);
                    condition.setLocation(new Location(f.world, 0, 70, 0));
                    assertFalse(condition.check(null));
                }
            }
            verify(f.world, never())
                    .getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble());
        }
    }

    @Test
    void remoteRejectsInvalidRangesAndMissingLocationsWithoutThrowing() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var trigger = new RemoteTrigger();
            trigger.setInstance(f.run);
            trigger.setAllowRetrigger(true);
            for (double range : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, 5}) {
                assertDoesNotThrow(
                        () ->
                                trigger.onTriggerReceive(
                                        new RemoteTriggerEvent(
                                                "trigger",
                                                null,
                                                f.run,
                                                range,
                                                new Location(f.world, 0, 70, 0),
                                                null)));
            }
            assertTrue(f.scheduler.immediate.isEmpty());
        }
    }

    @Test
    void roomLimitedRemoteRequiresAnActualSharedRoom() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var run = mock(BranchingInstance.class);
            when(run.as(BranchingInstance.class)).thenReturn(run);
            var trigger = new RemoteTrigger(Map.of("limitToRoom", true));
            trigger.initializeFields();
            trigger.setInstance(run);
            trigger.setLocation(new Location(f.world, 0, 70, 0));
            trigger.onTriggerReceive(
                    new RemoteTriggerEvent(
                            "trigger", null, run, 0, new Location(f.world, 1, 70, 0), null));
            verify(run, never()).asPlayInstance();
            assertTrue(f.scheduler.immediate.isEmpty());
        }
    }

    @Test
    void invalidSoundNumbersProduceNoPlayerEffects() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var session = mock(DungeonPlayerSession.class);
            var player = mock(Player.class);
            when(session.getPlayer()).thenReturn(player);
            for (double number :
                    new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE}) {
                for (String field : List.of("volume", "pitch")) {
                    var sound = new PlaySoundFunction(Map.of("sound", "test.sound", field, number));
                    sound.initializeFields();
                    sound.setInstance(f.run);
                    sound.runFunction(null, List.of(session));
                }
            }
            verifyNoInteractions(player);
        }
    }

    @Test
    void valueEditorsRejectMalformedInputAndKeepThePreviousValue() {
        try (var messages = mockStatic(LangUtils.class)) {
            var value = new AtomicInteger(3);
            var editor =
                    new ValueMenuItem(
                            "Count",
                            value::get,
                            text -> value.set(ValueMenuItem.nonNegativeInt(text)));
            var player = mock(Player.class);
            for (String invalid : List.of("-1", "NaN", "2147483648", "text")) {
                editor.onInput(player, invalid);
                assertEquals(3, value.get());
            }
            editor.onInput(player, " 7 ");
            assertEquals(7, value.get());
            for (String invalid : List.of("-1", "NaN", "Infinity", "1e309")) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ValueMenuItem.nonNegativeDouble(invalid));
            }
            assertEquals(0, ValueMenuItem.nonNegativeDouble("0"));
        }
    }
}
