package nl.hauntedmc.dungeons.util.instance;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class InstanceUtilsTest {
    @Test
    void comparisonsPreserveNumericAndTextSemantics() {
        for (String expression : new String[] {"2 = 2.0", "hello == hello", "3 > 2", "3 >= 3", "2 < 3", "3 <= 3"}) {
            assertTrue(InstanceUtils.compareVars(null, expression), expression);
        }
        for (String expression : new String[] {"2 == 3", "Hello = hello", "NaN = NaN", "2 > 3", "3 < 2", "2 >= 3", "3 <= 2", "2 === 2", "no operator"}) {
            assertFalse(InstanceUtils.compareVars(null, expression), expression);
        }
        assertFalse(InstanceUtils.compareVars(null, null));
        assertThrows(NumberFormatException.class, () -> InstanceUtils.compareVars(null, "text > 2"));
    }

    @Test
    void absentVariableValuesKeepTheirExistingNullText() {
        assertEquals("hello null null", InstanceUtils.parseVars(null, "hello <missing> <missing>"));
        assertNull(InstanceUtils.parseVars(null, null));
    }
}
