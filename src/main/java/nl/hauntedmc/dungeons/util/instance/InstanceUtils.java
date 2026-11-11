package nl.hauntedmc.dungeons.util.instance;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;

/** Helpers for parsing and evaluating instance variable placeholders. */
public final class InstanceUtils {
    private InstanceUtils() {}

    private static final Pattern COMPARISON_PATTERN =
            Pattern.compile("([^<=>!]*)(>=|<=|!=|==|=|>|<)([^<=>!]*)");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("<[^<>]*>");

    /** Resolves placeholders and evaluates one simple comparison expression. */
    public static boolean compareVars(PlayableInstance instance, String comparison) {
        String parsed = parseVars(instance, comparison);
        if (parsed == null) {
            return false;
        }
        Matcher matcher = COMPARISON_PATTERN.matcher(parsed);
        if (!matcher.matches()) {
            return false;
        }
        String left = matcher.group(1).trim();
        String right = matcher.group(3).trim();
        if (left.isEmpty() || right.isEmpty()) return false;
        try {
            return switch (matcher.group(2)) {
                case "=", "==" -> equalOperands(left, right);
                case "!=" -> !equalOperands(left, right);
                case ">" -> compareNumbers(left, right) > 0;
                case ">=" -> compareNumbers(left, right) >= 0;
                case "<" -> compareNumbers(left, right) < 0;
                case "<=" -> compareNumbers(left, right) <= 0;
                default -> false;
            };
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static int compareNumbers(String left, String right) {
        return new BigDecimal(left).compareTo(new BigDecimal(right));
    }

    private static boolean equalOperands(String left, String right) {
        if (left.matches("[+-]?(NaN|Infinity)") || right.matches("[+-]?(NaN|Infinity)"))
            return false;
        try {
            return compareNumbers(left, right) == 0;
        } catch (NumberFormatException exception) {
            return left.equals(right);
        }
    }

    /** Replaces all {@code <variable>} placeholders in text with instance variable values. */
    public static String parseVars(PlayableInstance instance, String text) {
        if (text == null) return null;
        Matcher matcher = VARIABLE_PATTERN.matcher(text);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String parsed = parseVar(instance, matcher.group(0));
            matcher.appendReplacement(
                    result, Matcher.quoteReplacement(Objects.requireNonNullElse(parsed, "null")));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Resolves one {@code <variable>} placeholder against instance variable storage. */
    public static String parseVar(PlayableInstance instance, String placeholder) {
        if (instance == null || placeholder == null) return null;
        String varName = placeholder.replace("<", "").replace(">", "");
        return instance.getInstanceVariables().getString(varName);
    }
}
