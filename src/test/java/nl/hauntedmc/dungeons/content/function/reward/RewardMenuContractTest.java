package nl.hauntedmc.dungeons.content.function.reward;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.gui.framework.actions.Action;
import nl.hauntedmc.dungeons.gui.framework.window.*;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.*;

class RewardMenuContractTest {
    @TempDir Path directory;

    static void enable(RewardFunction reward, RunFixture f) {
        reward.initializeFields();
        reward.setInstance(f.run);
        reward.setLocation(new Location(f.world, 0, 70, 0));
        reward.onEnable();
    }

    static final class Menu {
        final PlayerState contents = new PlayerState();
        final GuiInventory inventory = new GuiInventory(new HashMap<>(), contents.inventory);
        final Action<InventoryOpenEvent> open;
        final Action<InventoryCloseEvent> close;
        final InventoryOpenEvent opening;
        final InventoryCloseEvent closing;

        Menu(RewardFunction reward, PlayerState state) {
            contents.contents = new org.bukkit.inventory.ItemStack[54];
            when(reward.instanceGui.getInventoryFor(state.player)).thenReturn(inventory);
            ArgumentCaptor<Action<InventoryOpenEvent>> opens = ArgumentCaptor.captor();
            ArgumentCaptor<Action<InventoryCloseEvent>> closes = ArgumentCaptor.captor();
            verify(reward.instanceGui).addOpenAction(eq("loaditems"), opens.capture());
            verify(reward.instanceGui).addCloseAction(eq("dropitems"), closes.capture());
            open = opens.getValue();
            close = closes.getValue();
            opening = TestEvents.cancellable(InventoryOpenEvent.class);
            closing = mock(InventoryCloseEvent.class);
            when(opening.getPlayer()).thenReturn(state.player);
            when(closing.getPlayer()).thenReturn(state.player);
        }

        void open() {
            open.run(opening);
        }

        void close() {
            close.run(closing);
        }
    }

    @Test
    void randomRewardMenusClampCountsToAvailableItemsAndKeepPlayerStacksIndependent()
            throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus();
                var gui = new HeadlessInventoryMenus()) {
            var first = new PlayerState();
            var second = new PlayerState();
            var template = List.of(PlayerState.stack(4), PlayerState.stack(7));
            var reward =
                    new RandomRewardFunction(
                            Map.of(
                                    "rewards",
                                    template,
                                    "rewardMinCount",
                                    5,
                                    "rewardMaxCount",
                                    1,
                                    "cooldownDisabled",
                                    true));
            enable(reward, f);
            var left = new Menu(reward, first);
            var right = new Menu(reward, second);
            left.open();
            right.open();
            assertEquals(
                    Set.of(4, 7),
                    Arrays.stream(left.contents.contents)
                            .filter(Objects::nonNull)
                            .map(org.bukkit.inventory.ItemStack::getAmount)
                            .collect(java.util.stream.Collectors.toSet()));
            assertEquals(2, Arrays.stream(left.contents.contents).filter(Objects::nonNull).count());
            left.contents.contents[0].setAmount(1);
            assertEquals(
                    Set.of(4, 7),
                    Arrays.stream(right.contents.contents)
                            .filter(Objects::nonNull)
                            .map(org.bukkit.inventory.ItemStack::getAmount)
                            .collect(java.util.stream.Collectors.toSet()));
            assertEquals(
                    List.of(4, 7),
                    template.stream().map(org.bukkit.inventory.ItemStack::getAmount).toList());
            left.close();
            left.open();
            assertNull(left.contents.contents[0]);
        }
    }

    @Test
    void lootTableMenusRecoverAMissingTableAndClaimGeneratedLootOnlyOnce() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus();
                var gui = new HeadlessInventoryMenus()) {
            var repository = mock(nl.hauntedmc.dungeons.runtime.rewards.LootTableRepository.class);
            f.paper.runtime.setLootTableRepository(repository);
            var table =
                    new nl.hauntedmc.dungeons.content.reward.LootTable(
                            Map.of(
                                    "namespace",
                                    "cache",
                                    "minItems",
                                    1,
                                    "maxItems",
                                    1,
                                    "lootItems",
                                    Map.of(
                                            9,
                                            new nl.hauntedmc.dungeons.content.reward.LootTableItem(
                                                    PlayerState.stack(6)))));
            when(repository.get("cache")).thenReturn(null, table);
            var state = new PlayerState();
            var reward =
                    new LootTableRewardsFunction(
                            Map.of("loottableName", "cache", "cooldownDisabled", true));
            enable(reward, f);
            var menu = new Menu(reward, state);
            menu.open();
            assertTrue(Arrays.stream(menu.contents.contents).allMatch(Objects::isNull));
            menu.open();
            assertEquals(
                    List.of(6),
                    Arrays.stream(menu.contents.contents)
                            .filter(Objects::nonNull)
                            .map(org.bukkit.inventory.ItemStack::getAmount)
                            .toList());
            menu.close();
            menu.close();
            menu.open();
            verify(state.inventory).addItem(any(org.bukkit.inventory.ItemStack.class));
            assertTrue(Arrays.stream(menu.contents.contents).allMatch(Objects::isNull));
            verify(repository, times(2)).get("cache");
        }
    }
}
