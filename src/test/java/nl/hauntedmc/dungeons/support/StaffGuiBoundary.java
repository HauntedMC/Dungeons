package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.*;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.mockito.MockedConstruction;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * Inventory/network rendering boundary; real staff menu navigation and event handlers run
 * unchanged.
 */
public final class StaffGuiBoundary implements AutoCloseable {
    public final Map<Inventory, Map<Integer, ItemStack>> contents = new IdentityHashMap<>();
    public final Inventory empty = mock(Inventory.class);
    public Inventory top = empty;
    public Consumer<Inventory> closing = inventory -> {};
    private final MockedConstruction<ItemStack> items;

    public StaffGuiBoundary(StaffFixture fixture, PlayerState player) {
        items =
                mockConstruction(
                        ItemStack.class,
                        (item, context) -> {
                            Material material = (Material) context.arguments().getFirst();
                            var amount =
                                    new AtomicInteger(
                                            context.arguments().size() > 1
                                                    ? (Integer) context.arguments().get(1)
                                                    : 1);
                            when(item.getType()).thenReturn(material);
                            when(item.getAmount()).thenAnswer(c -> amount.get());
                            doAnswer(
                                            c -> {
                                                amount.set(c.getArgument(0));
                                                return null;
                                            })
                                    .when(item)
                                    .setAmount(anyInt());
                            when(item.clone()).thenReturn(item);
                            NativeItemBytes.support(item);
                            var meta = mock(ItemMeta.class);
                            var label = new AtomicReference<Component>();
                            when(meta.displayName()).thenAnswer(c -> label.get());
                            doAnswer(
                                            c -> {
                                                label.set(c.getArgument(0));
                                                return null;
                                            })
                                    .when(meta)
                                    .displayName(any(Component.class));
                            when(item.getItemMeta()).thenReturn(meta);
                        });
        fixture.run
                .paper
                .bukkit
                .when(() -> Bukkit.createInventory(isNull(), anyInt(), any(Component.class)))
                .thenAnswer(
                        c -> {
                            Inventory inventory = mock(Inventory.class);
                            int size = c.getArgument(1);
                            when(inventory.getSize()).thenReturn(size);
                            var slots = new HashMap<Integer, ItemStack>();
                            contents.put(inventory, slots);
                            doAnswer(
                                            a -> {
                                                slots.clear();
                                                return null;
                                            })
                                    .when(inventory)
                                    .clear();
                            doAnswer(
                                            a -> {
                                                slots.put(a.getArgument(0), a.getArgument(1));
                                                return null;
                                            })
                                    .when(inventory)
                                    .setItem(anyInt(), any());
                            when(inventory.getItem(anyInt()))
                                    .thenAnswer(a -> slots.get(a.getArgument(0)));
                            return inventory;
                        });
        var openView = mock(InventoryView.class);
        when(openView.getTopInventory()).thenAnswer(c -> top);
        when(player.player.getOpenInventory()).thenReturn(openView);
        when(player.player.openInventory(any(Inventory.class)))
                .thenAnswer(
                        c -> {
                            if (top != empty) closing.accept(top);
                            top = c.getArgument(0);
                            return openView;
                        });
        doAnswer(
                        c -> {
                            Inventory previous = top;
                            top = empty;
                            closing.accept(previous);
                            return null;
                        })
                .when(player.player)
                .closeInventory();
    }

    public String label(int slot) {
        ItemStack item = contents.getOrDefault(top, Map.of()).get(slot);
        return item == null
                ? ""
                : nl.hauntedmc.dungeons.util.text.ComponentUtils.plainText(
                        item.getItemMeta().displayName());
    }

    @Override
    public void close() {
        items.close();
    }
}
