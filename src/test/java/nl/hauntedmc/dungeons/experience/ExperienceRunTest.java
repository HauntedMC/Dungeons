package nl.hauntedmc.dungeons.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import nl.hauntedmc.dungeons.event.ExperienceEvent;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.support.CapturedScheduler;
import nl.hauntedmc.dungeons.support.PaperBoundary;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

class ExperienceRunTest {
    private static final class Fixture implements AutoCloseable {
        final org.mockito.MockedStatic<io.papermc.paper.registry.RegistryAccess> registries =
                mockStatic(io.papermc.paper.registry.RegistryAccess.class);
        final PaperBoundary paper = new PaperBoundary();
        final org.mockito.MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class);
        final CapturedScheduler scheduler = new CapturedScheduler(paper);
        final World world = mock(World.class);
        final AtomicInteger seconds = new AtomicInteger();
        final List<String> events = new ArrayList<>();
        final ExperienceRun run;
        final List<LivingEntity> mobs = new ArrayList<>();

        Fixture() throws Exception {
            var access =
                    mock(
                            io.papermc.paper.registry.RegistryAccess.class,
                            call ->
                                    mock(
                                            org.bukkit.Registry.class,
                                            lookup -> {
                                                if (lookup.getMethod()
                                                        .getName()
                                                        .equals("getOrThrow"))
                                                    return mock(
                                                            org.bukkit.attribute.Attribute.class);
                                                return org.mockito.Answers.RETURNS_DEFAULTS.answer(
                                                        lookup);
                                            }));
            registries
                    .when(io.papermc.paper.registry.RegistryAccess::registryAccess)
                    .thenReturn(access);
            var yaml = new YamlConfiguration();
            yaml.loadFromString(
                    """
                    groups:
                      guards:
                        entity: ZOMBIE
                        count: 3
                        location: {x: 0, y: 70, z: 0}
                    encounters:
                      arena:
                        preparation_seconds: 0
                        timeout_seconds: 10
                        waves:
                          first: {groups: [guards]}
                    """);
            var instance = mock(PlayableInstance.class);
            when(instance.experienceDefinition()).thenReturn(ExperienceDefinition.parse(yaml));
            when(instance.isStarted()).thenReturn(true);
            when(instance.getTimeElapsed()).thenAnswer(call -> seconds.get());
            when(instance.getInstanceWorld()).thenReturn(world);
            when(instance.asPlayInstance()).thenReturn(instance);
            when(instance.getEnvironment()).thenReturn(paper.runtime.environment());
            when(instance.getRuntime()).thenReturn(paper.runtime);
            when(instance.getDungeon()).thenReturn(mock(DungeonDefinition.class));
            when(instance.getInstanceId()).thenReturn(UUID.randomUUID());
            when(world.getBlockAt(any(Location.class))).thenReturn(mock(Block.class));
            when(world.spawnEntity(any(Location.class), eq(EntityType.ZOMBIE)))
                    .thenAnswer(
                            call -> {
                                var mob = mock(LivingEntity.class);
                                var id = UUID.randomUUID();
                                when(mob.getUniqueId()).thenReturn(id);
                                when(mob.getType()).thenReturn(EntityType.ZOMBIE);
                                when(mob.isValid()).thenReturn(true);
                                when(mob.getPersistentDataContainer())
                                        .thenReturn(mock(PersistentDataContainer.class));
                                paper.bukkit.when(() -> Bukkit.getEntity(id)).thenReturn(mob);
                                mobs.add(mob);
                                return mob;
                            });
            var plugins = Bukkit.getPluginManager();
            doAnswer(
                            call -> {
                                if (call.getArgument(0) instanceof ExperienceEvent event)
                                    events.add(event.kind());
                                return null;
                            })
                    .when(plugins)
                    .callEvent(any());
            run = new ExperienceRun(instance, mock(ExperienceService.class));
            run.start("arena");
            run.tick();
            assertEquals(1, scheduler.jobs.size());
        }

        void spawnTick() {
            scheduler.jobs.getFirst().run();
        }

        long count(String kind) {
            return events.stream().filter(kind::equals).count();
        }

        @Override
        public void close() {
            run.close();
            handlers.close();
            paper.close();
            registries.close();
        }
    }

    @Test
    void resetCancelsPendingSpawnsAndAllowsOneFreshCompletion() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.spawnTick();
            fixture.run.reset("arena");
            assertTrue(fixture.scheduler.jobs.getFirst().cancelled);
            for (int i = 0; i < 5; i++) fixture.spawnTick();
            assertEquals(1, fixture.mobs.size());
            verify(fixture.mobs.getFirst()).remove();
            assertEquals(0, fixture.count("encounter-completed"));
            assertEquals(0, fixture.count("encounter-failed"));
            fixture.run.start("arena");
            fixture.run.tick();
            for (int i = 0; i < 4; i++) fixture.scheduler.jobs.getLast().run();
            fixture.mobs.subList(1, 4).forEach(mob -> fixture.run.death(mob, null, false));
            fixture.run.tick();
            fixture.run.tick();
            assertEquals(1, fixture.count("encounter-completed"));
            assertEquals(1, fixture.count("encounter-reset"));
        }
    }

    @Test
    void timeoutCancelsPendingSpawnsAndFailsExactlyOnce() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.seconds.set(10);
            fixture.run.tick();
            for (int i = 0; i < 5; i++) fixture.spawnTick();
            fixture.run.tick();
            assertTrue(fixture.scheduler.jobs.getFirst().cancelled);
            assertTrue(fixture.mobs.isEmpty());
            assertEquals(1, fixture.count("encounter-failed"));
            assertEquals(0, fixture.count("encounter-completed"));
        }
    }

    @Test
    void unexpectedRemovalCancelsPendingSpawnsAndFailsExactlyOnce() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.spawnTick();
            var mob = fixture.mobs.getFirst();
            fixture.run.removed(mob, EntityRemoveEvent.Cause.PLUGIN);
            fixture.run.removed(mob, EntityRemoveEvent.Cause.PLUGIN);
            for (int i = 0; i < 5; i++) fixture.spawnTick();
            fixture.run.tick();
            assertTrue(fixture.scheduler.jobs.getFirst().cancelled);
            assertEquals(1, fixture.mobs.size());
            assertEquals(1, fixture.count("encounter-failed"));
            assertEquals(0, fixture.count("encounter-completed"));
        }
    }

    @Test
    void disposalCancelsPendingSpawnsAndCannotEmitTerminalEvents() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.run.close();
            fixture.run.close();
            for (int i = 0; i < 5; i++) fixture.spawnTick();
            fixture.run.tick();
            assertTrue(fixture.scheduler.jobs.getFirst().cancelled);
            assertTrue(fixture.mobs.isEmpty());
            assertEquals(0, fixture.count("encounter-failed"));
            assertEquals(0, fixture.count("encounter-completed"));
        }
    }
}
