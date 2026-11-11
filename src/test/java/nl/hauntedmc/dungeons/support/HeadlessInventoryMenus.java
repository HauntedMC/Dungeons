package nl.hauntedmc.dungeons.support;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.util.List;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.mockito.MockedConstruction;

/**
 * Substitutes rendered GUI items/windows; supplied gameplay stacks retain their stateful fixture.
 */
public final class HeadlessInventoryMenus implements AutoCloseable {
    private final MockedConstruction<ItemStack> items =
            mockConstruction(
                    ItemStack.class,
                    (item, context) -> {
                        if (!context.arguments().isEmpty()
                                && context.arguments().getFirst() instanceof Material material) {
                            when(item.getType()).thenReturn(material);
                        }
                        NativeItemBytes.support(item);
                        var meta = mock(ItemMeta.class);
                        when(meta.getPersistentDataContainer())
                                .thenReturn(mock(PersistentDataContainer.class));
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

    public List<GuiWindow> windows() {
        return windows.constructed();
    }

    @Override
    public void close() {
        windows.close();
        buttons.close();
        items.close();
    }
}
