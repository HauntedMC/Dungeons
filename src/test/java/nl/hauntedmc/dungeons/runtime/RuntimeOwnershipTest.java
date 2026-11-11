package nl.hauntedmc.dungeons.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import nl.hauntedmc.dungeons.content.function.meta.DelayedFunction;
import nl.hauntedmc.dungeons.content.trigger.DungeonStartTrigger;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.persistence.StateStore;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonPlayerDataStore;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.support.PlayerState;
import nl.hauntedmc.dungeons.support.RunFixture;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeOwnershipTest {
    @TempDir Path directory;

    @Test
    void boundMechanicsAndDurableRecordsUseTheirOwnerWhenTheGlobalBridgeChanges() throws Exception {
        try (var fixture = new RunFixture(directory, new YamlConfiguration())) {
            var owner = fixture.paper.runtime;
            var other =
                    new DungeonsRuntime(mock(nl.hauntedmc.dungeons.plugin.DungeonsPlugin.class));
            var foreignStore = mock(StateStore.class);
            other.setStateStore(foreignStore);
            RuntimeContext.initialize(other);
            try {
                var state = new PlayerState();
                var session = new DungeonPlayerSession(owner, state.player);
                assertNotNull(session);
                session.setInstance(fixture.run);
                var records =
                        new DungeonPlayerDataStore(
                                fixture.paper.plugin, UUID.randomUUID(), owner.stateStore());
                records.setAccessCooldown(state.player.getUniqueId(), new java.util.Date(1234));
                verifyNoInteractions(foreignStore);

                var child = mock(DungeonFunction.class);
                var delayed = new DelayedFunction(Map.of("delay", 7));
                delayed.initializeFields();
                delayed.setInstance(fixture.run);
                delayed.addFunction(child);
                var event = mock(TriggerFireEvent.class);
                delayed.runFunction(event, List.of(session));
                fixture.scheduler.jobs.getFirst().run();
                verify(child).runFunction(event, List.of(session));

                var trigger = new DungeonStartTrigger();
                trigger.setInstance(fixture.run);
                trigger.onDungeonStart(new DungeonStartEvent(fixture.run, List.of(session)));
                assertEquals(2, fixture.scheduler.jobs.size());
            } finally {
                RuntimeContext.initialize(owner);
            }
        }
    }
}
