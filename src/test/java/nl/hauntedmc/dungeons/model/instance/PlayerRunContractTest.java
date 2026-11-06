package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.event.DungeonEndEvent;
import nl.hauntedmc.dungeons.event.DungeonStartEvent;
import nl.hauntedmc.dungeons.event.PlayerLeaveDungeonEvent;
import nl.hauntedmc.dungeons.listener.instance.PlayListener;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class PlayerRunContractTest {
    @TempDir Path directory;

    static YamlConfiguration settings() {
        var settings = new YamlConfiguration();
        settings.set("players.keep_on_entry.inventory", false);
        settings.set("players.keep_on_entry.experience", false);
        settings.set("players.keep_on_entry.health", true);
        settings.set("players.keep_on_entry.food", true);
        return settings;
    }

    @Test
    void entryAndExitRestoreInventoryExperienceAndMembershipAndIgnoreDuplicateOperations()
            throws Exception {
        try (var fixture = new RunFixture(directory, settings())) {
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            state.contents[40] = PlayerState.stack(2);
            state.armor[0] = PlayerState.stack(1);
            var session = fixture.session(state);
            var origin = state.location.clone();
            fixture.run.addPlayer(session);
            fixture.run.addPlayer(session);
            assertSame(fixture.run, session.getInstance());
            assertEquals(1, fixture.run.getPlayers().size());
            assertEquals(GameMode.ADVENTURE, state.gameMode);
            fixture.scheduler.jobs.getFirst().run();
            assertNull(state.contents[0]);
            assertNull(state.contents[40]);
            assertEquals(0, state.level);
            fixture.run.removePlayer(session);
            fixture.run.removePlayer(session);
            assertNull(session.getInstance());
            assertTrue(fixture.run.getPlayers().isEmpty());
            assertTrue(fixture.run.getLivingPlayers().isEmpty());
            assertEquals(origin, state.location);
            assertEquals(GameMode.SURVIVAL, state.gameMode);
            assertEquals(17, state.level);
            assertEquals(.4f, state.exp);
            assertEquals(7, state.contents[0].getAmount());
            assertEquals(2, state.contents[40].getAmount());
            assertEquals(1, state.armor[0].getAmount());
        }
    }

    @Test
    void deferredEntryCannotClearRestoredItemsOrExperienceAfterThePlayerHasAlreadyLeft()
            throws Exception {
        try (var fixture = new RunFixture(directory, settings())) {
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(7);
            var session = fixture.session(state);
            fixture.run.addPlayer(session);
            fixture.run.removePlayer(session);
            fixture.scheduler.jobs.getFirst().run();
            assertEquals(7, state.contents[0].getAmount());
            assertEquals(17, state.level);
            assertEquals(.4f, state.exp);
            assertNull(session.getInstance());
        }
    }

    @Test
    void cancelledDeparturePreservesEveryMembershipAndEmitsNoEndUntilAForcedDepartureSucceeds()
            throws Exception {
        try (var fixture = new RunFixture(directory, settings())) {
            var state = new PlayerState();
            var session = fixture.session(state);
            fixture.run.addPlayer(session);
            fixture.scheduler.jobs.getFirst().run();
            List<Event> events = new ArrayList<>();
            var manager = Bukkit.getPluginManager();
            doAnswer(
                            c -> {
                                Event event = c.getArgument(0);
                                events.add(event);
                                if (event instanceof PlayerLeaveDungeonEvent leave)
                                    leave.setCancelled(true);
                                return null;
                            })
                    .when(manager)
                    .callEvent(any());
            fixture.run.removePlayer(session, false);
            assertSame(fixture.run, session.getInstance());
            assertTrue(fixture.run.getPlayers().contains(session));
            assertTrue(fixture.run.getLivingPlayers().contains(session));
            assertEquals(GameMode.ADVENTURE, state.gameMode);
            assertEquals(0, state.level);
            assertTrue(events.stream().noneMatch(e -> e instanceof DungeonEndEvent));
            fixture.run.removePlayer(session, true);
            assertNull(session.getInstance());
            assertTrue(fixture.run.getPlayers().isEmpty());
            assertEquals(1, events.stream().filter(e -> e instanceof DungeonEndEvent).count());
        }
    }

    @Test
    void startingIsIdempotentAndDisposalCancelsEveryRunTicker() throws Exception {
        try (var fixture = new RunFixture(directory, settings())) {
            fixture.run.startGame();
            fixture.run.startGame();
            assertTrue(fixture.run.isStarted());
            assertEquals(2, fixture.scheduler.jobs.size());
            var timer =
                    fixture.scheduler.jobs.stream()
                            .filter(j -> j.period == 20)
                            .findFirst()
                            .orElseThrow();
            for (int i = 0; i < 65; i++) timer.run();
            assertEquals(65, fixture.run.getTimeElapsed());
            assertFalse(timer.cancelled);
            fixture.run.onDispose();
            assertTrue(fixture.scheduler.jobs.stream().allMatch(j -> j.cancelled));
            timer.run();
            assertEquals(65, fixture.run.getTimeElapsed());
            Bukkit.getPluginManager();
            verify(Bukkit.getPluginManager(), times(1)).callEvent(any(DungeonStartEvent.class));
        }
    }

    @Test
    void timeLimitExpiresAtTheBoundaryAndRemovesEveryParticipantExactlyOnce() throws Exception {
        var settings = settings();
        settings.set("runs.time_limit_minutes", 1);
        try (var fixture = new RunFixture(directory, settings)) {
            var first = fixture.session(new PlayerState());
            var second = fixture.session(new PlayerState());
            fixture.run.addPlayer(first);
            fixture.run.addPlayer(second);
            fixture.run.startGame();
            var timer =
                    fixture.scheduler.jobs.stream()
                            .filter(j -> j.period == 20)
                            .findFirst()
                            .orElseThrow();
            for (int i = 0; i < 59; i++) timer.run();
            assertEquals(2, fixture.run.getPlayers().size());
            assertEquals(1, fixture.run.getTimeLeft());
            timer.run();
            assertEquals(0, fixture.run.getTimeLeft());
            assertTrue(timer.cancelled);
            assertTrue(fixture.run.getPlayers().isEmpty());
            assertNull(first.getInstance());
            assertNull(second.getInstance());
            timer.run();
            assertEquals(60, fixture.run.getTimeElapsed());
            verify(Bukkit.getPluginManager(), times(1)).callEvent(any(DungeonEndEvent.class));
        }
    }

    @Test
    void oneBrokenFunctionCannotAbandonOtherFunctionsTickersOrTrackedEntityCleanup()
            throws Exception {
        try (var fixture = new RunFixture(directory, settings())) {
            fixture.run.startGame();
            var broken = mock(DungeonFunction.class);
            var healthy = mock(DungeonFunction.class);
            doThrow(new IllegalStateException("broken disable")).when(broken).disable();
            fixture.run.getFunctions().put(new Location(fixture.world, 0, 0, 0), broken);
            fixture.run.getFunctions().put(new Location(fixture.world, 1, 0, 0), healthy);
            var entity = mock(Entity.class);
            fixture.run.getEntities().add(entity);
            assertDoesNotThrow(fixture.run::onDispose);
            verify(healthy).disable();
            verify(entity).remove();
            assertTrue(fixture.scheduler.jobs.stream().allMatch(j -> j.cancelled));
        }
    }

    @Test
    void deathRespawnAndLastLifeSpectatingAreIdempotentAndCloseTheRunOnTheNextTick()
            throws Exception {
        var settings = settings();
        settings.set("players.lives", 2);
        try (var fixture = new RunFixture(directory, settings)) {
            var state = new PlayerState();
            var session = fixture.session(state);
            fixture.run.addPlayer(session);
            var listener = (PlayListener) fixture.run.getListener();
            var death = mock(PlayerDeathEvent.class);
            when(death.getEntity()).thenReturn(state.player);
            when(death.getDrops()).thenReturn(new ArrayList<>());
            when(death.isCancelled()).thenReturn(true);
            listener.onPlayerDeath(death);
            assertEquals(2, fixture.run.getPlayerLives().get(state.player.getUniqueId()));
            when(death.isCancelled()).thenReturn(false);
            listener.onPlayerDeath(death);
            listener.onPlayerDeath(death);
            assertEquals(1, fixture.run.getPlayerLives().get(state.player.getUniqueId()));
            assertTrue(fixture.run.getLivingPlayers().contains(session));
            var respawn = mock(PlayerRespawnEvent.class);
            when(respawn.getPlayer()).thenReturn(state.player);
            listener.onPlayerRespawn(respawn);
            assertFalse(session.isDead());
            listener.onPlayerDeath(death);
            assertEquals(0, fixture.run.getPlayerLives().get(state.player.getUniqueId()));
            assertTrue(fixture.run.getLivingPlayers().isEmpty());
            listener.onPlayerRespawn(respawn);
            assertEquals(GameMode.SPECTATOR, state.gameMode);
            assertSame(fixture.run, session.getInstance());
            fixture.scheduler.jobs.getLast().run();
            assertNull(session.getInstance());
            assertTrue(fixture.run.getPlayers().isEmpty());
        }
    }

    @Test
    void
            savedCheckpointAdmissionKeepsPersistedWorldIndependentAndCannotTeleportAPlayerAfterDeparture()
                    throws Exception {
        try (var f = new RunFixture(directory, settings())) {
            var state = new PlayerState();
            var session = f.session(state);
            session.setDungeonSavePoint(new Location(null, 12, 81, -9), f.dungeon.getWorldName());
            f.run.addPlayer(session);
            assertNull(session.getDungeonSavePoint(f.dungeon.getWorldName()).getWorld());
            f.run.removePlayer(session);
            var restored = state.location.clone();
            var pending = List.copyOf(f.scheduler.jobs);
            pending.forEach(CapturedScheduler.Job::run);
            assertEquals(restored, state.location);
            assertNull(session.getInstance());
            assertFalse(f.run.isStarted());
        }
    }

    @Test
    void oneEntityRemovalFailureCannotAbandonTheRemainingOwnedEntities() throws Exception {
        try (var f = new RunFixture(directory, settings())) {
            var broken = mock(Entity.class);
            var healthy = mock(Entity.class);
            doThrow(new IllegalStateException("entity invalidated by another plugin"))
                    .when(broken)
                    .remove();
            f.run.getEntities().add(broken);
            f.run.getEntities().add(healthy);
            assertDoesNotThrow(f.run::onDispose);
            verify(healthy).remove();
            assertNull(f.run.getEntities());
        }
    }

    @Test
    void deathTransfersDoublyTaggedProgressionItemsToASurvivorExactlyOnceAndRemovesTheirDrops()
            throws Exception {
        var rules = settings();
        rules.set("players.lives", 1);
        try (var f = new RunFixture(directory, rules)) {
            var dead = new PlayerState();
            var survivor = new PlayerState();
            var source = f.session(dead);
            var target = f.session(survivor);
            f.run.addPlayer(source);
            f.run.addPlayer(target);
            var tags = new java.util.HashMap<org.bukkit.NamespacedKey, Object>();
            tags.put(new org.bukkit.NamespacedKey("dungeons", "dungeonkey"), 1);
            tags.put(new org.bukkit.NamespacedKey("dungeons", "dungeonitem"), 1);
            var item = TaggedItems.stack(org.bukkit.Material.TRIPWIRE_HOOK, 1, tags);
            dead.contents[0] = item;
            var offhand = item.clone();
            dead.contents[40] = offhand;
            var drops = new ArrayList<>(List.of(item, offhand));
            var event = mock(PlayerDeathEvent.class);
            when(event.getEntity()).thenReturn(dead.player);
            when(event.getDrops()).thenReturn(drops);
            ((PlayListener) f.run.getListener()).onPlayerDeath(event);
            verify(survivor.inventory, times(1)).addItem(item);
            verify(survivor.inventory, times(1)).addItem(offhand);
            assertTrue(drops.isEmpty());
            assertNull(dead.contents[0]);
            assertFalse(f.run.getLivingPlayers().contains(source));
            assertTrue(f.run.getLivingPlayers().contains(target));
        }
    }

    @Test
    void entryResetRestoresHealthAndFoodToPlayableMaximumsWithoutOverfillingHunger()
            throws Exception {
        var rules = settings();
        rules.set("players.keep_on_entry.health", false);
        rules.set("players.keep_on_entry.food", false);
        try (var f = new RunFixture(directory, rules);
                var entities =
                        org.mockito.Mockito.mockStatic(
                                nl.hauntedmc.dungeons.util.entity.EntityUtils.class)) {
            var state = new PlayerState();
            var session = f.session(state);
            entities.when(
                            () ->
                                    nl.hauntedmc.dungeons.util.entity.EntityUtils.getMaxHealth(
                                            state.player))
                    .thenReturn(20.0);
            f.run.addPlayer(session);
            f.scheduler.jobs.getFirst().run();
            assertEquals(20, state.health);
            assertEquals(20, state.food);
        }
    }

    @Test
    void
            departuresTransferIdenticalKeysInSeparateSlotsOnceEachAndHonorTheDungeonItemTransferPolicy()
                    throws Exception {
        for (boolean transferItems : List.of(false, true)) {
            var rules = settings();
            rules.set("players.transfer_dungeon_items", transferItems);
            try (var f =
                    new RunFixture(directory.resolve(Boolean.toString(transferItems)), rules)) {
                var departing = new PlayerState();
                var survivor = new PlayerState();
                var source = f.session(departing);
                var target = f.session(survivor);
                f.run.addPlayer(source);
                f.run.addPlayer(target);
                var tags = new java.util.HashMap<org.bukkit.NamespacedKey, Object>();
                tags.put(new org.bukkit.NamespacedKey("dungeons", "dungeonkey"), 1);
                tags.put(new org.bukkit.NamespacedKey("dungeons", "dungeonitem"), 1);
                var first = TaggedItems.stack(org.bukkit.Material.TRIPWIRE_HOOK, 1, tags);
                var second = first.clone();
                var runtimeItem =
                        TaggedItems.stack(
                                org.bukkit.Material.PAPER,
                                2,
                                java.util.Map.of(
                                        new org.bukkit.NamespacedKey("dungeons", "dungeonitem"),
                                        1));
                departing.contents[0] = first;
                departing.contents[40] = second;
                departing.contents[1] = runtimeItem;
                f.run.removePlayer(source);
                verify(survivor.inventory).addItem(first);
                verify(survivor.inventory).addItem(second);
                verify(survivor.inventory, times(transferItems ? 1 : 0)).addItem(runtimeItem);
                assertNull(source.getInstance());
                assertSame(f.run, target.getInstance());
            }
        }
    }
}
