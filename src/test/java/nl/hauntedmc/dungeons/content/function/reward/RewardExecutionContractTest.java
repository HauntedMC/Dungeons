package nl.hauntedmc.dungeons.content.function.reward;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class RewardExecutionContractTest {
    @TempDir Path directory;

    @Test
    void experienceRewardsGrantOncePerPlayerAndDoNotLeakClaimsToAnotherFunction() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus();
                var inventories = new HeadlessInventoryMenus()) {
            var first = new PlayerState();
            var firstSession = f.session(first);
            var second = new PlayerState();
            var secondSession = f.session(second);
            var config = Map.<String, Object>of("xp", 20, "levels", 2, "cooldownDisabled", true);
            var reward = new RewardFunction(config);
            reward.initializeFields();
            reward.setInstance(f.run);
            reward.setLocation(new Location(f.world, 0, 70, 0));
            reward.runFunction(null, List.of(firstSession));
            reward.runFunction(null, List.of(firstSession));
            verify(first.player, times(1)).giveExp(20);
            verify(first.player, times(1)).giveExpLevels(2);
            verify(second.player, never()).giveExp(anyInt());
            reward.runFunction(null, List.of(secondSession));
            verify(second.player).giveExp(20);
            var other = new RewardFunction(config);
            other.initializeFields();
            other.setInstance(f.run);
            other.setLocation(new Location(f.world, 1, 70, 0));
            other.runFunction(null, List.of(firstSession));
            verify(first.player, times(2)).giveExp(20);
        }
    }

    @Test
    void clonedRewardFunctionsKeepClaimHistoryAndMutableRewardStacksIndependent() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus();
                var inventories = new HeadlessInventoryMenus()) {
            var state = new PlayerState();
            var session = f.session(state);
            var template =
                    new RewardFunction(
                            Map.of(
                                    "xp",
                                    20,
                                    "rewards",
                                    List.of(PlayerState.stack(3)),
                                    "cooldownDisabled",
                                    true));
            template.initializeFields();
            template.setInstance(f.run);
            template.setLocation(new Location(f.world, 0, 70, 0));
            var left = (RewardFunction) template.clone();
            var right = (RewardFunction) template.clone();
            assertNotNull(left);
            assertNotNull(right);
            left.runFunction(null, List.of(session));
            right.runFunction(null, List.of(session));
            verify(state.player, times(2)).giveExp(20);
            left.getRewards().getFirst().setAmount(1);
            assertEquals(3, right.getRewards().getFirst().getAmount());
            assertEquals(3, template.getRewards().getFirst().getAmount());
            template.runFunction(null, List.of(session));
            verify(state.player, times(3)).giveExp(20);
        }
    }

    @Test
    void openingRewardMenusGivesEachPlayerIndependentStacksAndClosingTransfersLeftoversOnlyOnce()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus();
                var gui = new HeadlessInventoryMenus()) {
            var first = new PlayerState();
            var second = new PlayerState();
            var firstMenu = new PlayerState();
            var secondMenu = new PlayerState();
            var template = PlayerState.stack(5);
            var reward =
                    new RewardFunction(
                            Map.of("rewards", List.of(template), "cooldownDisabled", true));
            reward.initializeFields();
            reward.setInstance(f.run);
            reward.setLocation(new Location(f.world, 0, 70, 0));
            reward.onEnable();
            var window = gui.windows().getLast();
            org.mockito.Mockito.when(window.getInventoryFor(first.player))
                    .thenReturn(
                            new nl.hauntedmc.dungeons.gui.framework.window.GuiInventory(
                                    new HashMap<>(), firstMenu.inventory));
            org.mockito.Mockito.when(window.getInventoryFor(second.player))
                    .thenReturn(
                            new nl.hauntedmc.dungeons.gui.framework.window.GuiInventory(
                                    new HashMap<>(), secondMenu.inventory));
            org.mockito.ArgumentCaptor<
                            nl.hauntedmc.dungeons.gui.framework.actions.Action<
                                    org.bukkit.event.inventory.InventoryOpenEvent>>
                    open = org.mockito.ArgumentCaptor.captor();
            org.mockito.ArgumentCaptor<
                            nl.hauntedmc.dungeons.gui.framework.actions.Action<
                                    org.bukkit.event.inventory.InventoryCloseEvent>>
                    close = org.mockito.ArgumentCaptor.captor();
            verify(window).addOpenAction(org.mockito.Mockito.eq("loaditems"), open.capture());
            verify(window).addCloseAction(org.mockito.Mockito.eq("dropitems"), close.capture());
            for (var player : List.of(first, second)) {
                var event =
                        org.mockito.Mockito.mock(
                                org.bukkit.event.inventory.InventoryOpenEvent.class);
                org.mockito.Mockito.when(event.getPlayer()).thenReturn(player.player);
                open.getValue().run(event);
            }
            org.junit.jupiter.api.Assertions.assertNotSame(template, firstMenu.contents[0]);
            firstMenu.contents[0].setAmount(2);
            assertEquals(5, secondMenu.contents[0].getAmount());
            assertEquals(5, template.getAmount());
            var event =
                    org.mockito.Mockito.mock(org.bukkit.event.inventory.InventoryCloseEvent.class);
            org.mockito.Mockito.when(event.getPlayer()).thenReturn(first.player);
            close.getValue().run(event);
            close.getValue().run(event);
            org.mockito.ArgumentCaptor<org.bukkit.inventory.ItemStack> delivered =
                    org.mockito.ArgumentCaptor.captor();
            verify(first.inventory).addItem(delivered.capture());
            assertEquals(2, delivered.getValue().getAmount());
            org.junit.jupiter.api.Assertions.assertTrue(
                    Arrays.stream(firstMenu.contents).allMatch(Objects::isNull));
            var reopen =
                    org.mockito.Mockito.mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
            org.mockito.Mockito.when(reopen.getPlayer()).thenReturn(first.player);
            open.getValue().run(reopen);
            org.junit.jupiter.api.Assertions.assertNull(firstMenu.contents[0]);
        }
    }

    @Test
    void finishDeliveryQueuesClickedAndUnclaimedItemsWithoutGivingThemBeforeCompletion()
            throws Exception {
        var settings = new YamlConfiguration();
        settings.set("rewards.deliver_on_finish", true);
        try (var f = new RunFixture(directory, settings);
                var menus = new HeadlessMenus();
                var gui = new HeadlessInventoryMenus()) {
            var state = new PlayerState();
            var session = f.session(state);
            f.run.addPlayer(session);
            var menuState = new PlayerState();
            var reward =
                    new RewardFunction(
                            Map.of(
                                    "rewards",
                                    List.of(PlayerState.stack(2), PlayerState.stack(3)),
                                    "cooldownDisabled",
                                    true));
            reward.initializeFields();
            reward.setInstance(f.run);
            reward.setLocation(new Location(f.world, 0, 70, 0));
            reward.onEnable();
            var window = gui.windows().getLast();
            var inventory =
                    new nl.hauntedmc.dungeons.gui.framework.window.GuiInventory(
                            new HashMap<>(), menuState.inventory);
            org.mockito.Mockito.when(window.getInventoryFor(state.player)).thenReturn(inventory);
            org.mockito.ArgumentCaptor<
                            nl.hauntedmc.dungeons.gui.framework.actions.Action<
                                    org.bukkit.event.inventory.InventoryOpenEvent>>
                    open = org.mockito.ArgumentCaptor.captor();
            org.mockito.ArgumentCaptor<
                            nl.hauntedmc.dungeons.gui.framework.actions.Action<
                                    org.bukkit.event.inventory.InventoryCloseEvent>>
                    close = org.mockito.ArgumentCaptor.captor();
            verify(window).addOpenAction(org.mockito.Mockito.eq("loaditems"), open.capture());
            verify(window).addCloseAction(org.mockito.Mockito.eq("dropitems"), close.capture());
            var opening =
                    org.mockito.Mockito.mock(org.bukkit.event.inventory.InventoryOpenEvent.class);
            org.mockito.Mockito.when(opening.getPlayer()).thenReturn(state.player);
            open.getValue().run(opening);
            assertEquals(2, inventory.buttons().size());
            org.mockito.ArgumentCaptor<
                            nl.hauntedmc.dungeons.gui.framework.actions.Action<
                                    org.bukkit.event.inventory.InventoryClickEvent>>
                    click = org.mockito.ArgumentCaptor.captor();
            verify(inventory.buttons().get(0))
                    .addAction(org.mockito.Mockito.eq("click"), click.capture());
            var clicking =
                    TestEvents.cancellable(org.bukkit.event.inventory.InventoryClickEvent.class);
            org.mockito.Mockito.when(clicking.getWhoClicked()).thenReturn(state.player);
            click.getValue().run(clicking);
            org.junit.jupiter.api.Assertions.assertTrue(clicking.isCancelled());
            org.junit.jupiter.api.Assertions.assertNull(menuState.contents[0]);
            assertEquals(1, f.run.getRewardInventories().get(state.player.getUniqueId()).size());
            var closing =
                    org.mockito.Mockito.mock(org.bukkit.event.inventory.InventoryCloseEvent.class);
            org.mockito.Mockito.when(closing.getPlayer()).thenReturn(state.player);
            close.getValue().run(closing);
            close.getValue().run(closing);
            f.run.pushPlayerRewards(state.player);
            assertEquals(
                    List.of(2, 3),
                    session.getRewardItems().stream()
                            .map(org.bukkit.inventory.ItemStack::getAmount)
                            .toList());
            verify(state.inventory, never())
                    .addItem(org.mockito.Mockito.<org.bukkit.inventory.ItemStack>any());
            open.getValue().run(opening);
            org.junit.jupiter.api.Assertions.assertNull(menuState.contents[1]);
        }
    }
}
