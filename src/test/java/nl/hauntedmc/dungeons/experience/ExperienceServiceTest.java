package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.api.DungeonExperienceApi;
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

    @SuppressWarnings("unchecked")
    private static <T> T field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }
}
