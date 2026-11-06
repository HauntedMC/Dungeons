package nl.hauntedmc.dungeons.content.trigger.gate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class GateMechanicsTest {
    @Test
    void andGateRequiresAllChildrenResetsAfterFiringAndIgnoresOtherRuns() {
        try (var paper = new PaperBoundary()) {
            var run = mock(PlayableInstance.class);
            var otherRun = mock(PlayableInstance.class);
            when(run.getInstanceWorld()).thenReturn(mock(World.class));
            when(otherRun.getInstanceWorld()).thenReturn(mock(World.class));
            var first = mock(DungeonTrigger.class);
            var second = mock(DungeonTrigger.class);
            var unrelated = mock(DungeonTrigger.class);
            var source = mock(DungeonPlayerSession.class);
            when(source.getInstance()).thenReturn(run);
            var gate = new CountingAndGate();
            gate.setInstance(run);
            gate.addTrigger(first);
            gate.addTrigger(second);
            gate.onEnable();

            gate.onChildTriggered(new TriggerFireEvent(run, first));
            gate.onChildTriggered(new TriggerFireEvent(run, first));
            gate.onChildTriggered(new TriggerFireEvent(otherRun, second));
            gate.onChildTriggered(new TriggerFireEvent(run, unrelated));
            assertEquals(0, gate.fires);
            gate.onChildTriggered(new TriggerFireEvent(source, second));
            assertEquals(1, gate.fires);
            assertSame(source, gate.source);
            gate.onChildTriggered(new TriggerFireEvent(run, second));
            assertEquals(1, gate.fires);
            gate.onChildTriggered(new TriggerFireEvent(run, first));
            assertEquals(2, gate.fires);
        }
    }

    static final class CountingAndGate extends GateAndTrigger {
        int fires;
        DungeonPlayerSession source;
        @Override public void trigger(DungeonPlayerSession source) { this.fires++; this.source = source; }
    }
}
