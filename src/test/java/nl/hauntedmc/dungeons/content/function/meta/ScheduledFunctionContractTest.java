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
    @Test
    void disablingARetriggeredDelayCancelsEveryPendingActivation() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var child = mock(DungeonFunction.class);
            var delayed = new DelayedFunction();
            delayed.addFunction(child);
            delayed.runFunction(mock(TriggerFireEvent.class), List.of());
            delayed.runFunction(mock(TriggerFireEvent.class), List.of());
            delayed.onDisable();
            assertEquals(2, scheduler.jobs.size());
            assertTrue(scheduler.jobs.stream().allMatch(job -> job.cancelled));
            scheduler.jobs.forEach(CapturedScheduler.Job::run);
            verify(child, never()).runFunction(any(), anyList());
            verify(child).disable();
        }
    }
    @Test
    void repeatersUseAFiniteCountAndPreserveTargetsEvenWhenAChildMutatesItsArguments() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var child = mock(DungeonFunction.class);
            List<List<DungeonPlayerSession>> effects = new ArrayList<>();
            doAnswer(call -> {
                List<DungeonPlayerSession> targets = call.getArgument(1);
                effects.add(new ArrayList<>(targets));
                targets.clear();
                return null;
            }).when(child).runFunction(any(), anyList());
            var repeat = new RepeaterFunction(Map.of("maxRepeats", 2, "interval", 0));
            repeat.initializeFields();
            repeat.addFunction(child);
            var run = mock(nl.hauntedmc.dungeons.model.instance.PlayableInstance.class);
            var dungeon = mock(nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition.class);
            when(run.getDungeon()).thenReturn(dungeon);
            repeat.setInstance(run);
            repeat.setLocation(new org.bukkit.Location(null, 0, 64, 0));
            var player = mock(DungeonPlayerSession.class);
            repeat.runFunction(mock(TriggerFireEvent.class), List.of(player));
            var work = scheduler.jobs.getFirst();
            assertEquals(1, work.period);
            work.run(); work.run(); work.run(); work.run();
            assertEquals(List.of(List.of(player), List.of(player)), effects);
            assertTrue(work.cancelled);
        }
    }
    @Test
    void overlappingRepeatsHaveIndependentCountsAndDisableCancelsAllRemainingWork() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var child = mock(DungeonFunction.class);
            var repeat = new RepeaterFunction(Map.of("maxRepeats", 2));
            repeat.initializeFields();
            repeat.addFunction(child);
            var event = mock(TriggerFireEvent.class);
            repeat.runFunction(event, List.of());
            repeat.runFunction(event, List.of());
            for (int i = 0; i < 3; i++) {
                scheduler.jobs.get(0).run();
                scheduler.jobs.get(1).run();
            }
            verify(child, times(4)).runFunction(eq(event), anyList());
            assertTrue(scheduler.jobs.stream().allMatch(job -> job.cancelled));
            repeat.runFunction(event, List.of());
            repeat.runFunction(event, List.of());
            repeat.onDisable();
            assertTrue(scheduler.jobs.stream().allMatch(job -> job.cancelled));
            scheduler.jobs.forEach(CapturedScheduler.Job::run);
            verify(child, times(4)).runFunction(eq(event), anyList());
        }
    }
}
