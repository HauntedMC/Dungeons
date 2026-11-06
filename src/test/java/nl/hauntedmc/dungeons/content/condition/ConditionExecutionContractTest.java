package nl.hauntedmc.dungeons.content.condition;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConditionExecutionContractTest {
    @TempDir Path directory;
    static <T extends TriggerCondition> T bind(T condition,RunFixture fixture) {
        condition.initializeFields(); condition.setInstance(fixture.run);
        condition.setLocation(new Location(fixture.world,0,70,0)); return condition;
    }
    @Test
    void statusConditionObservesActualStatusFunctionChangesAndRejectsMissingOrDifferentStatus() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var condition=bind(new StatusCondition(Map.of("status","unlocked")),f);
            assertFalse(condition.check(null));
            var function=new SetStatusFunction(Map.of("status","unlocked"));
            function.initializeFields(); function.setInstance(f.run);
            function.runFunction(null,List.of());
            assertTrue(condition.check(null));
            f.run.setStatus("locked"); assertFalse(condition.check(null));
            assertFalse(bind(new StatusCondition(Map.of("status","")),f).check(null));
        }
    }
}
