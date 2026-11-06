package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.*;

import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.*;

import java.util.*;

/** In-memory Paper item/PDC boundary with copied metadata, quantities, and independent clones. */
public final class TaggedItems {
    private TaggedItems() {}

    public static ItemStack stack(
            Material material, int amount, Map<NamespacedKey, Object> initialTags) {
        var item = mock(ItemStack.class);
        var tags = new HashMap<>(initialTags);
        int[] quantity = {amount};
        Map<ItemMeta, Map<NamespacedKey, Object>> snapshots = new IdentityHashMap<>();
        when(item.getType()).thenReturn(material);
        when(item.getAmount()).thenAnswer(c -> quantity[0]);
        doAnswer(
                        c -> {
                            quantity[0] = c.getArgument(0);
                            return null;
                        })
                .when(item)
                .setAmount(anyInt());
        when(item.getItemMeta())
                .thenAnswer(
                        c -> {
                            var copy = new HashMap<>(tags);
                            var meta = metadata(copy);
                            snapshots.put(meta, copy);
                            return meta;
                        });
        when(item.setItemMeta(any(ItemMeta.class)))
                .thenAnswer(
                        c -> {
                            var copy = snapshots.get(c.getArgument(0));
                            if (copy == null) return false;
                            tags.clear();
                            tags.putAll(copy);
                            return true;
                        });
        when(item.clone()).thenAnswer(c -> stack(material, quantity[0], tags));
        when(item.isSimilar(any(ItemStack.class)))
                .thenAnswer(
                        c -> {
                            ItemStack other = c.getArgument(0);
                            if (other.getType() != material) return false;
                            var data = other.getItemMeta().getPersistentDataContainer();
                            if (!tags.keySet().equals(data.getKeys())) return false;
                            for (var entry : tags.entrySet()) {
                                Object value = entry.getValue();
                                Object actual =
                                        value instanceof String
                                                ? data.get(
                                                        entry.getKey(), PersistentDataType.STRING)
                                                : data.get(
                                                        entry.getKey(), PersistentDataType.INTEGER);
                                if (!Objects.equals(value, actual)) return false;
                            }
                            return true;
                        });
        return item;
    }

    private static ItemMeta metadata(Map<NamespacedKey, Object> tags) {
        var meta = mock(ItemMeta.class);
        var data = mock(PersistentDataContainer.class);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.getKeys()).thenAnswer(c -> Set.copyOf(tags.keySet()));
        when(data.get(any(NamespacedKey.class), any()))
                .thenAnswer(
                        c -> {
                            Object value = tags.get(c.getArgument(0));
                            PersistentDataType<?, ?> type = c.getArgument(1);
                            return type.getPrimitiveType().isInstance(value) ? value : null;
                        });
        when(data.has(any(NamespacedKey.class), any()))
                .thenAnswer(
                        c -> {
                            PersistentDataType<?, ?> type = c.getArgument(1);
                            return type.getPrimitiveType().isInstance(tags.get(c.getArgument(0)));
                        });
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
        return meta;
    }
}
