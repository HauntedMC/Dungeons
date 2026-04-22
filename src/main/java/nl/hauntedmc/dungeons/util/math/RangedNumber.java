package nl.hauntedmc.dungeons.util.math;

/**
 * Numeric range parser and randomizer.
 *
 * <p>Supports forms such as {@code 5}, {@code 2-8}, {@code 2to8}, {@code 10+}, {@code <=5},
 * and {@code >=3}.</p>
 */
public class RangedNumber {
    private double min;
    private double max;

    /** Parses a textual range expression into min/max bounds. */
    public RangedNumber(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("null")) {
            raw = "0+";
        }

        String[] split = null;
        if (raw.contains("to")) {
            split = raw.split("to");
        } else if (!raw.startsWith("-") && raw.contains("-")) {
            split = raw.split("-");
        } else {
            if (raw.endsWith("+")) {
                split = raw.split("\\+");
                this.min = Double.parseDouble(split[0]);
                this.max = -1.0;
                return;
            }

            if (raw.startsWith("<=")) {
                String sub = raw.substring(2);
                this.min = 0.0;
                this.max = Double.parseDouble(sub);
                return;
            }

            if (raw.startsWith(">=")) {
                String sub = raw.substring(2);
                this.min = Double.parseDouble(sub);
                this.max = -1.0;
                return;
            }

            if (raw.startsWith("<")) {
                String sub = raw.substring(1);
                this.min = 0.0;
                this.max = Double.parseDouble(sub) - 1.0;
                return;
            }

            if (raw.startsWith(">")) {
                String sub = raw.substring(1);
                this.min = Double.parseDouble(sub) + 1.0;
                this.max = -1.0;
                return;
            }
        }

        if (split == null) {
            this.min = Double.parseDouble(raw);
            this.max = this.min;
        } else {
            this.min = Double.parseDouble(split[0]);
            this.max = Double.parseDouble(split[1]);
            if (this.min > this.max) {
                double stored = this.min;
                this.min = this.max;
                this.max = stored;
            }
        }
    }

    /** Creates a range directly from min/max bounds. */
    public RangedNumber(double min, double max) {
        this.min = min;
        this.max = max;
        if (max != -1.0) {
            if (this.min > this.max) {
                double stored = this.min;
                this.min = this.max;
                this.max = stored;
            }
        }
    }

    /** Returns one randomized integer within this range representation. */
    public int randomizeAsInt() {
        return (int) this.min == (int) this.max
                ? (int) this.min
                : MathUtils.getRandomNumberInRange(
                        (int) this.min, (int) (this.max == -1.0 ? 2.147483647E9 : this.max));
    }

    /** Returns a normalized string representation of this range. */
    @Override
    public String toString() {
        if (this.min == this.max) {
            return String.valueOf(this.min);
        } else {
            return this.max == -1.0 ? this.min + "+" : this.min + "-" + this.max;
        }
    }

    /** Returns a normalized integer-style representation of this range. */
    public String toIntString() {
        if (this.min == this.max) {
            return String.valueOf((int) this.min);
        } else {
            return this.max == -1.0 ? (int) this.min + "+" : (int) this.min + "-" + (int) this.max;
        }
    }

    /** Returns whether a numeric value is within this range. */
    public boolean isValueWithin(double value) {
        return this.max == -1.0 ? value >= this.min : value >= this.min && value <= this.max;
    }

    /** Returns the lower bound. */
    public double getMin() {
        return this.min;
    }

    /** Returns the upper bound, or {@code -1.0} for open-ended ranges. */
    public double getMax() {
        return this.max;
    }

    /** Updates the upper bound value. */
    public void setMax(double max) {
        this.max = max;
    }
}
