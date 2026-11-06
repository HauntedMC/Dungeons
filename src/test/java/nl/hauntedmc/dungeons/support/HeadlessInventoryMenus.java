package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.mockito.MockedConstruction;

/** Substitutes rendered GUI items/windows; supplied gameplay stacks retain their stateful fixture. */
public final class HeadlessInventoryMenus implements AutoCloseable {
    private final MockedConstruction<ItemStack> items =
            mockConstruction(
                    ItemStack.class,
                    (item, context) -> {
                        when(item.getItemMeta()).thenReturn(mock(ItemMeta.class));
                        when(item.clone()).thenReturn(item);
                    });
    private final MockedConstruction<Button> buttons = mockConstruction(Button.class);
    private final MockedConstruction<GuiWindow> windows = mockConstruction(GuiWindow.class);

    @Override
    public void close() {
        windows.close();
        buttons.close();
        items.close();
    }
}
