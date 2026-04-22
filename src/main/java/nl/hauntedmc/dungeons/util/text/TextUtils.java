package nl.hauntedmc.dungeons.util.text;

import java.util.Locale;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;

/** Text formatting and lightweight timing helpers. */
public final class TextUtils {

    /** Converts underscore-separated identifiers to title-case words. */
    public static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String[] parts = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder output = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }

            if (!output.isEmpty()) {
                output.append(' ');
            }

            output.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                output.append(part.substring(1));
            }
        }

        return output.toString();
    }

    /** Runs a task and logs elapsed milliseconds. */
    public static void runTimed(String taskName, Runnable task) {
        long start = System.currentTimeMillis();
        task.run();
        RuntimeContext.logger()
                .info("Completed '{}' in {}ms.", taskName, System.currentTimeMillis() - start);
    }
}
