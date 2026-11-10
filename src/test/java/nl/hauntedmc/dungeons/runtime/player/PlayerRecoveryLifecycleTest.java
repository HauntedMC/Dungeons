package nl.hauntedmc.dungeons.runtime.player;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;

import nl.hauntedmc.dungeons.listener.DungeonListener;
import nl.hauntedmc.dungeons.runtime.queue.DungeonQueueRegistry;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.support.TestStateStore;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class PlayerRecoveryLifecycleTest {
    @TempDir Path directory;

    @Test
    void onlineRefundSurvivesPlayerSaveFailureAndReconnectWithoutDuplication() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            state.contents[0] = PlayerState.stack(3);
            var session = f.session(state);
            assertTrue(session.reserveAccessKey("crypt", PlayerState.stack(1)));
            doThrow(new IllegalStateException("native save failed")).when(state.player).saveData();
            assertTrue(session.refundReservedAccessKey());
            var file = directory.resolve("players/" + state.player.getUniqueId() + ".recovery.yml");
            assertFalse(f.paper.state.payload("player-recovery", state.player.getUniqueId().toString()).isEmpty());
            var persisted = f.paper.state.yaml("player-recovery", state.player.getUniqueId().toString());
            assertTrue(persisted.contains("delivery"));
            state.contents[0] = PlayerState.stack(3); // state loaded from the last native save
            doNothing().when(state.player).saveData();
            var restarted = new DungeonPlayerSession(f.paper.plugin, state.player);
            assertTrue(restarted.recoverPendingState());
            assertEquals(4, state.contents[0].getAmount());
            assertTrue(restarted.recoverPendingState());
            assertEquals(4, state.contents[0].getAmount());
        }
    }

    @Test
    void reconstructedSessionUsesThePersistedBaselineRatherThanDungeonItemsForNormalExit()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessInventoryMenus()) {
            var player = new PlayerState();
            player.contents[0] = PlayerState.stack(8);
            var first = f.session(player);
            first.saveInventory();
            player.contents[0].setAmount(1);
            player.gameMode = GameMode.CREATIVE;
            player.exp = 0;
            player.level = 0;
            player.heldSlot = 6;
            var reconstructed = new DungeonPlayerSession(f.paper.plugin, player.player);
            reconstructed.saveInventory();
            reconstructed.captureAndShowHotbar(mock(PlayerHotbarSnapshot.class));
            f.scheduler.jobs.getLast().run();
            reconstructed.restoreCapturedHotbar();
            Path path =
                    directory.resolve("players/" + player.player.getUniqueId() + ".recovery.yml");
            assertTrue(new PlayerRecoveryJournal(TestStateStore.player(path)).hasRecovery());
            reconstructed.completeDeparture(true, true);
            assertEquals(8, player.contents[0].getAmount());
            assertEquals(GameMode.SURVIVAL, player.gameMode);
            assertEquals(17, player.level);
            assertEquals(.4f, player.exp);
            assertEquals(2, player.heldSlot);
        }
    }

    @Test
    void standaloneEditorKeepsRecoveryAfterClosingMenuAndReconnectClearsStaleControls()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessInventoryMenus();
                var tools = mockStatic(ItemUtils.class)) {
            var player = new PlayerState();
            var session = f.session(player);
            f.paper
                    .bukkit
                    .when(() -> Bukkit.getWorld(player.originWorld.getUID()))
                    .thenReturn(player.originWorld);
            f.paper.entities.when(() -> EntityUtils.getMaxHealth(player.player)).thenReturn(20.0);
            player.contents[0] = PlayerState.stack(8);
            session.setEditMode(true);
            session.captureAndShowHotbar(mock(PlayerHotbarSnapshot.class));
            f.scheduler.jobs.getLast().run();
            session.restoreCapturedHotbar();
            Path path =
                    directory.resolve("players/" + player.player.getUniqueId() + ".recovery.yml");
            assertTrue(new PlayerRecoveryJournal(TestStateStore.player(path)).hasRecovery());
            assertTrue(session.isEditMode());
            session.setChatListening(true);
            var listener =
                    new DungeonListener(
                            f.paper.plugin,
                            f.paper.runtime.playerSessions(),
                            mock(DungeonQueueRegistry.class),
                            null,
                            null);
            var event = mock(PlayerJoinEvent.class);
            when(event.getPlayer()).thenReturn(player.player);
            listener.onPlayerJoin(event);
            assertEquals(8, player.contents[0].getAmount());
            assertEquals(GameMode.SURVIVAL, player.gameMode);
            assertFalse(session.isEditMode());
            assertFalse(session.isChatListening());
            assertNull(session.getSavedHotbar());
            assertNull(session.getCurrentHotbar());
            assertFalse(new PlayerRecoveryJournal(TestStateStore.player(path)).hasRecovery());
            player.contents[0].setAmount(3);
            session.restoreCapturedHotbar();
            session.restoreInventory();
            assertEquals(3, player.contents[0].getAmount());
        }
    }

    @Test
    void newRegistryOnJoinRestoresFullPreDungeonSnapshot() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var player = new PlayerState();
            var session = f.session(player);
            f.paper
                    .bukkit
                    .when(() -> Bukkit.getWorld(player.originWorld.getUID()))
                    .thenReturn(player.originWorld);
            f.paper.entities.when(() -> EntityUtils.getMaxHealth(player.player)).thenReturn(20.0);
            player.contents[0] = PlayerState.stack(12);
            f.run.addPlayer(session);
            player.inventory.clear();
            player.gameMode = GameMode.ADVENTURE;
            player.exp = 0;
            var registry = new PlayerSessionRegistry(f.paper.plugin);
            var listener =
                    new DungeonListener(
                            f.paper.plugin, registry, mock(DungeonQueueRegistry.class), null, null);
            var event = mock(PlayerJoinEvent.class);
            when(event.getPlayer()).thenReturn(player.player);
            listener.onPlayerJoin(event);
            assertEquals(12, player.contents[0].getAmount());
            assertEquals(GameMode.SURVIVAL, player.gameMode);
            assertEquals(.4f, player.exp);
            player.contents[0].setAmount(3);
            listener.onPlayerJoin(event);
            assertEquals(3, player.contents[0].getAmount());
        }
    }

    @Test
    void deadReconnectWaitsForRespawnBeforeRecovery() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var player = new PlayerState();
            var session = f.session(player);
            f.paper
                    .bukkit
                    .when(() -> Bukkit.getWorld(player.originWorld.getUID()))
                    .thenReturn(player.originWorld);
            f.paper.entities.when(() -> EntityUtils.getMaxHealth(player.player)).thenReturn(20.0);
            player.contents[0] = PlayerState.stack(12);
            assertTrue(session.prepareRecovery());
            player.inventory.clear();
            player.vanillaDead = true;
            var registry = new PlayerSessionRegistry(f.paper.plugin);
            var listener =
                    new DungeonListener(
                            f.paper.plugin, registry, mock(DungeonQueueRegistry.class), null, null);
            var event = mock(PlayerJoinEvent.class);
            when(event.getPlayer()).thenReturn(player.player);
            listener.onPlayerJoin(event);
            assertNull(player.contents[0]);
            verify(player.player, never()).kick(any(Component.class));
            var respawn = mock(PlayerRespawnEvent.class);
            when(respawn.getPlayer()).thenReturn(player.player);
            listener.onRecoveryRespawn(respawn);
            player.vanillaDead = false;
            f.scheduler.immediate.getLast().run();
            assertEquals(12, player.contents[0].getAmount());
        }
    }

    @Test
    void corruptJournalKicksPlayerAndCannotBeOverwrittenByANewSession() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var player = new PlayerState();
            Path path =
                    directory.resolve("players/" + player.player.getUniqueId() + ".recovery.yml");
            Files.createDirectories(path.getParent());
            f.paper.state.journal(path, "broken: [");
            assertThrows(
                    IllegalStateException.class,
                    () -> f.paper.runtime.playerSessions().put(player.player));
            assertEquals("broken: [", f.paper.state.payload("player-recovery", player.player.getUniqueId().toString()));
            verify(player.player).kick(any(Component.class));
            assertNull(f.paper.runtime.playerSessions().get(player.player));
        }
    }

    @Test
    void queuedHotbarCannotReplaceInventoryAfterRecovery() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessInventoryMenus()) {
            var player = new PlayerState();
            var session = f.session(player);
            f.paper
                    .bukkit
                    .when(() -> Bukkit.getWorld(player.originWorld.getUID()))
                    .thenReturn(player.originWorld);
            f.paper.entities.when(() -> EntityUtils.getMaxHealth(player.player)).thenReturn(20.0);
            player.contents[0] = PlayerState.stack(8);
            var menu = mock(PlayerHotbarSnapshot.class);
            session.captureAndShowHotbar(menu);
            assertTrue(session.recoverPendingState());
            f.scheduler.jobs.getLast().run();
            verify(menu, never()).applyTo(any(), anyBoolean());
            assertEquals(8, player.contents[0].getAmount());
        }
    }
}
