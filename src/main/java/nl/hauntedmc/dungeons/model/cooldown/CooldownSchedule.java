package nl.hauntedmc.dungeons.model.cooldown;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;

/** Shared next-reset calculation for access cooldowns in the server time zone. */
public final class CooldownSchedule {
    private CooldownSchedule() {}

    public static Date nextReset(CooldownPeriod period, int value, int resetDay) {
        return Date.from(nextReset(period, value, resetDay, ZonedDateTime.now()).toInstant());
    }

    /** Calendar resets are strictly after now; a zero-minute timer expires immediately. */
    public static ZonedDateTime nextReset(
            CooldownPeriod period, int value, int resetDay, ZonedDateTime now) {
        if (period == CooldownPeriod.TIMER) {
            return now.plusMinutes(Math.max(0, value));
        }
        if (period == CooldownPeriod.HOURLY) {
            return now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        }
        LocalTime resetTime = LocalTime.of(Math.clamp(value, 0, 23), 0);
        LocalDate today = now.toLocalDate();
        if (period == CooldownPeriod.MONTHLY) {
            int day = Math.clamp(resetDay, 1, 31);
            ZonedDateTime candidate = monthlyReset(today, day, resetTime, now);
            return candidate.isAfter(now)
                    ? candidate
                    : monthlyReset(today.withDayOfMonth(1).plusMonths(1), day, resetTime, now);
        }
        if (period == CooldownPeriod.WEEKLY) {
            int calendarDay = Math.clamp(resetDay, 1, 7);
            DayOfWeek day = DayOfWeek.of(calendarDay == 1 ? 7 : calendarDay - 1);
            LocalDate date = today.with(TemporalAdjusters.nextOrSame(day));
            ZonedDateTime candidate = date.atTime(resetTime).atZone(now.getZone());
            return candidate.isAfter(now)
                    ? candidate
                    : date.plusWeeks(1).atTime(resetTime).atZone(now.getZone());
        }
        ZonedDateTime candidate = today.atTime(resetTime).atZone(now.getZone());
        return candidate.isAfter(now)
                ? candidate
                : today.plusDays(1).atTime(resetTime).atZone(now.getZone());
    }

    private static ZonedDateTime monthlyReset(
            LocalDate month, int day, LocalTime time, ZonedDateTime now) {
        return month.withDayOfMonth(Math.min(day, month.lengthOfMonth()))
                .atTime(time)
                .atZone(now.getZone());
    }
}
