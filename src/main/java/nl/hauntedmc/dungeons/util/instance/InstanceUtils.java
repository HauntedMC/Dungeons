package nl.hauntedmc.dungeons.util.instance;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;

/**
 * Helpers for parsing and evaluating instance variable placeholders.
 */
public final class InstanceUtils {

    /**
     * Resolves placeholders and evaluates one simple comparison expression.
     */
    public static boolean compareVars(PlayableInstance instance, String comparison) {
        String parsed = parseVars(instance, comparison);
        if (parsed == null) {
            return false;
        } else {
            Pattern comparisonPattern = Pattern.compile("([^<=>]*)(>=|<=|=+|>|<)([^<=>]*)");
            Matcher matcher = comparisonPattern.matcher(parsed);
            if (!matcher.find()) return false;
            String leftOperand = matcher.group(1).trim();
            String rightOperand = matcher.group(3).trim();
            String operator = matcher.group(2);
            switch (operator) {
                case "=":
                case "==":
                    try {
                        double leftNumber = Double.parseDouble(leftOperand);
                        double rightNumber = Double.parseDouble(rightOperand);
                        return leftNumber == rightNumber;
                    } catch (NumberFormatException exception) {
                        return leftOperand.equals(rightOperand);
                    }
                case ">":
                    {
                        double leftNumber = Double.parseDouble(leftOperand);
                        double rightNumber = Double.parseDouble(rightOperand);
                        return leftNumber > rightNumber;
                    }
                case ">=":
                    {
                        double leftNumber = Double.parseDouble(leftOperand);
                        double rightNumber = Double.parseDouble(rightOperand);
                        return leftNumber >= rightNumber;
                    }
                case "<":
                    {
                        double leftNumber = Double.parseDouble(leftOperand);
                        double rightNumber = Double.parseDouble(rightOperand);
                        return leftNumber < rightNumber;
                    }
                case "<=":
                    {
                        double leftNumber = Double.parseDouble(leftOperand);
                        double rightNumber = Double.parseDouble(rightOperand);
                        return leftNumber <= rightNumber;
                    }
                default:
                    return false;
            }
        }
    }

    /** Replaces all {@code <variable>} placeholders in text with instance variable values. */
    public static String parseVars(PlayableInstance instance, String text) {
        if (text == null) return null;
        Pattern variablePattern = Pattern.compile("<[^<>]*>");
        Matcher matcher = variablePattern.matcher(text);

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
