package nl.hauntedmc.dungeons.util.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InstanceUtilsTest {
    @Test
    void comparisonsPreserveNumericAndTextSemantics() {
        for (String expression :
                new String[] {"2 = 2.0", "hello == hello", "3 > 2", "3 >= 3", "2 < 3", "3 <= 3"}) {
            assertTrue(InstanceUtils.compareVars(null, expression), expression);
        }
        for (String expression :
                new String[] {
                    "2 == 3",
                    "Hello = hello",
                    "NaN = NaN",
                    "2 > 3",
                    "3 < 2",
                    "2 >= 3",
                    "3 <= 2",
                    "2 === 2",
                    "no operator"
                }) {
            assertFalse(InstanceUtils.compareVars(null, expression), expression);
        }
        assertFalse(InstanceUtils.compareVars(null, null));
        assertFalse(InstanceUtils.compareVars(null, "text > 2"));
        assertTrue(InstanceUtils.compareVars(null, "text != other"));
        assertFalse(InstanceUtils.compareVars(null, "2 != 2.0"));
        for (String invalid : new String[] {"2 > 1 < 4", "2 === 2", "2 => 1", "2 ! = 1"}) {
            assertFalse(InstanceUtils.compareVars(null, invalid), invalid);
        }
    }

    @Test
    void absentVariableValuesKeepTheirExistingNullText() {
        assertEquals("hello null null", InstanceUtils.parseVars(null, "hello <missing> <missing>"));
        assertNull(InstanceUtils.parseVars(null, null));
    }

    @Test
    void numericComparisonsKeepPrecisionBeyondDoubleIntegerAndExponentRanges() {
        assertFalse(InstanceUtils.compareVars(null, "9007199254740993 = 9007199254740992"));
        assertTrue(InstanceUtils.compareVars(null, "9007199254740993 > 9007199254740992"));
        assertTrue(InstanceUtils.compareVars(null, "1e400 > 1e399"));
        assertTrue(InstanceUtils.compareVars(null, "0.10000000000000001 > 0.1"));
        assertFalse(InstanceUtils.compareVars(null, "Infinity > 2"));
        assertFalse(InstanceUtils.compareVars(null, "NaN >= 0"));
        for (String invalid : new String[] {"=", " == ", "1 > ", " != 1"}) {
            assertFalse(InstanceUtils.compareVars(null, invalid));
        }
    }
}
