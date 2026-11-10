package nl.hauntedmc.dungeons.model.cooldown;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Named cooldown cadences used by access checks.
 *
 * <p>Each constant stores the {@link ChronoUnit} unit it advances and the default amount used when
 * no explicit override is supplied.
 */
public enum CooldownPeriod {
    TIMER(ChronoUnit.MINUTES),
    HOURLY(ChronoUnit.HOURS),
    DAILY(ChronoUnit.DAYS),
    WEEKLY(ChronoUnit.DAYS, 7),
    MONTHLY(ChronoUnit.MONTHS);

    public final ChronoUnit period;
    public final int amount;

    /**
     * Creates a new cooldown period instance.
     */
    CooldownPeriod(ChronoUnit period) {
        this.period = period;
        this.amount = 1;
    }

    /**
     * Creates a new cooldown period instance.
     */
    CooldownPeriod(ChronoUnit period, int amount) {
        this.period = period;
        this.amount = amount;
    }

    /**
     * Creates a value from now.
     */
    public Date fromNow(int amount) {
        return Date.from(ZonedDateTime.now().plus(amount, this.period).toInstant());
    }

    /**
     * Creates a value from date.
     */
    public Date fromDate(Date date) {
        return this.fromDate(date, this.amount);
    }

    /**
     * Creates a value from date.
     */
    public Date fromDate(Date date, int amount) {
        return Date.from(
                date.toInstant().atZone(ZoneId.systemDefault()).plus(amount, this.period).toInstant());
    }


}
