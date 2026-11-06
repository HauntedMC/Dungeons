package nl.hauntedmc.dungeons.model.element;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.CapturedScheduler.Job;
import nl.hauntedmc.dungeons.support.PaperBoundary;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicBoolean;

class TriggerLifecycleContractTest {
    @Test
    void waitingTriggersPollConditionsThenFireExactlyOnceAndCancelTheirScheduledWork() {
        try (var paper = new PaperBoundary()) {
            var scheduler = mock(BukkitScheduler.class);
            paper.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var task = mock(BukkitTask.class);
            when(task.getTaskId()).thenReturn(7);
            var cancelled = new AtomicBoolean();
            when(task.isCancelled()).thenAnswer(call -> cancelled.get());
            doAnswer(
                            call -> {
                                cancelled.set(true);
                                return null;
                            })
                    .when(scheduler)
                    .cancelTask(7);
            when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), eq(0L), eq(20L)))
                    .thenReturn(task);
            var run = mock(PlayableInstance.class);
            when(run.asPlayInstance()).thenReturn(run);
            when(run.getEnvironment()).thenReturn(paper.runtime.environment());
            var ready = new AtomicBoolean();
            var condition = mock(TriggerCondition.class);
            when(condition.check(any())).thenAnswer(call -> ready.get());
            var function = mock(DungeonFunction.class);
            var trigger = new WaitingTrigger();
            trigger.addCondition(condition);
            trigger.enable(function, run);
            trigger.trigger();
            var work = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTaskTimer(eq(paper.plugin), work.capture(), eq(0L), eq(20L));
            work.getValue().run();
            assertEquals(0, trigger.effects);
            verify(function, never()).execute(any());
            assertFalse(cancelled.get());
            ready.set(true);
            work.getValue().run();
            assertEquals(1, trigger.effects);
            verify(function, times(1)).execute(any());
            assertTrue(cancelled.get());
            verify(condition).disable();
            verify(run).unregisterTriggerListener(trigger);
        }
    }

    @Test
    void serverEventCancellationPreventsFunctionEffectsAndKeepsTheOriginalPlayerSource() {
        try (var paper = new PaperBoundary()) {
            var scheduler = mock(BukkitScheduler.class);
            paper.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTask(any(Plugin.class), any(Runnable.class)))
                    .thenReturn(mock(BukkitTask.class));
            var run = mock(PlayableInstance.class);
            when(run.asPlayInstance()).thenReturn(run);
            when(run.getEnvironment()).thenReturn(paper.runtime.environment());
            var source = mock(DungeonPlayerSession.class);
            when(source.getInstance()).thenReturn(run);
            var function = mock(DungeonFunction.class);
            var trigger = new WaitingTrigger();
            trigger.setAllowRetrigger(true);
            trigger.enable(function, run);
            var manager = Bukkit.getPluginManager();
            doAnswer(
                            call -> {
                                ((TriggerFireEvent) call.getArgument(0)).setCancelled(true);
                                return null;
                            })
                    .when(manager)
                    .callEvent(any(TriggerFireEvent.class));
            trigger.trigger(source);
            var work = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).runTask(eq(paper.plugin), work.capture());
            work.getValue().run();
            var emitted = ArgumentCaptor.forClass(TriggerFireEvent.class);
            verify(manager).callEvent(emitted.capture());
            assertSame(source, emitted.getValue().getPlayerSession());
            assertSame(trigger, emitted.getValue().getTrigger());
            assertEquals(0, trigger.effects);
            verify(function, never()).execute(any());
        }
    }

    @Test
    void disablingATriggerCancelsEveryOverlappingConditionWaiter() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = mock(PlayableInstance.class);
            when(run.asPlayInstance()).thenReturn(run);
            when(run.getEnvironment()).thenReturn(paper.runtime.environment());
            var condition = mock(TriggerCondition.class);
            var function = mock(DungeonFunction.class);
            var trigger = new WaitingTrigger();
            trigger.addCondition(condition);
            trigger.enable(function, run);
            trigger.trigger();
            trigger.trigger();
            assertEquals(2, scheduler.jobs.size());
            trigger.disable();
            assertTrue(scheduler.jobs.stream().allMatch(job -> job.cancelled));
            when(condition.check(any())).thenReturn(true);
            scheduler.jobs.forEach(Job::run);
            verify(function, never()).execute(any());
            assertEquals(0, trigger.effects);
        }
    }

    @Test
    void failingConditionCleanupCannotLeaveOtherConditionsOrPendingPollsActive() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = mock(PlayableInstance.class);
            when(run.asPlayInstance()).thenReturn(run);
            when(run.getEnvironment()).thenReturn(paper.runtime.environment());
            var bad = mock(TriggerCondition.class);
            var healthy = mock(TriggerCondition.class);
            doThrow(new IllegalStateException("condition cleanup failed")).when(bad).disable();
            var trigger = new WaitingTrigger();
            trigger.addCondition(bad);
            trigger.addCondition(healthy);
            trigger.enable(null, run);
            trigger.trigger();
            assertDoesNotThrow(trigger::disable);
            assertTrue(scheduler.jobs.getFirst().cancelled);
            verify(healthy).disable();
            verify(run).unregisterTriggerListener(trigger);
        }
    }

    static class WaitingTrigger extends DungeonTrigger {
        int effects;

        WaitingTrigger() {
            super("waiting");
            waitForConditions = true;
        }

        @Override
        public void onTrigger(TriggerFireEvent event) {
            effects++;
        }

        @Override
        public MenuButton buildMenuButton() {
            return null;
        }

        @Override
        public void buildHotbarMenu() {}
    }
}
