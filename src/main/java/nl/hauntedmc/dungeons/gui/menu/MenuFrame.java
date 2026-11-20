package nl.hauntedmc.dungeons.gui.menu;

import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import org.bukkit.Material;

/** Header and utility rail shared by player inventories. Install before the controls. */
public final class MenuFrame {
    private MenuFrame() {}

    public static void apply(GuiWindow window) {
        Button slate = tile(Material.BLACK_STAINED_GLASS_PANE);
        Button teal = tile(Material.CYAN_STAINED_GLASS_PANE);
        for (int column = 0; column < 9; column++) {
            window.addButton(column, column == 3 || column == 5 ? teal : slate);
            window.addButton(window.getSize() - 9 + column, slate);
        }
    }

    private static Button tile(Material material) {
        var button = new Button("frame", material, " ");
        button.getItem().editMeta(meta -> meta.setHideTooltip(true));
        return button;
    }
}
