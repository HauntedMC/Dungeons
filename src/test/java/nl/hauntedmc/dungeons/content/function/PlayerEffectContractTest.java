package nl.hauntedmc.dungeons.content.function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class PlayerEffectContractTest {
    @TempDir Path directory;

    @Test
    void commandContextsSanitizeInputDeduplicateOnlineTargetsAndAnchorConsoleExecution()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var first = new PlayerState();
            when(first.player.getName()).thenReturn("Alice");
            var source = f.session(first);
            var second = new PlayerState();
            when(second.player.getName()).thenReturn("Bob");
            var other = f.session(second);
            var event = mock(TriggerFireEvent.class);
            when(event.getPlayerSession()).thenReturn(source);
            var command =
                    GameplayFunctionContractTest.bind(
                            new CommandFunction(
                                    Map.of(
                                            "command",
                                            " /say {player} ",
                                            "commandType",
                                            1,
                                            "forEachPlayer",
                                            true)),
                            f);
            command.setTargetType(FunctionTargetType.PLAYER);
            command.runFunction(event, List.of(source, source, other));
            f.paper.bukkit.verify(() -> Bukkit.dispatchCommand(first.player, "say Alice"));
            f.paper.bukkit.verify(() -> Bukkit.dispatchCommand(second.player, "say Bob"));
            second.online = false;
            command.runFunction(event, List.of(other));
            f.paper.bukkit.verify(() -> Bukkit.dispatchCommand(second.player, "say Bob"), times(1));
            var console = mock(ConsoleCommandSender.class);
            f.paper.bukkit.when(Bukkit::getConsoleSender).thenReturn(console);
            when(f.world.getKey()).thenReturn(new NamespacedKey("dungeons", "crypt_0"));
            var consoleCommand =
                    GameplayFunctionContractTest.bind(
                            new CommandFunction(Map.of("command", "/say ready", "commandType", 2)),
                            f);
            consoleCommand.runFunction(event, List.of());
            f.paper.bukkit.verify(
                    () ->
                            Bukkit.dispatchCommand(
                                    console,
                                    "execute in dungeons:crypt_0 positioned 0.0 70.0 0.0 run say ready"));
            GameplayFunctionContractTest.bind(new CommandFunction(Map.of("command", " / ")), f)
                    .runFunction(event, List.of(source));
            f.paper.bukkit.verify(() -> Bukkit.dispatchCommand(first.player, ""), never());
        }
    }

    @Test
    void revivalRestoresOnlyDeadMembersAndConsumesAUseOnlyAfterSuccessfulRevival()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("players.lives", 1);
        settings.set("players.gamemode", "invalid");
        try (var f = new RunFixture(directory, settings)) {
            var living = new PlayerState();
            var reviver = f.session(living);
            var dead = new PlayerState();
            var target = f.session(dead);
            f.run.addPlayer(reviver);
            f.run.addPlayer(target);
            var revive =
                    GameplayFunctionContractTest.bind(
                            new RevivePlayerFunction(
                                    Map.of("maxRevives", 1, "livesAfterRevival", 3)),
                            f);
            revive.revivePlayer(target, reviver);
            assertEquals(2, f.run.getLivingPlayers().size());
            f.run.getLivingPlayers().remove(target);
            dead.gameMode = GameMode.SPECTATOR;
            f.run.getPlayerLives().put(dead.player.getUniqueId(), 0);
            revive.revivePlayer(f.session(new PlayerState()), reviver);
            revive.revivePlayer(target, reviver);
            assertEquals(GameMode.ADVENTURE, dead.gameMode);
            assertEquals(3, f.run.getPlayerLives().get(dead.player.getUniqueId()));
            assertTrue(f.run.getLivingPlayers().contains(target));
            assertEquals(living.location, dead.location);
            f.run.getLivingPlayers().remove(target);
            dead.gameMode = GameMode.SPECTATOR;
            revive.revivePlayer(target, reviver);
            assertFalse(f.run.getLivingPlayers().contains(target));
            assertEquals(GameMode.SPECTATOR, dead.gameMode);
        }
    }

    @Test
    void itemAndKeyDispensersGiveIndependentTaggedStacksToEveryTargetAndCanDropAtTheirLocation()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var gui = new HeadlessInventoryMenus()) {
            var first = new PlayerState();
            var second = new PlayerState();
            var left = f.session(first);
            var right = f.session(second);
            var tags = new HashMap<NamespacedKey, Object>();
            tags.put(new NamespacedKey("dungeons", "dungeonkey"), 1);
            tags.put(new NamespacedKey("dungeons", "dungeonitem"), 1);
            var template = TaggedItems.stack(Material.TRIPWIRE_HOOK, 3, tags);
            var config = Map.<String, Object>of("item", template, "notify", false);
            for (var function :
                    List.of(new GiveItemFunction(config), new GiveKeyFunction(config))) {
                GameplayFunctionContractTest.bind(function, f)
                        .runFunction(null, List.of(left, right));
                org.mockito.ArgumentCaptor<org.bukkit.inventory.ItemStack> delivered =
                        org.mockito.ArgumentCaptor.captor();
                verify(first.inventory).addItem(delivered.capture());
                var firstItem = delivered.getValue();
                verify(second.inventory).addItem(delivered.capture());
                var secondItem = delivered.getValue();
                assertNotSame(template, firstItem);
                assertNotSame(firstItem, secondItem);
                assertTrue(nl.hauntedmc.dungeons.util.item.ItemUtils.verifyKeyItem(firstItem));
                assertTrue(nl.hauntedmc.dungeons.util.item.ItemUtils.verifyDungeonItem(secondItem));
                firstItem.setAmount(1);
                assertEquals(3, secondItem.getAmount());
                assertEquals(3, template.getAmount());
                clearInvocations(first.inventory, second.inventory);
            }
            GameplayFunctionContractTest.bind(
                            new GiveItemFunction(Map.of("item", template, "drop", true)), f)
                    .runFunction(null, List.of(left));
            verify(f.world)
                    .dropItem(
                            eq(new Location(f.world, 0, 70, 0)),
                            any(org.bukkit.inventory.ItemStack.class));
            verify(first.inventory, never()).addItem(any(org.bukkit.inventory.ItemStack.class));
        }
    }

    @Test
    void leaveMechanicsRemoveOnlyTheirRequestedMembersAndRestoreThePlayersOriginalWorld()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            var departing = f.session(state);
            var staying = f.session(new PlayerState());
            var outsider = new PlayerState();
            var untracked = f.session(outsider);
            var original = state.location.clone();
            var outsideOriginal = outsider.location.clone();
            f.run.addPlayer(departing);
            f.run.addPlayer(staying);
            GameplayFunctionContractTest.bind(new LeaveDungeonFunction(), f)
                    .runFunction(null, List.of(departing, untracked));
            assertNull(departing.getInstance());
            assertEquals(original, state.location);
            assertSame(f.run, staying.getInstance());
            assertEquals(List.of(staying), f.run.getPlayers());
            assertEquals(outsideOriginal, outsider.location);
        }
    }

    @Test
    void remoteSignalsPreserveTheirSourceAndReleaseTemporaryListenersEvenWhenDispatchFails()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var handlers = mockStatic(org.bukkit.event.HandlerList.class)) {
            var session = f.session(new PlayerState());
            var source = mock(TriggerFireEvent.class);
            when(source.getPlayerSession()).thenReturn(session);
            var signal =
                    GameplayFunctionContractTest.bind(
                            new RemoteTriggerFunction(
                                    Map.of("triggerName", "puzzle", "range", 6.0)),
                            f);
            var manager = Bukkit.getPluginManager();
            clearInvocations(manager);
            signal.runFunction(source, List.of());
            signal.runFunction(source, List.of());
            org.mockito.ArgumentCaptor<nl.hauntedmc.dungeons.event.RemoteTriggerEvent> events =
                    org.mockito.ArgumentCaptor.captor();
            verify(manager, times(2)).callEvent(events.capture());
            for (var event : events.getAllValues()) {
                assertEquals("puzzle", event.getTriggerName());
                assertEquals(6, event.getRange());
                assertEquals(signal.getLocation(), event.getOrigin());
                assertSame(session, event.getPlayerSession());
                assertSame(f.run, event.getInstance());
            }
            doThrow(new IllegalStateException("external listener failed"))
                    .when(manager)
                    .callEvent(any(nl.hauntedmc.dungeons.event.RemoteTriggerEvent.class));
            assertThrows(IllegalStateException.class, () -> signal.runFunction(source, List.of()));
            org.mockito.ArgumentCaptor<org.bukkit.event.Listener> listeners =
                    org.mockito.ArgumentCaptor.captor();
            verify(manager, times(3)).registerEvents(listeners.capture(), eq(f.paper.plugin));
            for (var listener : listeners.getAllValues()) {
                handlers.verify(() -> org.bukkit.event.HandlerList.unregisterAll(listener));
            }
        }
    }
}
