package nl.hauntedmc.dungeons.model.element;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicBoolean;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
            doAnswer(call -> { cancelled.set(true); return null; }).when(scheduler).cancelTask(7);
            when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), eq(0L), eq(20L))).thenReturn(task);
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

    static class WaitingTrigger extends DungeonTrigger {
        int effects;
        WaitingTrigger() { super("waiting"); waitForConditions = true; }
        @Override public void onTrigger(TriggerFireEvent event) { effects++; }
        @Override public MenuButton buildMenuButton() { return null; }
        @Override public void buildHotbarMenu() {}
    }
}
