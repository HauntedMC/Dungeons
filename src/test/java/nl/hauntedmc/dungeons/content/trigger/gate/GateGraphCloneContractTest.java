package nl.hauntedmc.dungeons.content.trigger.gate;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.trigger.RemoteTrigger;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GateGraphCloneContractTest {
    @TempDir Path directory;

    @Test
    void cloningPartiallySatisfiedGatesCreatesIndependentChildGraphsAndCleanupOwnership()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var _ = new HeadlessMenus()) {
            var gate = new GateAndTrigger();
            gate.addTrigger(new RemoteTrigger());
            gate.addTrigger(new RemoteTrigger());
            var effect = new SetStatusFunction(Map.of("status", "gate-open"));
            effect.initializeFields();
            effect.setTrigger(gate);
            effect.enable(f.run, new Location(f.world, 0, 70, 0));
            gate.onChildTriggered(new TriggerFireEvent(f.run, gate.getTriggers().getFirst()));
            var copy = effect.clone();
            var other = new RunFixture.Run(f.dungeon, f.world);
            copy.enable(other, new Location(f.world, 4, 70, 0));
            var copiedGate = assertInstanceOf(GateAndTrigger.class, copy.getTrigger());
            assertNotSame(gate.getTriggers().getFirst(), copiedGate.getTriggers().getFirst());
            copiedGate.onChildTriggered(
                    new TriggerFireEvent(other, copiedGate.getTriggers().getLast()));
            assertTrue(f.scheduler.immediate.isEmpty());
            gate.onChildTriggered(new TriggerFireEvent(f.run, gate.getTriggers().getLast()));
            f.scheduler.immediate.getLast().run();
            assertEquals("gate-open", f.run.getStatus());
            assertNull(other.getStatus());
            assertEquals(2, other.getTriggerListeners(RemoteTrigger.class).size());
            copiedGate.onChildTriggered(
                    new TriggerFireEvent(other, copiedGate.getTriggers().getFirst()));
            f.scheduler.immediate.getLast().run();
            assertEquals("gate-open", other.getStatus());
            assertTrue(other.getTriggerListeners(RemoteTrigger.class).isEmpty());
            effect.disable();
            copy.disable();
        }
    }
}
