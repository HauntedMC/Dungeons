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
                        var meta = mock(ItemMeta.class);
                        when(meta.getPersistentDataContainer())
                                .thenReturn(
                                        mock(org.bukkit.persistence.PersistentDataContainer.class));
                        when(item.getItemMeta()).thenReturn(meta);
                        when(item.clone()).thenReturn(item);
                    });
    private final MockedConstruction<Button> buttons =
            mockConstruction(
                    Button.class,
                    (button, context) -> {
                        if (context.arguments().size() > 1
                                && context.arguments().get(1) instanceof ItemStack item) {
                            when(button.getItem()).thenReturn(item);
                        }
                    });
    private final MockedConstruction<GuiWindow> windows = mockConstruction(GuiWindow.class);

    public java.util.List<GuiWindow> windows() {
        return windows.constructed();
    }

    @Override
    public void close() {
        windows.close();
        buttons.close();
        items.close();
    }
}
