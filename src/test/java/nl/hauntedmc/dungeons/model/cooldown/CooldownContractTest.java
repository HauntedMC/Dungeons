package nl.hauntedmc.dungeons.model.cooldown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

class CooldownContractTest {

    @Test
    void relativeCooldownsPreserveCalendarDaysAndClampMonthEnds() {
        var previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Amsterdam"));
            var beforeDst = ZonedDateTime.parse("2026-03-28T12:00+01:00[Europe/Amsterdam]");
            var nextDay = CooldownPeriod.DAILY.fromDate(Date.from(beforeDst.toInstant()));
            assertEquals(beforeDst.plusDays(1).toInstant(), nextDay.toInstant());
            var monthEnd = ZonedDateTime.parse("2028-01-31T12:00+01:00[Europe/Amsterdam]");
            assertEquals(monthEnd.plusMonths(1).toInstant(),
                    CooldownPeriod.MONTHLY.fromDate(Date.from(monthEnd.toInstant())).toInstant());
            assertEquals(beforeDst.plusDays(7).toInstant(),
                    CooldownPeriod.WEEKLY.fromDate(Date.from(beforeDst.toInstant())).toInstant());
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    @Test
    void resetSchedulesHandleExactBoundariesMonthEndsLeapYearsAndDst() {
        record Case(CooldownPeriod period, int value, int day, String now, String expected) {}
        var cases =
                List.of(
                        new Case(
                                CooldownPeriod.TIMER,
                                30,
                                1,
                                "2026-11-20T10:15+01:00[Europe/Amsterdam]",
                                "2026-11-20T10:45+01:00[Europe/Amsterdam]"),
                        new Case(
                                CooldownPeriod.HOURLY,
                                0,
                                1,
                                "2026-11-20T10:15+01:00[Europe/Amsterdam]",
                                "2026-11-20T11:00+01:00[Europe/Amsterdam]"),
                        new Case(
                                CooldownPeriod.DAILY,
                                8,
                                1,
                                "2026-11-20T08:00+01:00[Europe/Amsterdam]",
                                "2026-11-21T08:00+01:00[Europe/Amsterdam]"),
                        new Case(
                                CooldownPeriod.WEEKLY,
                                8,
                                1,
                                "2026-11-21T10:00+01:00[Europe/Amsterdam]",
                                "2026-11-22T08:00+01:00[Europe/Amsterdam]"),
                        new Case(
                                CooldownPeriod.MONTHLY,
                                8,
                                31,
                                "2028-02-01T10:00+01:00[Europe/Amsterdam]",
                                "2028-02-29T08:00+01:00[Europe/Amsterdam]"),
                        new Case(
                                CooldownPeriod.MONTHLY,
                                8,
                                31,
                                "2027-02-28T09:00+01:00[Europe/Amsterdam]",
                                "2027-03-31T08:00+02:00[Europe/Amsterdam]"),
                        new Case(
                                CooldownPeriod.DAILY,
                                2,
                                1,
                                "2026-03-28T10:00+01:00[Europe/Amsterdam]",
                                "2026-03-29T03:00+02:00[Europe/Amsterdam]"),
                        new Case(
                                CooldownPeriod.HOURLY,
                                0,
                                1,
                                "2026-10-25T02:30+02:00[Europe/Amsterdam]",
                                "2026-10-25T02:00+01:00[Europe/Amsterdam]"));
        for (Case c : cases) {
            ZonedDateTime now = ZonedDateTime.parse(c.now());
            ZonedDateTime actual = CooldownSchedule.nextReset(c.period(), c.value(), c.day(), now);
            assertEquals(ZonedDateTime.parse(c.expected()), actual, c.toString());
            assertTrue(actual.isAfter(now), c.toString());
        }
    }

}
