package nl.hauntedmc.dungeons.content.trigger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.event.*;
import nl.hauntedmc.dungeons.content.instance.play.StaticInstance;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.*;
import org.bukkit.block.Block;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TriggerExecutionContractTest {
    @TempDir Path directory;
    static <T extends DungeonTrigger> T bind(T trigger, RunFixture f) {
        trigger.initializeFields(); trigger.setInstance(f.run);
        trigger.setLocation(new Location(f.world,0,70,0)); trigger.setAllowRetrigger(true); return trigger;
    }
    static DungeonPlayerSession source(RunFixture f, PlayerState state) {
        state.location=new Location(f.world,0,70,0);
        var session=f.session(state); session.setInstance(f.run); return session;
    }
    @Test
    void startTriggerSchedulesOnlyItsOwnRunAndPreservesThePlayerSource() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var session=source(f,new PlayerState());
            var trigger=bind(new DungeonStartTrigger(),f);
            trigger.onDungeonStart(new DungeonStartEvent(new StaticInstance(f.dungeon,null),List.of(session)));
            assertTrue(f.scheduler.jobs.isEmpty());
            trigger.onDungeonStart(new DungeonStartEvent(f.run,List.of(session)));
            assertEquals(1,f.scheduler.jobs.size());
            assertTrue(f.scheduler.immediate.isEmpty());
            f.scheduler.jobs.getFirst().run();
            assertEquals(1,f.scheduler.immediate.size());
            f.scheduler.immediate.getFirst().run();
            var event=org.mockito.ArgumentCaptor.forClass(TriggerFireEvent.class);
            verify(Bukkit.getPluginManager()).callEvent(event.capture());
            assertSame(session,event.getValue().getPlayerSession());
            assertSame(f.run,event.getValue().getInstance());
        }
    }
    @Test
    void leaveTriggersCannotCancelOrExecuteForAnotherRun() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var session=source(f,new PlayerState());
            var trigger=bind(new LeaveDungeonTrigger(Map.of("preventLeaving",true)),f);
            var other=new PlayerLeaveDungeonEvent(new StaticInstance(f.dungeon,null),session);
            trigger.onPlayerLeave(other);
            assertFalse(other.isCancelled()); assertTrue(f.scheduler.immediate.isEmpty());
            var own=new PlayerLeaveDungeonEvent(f.run,session);
            trigger.onPlayerLeave(own);
            assertTrue(own.isCancelled()); assertEquals(1,f.scheduler.immediate.size());
        }
    }

    @Test
    void remoteSignalsHonorCancellationNameRunAndInclusiveSpatialBounds() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var trigger=bind(new RemoteTrigger(Map.of("triggerName","open")),f);
            trigger.onTriggerReceive(new RemoteTriggerEvent("other",null,f.run,0,null,null));
            trigger.onTriggerReceive(new RemoteTriggerEvent("open",null,f.run,5,new Location(f.world,6,70,0),null));
            trigger.onTriggerReceive(new RemoteTriggerEvent("open",null,new StaticInstance(f.dungeon,null),0,null,null));
            var cancelled=new RemoteTriggerEvent("open",null,f.run,0,null,null); cancelled.setCancelled(true);
            trigger.onTriggerReceive(cancelled);
            assertTrue(f.scheduler.immediate.isEmpty());
            trigger.onTriggerReceive(new RemoteTriggerEvent("open",null,f.run,5,new Location(f.world,3,70,4),null));
            assertEquals(1,f.scheduler.immediate.size());
        }
    }

}
