package nl.hauntedmc.dungeons.util.time;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.hauntedmc.dungeons.localization.LangUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

/** Time conversion and formatting helpers. */
public final class TimeUtils {
    private TimeUtils() {}

    private static final Pattern DURATION_PATTERN = Pattern.compile("([0-9]+)([smhdwySMHDWY])");

    /** Formats a date using configured plugin locale and human-friendly pattern. */
    public static String formatDate(Date date) {
        return DateTimeFormatter.ofPattern("EEE, MMM d, hh:mm a z", LangUtils.getLocale())
                .withZone(ZoneId.systemDefault())
                .format(date.toInstant());
    }

    /** Converts short duration tokens such as {@code 5m} or {@code 2h} to a future timestamp. */
    public static Date convertDurationString(String durationString) {
        if (durationString == null) {
            return null;
        }
        Matcher matcher = DURATION_PATTERN.matcher(durationString);
        if (!matcher.matches()) {
            return null;
        }

        long multiplier =
                switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                    case "m" -> 60;
                    case "h" -> 3600;
                    case "d" -> 86400;
                    case "w" -> 604800;
                    case "y" -> 31557600;
                    default -> 1;
                };
        try {
            long seconds = Math.multiplyExact(Long.parseLong(matcher.group(1)), multiplier);
            return getFutureTimeInSeconds(seconds);
        } catch (NumberFormatException | ArithmeticException exception) {
            return null;
        }
    }

    /** Formats a duration in milliseconds as {@code HH:mm:ss}. */
    public static String formatDuration(long durationInMillis) {
        return DurationFormatUtils.formatDuration(durationInMillis, "HH:mm:ss");
    }

    /** Returns a timestamp offset by a number of seconds from now. */
    public static Timestamp getFutureTimeInSeconds(int seconds) {
        return getFutureTimeInSeconds((long) seconds);
    }

    /** Returns a timestamp offset by seconds, rejecting arithmetic overflow. */
    public static Timestamp getFutureTimeInSeconds(long seconds) {
        return new Timestamp(
                Math.addExact(System.currentTimeMillis(), Math.multiplyExact(seconds, 1000L)));
    }
}
