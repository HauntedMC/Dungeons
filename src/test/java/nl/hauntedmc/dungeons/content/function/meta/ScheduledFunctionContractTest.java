package nl.hauntedmc.dungeons.content.function.meta;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.junit.jupiter.api.Test;

class ScheduledFunctionContractTest {
    @Test
    void delayedExecutionUsesItsConfiguredDelayAndCapturesTheOriginalTargets() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var child = mock(DungeonFunction.class);
            var delayed = new DelayedFunction(Map.of("delay", 12));
            delayed.initializeFields();
            delayed.addFunction(child);
            var event = mock(TriggerFireEvent.class);
            var player = mock(DungeonPlayerSession.class);
            var targets = new ArrayList<>(List.of(player));
            delayed.runFunction(event, targets);
            targets.clear();
            verify(child, never()).runFunction(any(), anyList());
            assertEquals(12, scheduler.jobs.getFirst().delay);
            scheduler.jobs.getFirst().run();
            verify(child).runFunction(eq(event), eq(List.of(player)));
        }
    }
}
