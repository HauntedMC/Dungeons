package nl.hauntedmc.dungeons.util.time;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;
import org.junit.jupiter.api.Test;

class TimeUtilsTest {
    @Test
    void supportsEveryUnitAndUppercaseTokensWithoutIntegerOverflow() {
        assertOffset("5s", 5);
        assertOffset("2M", 120);
        assertOffset("2h", 7200);
        assertOffset("3D", 259200);
        assertOffset("2w", 1209600);
        assertOffset("100y", 3155760000L);
        assertOffset("0s", 0);
    }

    @Test
    void rejectsMalformedAndOverflowingDurations() {
        for (String value : new String[] {null, "", "m", "5minutes", "5m junk", "-1h", "1.5h", " 5m",
                "9999999999999999999999999s", "9223372036854775807y", "9223372036854775807s"}) {
            assertNull(TimeUtils.convertDurationString(value), value);
        }
    }

    private static void assertOffset(String token, long seconds) {
        long before = System.currentTimeMillis();
        Date result = TimeUtils.convertDurationString(token);
        long after = System.currentTimeMillis();
        assertTrue(result.getTime() >= before + seconds * 1000L);
        assertTrue(result.getTime() <= after + seconds * 1000L);
    }
}
