package nl.hauntedmc.dungeons.generation.layout;

import static org.junit.jupiter.api.Assertions.*;
import nl.hauntedmc.dungeons.generation.room.*;
import nl.hauntedmc.dungeons.util.math.RandomCollection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LayoutCloneTest {
    @Test
    void clonesHaveIndependentDiagnosticsAndEmptyGenerationCounts() {
        TestLayout source = new TestLayout();
        source.recordFailure(GenerationFailureReason.INVALID_CONFIGURATION);
        source.roomCount = 7;
        source.totalRooms = 9;
        source.queuedRoomCount = 3;
        source.cancelled = true;

        TestLayout clone = (TestLayout) source.clone();
        assertEquals(1, source.failureReasons.get(GenerationFailureReason.INVALID_CONFIGURATION));
        assertTrue(clone.failureReasons.isEmpty());
        clone.recordFailure(GenerationFailureReason.NO_VALID_ORIENTATION);
        assertFalse(
                source.failureReasons.containsKey(GenerationFailureReason.NO_VALID_ORIENTATION));
        assertEquals(0, clone.roomCount);
        assertEquals(0, clone.totalRooms);
        assertEquals(0, clone.queuedRoomCount);
        assertFalse(clone.cancelled);
        assertEquals(7, source.roomCount);
        assertTrue(source.cancelled);
    }

    private static final class TestLayout extends Layout {
        TestLayout() {
            super(null, testConfig());
        }

        private static YamlConfiguration testConfig() {
            var config = new YamlConfiguration();
            config.set("generator.seal_open_connectors_with", "AIR");
            return config;
        }

        @Override
        public void initializeConnectorEditMenu() {}

        @Override
        protected boolean tryConnectors(InstanceRoom room) {
            return false;
        }

        @Override
        protected RandomCollection<BranchingRoomDefinition> filterRooms(
                Connector connector, InstanceRoom from) {
            return new RandomCollection<>();
        }
    }
}
