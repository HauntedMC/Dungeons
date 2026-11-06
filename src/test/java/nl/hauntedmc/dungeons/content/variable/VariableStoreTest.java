package nl.hauntedmc.dungeons.content.variable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class VariableStoreTest {
    @Test
    void missingAndMalformedNumbersDefaultToZero() {
        VariableStore store = new VariableStore();
        assertNull(store.getString("missing"));
        assertEquals(0, store.getInt("missing"));
        assertEquals(0.0, store.getDouble("missing"));
        store.set("invalid", "text");
        assertEquals(0.0, store.getDouble("invalid"));
        store.set("null", (String) null);
        assertEquals(0.0, store.getDouble("null"));
    }

    @Test
    void arithmeticCanInitializeMissingVariables() {
        VariableStore store = new VariableStore();
        store.add("new", 2.5);
        assertEquals(2.5, store.getDouble("new"));
        store.subtract("other", 3.0);
        assertEquals(-3.0, store.getDouble("other"));
        assertEquals("-3", store.getString("other"));
    }
}
