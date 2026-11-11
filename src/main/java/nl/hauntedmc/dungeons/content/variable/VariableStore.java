package nl.hauntedmc.dungeons.content.variable;

import java.math.BigDecimal;
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

    /** Returns the string. */
    public String getString(String key) {
        String value = this.values.get(key);
        if (value == null) {
            return null;
        }
        try {
            BigDecimal normalized = new BigDecimal(value).stripTrailingZeros();
            // Keep extreme exponents compact rather than allocating enormous display strings.
            return Math.abs((long) normalized.scale()) > 1000
                    ? normalized.toString()
                    : normalized.toPlainString();
        } catch (NumberFormatException exception) {
            return value;
        }
    }

    /** Returns the int. */
    public int getInt(String key) {
        try {
            return Integer.parseInt(this.values.get(key));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /** Returns the double. */
    public double getDouble(String key) {
        String value = this.values.get(key);
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    /** Performs set. */
    public void set(String key, String value) {
        this.values.put(key, value);
    }

    /** Performs set. */
    public void set(String key, int value) {
        this.values.put(key, String.valueOf(value));
    }

    /** Performs set. */
    public void set(String key, double value) {
        this.values.put(key, String.valueOf(value));
    }

    /** Performs add. */
    public void add(String key, double value) {
        double current = this.getDouble(key);
        this.values.put(key, String.valueOf(current + value));
    }

    /** Performs subtract. */
    public void subtract(String key, double value) {
        double current = this.getDouble(key);
        this.values.put(key, String.valueOf(current - value));
    }
}
