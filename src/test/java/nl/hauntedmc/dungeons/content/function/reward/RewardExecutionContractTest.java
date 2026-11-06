package nl.hauntedmc.dungeons.content.function.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.gui.framework.actions.Action;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

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
            var left = template.clone();
            var right = template.clone();
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
            when(window.getInventoryFor(first.player))
                    .thenReturn(new GuiInventory(new HashMap<>(), firstMenu.inventory));
            when(window.getInventoryFor(second.player))
                    .thenReturn(new GuiInventory(new HashMap<>(), secondMenu.inventory));
            ArgumentCaptor<Action<InventoryOpenEvent>> open = ArgumentCaptor.captor();
            ArgumentCaptor<Action<InventoryCloseEvent>> close = ArgumentCaptor.captor();
            verify(window).addOpenAction(eq("loaditems"), open.capture());
            verify(window).addCloseAction(eq("dropitems"), close.capture());
            for (var player : List.of(first, second)) {
                var event = mock(InventoryOpenEvent.class);
                when(event.getPlayer()).thenReturn(player.player);
                open.getValue().run(event);
            }
            assertNotSame(template, firstMenu.contents[0]);
            firstMenu.contents[0].setAmount(2);
            assertEquals(5, secondMenu.contents[0].getAmount());
            assertEquals(5, template.getAmount());
            var event = mock(InventoryCloseEvent.class);
            when(event.getPlayer()).thenReturn(first.player);
            close.getValue().run(event);
            close.getValue().run(event);
            ArgumentCaptor<ItemStack> delivered = ArgumentCaptor.captor();
            verify(first.inventory).addItem(delivered.capture());
            assertEquals(2, delivered.getValue().getAmount());
            assertTrue(Arrays.stream(firstMenu.contents).allMatch(Objects::isNull));
            var reopen = mock(InventoryOpenEvent.class);
            when(reopen.getPlayer()).thenReturn(first.player);
            open.getValue().run(reopen);
            assertNull(firstMenu.contents[0]);
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
            var inventory = new GuiInventory(new HashMap<>(), menuState.inventory);
            when(window.getInventoryFor(state.player)).thenReturn(inventory);
            ArgumentCaptor<Action<InventoryOpenEvent>> open = ArgumentCaptor.captor();
            ArgumentCaptor<Action<InventoryCloseEvent>> close = ArgumentCaptor.captor();
            verify(window).addOpenAction(eq("loaditems"), open.capture());
            verify(window).addCloseAction(eq("dropitems"), close.capture());
            var opening = mock(InventoryOpenEvent.class);
            when(opening.getPlayer()).thenReturn(state.player);
            open.getValue().run(opening);
            assertEquals(2, inventory.buttons().size());
            ArgumentCaptor<Action<InventoryClickEvent>> click = ArgumentCaptor.captor();
            verify(inventory.buttons().get(0)).addAction(eq("click"), click.capture());
            var clicking = TestEvents.cancellable(InventoryClickEvent.class);
            when(clicking.getWhoClicked()).thenReturn(state.player);
            click.getValue().run(clicking);
            assertTrue(clicking.isCancelled());
            assertNull(menuState.contents[0]);
            assertEquals(1, f.run.getRewardInventories().get(state.player.getUniqueId()).size());
            var closing = mock(InventoryCloseEvent.class);
            when(closing.getPlayer()).thenReturn(state.player);
            close.getValue().run(closing);
            close.getValue().run(closing);
            f.run.pushPlayerRewards(state.player);
            assertEquals(
                    List.of(2, 3),
                    session.getRewardItems().stream().map(ItemStack::getAmount).toList());
            verify(state.inventory, never()).addItem(Mockito.<ItemStack>any());
            open.getValue().run(opening);
            assertNull(menuState.contents[1]);
        }
    }
}
