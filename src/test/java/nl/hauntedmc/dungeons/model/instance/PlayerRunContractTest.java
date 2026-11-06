package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerRunContractTest {
    @TempDir Path directory;
    static YamlConfiguration settings() {
        var settings=new YamlConfiguration();
        settings.set("players.keep_on_entry.inventory",false);
        settings.set("players.keep_on_entry.experience",false);
        settings.set("players.keep_on_entry.health",true);
        settings.set("players.keep_on_entry.food",true);
        return settings;
    }
    @Test
    void entryAndExitRestoreInventoryExperienceAndMembershipAndIgnoreDuplicateOperations() throws Exception {
        try(var fixture=new RunFixture(directory,settings())) {
            var state=new PlayerState();
            state.contents[0]=PlayerState.stack(7);
            state.contents[40]=PlayerState.stack(2);
            state.armor[0]=PlayerState.stack(1);
            var session=fixture.session(state);
            var origin=state.location.clone();
            fixture.run.addPlayer(session);
            fixture.run.addPlayer(session);
            assertSame(fixture.run,session.getInstance());
            assertEquals(1,fixture.run.getPlayers().size());
            assertEquals(GameMode.ADVENTURE,state.gameMode);
            fixture.scheduler.jobs.getFirst().run();
            assertNull(state.contents[0]); assertNull(state.contents[40]);
            assertEquals(0,state.level);
            fixture.run.removePlayer(session);
            fixture.run.removePlayer(session);
            assertNull(session.getInstance());
            assertTrue(fixture.run.getPlayers().isEmpty());
            assertTrue(fixture.run.getLivingPlayers().isEmpty());
            assertEquals(origin,state.location);
            assertEquals(GameMode.SURVIVAL,state.gameMode);
            assertEquals(17,state.level); assertEquals(.4f,state.exp);
            assertEquals(7,state.contents[0].getAmount());
            assertEquals(2,state.contents[40].getAmount());
            assertEquals(1,state.armor[0].getAmount());
        }
    }
    @Test
    void deferredEntryCannotClearRestoredItemsOrExperienceAfterThePlayerHasAlreadyLeft() throws Exception {
        try(var fixture=new RunFixture(directory,settings())) {
            var state=new PlayerState(); state.contents[0]=PlayerState.stack(7);
            var session=fixture.session(state);
            fixture.run.addPlayer(session);
            fixture.run.removePlayer(session);
            fixture.scheduler.jobs.getFirst().run();
            assertEquals(7,state.contents[0].getAmount());
            assertEquals(17,state.level); assertEquals(.4f,state.exp);
            assertNull(session.getInstance());
        }
    }

    @Test
    void cancelledDeparturePreservesEveryMembershipAndEmitsNoEndUntilAForcedDepartureSucceeds() throws Exception {
        try(var fixture=new RunFixture(directory,settings())) {
            var state=new PlayerState(); var session=fixture.session(state);
            fixture.run.addPlayer(session);
            fixture.scheduler.jobs.getFirst().run();
            java.util.List<org.bukkit.event.Event> events=new java.util.ArrayList<>();
            var manager=org.bukkit.Bukkit.getPluginManager();
            doAnswer(c -> {
                org.bukkit.event.Event event=c.getArgument(0); events.add(event);
                if(event instanceof nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent leave) leave.setCancelled(true);
                return null;
            }).when(manager).callEvent(any());
            fixture.run.removePlayer(session,false);
            assertSame(fixture.run,session.getInstance());
            assertTrue(fixture.run.getPlayers().contains(session));
            assertTrue(fixture.run.getLivingPlayers().contains(session));
            assertEquals(GameMode.ADVENTURE,state.gameMode);
            assertEquals(0,state.level);
            assertTrue(events.stream().noneMatch(e -> e instanceof nl.hauntedmc.dungeons.event.DungeonEndEvent));
            fixture.run.removePlayer(session,true);
            assertNull(session.getInstance());
            assertTrue(fixture.run.getPlayers().isEmpty());
            assertEquals(1,events.stream().filter(e -> e instanceof nl.hauntedmc.dungeons.event.DungeonEndEvent).count());
        }
    }

    @Test
    void startingIsIdempotentAndDisposalCancelsEveryRunTicker() throws Exception {
        try(var fixture=new RunFixture(directory,settings())) {
            fixture.run.startGame(); fixture.run.startGame();
            assertTrue(fixture.run.isStarted());
            assertEquals(2,fixture.scheduler.jobs.size());
            var timer=fixture.scheduler.jobs.stream().filter(j -> j.period==20).findFirst().orElseThrow();
            for(int i=0;i<65;i++) timer.run();
            assertEquals(65,fixture.run.getTimeElapsed());
            assertFalse(timer.cancelled);
            fixture.run.onDispose();
            assertTrue(fixture.scheduler.jobs.stream().allMatch(j -> j.cancelled));
            timer.run(); assertEquals(65,fixture.run.getTimeElapsed());
            org.bukkit.Bukkit.getPluginManager();
            verify(org.bukkit.Bukkit.getPluginManager(),times(1)).callEvent(any(nl.hauntedmc.dungeons.event.DungeonStartEvent.class));
        }
    }

    @Test
    void timeLimitExpiresAtTheBoundaryAndRemovesEveryParticipantExactlyOnce() throws Exception {
        var settings=settings(); settings.set("runs.time_limit_minutes",1);
        try(var fixture=new RunFixture(directory,settings)) {
            var first=fixture.session(new PlayerState()); var second=fixture.session(new PlayerState());
            fixture.run.addPlayer(first); fixture.run.addPlayer(second);
            fixture.run.startGame();
            var timer=fixture.scheduler.jobs.stream().filter(j -> j.period==20).findFirst().orElseThrow();
            for(int i=0;i<59;i++) timer.run();
            assertEquals(2,fixture.run.getPlayers().size());
            assertEquals(1,fixture.run.getTimeLeft());
            timer.run();
            assertEquals(0,fixture.run.getTimeLeft());
            assertTrue(timer.cancelled);
            assertTrue(fixture.run.getPlayers().isEmpty());
            assertNull(first.getInstance()); assertNull(second.getInstance());
            timer.run(); assertEquals(60,fixture.run.getTimeElapsed());
            verify(org.bukkit.Bukkit.getPluginManager(),times(1)).callEvent(any(nl.hauntedmc.dungeons.event.DungeonEndEvent.class));
        }
    }

    @Test
    void oneBrokenFunctionCannotAbandonOtherFunctionsTickersOrTrackedEntityCleanup() throws Exception {
        try(var fixture=new RunFixture(directory,settings())) {
            fixture.run.startGame();
            var broken=mock(nl.hauntedmc.dungeons.model.element.DungeonFunction.class);
            var healthy=mock(nl.hauntedmc.dungeons.model.element.DungeonFunction.class);
            doThrow(new IllegalStateException("broken disable")).when(broken).disable();
            fixture.run.getFunctions().put(new org.bukkit.Location(fixture.world,0,0,0),broken);
            fixture.run.getFunctions().put(new org.bukkit.Location(fixture.world,1,0,0),healthy);
            var entity=mock(org.bukkit.entity.Entity.class); fixture.run.getEntities().add(entity);
            assertDoesNotThrow(fixture.run::onDispose);
            verify(healthy).disable(); verify(entity).remove();
            assertTrue(fixture.scheduler.jobs.stream().allMatch(j -> j.cancelled));
        }
    }

}
