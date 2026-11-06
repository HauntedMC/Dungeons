package nl.hauntedmc.dungeons.util.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MathUtilsTest {
    @Test
    void supportsInclusiveRangesWhoseWidthOverflowsAnInt() {
        for (int i = 0; i < 1000; i++) {
            int positive = MathUtils.getRandomNumberInRange(0, Integer.MAX_VALUE);
            assertTrue(positive >= 0);
            int negative = MathUtils.getRandomNumberInRange(Integer.MIN_VALUE, 0);
            assertTrue(negative <= 0);
            MathUtils.getRandomNumberInRange(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int nearMaximum = MathUtils.getRandomNumberInRange(Integer.MAX_VALUE - 1, Integer.MAX_VALUE);
            assertTrue(nearMaximum >= Integer.MAX_VALUE - 1);
        }
    }

    @Test
    void preservesEqualAndDescendingBoundBehavior() {
        assertEquals(7, MathUtils.getRandomNumberInRange(7, 7));
        assertEquals(7, MathUtils.getRandomNumberInRange(7, 2));
    }

    @Test
    void openEndedRangesCanBeRandomized() {
        RangedNumber range = new RangedNumber("0+");
        for (int i = 0; i < 100; i++) {
            assertTrue(range.randomizeAsInt() >= 0);
        }
    }

    @Test
    void respectsProbabilityEndpoints() {
        assertFalse(MathUtils.getRandomBoolean(0));
        assertTrue(MathUtils.getRandomBoolean(1));
    }
}
