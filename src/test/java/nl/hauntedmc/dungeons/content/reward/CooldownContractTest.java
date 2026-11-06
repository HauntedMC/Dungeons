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
    void resetSchedulesHandleExactBoundariesMonthEndsLeapYearsAndDst() {
        record Case(CooldownPeriod period, int value, int day, String now, String expected) {}
        var cases = java.util.List.of(
            new Case(CooldownPeriod.TIMER, 30, 1, "2026-11-20T10:15+01:00[Europe/Amsterdam]", "2026-11-20T10:45+01:00[Europe/Amsterdam]"),
            new Case(CooldownPeriod.HOURLY, 0, 1, "2026-11-20T10:15+01:00[Europe/Amsterdam]", "2026-11-20T11:00+01:00[Europe/Amsterdam]"),
            new Case(CooldownPeriod.DAILY, 8, 1, "2026-11-20T08:00+01:00[Europe/Amsterdam]", "2026-11-21T08:00+01:00[Europe/Amsterdam]"),
            new Case(CooldownPeriod.WEEKLY, 8, 1, "2026-11-21T10:00+01:00[Europe/Amsterdam]", "2026-11-22T08:00+01:00[Europe/Amsterdam]"),
            new Case(CooldownPeriod.MONTHLY, 8, 31, "2028-02-01T10:00+01:00[Europe/Amsterdam]", "2028-02-29T08:00+01:00[Europe/Amsterdam]"),
            new Case(CooldownPeriod.MONTHLY, 8, 31, "2027-02-28T09:00+01:00[Europe/Amsterdam]", "2027-03-31T08:00+02:00[Europe/Amsterdam]"),
            new Case(CooldownPeriod.DAILY, 2, 1, "2026-03-28T10:00+01:00[Europe/Amsterdam]", "2026-03-29T03:00+02:00[Europe/Amsterdam]"),
            new Case(CooldownPeriod.HOURLY, 0, 1, "2026-10-25T02:30+02:00[Europe/Amsterdam]", "2026-10-25T02:00+01:00[Europe/Amsterdam]")
        );
        for (Case c : cases) {
            ZonedDateTime now = ZonedDateTime.parse(c.now());
            ZonedDateTime actual = CooldownSchedule.nextReset(c.period(), c.value(), c.day(), now);
            assertEquals(ZonedDateTime.parse(c.expected()), actual, c.toString());
            assertTrue(actual.isAfter(now), c.toString());
        }
    }


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
