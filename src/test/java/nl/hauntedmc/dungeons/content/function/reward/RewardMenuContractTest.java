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
}
