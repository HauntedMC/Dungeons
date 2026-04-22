package nl.hauntedmc.dungeons.content.reward;

import java.util.Calendar;
import java.util.Date;

/**
 * Named cooldown cadences used by reward functions.
 *
 * <p>Each constant stores the {@link Calendar} field it advances and the default amount used when
 * no explicit override is supplied.
 */
public enum CooldownPeriod {
    TIMER(12),
    HOURLY(10),
    DAILY(5),
    WEEKLY(5, 7),
    MONTHLY(2);

    public final int period;
    public final int amount;

    /**
     * Creates a new cooldown period instance.
     */
    CooldownPeriod(int period) {
        this.period = period;
        this.amount = 1;
    }

    /**
     * Creates a new cooldown period instance.
     */
    CooldownPeriod(int period, int amount) {
        this.period = period;
        this.amount = amount;
    }

    /**
     * Creates a value from now.
     */
    public Date fromNow(int amount) {
        Calendar cal = Calendar.getInstance();
        cal.add(this.period, amount);
        return cal.getTime();
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
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(this.period, amount);
        return cal.getTime();
    }

    /**
     * Creates a value from index.
     */
    public static CooldownPeriod fromIndex(int index) {
                return switch (index) {
            case 1 -> HOURLY;
            case 2 -> DAILY;
            case 3 -> WEEKLY;
            case 4 -> MONTHLY;
            default -> TIMER;
        };
    }
}
