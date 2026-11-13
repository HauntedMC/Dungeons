package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.api.DungeonExperienceApi;
import nl.hauntedmc.dungeons.content.function.SpawnMobFunction;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import nl.hauntedmc.featureframework.hud.HudService;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class ExperienceServiceTest {
    @Test
    void repeatedStartupOwnsExactlyOneRegistrationAndTimer() {
        try (var paper = new PaperBoundary();
                var handlers = mockStatic(HandlerList.class)) {
            var services = mock(ServicesManager.class);
            var scheduler = mock(BukkitScheduler.class);
            var task = mock(BukkitTask.class);
            paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            paper.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(paper.plugin), any(Runnable.class), eq(1L), eq(5L)))
                    .thenReturn(task);
            var service = new ExperienceService(paper.runtime);
            service.start();
            service.start();
            verify(Bukkit.getPluginManager()).registerEvents(service, paper.plugin);
            verify(services)
                    .register(
                            DungeonExperienceApi.class,
                            service,
                            paper.plugin,
                            ServicePriority.Normal);
            verify(scheduler).runTaskTimer(eq(paper.plugin), any(Runnable.class), eq(1L), eq(5L));
            verify(task, never()).cancel();
            verify(services, never()).unregister(DungeonExperienceApi.class, service);
            service.close();
            service.close();
            assertThrows(IllegalStateException.class, service::start);
            paper.runtime.resources().quiesce();
            verify(task).cancel();
            verify(services).unregister(DungeonExperienceApi.class, service);
            handlers.verify(() -> HandlerList.unregisterAll(service));
            paper.runtime.resources().cleanup();
        }
    }

    @Test
    void closedServiceCannotStartEvenIfNeverStarted() {
        try (var paper = new PaperBoundary();
                var handlers = mockStatic(HandlerList.class)) {
            var services = mock(ServicesManager.class);
            paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            var service = new ExperienceService(paper.runtime);
            service.close();
            assertThrows(IllegalStateException.class, service::start);
            verify(services, never()).register(any(), any(), any(), any());
        }
    }

    @Test
    void cleanupAttemptsEveryResourceAndRetainsFailures() throws Exception {
        try (var paper = new PaperBoundary();
                var handlers = mockStatic(HandlerList.class)) {
            var services = mock(ServicesManager.class);
            var scheduler = mock(BukkitScheduler.class);
            var task = mock(BukkitTask.class);
            paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            paper.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(paper.plugin), any(Runnable.class), eq(1L), eq(5L)))
                    .thenReturn(task);
            var service = new ExperienceService(paper.runtime);
            service.start();
            var timerFailure = new IllegalStateException("timer");
            var leaseFailure = new IllegalArgumentException("lease");
            doThrow(timerFailure).when(task).cancel();
            var failing = mock(HudService.Lease.class);
            var remaining = mock(HudService.Lease.class);
            doThrow(leaseFailure).when(failing).close();
            Map<UUID, HudService.Lease> previews = field(service, "previews");
            previews.put(UUID.randomUUID(), failing);
            previews.put(UUID.randomUUID(), remaining);
            var subscription = service.subscribe(UUID.randomUUID(), change -> {});

            assertSame(timerFailure, assertThrows(IllegalStateException.class, service::close));
            assertArrayEquals(new Throwable[] {leaseFailure}, timerFailure.getSuppressed());
            verify(failing).close();
            verify(remaining).close();
            assertTrue(previews.isEmpty());
            handlers.verify(() -> HandlerList.unregisterAll(service));
            verify(services).unregister(DungeonExperienceApi.class, service);
            assertThrows(
                    IllegalStateException.class,
                    () -> service.subscribe(UUID.randomUUID(), change -> {}));
            subscription.close();
            service.close();
            paper.runtime.resources().quiesce();
            verify(task).cancel();
            verify(failing).close();
            paper.runtime.resources().cleanup();
        }
    }

    @Test
    void schedulingFailureImmediatelyRemovesListenersAndClosesService() {
        try (var paper = new PaperBoundary();
                var handlers = mockStatic(HandlerList.class)) {
            var services = mock(ServicesManager.class);
            var scheduler = mock(BukkitScheduler.class);
            paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            paper.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var failure = new IllegalStateException("scheduler stopped");
            when(scheduler.runTaskTimer(eq(paper.plugin), any(Runnable.class), eq(1L), eq(5L)))
                    .thenThrow(failure);
            var service = new ExperienceService(paper.runtime);
            assertSame(failure, assertThrows(IllegalStateException.class, service::start));
            handlers.verify(() -> HandlerList.unregisterAll(service));
            handlers.verify(() -> HandlerList.unregisterAll(any(ExperienceEditor.class)));
            verify(services, never()).register(any(), any(), any(), any());
            verify(services).unregister(DungeonExperienceApi.class, service);
            assertThrows(IllegalStateException.class, service::start);
            paper.runtime.resources().quiesce();
            verify(services).unregister(DungeonExperienceApi.class, service);
            paper.runtime.resources().cleanup();
        }
    }

    @Test
    void publicationFailureCancelsTimerAndSuppressesRollbackFailure() {
        try (var paper = new PaperBoundary();
                var handlers = mockStatic(HandlerList.class)) {
            var services = mock(ServicesManager.class);
            var scheduler = mock(BukkitScheduler.class);
            var task = mock(BukkitTask.class);
            paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            paper.bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(paper.plugin), any(Runnable.class), eq(1L), eq(5L)))
                    .thenReturn(task);
            var service = new ExperienceService(paper.runtime);
            var failure = new IllegalStateException("publication failed");
            var cleanup = new IllegalArgumentException("cancel failed");
            doThrow(failure)
                    .when(services)
                    .register(
                            DungeonExperienceApi.class,
                            service,
                            paper.plugin,
                            ServicePriority.Normal);
            doThrow(cleanup).when(task).cancel();
            assertSame(failure, assertThrows(IllegalStateException.class, service::start));
            assertArrayEquals(new Throwable[] {cleanup}, failure.getSuppressed());
            verify(task).cancel();
            handlers.verify(() -> HandlerList.unregisterAll(service));
            handlers.verify(() -> HandlerList.unregisterAll(any(ExperienceEditor.class)));
            verify(services).unregister(DungeonExperienceApi.class, service);
            assertThrows(IllegalStateException.class, service::start);
            paper.runtime.resources().quiesce();
            verify(task).cancel();
            paper.runtime.resources().cleanup();
        }
    }

    @Test
    void editorRegistrationFailureRemovesPartiallyRegisteredEditor() {
        try (var paper = new PaperBoundary();
                var handlers = mockStatic(HandlerList.class)) {
            var services = mock(ServicesManager.class);
            paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            var failure = new IllegalStateException("listener registration failed");
            var plugins = Bukkit.getPluginManager();
            doThrow(failure)
                    .when(plugins)
                    .registerEvents(any(ExperienceEditor.class), eq(paper.plugin));
            var service = new ExperienceService(paper.runtime);
            assertSame(failure, assertThrows(IllegalStateException.class, service::start));
            handlers.verify(() -> HandlerList.unregisterAll(any(ExperienceEditor.class)));
            handlers.verify(() -> HandlerList.unregisterAll(service));
            verify(services, never()).register(any(), any(), any(), any());
            assertThrows(IllegalStateException.class, service::start);
            paper.runtime.resources().quiesce();
            paper.runtime.resources().cleanup();
        }
    }

    @Test
    void observationCleanupAttemptsEveryLease() throws Exception {
        var hud = new ObservationHud(null, () -> null, instance -> null);
        Map<UUID, HudService.Lease> leases = field(hud, "leases");
        var first = mock(HudService.Lease.class);
        var second = mock(HudService.Lease.class);
        var failure = new IllegalStateException("observation lease");
        doThrow(failure).when(first).close();
        leases.put(UUID.randomUUID(), first);
        leases.put(UUID.randomUUID(), second);
        assertSame(failure, assertThrows(IllegalStateException.class, hud::close));
        verify(first).close();
        verify(second).close();
        assertTrue(leases.isEmpty());
        hud.close();
        verify(first).close();
    }

    @Test
    void mechanicsCleanupAttemptsEverySpawner() throws Exception {
        var instance = mock(PlayableInstance.class);
        when(instance.experienceDefinition()).thenReturn(ExperienceDefinition.empty());
        var run = new ExperienceRun(instance, null);
        List<SpawnMobFunction> spawners = field(run, "spawners");
        var first = mock(SpawnMobFunction.class);
        var second = mock(SpawnMobFunction.class);
        var failure = new IllegalStateException("spawner");
        doThrow(failure).when(first).disable();
        spawners.add(first);
        spawners.add(second);
        assertSame(failure, assertThrows(IllegalStateException.class, run::close));
        verify(first).disable();
        verify(second).disable();
        assertTrue(spawners.isEmpty());
        run.close();
        verify(first).disable();
    }

    @Test
    void participationCleanupAttemptsBothChannelAndFeedbackLeases() throws Exception {
        try (var paper = new PaperBoundary();
                var handlers = mockStatic(HandlerList.class)) {
            var services = mock(ServicesManager.class);
            paper.bukkit.when(Bukkit::getServicesManager).thenReturn(services);
            var service = new ExperienceService(paper.runtime);
            var type = Class.forName(ExperienceService.class.getName() + "$Participation");
            var constructor = type.getDeclaredConstructor(PlayableInstance.class);
            constructor.setAccessible(true);
            var state = constructor.newInstance(mock(PlayableInstance.class));
            Map<HudService.Channel, HudService.Lease> channels = field(state, "channels");
            Map<HudService.Channel, HudService.Lease> feedback = field(state, "feedback");
            var first = mock(HudService.Lease.class);
            var second = mock(HudService.Lease.class);
            var failure = new IllegalStateException("channel");
            doThrow(failure).when(first).close();
            channels.put(HudService.Channel.SIDEBAR, first);
            feedback.put(HudService.Channel.ACTION_BAR, second);
            Map<UUID, Object> players = field(service, "players");
            players.put(UUID.randomUUID(), state);
            assertSame(failure, assertThrows(IllegalStateException.class, service::close));
            verify(first).close();
            verify(second).close();
            assertTrue(channels.isEmpty());
            assertTrue(feedback.isEmpty());
            assertTrue(players.isEmpty());
            verify(services).unregister(DungeonExperienceApi.class, service);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }
}
