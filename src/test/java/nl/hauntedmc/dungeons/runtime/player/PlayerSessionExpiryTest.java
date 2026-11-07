package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

class PlayerSessionExpiryTest {
    @TempDir Path directory;

    static PlayerSessionRegistry registry(RunFixture fixture, AtomicLong clock) {
        fixture.paper.config.set("sessions.offline_retention_seconds", 1);
        var registry = new PlayerSessionRegistry(fixture.paper.plugin, clock::get);
        fixture.paper.runtime.setPlayerSessions(registry);
        return registry;
    }

    static int amount(PlayerState state) {
        return Arrays.stream(state.contents)
                .filter(java.util.Objects::nonNull)
                .mapToInt(org.bukkit.inventory.ItemStack::getAmount)
                .sum();
    }

    @Test
    void refundedKeysWithNoInventorySpaceRemainClaimableAfterEviction() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            var state = new PlayerState();
            for (int slot = 0; slot < state.contents.length; slot++)
                state.contents[slot] = PlayerState.stack(64);
            var session = registry.put(state.player);
            state.online = false;
            assertTrue(session.reserveAccessKey("crypt", PlayerState.stack(1)));
            assertTrue(session.refundReservedAccessKey());
            registry.disconnected(state.player);
            clock.set(TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, registry.expireOfflineSessions());
            state.online = true;
            var recreated = registry.put(state.player);
            assertTrue(recreated.recoverPendingState());
            assertEquals(1, recreated.getRewardItems().getFirst().getAmount());
            state.contents[0] = null;
            assertTrue(recreated.claimReward(recreated.getRewardItems().getFirst()));
            assertEquals(1, state.contents[0].getAmount());
            assertTrue(recreated.getRewardItems().isEmpty());
        }
    }

    @Test
    void inactiveDisconnectHasAGracePeriodThenReleasesSelectionsAndRejectsStaleWrites()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            var state = new PlayerState();
            var session = registry.put(state.player);
            session.setActiveFunction(mock(DungeonFunction.class));
            session.setCopiedFunction(mock(DungeonFunction.class));
            session.setTargetLocation(state.location);
            state.online = false;
            registry.disconnected(state.player);
            assertSame(session, registry.get(state.player));
            assertEquals(0, registry.expireOfflineSessions());
            clock.set(TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, registry.expireOfflineSessions());
            assertEquals(0, registry.size());
            assertTrue(session.isExpired());
            assertNull(session.getActiveFunction());
            assertNull(session.getCopiedFunction());
            assertNull(session.getTargetLocation());
            assertThrows(IllegalStateException.class, session::getPlayer);
            assertThrows(
                    IllegalStateException.class, () -> session.addReward(PlayerState.stack(1)));
            assertThrows(IllegalStateException.class, () -> session.setDungeonFinished(f.dungeon));
            state.online = true;
            assertNotSame(session, registry.put(state.player));
        }
    }

    @Test
    void activeRunsRemainPinnedAndReconnectRebindsTheSameSessionBeforeNormalListeners()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("players.offline_kick.enabled", false);
        settings.set("players.keep_on_entry.inventory", false);
        try (var f = new RunFixture(directory, settings)) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            var original = new PlayerState();
            original.contents[0] = PlayerState.stack(12);
            var session = registry.put(original.player);
            f.run.addPlayer(session);
            original.online = false;
            registry.disconnected(original.player);
            clock.set(TimeUnit.DAYS.toNanos(1));
            assertEquals(0, registry.expireOfflineSessions());
            assertSame(session, registry.get(original.player));
            var reconnected = new PlayerState();
            var originalId = original.player.getUniqueId();
            when(reconnected.player.getUniqueId()).thenReturn(originalId);
            var join = mock(PlayerJoinEvent.class);
            when(join.getPlayer()).thenReturn(reconnected.player);
            registry.onJoin(join);
            assertSame(session, registry.get(reconnected.player));
            assertSame(f.run, session.getInstance());
            assertTrue(f.run.getPlayers().contains(session));
            assertNull(registry.get(original.player));
            registry.disconnected(original.player); // stale quit cannot expire the new connection
            clock.addAndGet(TimeUnit.DAYS.toNanos(1));
            assertEquals(0, registry.expireOfflineSessions());
            f.run.removePlayer(session);
            assertEquals(12, reconnected.contents[0].getAmount());
            reconnected.online = false;
            registry.disconnected(reconnected.player);
            clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, registry.expireOfflineSessions());
        }
    }

    @Test
    void deferredRecoveryAndRewardsSurviveActualEvictionAndAreRestoredOnlyOnce() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            var state = new PlayerState();
            f.paper
                    .bukkit
                    .when(() -> Bukkit.getWorld(state.originWorld.getUID()))
                    .thenReturn(state.originWorld);
            f.paper.bukkit.when(() -> Bukkit.getWorld("overworld")).thenReturn(state.originWorld);
            state.contents[0] = PlayerState.stack(12);
            var session = registry.put(state.player);
            session.setDungeonFinished(f.dungeon);
            f.run.addPlayer(session);
            session.addReward(PlayerState.stack(4));
            state.inventory.clear();
            state.online = false;
            f.run.removePlayer(session);
            registry.disconnected(state.player);
            clock.set(TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, registry.expireOfflineSessions());
            state.online = true;
            var recreated = registry.put(state.player);
            assertNotSame(session, recreated);
            assertTrue(recreated.recoverPendingState());
            assertEquals(12, amount(state));
            assertEquals(4, recreated.getRewardItems().getFirst().getAmount());
            assertTrue(recreated.claimReward(recreated.getRewardItems().getFirst()));
            assertEquals(16, amount(state));
            assertTrue(recreated.recoverPendingState());
            assertEquals(16, amount(state));
            assertTrue(recreated.getRewardItems().isEmpty());
            assertTrue(
                    Files.readString(
                                    directory.resolve(
                                            "players/" + state.player.getUniqueId() + ".yml"))
                            .contains("crypt"));
        }
    }

    @Test
    void multipleOfflineRefundsSurviveExpiryAndReconnectDeliversThemWithoutDuplication()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            var state = new PlayerState();
            var session = registry.put(state.player);
            state.online = false;
            for (int count : new int[] {1, 2}) {
                assertTrue(session.reserveAccessKey("crypt", PlayerState.stack(count)));
                assertTrue(session.refundReservedAccessKey());
            }
            registry.disconnected(state.player);
            clock.set(TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, registry.expireOfflineSessions());
            state.online = true;
            var recreated = registry.put(state.player);
            assertTrue(recreated.recoverPendingState());
            recreated.flushPendingAccessKeyRefund();
            assertEquals(3, amount(state));
            assertTrue(recreated.getRewardItems().isEmpty());
            assertTrue(recreated.recoverPendingState());
            assertEquals(3, amount(state));
        }
    }

    @Test
    void failedDeliveryCanBeEvictedBecauseTheUnacknowledgedTransactionIsDurable() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            var state = new PlayerState();
            var session = registry.put(state.player);
            session.addReward(PlayerState.stack(4));
            doThrow(new IllegalStateException("player save failed")).when(state.player).saveData();
            assertFalse(session.claimReward(session.getRewardItems().getFirst()));
            state.online = false;
            registry.disconnected(state.player);
            clock.set(TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, registry.expireOfflineSessions());
            doNothing().when(state.player).saveData();
            state.online = true;
            var recreated = registry.put(state.player);
            assertTrue(recreated.recoverPendingState());
            assertEquals(4, amount(state));
            assertTrue(recreated.recoverPendingState());
            assertEquals(4, amount(state));
        }
    }

    @Test
    void failedCheckpointsRetainSessionsAndFairBoundedSweepsStillExpireOtherPlayers()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            PlayerState blocked = null;
            for (int i = 0; i < 12; i++) {
                var state = new PlayerState();
                registry.put(state.player);
                state.online = false;
                registry.disconnected(state.player);
                if (i == 0) blocked = state;
            }
            var failedPlayer = blocked.player;
            var session = registry.get(failedPlayer);
            session.setActiveFunction(mock(DungeonFunction.class));
            var target = directory.resolve("players/" + failedPlayer.getUniqueId() + ".yml");
            clock.set(TimeUnit.SECONDS.toNanos(2));
            try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                files.when(
                                () ->
                                        Files.move(
                                                any(Path.class),
                                                eq(target),
                                                eq(StandardCopyOption.ATOMIC_MOVE),
                                                eq(StandardCopyOption.REPLACE_EXISTING)))
                        .thenThrow(new IOException("unwritable player data"));
                assertEquals(7, registry.expireOfflineSessions());
                assertEquals(5, registry.size());
                assertEquals(4, registry.expireOfflineSessions());
                assertEquals(1, registry.size());
                assertSame(session, registry.get(failedPlayer));
                assertFalse(session.isExpired());
                assertNotNull(session.getActiveFunction());
            }
            assertEquals(1, registry.expireOfflineSessions());
            assertTrue(session.isExpired());
            assertNull(session.getActiveFunction());
        }
    }

    @Test
    void preparationQueueAndReservationOwnershipPreventExpiryAndShutdownStopsItsTask()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            var queues = mock(DungeonQueueRegistry.class);
            f.paper.runtime.setQueueRegistry(queues);
            registry.start(f.paper.runtime);
            registry.start(f.paper.runtime);
            var ticker = f.scheduler.jobs.getLast();
            assertEquals(20L, ticker.period);
            assertEquals(1, f.scheduler.jobs.size());
            var state = new PlayerState();
            var session = registry.put(state.player);
            state.online = false;
            registry.disconnected(state.player);
            clock.set(TimeUnit.SECONDS.toNanos(2));
            var preparation = mock(PreparationRegistry.Pending.class);
            assertTrue(f.paper.runtime.preparations().register(session, preparation));
            assertEquals(0, registry.expireOfflineSessions());
            f.paper.runtime.preparations().remove(session, preparation);
            assertTrue(session.reserveAccessKey("crypt", PlayerState.stack(1)));
            assertEquals(0, registry.expireOfflineSessions());
            assertTrue(session.refundReservedAccessKey());
            var queue = mock(nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry.class);
            when(queues.getQueue(session)).thenReturn(queue);
            assertEquals(0, registry.expireOfflineSessions());
            when(queues.getQueue(session)).thenReturn(null);
            registry.shutdown();
            assertTrue(ticker.cancelled);
            assertEquals(0, registry.size());
            assertThrows(IllegalStateException.class, () -> registry.put(state.player));
        }
    }

    @Test
    void corruptGlobalPlayerDataIsNeverAcceptedAsAnEmptyEvictableSession() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var registry = registry(f, new AtomicLong());
            var state = new PlayerState();
            var file = directory.resolve("players/" + state.player.getUniqueId() + ".yml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, "CompletedDungeons: [broken\n");
            assertThrows(IllegalStateException.class, () -> registry.put(state.player));
            assertEquals(0, registry.size());
            assertEquals("CompletedDungeons: [broken\n", Files.readString(file));
        }
    }
}
