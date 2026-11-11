package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.inventory.ItemStack;
import org.mockito.MockedStatic;

/**
 * Native item codec boundary; persisted bytes resolve to independent metadata-preserving clones.
 */
public final class NativeItemBytes {
    private static final Map<String, ItemStack> ITEMS = new HashMap<>();

    private NativeItemBytes() {}

    public static void support(ItemStack item) {
        when(item.getMaxStackSize()).thenReturn(64);
        when(item.serializeAsBytes())
                .thenAnswer(
                        call -> {
                            String id = UUID.randomUUID().toString();
                            ITEMS.put(id, item.clone());
                            return id.getBytes(StandardCharsets.UTF_8);
                        });
    }

    public static void decode(MockedStatic<ItemStack> items) {
        items.when(() -> ItemStack.deserializeBytes(any(byte[].class)))
                .thenAnswer(
                        call -> {
                            byte[] bytes = call.getArgument(0);
                            ItemStack stored = ITEMS.get(new String(bytes, StandardCharsets.UTF_8));
                            if (stored == null)
                                throw new IllegalArgumentException("Unknown test item bytes");
                            return stored.clone();
                        });
    }

    public static void clear() {
        ITEMS.clear();
    }
}
