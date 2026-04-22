package nl.hauntedmc.dungeons.util.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RangedNumberTest {

    @Test
    void constructor_normalizesDescendingRanges() {
        RangedNumber range = new RangedNumber("8-2");

        assertEquals(2.0, range.getMin());
        assertEquals(8.0, range.getMax());
        assertEquals("2-8", range.toIntString());
    }

    @Test
    void constructor_supportsOpenEndedRanges() {
        RangedNumber range = new RangedNumber("4+");

        assertEquals(4.0, range.getMin());
        assertEquals(-1.0, range.getMax());
        assertTrue(range.isValueWithin(4.0));
        assertTrue(range.isValueWithin(250.0));
        assertFalse(range.isValueWithin(3.9));
    }

    @Test
    void constructor_supportsComparators() {
        RangedNumber lessThanRange = new RangedNumber("<5");
        RangedNumber greaterThanRange = new RangedNumber(">2");

        assertEquals(0.0, lessThanRange.getMin());
        assertEquals(4.0, lessThanRange.getMax());
        assertEquals(3.0, greaterThanRange.getMin());
        assertEquals(-1.0, greaterThanRange.getMax());
    }

    @Test
    void constructor_usesSafeDefaultForNullInput() {
        RangedNumber range = new RangedNumber((String) null);

        assertEquals(0.0, range.getMin());
        assertEquals(-1.0, range.getMax());
        assertEquals("0+", range.toIntString());
    }
}
