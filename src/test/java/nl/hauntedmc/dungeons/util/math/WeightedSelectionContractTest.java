package nl.hauntedmc.dungeons.util.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

class WeightedSelectionContractTest {
    @Test
    void weightedIntervalsSelectExactlyAtBoundariesAndClearingAllowsSafeReuse() {
        double[] draws = {0, 0.249999, 0.25, 0.999999};
        Random source =
                new Random() {
                    int index;

                    @Override
                    public double nextDouble() {
                        return draws[index++];
                    }
                };
        var pool = new RandomCollection<String>(source);
        pool.add(1, "common").add(3, "rare");
        assertEquals("common", pool.next());
        assertEquals("common", pool.next());
        assertEquals("rare", pool.next());
        assertEquals("rare", pool.next());
        pool.clear();
        assertNull(pool.next());
        assertTrue(pool.isEmpty());
        pool.add(10, "new room");
        assertEquals(List.of("new room"), pool.values().stream().toList());
    }

    @Test
    void invalidOrUnrepresentableWeightsCannotPoisonValidRoomCandidates() {
        var pool = new RandomCollection<String>(new Random(1));
        pool.add(1, "safe")
                .add(Double.NaN, "nan")
                .add(Double.POSITIVE_INFINITY, "infinity")
                .add(-1, "negative")
                .add(0, "zero")
                .add(1, null);
        assertEquals(1, pool.size());
        assertEquals("safe", pool.next());
        var huge = new RandomCollection<String>(new Random(1));
        huge.add(Double.MAX_VALUE, "large valid")
                .add(Double.MAX_VALUE, "overflow")
                .add(1, "rounded away");
        assertEquals(1, huge.size());
        assertEquals("large valid", huge.next());
    }
}
