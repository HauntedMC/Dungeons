package nl.hauntedmc.dungeons.util.text;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Legacy and hex color translation helpers. */
public final class ColorUtils {

    private static final char SECTION_CHAR = '§';
    private static final String LEGACY_COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final Pattern ANGLE_HEX_PATTERN = Pattern.compile("<(#[a-fA-F0-9]{6})>");
    private static final Pattern BRACE_HEX_PATTERN = Pattern.compile("\\{(#[a-fA-F0-9]{6})}");

    /** Converts legacy ampersand color codes to section-symbol codes. */
    public static String colorize(String s) {
        if (s == null) {
            return null;
        }

        char[] characters = s.toCharArray();
        for (int i = 0; i < characters.length - 1; i++) {
            if (characters[i] == '&' && LEGACY_COLOR_CODES.indexOf(characters[i + 1]) >= 0) {
                characters[i] = SECTION_CHAR;
                characters[i + 1] = Character.toLowerCase(characters[i + 1]);
            }
        }

        return new String(characters);
    }

    /** Applies hex and legacy color translation. */
    public static String fullColor(String s) {
        if (s == null) {
            return null;
        }
        return colorize(replaceHexColors(s, ANGLE_HEX_PATTERN));
    }

    /** Converts a {@code #RRGGBB} string to Bukkit color. */
    public static org.bukkit.Color hexToColor(String colorStr) {
        return org.bukkit.Color.fromRGB(
                Integer.valueOf(colorStr.substring(1, 3), 16),
                Integer.valueOf(colorStr.substring(3, 5), 16),
                Integer.valueOf(colorStr.substring(5, 7), 16));
    }

    /** Replaces matched hex tokens with legacy hex color sequences. */
    public static String replaceHexColors(String input, Pattern pattern) {
        if (input == null) {
            return null;
        }

        Matcher matcher = pattern.matcher(input);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement(toLegacyHex(matcher.group(1))));
        }

        matcher.appendTail(output);
        return output.toString();
    }

    /** Converts {@code {#RRGGBB}} style tokens to {@code <#RRGGBB>} style tokens. */
    public static String convertHexBraces(String input) {
        if (input == null) {
            return null;
        }

        Matcher matcher = BRACE_HEX_PATTERN.matcher(input);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(output, Matcher.quoteReplacement("<" + matcher.group(1) + ">"));
        }

        matcher.appendTail(output);
        return output.toString();
    }

    /** Converts one {@code #RRGGBB} token to Bukkit legacy hex format. */
    public static String toLegacyHex(String hex) {
        StringBuilder output = new StringBuilder().append(SECTION_CHAR).append('x');
        for (char character : hex.substring(1).toLowerCase(Locale.ROOT).toCharArray()) {
            output.append(SECTION_CHAR).append(character);
        }

        return output.toString();
    }

    /** Returns whether a char is a legacy color/reset code. */
    public static boolean isLegacyColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
    }

    /** Returns trailing active legacy colors from the end of a string. */
    public static String getLastColors(String input) {
        StringBuilder result = new StringBuilder();
        int length = input.length();

        for (int i = length - 1; i > -1; i--) {
            char section = input.charAt(i);
            if (section == SECTION_CHAR && i < length - 1) {
                char c = input.charAt(i + 1);
                if (isLegacyColorCode(c)) {
                    result.insert(0, "§" + c);
                    if ("0123456789AaBbCcDdEeFfRr".indexOf(c) != -1) {
                        break;
                    }
                }
            }
        }

        return result.toString();
    }
}
