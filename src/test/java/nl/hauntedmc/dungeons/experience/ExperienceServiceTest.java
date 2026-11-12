package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.api.DungeonExperienceApi;
import nl.hauntedmc.dungeons.support.PaperBoundary;
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
}
