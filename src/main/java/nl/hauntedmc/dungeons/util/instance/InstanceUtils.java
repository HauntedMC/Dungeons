package nl.hauntedmc.dungeons.util.instance;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;

/**
 * Helpers for parsing and evaluating instance variable placeholders.
 */
public final class InstanceUtils {
    private InstanceUtils() {}

    private static final Pattern COMPARISON_PATTERN = Pattern.compile("([^<=>]*)(>=|<=|=+|>|<)([^<=>]*)");
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("<[^<>]*>");

    /**
     * Resolves placeholders and evaluates one simple comparison expression.
     */
    public static boolean compareVars(PlayableInstance instance, String comparison) {
        String parsed = parseVars(instance, comparison);
        if (parsed == null) {
            return false;
        }
        Matcher matcher = COMPARISON_PATTERN.matcher(parsed);
        if (!matcher.find()) {
            return false;
        }
        String left = matcher.group(1).trim();
        String right = matcher.group(3).trim();
        return switch (matcher.group(2)) {
            case "=", "==" -> equalOperands(left, right);
            case ">" -> Double.parseDouble(left) > Double.parseDouble(right);
            case ">=" -> Double.parseDouble(left) >= Double.parseDouble(right);
            case "<" -> Double.parseDouble(left) < Double.parseDouble(right);
            case "<=" -> Double.parseDouble(left) <= Double.parseDouble(right);
            default -> false;
        };
    }

    private static boolean equalOperands(String left, String right) {
        try {
            return Double.parseDouble(left) == Double.parseDouble(right);
        } catch (NumberFormatException exception) {
            return left.equals(right);
        }
    }

    /** Replaces all {@code <variable>} placeholders in text with instance variable values. */
    public static String parseVars(PlayableInstance instance, String text) {
        if (text == null) return null;
        Matcher matcher = VARIABLE_PATTERN.matcher(text);

        while (matcher.find()) {
            String parsed = parseVar(instance, matcher.group(0));
            text = text.replace(matcher.group(0), Objects.requireNonNullElse(parsed, "null"));
        }

        return text;
    }

    /** Resolves one {@code <variable>} placeholder against instance variable storage. */
    public static String parseVar(PlayableInstance instance, String placeholder) {
        if (instance == null || placeholder == null) return null;
        String varName = placeholder.replace("<", "").replace(">", "");
        return instance.getInstanceVariables().getString(varName);
    }
}
