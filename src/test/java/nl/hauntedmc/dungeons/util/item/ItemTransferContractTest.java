package nl.hauntedmc.dungeons.util.item;

import static org.mockito.Mockito.*;

import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class ItemTransferContractTest {
    @Test
    void partialInventoryAcceptanceDropsOnlyTheRemainderSoRewardsAndRefundsCannotDuplicateItems() {
        Player player = mock(Player.class);
        var inventory = mock(PlayerInventory.class);
        var world = mock(World.class);
        var location = new Location(world, 1, 70, 2);
        var supplied = mock(ItemStack.class);
        var remainder = mock(ItemStack.class);
        when(supplied.getType()).thenReturn(Material.DIAMOND);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(location);
        when(inventory.addItem(supplied)).thenReturn(new java.util.HashMap<>(Map.of(0, remainder)));

        ItemUtils.giveOrDrop(player, supplied);
        verify(world).dropItemNaturally(location, remainder);
        verify(world, never()).dropItemNaturally(location, supplied);
        clearInvocations(world);
        ItemUtils.giveOrDropSilently(player, supplied);
        verify(world).dropItemNaturally(location, remainder);
        verify(world, never()).dropItemNaturally(location, supplied);
    }
}
