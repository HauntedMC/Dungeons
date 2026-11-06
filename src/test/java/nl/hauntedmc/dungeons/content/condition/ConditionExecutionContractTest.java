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
    @Test
    void elapsedTimeConditionsPassAtTheBoundaryAndClampNegativeThresholds() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var condition=bind(new TimeElapsedCondition(Map.of("time",10)),f);
            f.run.elapsed(9); assertFalse(condition.check(null));
            f.run.elapsed(10); assertTrue(condition.check(null));
            f.run.elapsed(11); assertTrue(condition.check(null));
            f.run.elapsed(0); assertTrue(bind(new TimeElapsedCondition(Map.of("time",-1)),f).check(null));
        }
    }

    @Test
    void chanceEndpointsAndOutOfRangeValuesHaveDeterministicOutcomes() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            for(double chance:List.of(-2.0,0.0,1.0,2.0)) {
                var condition=bind(new ChanceCondition(Map.of("chance",chance)),f);
                for(int i=0;i<10;i++) assertEquals(chance>=1,condition.check(null));
            }
        }
    }

    @Test
    void nearbyPlayerConditionsExcludeSpectatorsAndDistinguishExactAndMinimumCounts() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var first=new PlayerState(); var second=new PlayerState(); var spectator=new PlayerState();
            spectator.gameMode=GameMode.SPECTATOR;
            when(f.world.getNearbyEntities(any(Location.class),anyDouble(),anyDouble(),anyDouble()))
                .thenReturn(List.of(first.player,second.player,spectator.player,mock(Entity.class)));
            assertTrue(bind(new PlayersWithinCondition(Map.of("count",2,"playersExact",true,"radius",2.5)),f).check(null));
            assertFalse(bind(new PlayersWithinCondition(Map.of("count",1,"playersExact",true)),f).check(null));
            assertTrue(bind(new PlayersWithinCondition(Map.of("count",1)),f).check(null));
            assertFalse(bind(new PlayersWithinCondition(Map.of("count",3)),f).check(null));
            var missing=bind(new PlayersWithinCondition(),f); missing.setLocation(null); assertFalse(missing.check(null));
        }
    }

    @Test
    void mobConditionsCountOnlyTheConfiguredLivingTypeAndFallbackSafelyForUnknownTypes() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var skeleton=mock(LivingEntity.class); when(skeleton.getType()).thenReturn(EntityType.SKELETON);
            var zombie=mock(LivingEntity.class); when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
            when(f.world.getNearbyEntities(any(Location.class),anyDouble(),anyDouble(),anyDouble()))
                .thenReturn(List.of(skeleton,zombie,mock(Entity.class)));
            assertTrue(bind(new MobWithinCondition(Map.of("entityName","skeleton","count",1,"mobsExact",true)),f).check(null));
            assertFalse(bind(new MobWithinCondition(Map.of("entityName","skeleton","count",2)),f).check(null));
            assertTrue(bind(new MobWithinCondition(Map.of("entityName","not-a-mob","count",1)),f).check(null));
            var missing=bind(new MobWithinCondition(),f); missing.setLocation(null); assertFalse(missing.check(null));
        }
    }

    @Test
    void difficultyConditionsDistinguishDefaultNamedAndMissingConfiguration() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            assertTrue(bind(new DifficultyCondition(Map.of("difficulty","DEFAULT")),f).check(null));
            var difficulty=mock(nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty.class);
            when(difficulty.getNamespace()).thenReturn("hard"); f.run.setDifficulty(difficulty);
            assertTrue(bind(new DifficultyCondition(Map.of("difficulty","hard")),f).check(null));
            assertFalse(bind(new DifficultyCondition(Map.of("difficulty","DEFAULT")),f).check(null));
            assertFalse(bind(new DifficultyCondition(Map.of("difficulty","")),f).check(null));
        }
    }

}
