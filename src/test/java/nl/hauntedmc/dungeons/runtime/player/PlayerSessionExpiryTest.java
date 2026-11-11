package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.persistence.StateStore;
import nl.hauntedmc.dungeons.runtime.dungeon.DungeonRepository;
import nl.hauntedmc.dungeons.runtime.instance.PreparationRegistry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueEntry;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerSessionExpiryTest {
    @TempDir Path directory;

    static PlayerSessionRegistry registry(RunFixture fixture, AtomicLong clock) {
        fixture.paper.config.set("sessions.offline_retention_seconds", 1);
        var registry = new PlayerSessionRegistry(fixture.paper.runtime, clock::get);
        fixture.paper.runtime.setPlayerSessions(registry);
        return registry;
    }

    static int amount(PlayerState state) {
        return Arrays.stream(state.contents)
                .filter(Objects::nonNull)
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    @Test
    void refundedKeysWithNoInventorySpaceRetryAutomaticallyAfterEviction() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            var state = new PlayerState();
            for (int slot = 0; slot < state.contents.length; slot++)
                state.contents[slot] = PlayerState.stack(64);
            var session = registry.put(state.player);
            assertTrue(session.reserveAccessKey("crypt", PlayerState.stack(1)));
            state.online = false;
            assertTrue(session.refundReservedAccessKey());
            registry.disconnected(state.player);
            clock.set(TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, registry.expireOfflineSessions());
            state.online = true;
            var recreated = registry.put(state.player);
            assertTrue(recreated.recoverPendingState());
            state.contents[0] = null;
            recreated.flushPendingAccessKeyRefund();
            assertEquals(1, state.contents[0].getAmount());
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
                    IllegalStateException.class,
                    () ->
                            session.addStaffRefunds(
                                    java.util.UUID.randomUUID(), PlayerState.stack(1)));

            assertThrows(IllegalStateException.class, () -> session.setDungeonFinished(f.dungeon));
            state.online = true;
            assertNotSame(session, registry.put(state.player));
        }
    }

    @Test
    void sessionExpiryReleasesDecodedDungeonRecordsAndReconnectReadsDurableState()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var clock = new AtomicLong();
            var registry = registry(f, clock);
            f.paper.runtime.setQueueRegistry(
                    new DungeonQueueRegistry(registry, f.paper.runtime.activeInstances()));
            var catalog = mock(DungeonRepository.class);
            when(catalog.getLoadedDungeons()).thenReturn(List.of(f.dungeon));
            f.paper.runtime.setDungeonCatalog(catalog);
            registry.start();
            var player = new PlayerState();
            registry.put(player.player);
            var id = player.player.getUniqueId();
            var progress = f.dungeon.playerDataStore();
            progress.setCompleted(id, true);
            player.online = false;
            registry.disconnected(player.player);
            clock.set(TimeUnit.SECONDS.toNanos(2));
            assertEquals(1, registry.expireOfflineSessions());
            // Simulate a durable correction after eviction, before the next connection.
            f.paper.state.seed(
                    "dungeon-progress/" + f.dungeon.getDungeonId(),
                    id.toString(),
                    id,
                    "Finished: false\n");
            assertFalse(progress.isCompleted(id));
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
    void deferredRecoverySurvivesActualEvictionAndIsRestoredOnlyOnce() throws Exception {
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
            assertTrue(recreated.recoverPendingState());
            assertEquals(12, amount(state));
            assertTrue(
                    f.paper
                            .state
                            .payload("player-profile", state.player.getUniqueId().toString())
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
            for (int count : new int[] {1, 2}) {
                state.online = true;
                assertTrue(session.reserveAccessKey("crypt", PlayerState.stack(count)));
                state.online = false;
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
            session.addStaffRefunds(java.util.UUID.randomUUID(), PlayerState.stack(4));
            doThrow(new IllegalStateException("player save failed")).when(state.player).saveData();
            session.flushPendingAccessKeyRefund();
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
            var failedKey =
                    new StateStore.Key(
                            "player-profile",
                            failedPlayer.getUniqueId().toString(),
                            failedPlayer.getUniqueId());
            f.paper.state.failedWrites.add(failedKey);
            try {
                assertEquals(7, registry.expireOfflineSessions());
                assertEquals(5, registry.size());
                assertEquals(4, registry.expireOfflineSessions());
                assertEquals(1, registry.size());
                assertSame(session, registry.get(failedPlayer));
                assertFalse(session.isExpired());
                assertNotNull(session.getActiveFunction());
            } finally {
                f.paper.state.failedWrites.remove(failedKey);
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
            registry.start();
            registry.start();
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
            state.online = true;
            assertTrue(session.reserveAccessKey("crypt", PlayerState.stack(1)));
            state.online = false;
            assertEquals(0, registry.expireOfflineSessions());
            assertTrue(session.refundReservedAccessKey());
            var queue = mock(DungeonQueueEntry.class);
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
            f.paper.state.seed(
                    "player-profile",
                    state.player.getUniqueId().toString(),
                    state.player.getUniqueId(),
                    "CompletedDungeons: [broken\n");
            assertThrows(IllegalStateException.class, () -> registry.put(state.player));
            assertEquals(0, registry.size());
            assertEquals(
                    "CompletedDungeons: [broken\n",
                    f.paper.state.payload("player-profile", state.player.getUniqueId().toString()));
        }
    }
}
