package nl.hauntedmc.dungeons.content.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CooldownContractTest {
    @TempDir Path directory;

    @Test
    void accessAndLootMonthlyResetsChooseTheNextOccurrenceRatherThanAPastDate() throws Exception {
        ZonedDateTime now = ZonedDateTime.parse("2026-11-20T10:00:00+01:00[Europe/Amsterdam]");
        Clock fixed = Clock.fixed(now.toInstant(), now.getZone());
        Calendar calendar = GregorianCalendar.from(now);
        try (var paper = new PaperBoundary(); var calendars = mockStatic(Calendar.class, CALLS_REAL_METHODS);
                var clocks = mockStatic(Clock.class, CALLS_REAL_METHODS)) {
            calendars.when(Calendar::getInstance).thenAnswer(call -> calendar.clone());
            clocks.when(Clock::systemDefaultZone).thenReturn(fixed);
            var settings = new YamlConfiguration();
            for (String path : new String[] {"access.cooldown", "rewards.loot_cooldown"}) {
                settings.set(path + ".enabled", true);
                settings.set(path + ".period", "MONTHLY");
                settings.set(path + ".value", 8);
                settings.set(path + ".reset_day", 15);
            }
            var dungeon = paper.dungeon(directory.resolve("crypt"), settings);
            var expected = ZonedDateTime.parse("2026-12-15T08:00:00+01:00[Europe/Amsterdam]").toInstant();
            assertEquals(expected, dungeon.getNextUnlockTime().toInstant());
            assertEquals(expected, dungeon.getNextLootTime().toInstant());
        }
    }
}
