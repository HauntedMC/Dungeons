package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.*;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.util.*;

/** In-memory Paper item/PDC boundary. Clones own their quantities and tag maps. */
public final class TaggedItems {
    private TaggedItems() {}

    public static ItemStack stack(Material material, int amount, Map<NamespacedKey, Object> tags) {
        var item = mock(ItemStack.class);
        var meta = mock(ItemMeta.class);
        var data = mock(PersistentDataContainer.class);
        int[] quantity = {amount};
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenAnswer(c -> quantity[0]);
        doAnswer(
                        c -> {
                            quantity[0] = c.getArgument(0);
                            return null;
                        })
                .when(item)
                .setAmount(anyInt());
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(item.clone()).thenAnswer(c -> stack(material, quantity[0], new HashMap<>(tags)));
        when(data.get(any(NamespacedKey.class), any())).thenAnswer(c -> tags.get(c.getArgument(0)));
        when(data.has(any(NamespacedKey.class), any()))
                .thenAnswer(c -> tags.containsKey(c.getArgument(0)));
        doAnswer(
                        c -> {
                            tags.put(c.getArgument(0), c.getArgument(2));
                            return null;
                        })
                .when(data)
                .set(any(NamespacedKey.class), any(), any());
        doAnswer(
                        c -> {
                            tags.remove(c.getArgument(0));
                            return null;
                        })
                .when(data)
                .remove(any(NamespacedKey.class));
        return item;
    }
}
