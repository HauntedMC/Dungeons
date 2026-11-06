package nl.hauntedmc.dungeons.content.function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameplayFunctionContractTest {
    @TempDir Path directory;
    static <T extends DungeonFunction> T bind(T function,RunFixture f) {
        function.initializeFields(); function.setInstance(f.run); function.setLocation(new Location(f.world,0,70,0)); return function;
    }
    @Test
    void startMechanicRequiresEveryParticipantAndDuplicateReadySignalsCannotStartEarly() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var first=f.session(new PlayerState()); var second=f.session(new PlayerState());
            f.run.addPlayer(first); f.run.addPlayer(second);
            var start=bind(new StartDungeonFunction(),f);
            start.runFunction(null,List.of(first)); start.runFunction(null,List.of(first));
            assertFalse(f.run.isStarted());
            start.runFunction(null,List.of(second)); assertTrue(f.run.isStarted());
            int jobs=f.scheduler.jobs.size(); start.runFunction(null,List.of(first,second));
            assertEquals(jobs,f.scheduler.jobs.size());
            verify(Bukkit.getPluginManager(),times(1)).callEvent(any(nl.hauntedmc.dungeons.event.DungeonStartEvent.class));
        }
    }
    @Test
    void finishingRecordsCompletionAndTransfersQueuedRewardsOnceWithoutForcingDeparture() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var state=new PlayerState(); var session=f.session(state); f.run.addPlayer(session);
            var outsider=new PlayerState(); f.session(outsider);
            var reward=PlayerState.stack(2); f.run.addPlayerReward(state.player,reward);
            var finish=bind(new FinishDungeonFunction(Map.of("leave",false)),f);
            finish.runFunction(null,List.of(session)); finish.runFunction(null,List.of(session));
            assertTrue(f.dungeon.hasPlayerCompletedDungeon(state.player));
            assertFalse(f.dungeon.hasPlayerCompletedDungeon(outsider.player));
            assertEquals(List.of(reward),session.getRewardItems()); assertSame(f.run,session.getInstance());
        }
    }

    @Test
    void teleportResolvesTheRuntimeWorldWithoutMutatingItsTemplateAndIgnoresOtherRunTargets() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var state=new PlayerState(); var session=f.session(state); f.run.addPlayer(session);
            var outsider=new PlayerState(); var other=f.session(outsider); var otherLocation=outsider.location.clone();
            var destination=new Location(state.originWorld,30,80,40,45,10);
            var teleport=bind(new TeleportFunction(Map.of("teleportTarget",destination)),f); teleport.onEnable();
            var vehicle=mock(org.bukkit.entity.Entity.class); when(state.player.getVehicle()).thenReturn(vehicle);
            teleport.runFunction(null,List.of(session,other));
            assertEquals(new Location(f.world,30,80,40,45,10),state.location);
            assertEquals(otherLocation,outsider.location); assertSame(state.originWorld,destination.getWorld());
            verify(vehicle).removePassenger(state.player);
        }
    }

    @Test
    void checkpointCentersTheStandingPointAndPersistsAWorldIndependentSavePoint() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var state=new PlayerState(); var session=f.session(state); f.run.addPlayer(session);
            var block=mock(org.bukkit.block.Block.class); when(block.isSolid()).thenReturn(true);
            when(f.world.getBlockAt(any(Location.class))).thenReturn(block);
            when(f.world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
            var checkpoint=bind(new CheckpointFunction(Map.of("savePoint",true,"yaw",90.0,"pitch",15.0)),f);
            var location=new Location(f.world,4,70,6); checkpoint.setLocation(location);
            checkpoint.runFunction(null,List.of(session));
            assertEquals(new Location(f.world,4.5,71,6.5,90,15),session.getDungeonRespawn());
            var saved=session.getDungeonSavePoint(f.dungeon.getWorldName());
            assertNull(saved.getWorld()); assertEquals(4.5,saved.getX()); assertEquals(71,saved.getY());
            assertEquals(70,location.getY());
        }
    }

    @Test
    void livesMechanicsApplySetMultiplyAndAddWithoutChangingUntargetedParticipants() throws Exception {
        var settings=new YamlConfiguration(); settings.set("players.lives",3);
        try(var f=new RunFixture(directory,settings)) {
            var first=f.session(new PlayerState()); var second=f.session(new PlayerState()); f.run.addPlayer(first); f.run.addPlayer(second);
            bind(new LivesFunction(Map.of("mode",1,"amount",5)),f).runFunction(null,List.of(first));
            assertEquals(5,f.run.getPlayerLives().get(first.getPlayer().getUniqueId()));
            bind(new LivesFunction(Map.of("mode",2,"amount",2)),f).runFunction(null,List.of(first));
            assertEquals(10,f.run.getPlayerLives().get(first.getPlayer().getUniqueId()));
            bind(new LivesFunction(Map.of("mode",0,"amount",-3)),f).runFunction(null,List.of(first));
            assertEquals(7,f.run.getPlayerLives().get(first.getPlayer().getUniqueId()));
            assertEquals(3,f.run.getPlayerLives().get(second.getPlayer().getUniqueId()));
        }
    }

    @Test
    void difficultyMechanicsResolveKnownPresetsAndLeaveTheCurrentDifficultyOnInvalidInput() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var difficulty=mock(nl.hauntedmc.dungeons.model.dungeon.DungeonDifficulty.class);
            when(difficulty.getNamespace()).thenReturn("hard"); f.dungeon.getDifficultyLevels().put("hard",difficulty);
            bind(new SetDifficultyFunction(Map.of("difficulty","hard")),f).runFunction(null,List.of());
            assertSame(difficulty,f.run.getDifficulty());
            bind(new SetDifficultyFunction(Map.of("difficulty","missing")),f).runFunction(null,List.of());
            bind(new SetDifficultyFunction(Map.of("difficulty","")),f).runFunction(null,List.of());
            assertSame(difficulty,f.run.getDifficulty());
        }
    }

    @Test
    void outputMechanicsResolveVariablesAndRouteMessagesTitlesAndSoundToOnlyTheRequestedPlayer() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var state=new PlayerState(); var target=f.session(state); var outsider=new PlayerState(); f.session(outsider);
            f.run.getInstanceVariables().set("score",8);
            bind(new MessageFunction(Map.of("message","score=<score>")),f).runFunction(null,List.of(target));
            var text=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.text.Component.class);
            verify(state.player).sendMessage(text.capture());
            assertEquals("score=8",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text.getValue()));
            bind(new TitleFunction(Map.of("title","score=<score>","subtitle","done","fadeIn",2,"stay",10,"fadeOut",3)),f).runFunction(null,List.of(target));
            var title=org.mockito.ArgumentCaptor.forClass(net.kyori.adventure.title.Title.class);
            verify(state.player).showTitle(title.capture());
            assertEquals("score=8",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title.getValue().title()));
            assertEquals(java.time.Duration.ofMillis(100),title.getValue().times().fadeIn());
            bind(new PlaySoundFunction(Map.of("sound","minecraft:block.note_block.bell","soundCategory","invalid")),f).runFunction(null,List.of(target));
            verify(state.player).playSound(any(Location.class),eq("minecraft:block.note_block.bell"),eq(SoundCategory.MASTER),eq(1f),eq(1f));
            verifyNoInteractions(outsider.player.getInventory());
            verify(outsider.player,never()).showTitle(any(net.kyori.adventure.title.Title.class)); verify(outsider.player,never()).sendMessage(any(net.kyori.adventure.text.Component.class));
        }
    }

    @Test
    void cloningAnEnabledMechanicAllowsIndependentRegistrationAndExecutionInAnotherRun() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration());var menus=new HeadlessMenus()) {
            var original=bind(new SetStatusFunction(Map.of("status","activated")),f);
            original.enable(f.run,original.getLocation());
            var copy=original.clone(); assertNotNull(copy);
            var second=new RunFixture.Run(f.dungeon,f.world);
            copy.enable(second,new Location(f.world,2,70,0));
            assertSame(second,copy.getInstance());
            copy.runFunction(null,List.of()); assertEquals("activated",second.getStatus()); assertNull(f.run.getStatus());
            original.runFunction(null,List.of()); assertEquals("activated",f.run.getStatus());
            original.disable();
            assertTrue(second.getFunctionListeners(SetStatusFunction.class).contains(copy));
            copy.disable(); assertTrue(second.getFunctionListeners(SetStatusFunction.class).isEmpty());
        }
    }

}
