package nl.hauntedmc.dungeons.generation.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.generation.room.Connector;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;
import nl.hauntedmc.dungeons.support.HeadlessMenus;
import nl.hauntedmc.dungeons.util.math.RandomCollection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LayoutCloneTest {
    @Test
    void connectorMenusWaitForLayoutBindingAndClonesBuildIndependentControls() {
        var dungeon = mock(BranchingDungeon.class);
        when(dungeon.getRuntime()).thenReturn(mock(DungeonsRuntime.class));
        try (var _ = new HeadlessMenus()) {
            var layout = new ConnectorExpansionLayout(dungeon, TestLayout.testConfig());
            verify(dungeon, never()).getLayout();
            when(dungeon.getLayout()).thenReturn(layout);
            var menu = layout.getConnectorEditMenu();
            verify(menu, times(8)).addMenuItem(any());
            assertSame(menu, layout.getConnectorEditMenu());
            var clone = layout.clone();
            assertNotSame(menu, clone.getConnectorEditMenu());
        }
    }

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
