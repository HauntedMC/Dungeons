package nl.hauntedmc.dungeons.util.time;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

/**
 * Time conversion and formatting helpers.
 */
public final class TimeUtils {

    /** Formats a date using configured plugin locale and human-friendly pattern. */
    public static String formatDate(Date date) {
        SimpleDateFormat format =
                new SimpleDateFormat("EEE, MMM d, hh:mm aaa z", LangUtils.getLocale());
        return format.format(date);
    }

    /** Converts short duration tokens such as {@code 5m} or {@code 2h} to a future timestamp. */
    public static Date convertDurationString(String durationString) {
        Pattern durationPattern = Pattern.compile("((^[0-9]*)([smhdwySMHDWY]))");
        Matcher matcher = durationPattern.matcher(durationString);
        if (matcher.find()) {
            String length = matcher.group(2);
            int duration = Integer.parseInt(length);
            String timeFrame = matcher.group(3);
            int multiplier = 1;
            String normalizedTimeFrame = timeFrame.toLowerCase();
            multiplier =
                    switch (normalizedTimeFrame) {
                        case "m" -> 60;
                        case "h" -> 3600;
                        case "d" -> 86400;
                        case "w" -> 604800;
                        case "y" -> 31557600;
                        default -> multiplier;
                    };

            return getFutureTimeInSeconds(duration * multiplier);
        } else {
            return null;
        }
    }

    /** Formats a duration in milliseconds as {@code HH:mm:ss}. */
    public static String formatDuration(long durationInMillis) {
        return DurationFormatUtils.formatDuration(durationInMillis, "HH:mm:ss");
    }

    /** Returns a timestamp offset by a number of seconds from now. */
    public static Timestamp getFutureTimeInSeconds(int seconds) {
        return new Timestamp(System.currentTimeMillis() + seconds * 1000L);
    }
}
