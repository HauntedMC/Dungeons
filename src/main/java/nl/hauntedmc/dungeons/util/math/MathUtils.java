package nl.hauntedmc.dungeons.util.math;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Numeric utility helpers for randomization and sign-aware arithmetic.
 */
public final class MathUtils {
    /** Rounds a decimal value to the requested number of places. */
    public static double round(double value, int places) {
        double scale = Math.pow(10.0, places);
        return Math.round(value * scale) / scale;
    }

    /** Returns one random value from the provided integers, or {@code -1} when empty. */
    public static int getRandomNumber(Integer... values) {
        return values.length == 0 ? -1 : values[getRandomNumberInRange(0, values.length - 1)];
    }

    /** Returns an inclusive random integer between min and max. */
    public static int getRandomNumberInRange(int min, int max) {
        if (min >= max) {
            return min;
        } else {
            return ThreadLocalRandom.current().nextInt(max - min + 1) + min;
        }
    }

    /** Returns true with the provided probability in range {@code [0.0, 1.0]}. */
    public static boolean getRandomBoolean(double chance) {
        return Math.random() < chance;
    }

    /** Returns whether the double value is represented with a negative sign bit. */
    public static boolean isNegative(double d) {
        return Double.doubleToRawLongBits(d) < 0L;
    }

    /** Adds velocity while preserving direction sign semantics. */
    public static double addRespectedVelocity(double start, double addition) {
        return isNegative(start) ? start - addition : start + addition;
    }
}
