package nl.hauntedmc.dungeons.util.metadata;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class PersistedFieldSupportTest {
    enum Mode { FIRST, SECOND }
    private Mode mode;

    @Test
    void enumCoercionPreservesExactNamesAndSkipsInvalidValues() throws Exception {
        var metadata = new PersistedFieldSupport.FieldMetadata(
                getClass().getDeclaredField("mode"), false);
        assertSame(Mode.SECOND, PersistedFieldSupport.coerceSimpleValue(metadata, "SECOND"));
        assertSame(Mode.FIRST, PersistedFieldSupport.coerceSimpleValue(metadata, Mode.FIRST));
        assertSame(PersistedFieldSupport.SKIP, PersistedFieldSupport.coerceSimpleValue(metadata, "second"));
        assertSame(PersistedFieldSupport.SKIP, PersistedFieldSupport.coerceSimpleValue(metadata, "unknown"));
    }
}
