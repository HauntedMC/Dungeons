package nl.hauntedmc.dungeons.gui.menu;

import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import org.bukkit.Material;

/** Header and utility rail shared by player inventories. Install before the controls. */
public final class MenuFrame {
    private MenuFrame() {}

    public static void apply(GuiWindow window) {
        Button accent = tile(Material.CYAN_STAINED_GLASS_PANE);
        window.addButton(3, accent);
        window.addButton(5, accent);
    }

    public static void divideNavigation(GuiWindow window) {
        var divider = tile(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 1; slot < window.getSize(); slot += 9) window.addButton(slot, divider);
    }

    private static Button tile(Material material) {
        var button = new Button("frame", material, " ");
        button.getItem().editMeta(meta -> meta.setHideTooltip(true));
        return button;
    }
}
