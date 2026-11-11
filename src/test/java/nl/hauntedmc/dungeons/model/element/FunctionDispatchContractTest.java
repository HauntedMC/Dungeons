package nl.hauntedmc.dungeons.model.element;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.junit.jupiter.api.Test;

class FunctionDispatchContractTest {
    @Test
    void onlyTheBoundTriggerCanExecuteAFunctionAndCancellationPreventsAllEffects() {
        try (var _ = new PaperBoundary()) {
            var function = new RecordingFunction();
            var bound = mock(DungeonTrigger.class);
            var unrelated = mock(DungeonTrigger.class);
            var source = mock(DungeonPlayerSession.class);
            when(source.getInstance()).thenReturn(mock(PlayableInstance.class));
            function.setTrigger(bound);
            function.setTargetType(FunctionTargetType.PLAYER);
            var cancelled = new TriggerFireEvent(source, bound);
            cancelled.setCancelled(true);
            function.execute(cancelled);
            function.execute(new TriggerFireEvent(source, unrelated));
            assertEquals(0, function.calls);
            function.execute(new TriggerFireEvent(source, bound));
            assertEquals(1, function.calls);
            assertEquals(List.of(source), function.targets);
        }
    }

    @Test
    void foreignInstanceEventsAndDepartedTargetsCannotReachFunctionEffects() {
        try (var paper = new PaperBoundary()) {
            var function = new RecordingFunction();
            var bound = mock(DungeonTrigger.class);
            function.setTrigger(bound);
            function.setTargetType(FunctionTargetType.PLAYER);
            var run = mock(PlayableInstance.class);
            var other = mock(PlayableInstance.class);
            when(run.getEnvironment()).thenReturn(paper.runtime.environment());
            function.setInstance(run);
            function.execute(new TriggerFireEvent(other, bound));
            function.execute(null);
            assertEquals(0, function.calls);
            var source = mock(DungeonPlayerSession.class);
            when(source.getInstance()).thenReturn(run);
            var event = new TriggerFireEvent(source, bound);
            when(source.getInstance()).thenReturn(other);
            function.execute(event);
            assertEquals(List.of(), function.targets);
        }
    }

    static class RecordingFunction extends DungeonFunction {
        int calls;
        List<DungeonPlayerSession> targets = new ArrayList<>();

        RecordingFunction() {
            super("recording");
        }

        @Override
        public void runFunction(TriggerFireEvent event, List<DungeonPlayerSession> targets) {
            calls++;
            this.targets = new ArrayList<>(targets);
        }

        @Override
        public MenuButton buildMenuButton() {
            return null;
        }

        @Override
        public void buildHotbarMenu() {}
    }
}
