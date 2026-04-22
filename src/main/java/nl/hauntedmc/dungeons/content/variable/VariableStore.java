package nl.hauntedmc.dungeons.content.variable;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight string-backed store for instance variables.
 *
 * <p>Values are persisted as strings, but convenience accessors expose numeric views when the
 * stored value can be parsed.
 */
public class VariableStore {
    private final Map<String, String> values = new HashMap<>();

    /**
     * Returns the string.
     */
    public String getString(String key) {
        String value = this.values.get(key);
        if (value == null) {
            return null;
        } else {
            // Numeric values are normalized before display so editor-facing strings do not show
            // unnecessary decimal suffixes such as "5.0".
            Double numeric = parseDoubleOrNull(value);
            if (numeric != null) {
                if (numeric % 1.0 == 0.0) {
                    value = String.valueOf(numeric.intValue());
                } else {
                    value = String.valueOf(numeric);
                }
            }

            return value;
        }
    }

    /**
     * Returns the int.
     */
    public int getInt(String key) {
        try {
            return Integer.parseInt(this.values.get(key));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /**
     * Returns the double.
     */
    public double getDouble(String key) {
        try {
            return Double.parseDouble(this.values.get(key));
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    /**
     * Performs set.
     */
    public void set(String key, String value) {
        this.values.put(key, value);
    }

    /**
     * Performs set.
     */
    public void set(String key, int value) {
        this.values.put(key, String.valueOf(value));
    }

    /**
     * Performs set.
     */
    public void set(String key, double value) {
        this.values.put(key, String.valueOf(value));
    }

    /**
     * Performs add.
     */
    public void add(String key, double value) {
        double current = this.getDouble(key);
        this.values.put(key, String.valueOf(current + value));
    }

    /**
     * Performs subtract.
     */
    public void subtract(String key, double value) {
        double current = this.getDouble(key);
        this.values.put(key, String.valueOf(current - value));
    }

    /**
     * Performs parse double or null.
     */
    private static Double parseDoubleOrNull(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
