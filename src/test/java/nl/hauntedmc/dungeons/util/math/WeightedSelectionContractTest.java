package nl.hauntedmc.dungeons.util.math;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class WeightedSelectionContractTest {
    @Test
    void weightedIntervalsSelectExactlyAtBoundariesAndClearingAllowsSafeReuse() {
        double[] draws = {0, 0.249999, 0.25, 0.999999};
        Random source = new Random() {
            int index;
            @Override public double nextDouble() { return draws[index++]; }
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
        assertEquals(java.util.List.of("new room"), pool.values().stream().toList());
    }
}
