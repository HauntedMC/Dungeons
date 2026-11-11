package nl.hauntedmc.dungeons.model.element;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import nl.hauntedmc.dungeons.content.trigger.LeaveDungeonTrigger;
import nl.hauntedmc.dungeons.content.trigger.gate.GateOrTrigger;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class LifecycleFailureRegressionTest {
    private PlayableInstance run(PaperBoundary paper) {
        var run = mock(PlayableInstance.class);
        when(run.asPlayInstance()).thenReturn(run);
        when(run.getEnvironment()).thenReturn(paper.runtime.environment());
        return run;
    }

    @Test
    void repeatedSignalsKeepOnePollerPerSourceAndReleaseItOnDisable() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            var trigger = new TriggerLifecycleContractTest.WaitingTrigger();
            trigger.addCondition(mock(TriggerCondition.class));
            trigger.enable(null, run);
            for (int i = 0; i < 100; i++) trigger.trigger();
            assertEquals(1, scheduler.jobs.size());
            trigger.disable();
            trigger.enable(null, run);
            trigger.trigger();
            assertEquals(2, scheduler.jobs.size());
            assertTrue(scheduler.jobs.getFirst().cancelled);
            trigger.disable();
        }
    }

    @Test
    void schedulerRejectionReleasesAdmissionAndNeverLeavesAnUnscheduledWaiter() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            when(scheduler.scheduler.runTaskTimer(
                            any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                    .thenThrow(new IllegalStateException("scheduler stopped"));
            var trigger = new TriggerLifecycleContractTest.WaitingTrigger();
            trigger.enable(null, run(paper));
            assertThrows(IllegalStateException.class, trigger::trigger);
            var replacement = new CapturedScheduler(paper);
            trigger.trigger();
            assertEquals(1, replacement.jobs.size());
            assertDoesNotThrow(trigger::disable);
            assertTrue(replacement.jobs.getFirst().cancelled);
        }
    }

    @Test
    void pendingEventsDoNotFollowPlayersIntoAnotherInstance() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            var player = mock(DungeonPlayerSession.class);
            when(player.getInstance()).thenReturn(run);
            var function = mock(DungeonFunction.class);
            var trigger = new TriggerLifecycleContractTest.WaitingTrigger();
            trigger.enable(function, run);
            trigger.trigger(player);
            when(player.getInstance()).thenReturn(null);
            scheduler.jobs.getFirst().run();
            assertTrue(scheduler.jobs.getFirst().cancelled);
            verify(function, never()).execute(any());
            when(player.getInstance()).thenReturn(run);
            trigger.trigger(player);
            scheduler.jobs.getLast().run();
            verify(function).execute(any());
        }
    }

    @Test
    void departureListenersKeepTheirOriginalCauseAfterThePlayerHasLeft() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            var player = mock(DungeonPlayerSession.class);
            when(player.getInstance()).thenReturn(run);
            var function = mock(DungeonFunction.class);
            var trigger = new LeaveDungeonTrigger();
            trigger.enable(function, run);
            trigger.trigger(player);
            when(player.getInstance()).thenReturn(null);
            scheduler.jobs.getFirst().run();
            verify(function).execute(any());
        }
    }

    @Test
    void failedEnableRollsBackResourcesAndAllowsASecondEnable() {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            var fail = new AtomicBoolean(true);
            var trigger =
                    new TriggerLifecycleContractTest.WaitingTrigger() {
                        @Override
                        public void onEnable() {
                            if (fail.getAndSet(false))
                                throw new IllegalStateException("startup failed");
                        }
                    };
            var condition = mock(TriggerCondition.class);
            trigger.addCondition(condition);
            assertThrows(IllegalStateException.class, () -> trigger.enable(null, run));
            assertFalse(trigger.enabled);
            verify(run).unregisterTriggerListener(trigger);
            verify(condition).disable();
            trigger.enable(null, run);
            assertTrue(trigger.enabled);
            verify(condition, times(2)).enable(trigger);
            trigger.disable();
        }
    }

    @Test
    void triggerCleanupFailureStillResetsBaseStateAndReleasesOtherResources() {
        try (var paper = new PaperBoundary()) {
            var run = run(paper);
            var trigger =
                    new TriggerLifecycleContractTest.WaitingTrigger() {
                        @Override
                        public void onDisable() {
                            throw new IllegalStateException("cleanup failed");
                        }
                    };
            trigger.enable(null, run);
            assertDoesNotThrow(trigger::disable);
            assertFalse(trigger.enabled);
            trigger.enable(null, run);
            verify(run, times(2)).registerTriggerListener(trigger);
            trigger.disable();
        }
    }

    @Test
    void badFunctionTriggerCannotPreventFunctionResourceCleanup() {
        try (var paper = new PaperBoundary()) {
            var function = new Effect();
            var trigger = mock(DungeonTrigger.class);
            doThrow(new IllegalStateException("trigger cleanup")).when(trigger).disable();
            function.setTrigger(trigger);
            var run = run(paper);
            function.enable(run, null);
            assertDoesNotThrow(function::disable);
            assertEquals(1, function.cleanups);
            assertFalse(function.enabled);
            verify(run).unregisterFunctionListener(function);
        }
    }

    @Test
    void failedFunctionStartupReleasesListenersAndChildResources() {
        try (var paper = new PaperBoundary()) {
            var function =
                    new Effect() {
                        @Override
                        public void onEnable() {
                            throw new IllegalStateException("startup");
                        }
                    };
            var run = run(paper);
            assertThrows(IllegalStateException.class, () -> function.enable(run, null));
            assertEquals(1, function.cleanups);
            assertFalse(function.enabled);
            verify(run).unregisterFunctionListener(function);
        }
    }

    @Test
    void gateCleanupContinuesAfterOneChildFails() {
        try (var paper = new PaperBoundary()) {
            var gate = new GateOrTrigger();
            var bad = mock(DungeonTrigger.class);
            var good = mock(DungeonTrigger.class);
            doThrow(new IllegalStateException("child failed")).when(bad).disable();
            gate.addTrigger(bad);
            gate.addTrigger(good);
            assertDoesNotThrow(gate::onDisable);
            verify(good).disable();
            assertTrue(gate.getTriggerTracker().isEmpty());
        }
    }

    @Test
    void disablingDuringEventDeliveryPreventsEffects() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var trigger = new TriggerLifecycleContractTest.WaitingTrigger();
            var function = mock(DungeonFunction.class);
            trigger.enable(function, run(paper));
            var manager = Bukkit.getPluginManager();
            doAnswer(
                            call -> {
                                trigger.disable();
                                return null;
                            })
                    .when(manager)
                    .callEvent(any(TriggerFireEvent.class));
            trigger.trigger();
            scheduler.jobs.getFirst().run();
            verify(function, never()).execute(any());
            assertEquals(0, trigger.effects);
        }
    }

    @Test
    void movingTheSourceDuringEventDeliveryPreventsEffects() {
        try (var paper = new PaperBoundary()) {
            var scheduler = new CapturedScheduler(paper);
            var run = run(paper);
            var source = mock(DungeonPlayerSession.class);
            when(source.getInstance()).thenReturn(run);
            var trigger = new TriggerLifecycleContractTest.WaitingTrigger();
            var function = mock(DungeonFunction.class);
            trigger.enable(function, run);
            var manager = Bukkit.getPluginManager();
            doAnswer(
                            call -> {
                                when(source.getInstance()).thenReturn(null);
                                return null;
                            })
                    .when(manager)
                    .callEvent(any(TriggerFireEvent.class));
            trigger.trigger(source);
            scheduler.jobs.getFirst().run();
            verify(function, never()).execute(any());
            assertEquals(0, trigger.effects);
            scheduler.jobs.getFirst().run();
            assertTrue(scheduler.jobs.getFirst().cancelled);
        }
    }

    static class Effect extends DungeonFunction {
        int cleanups;

        Effect() {
            super("effect");
        }

        @Override
        public void onDisable() {
            cleanups++;
        }

        @Override
        public void runFunction(TriggerFireEvent event, List<DungeonPlayerSession> targets) {}

        @Override
        public MenuButton buildMenuButton() {
            return null;
        }

        @Override
        public void buildHotbarMenu() {}
    }
}
