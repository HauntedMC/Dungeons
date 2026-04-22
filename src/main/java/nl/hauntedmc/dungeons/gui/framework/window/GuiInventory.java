package nl.hauntedmc.dungeons.gui.framework.window;

import java.util.HashMap;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import org.bukkit.inventory.Inventory;

/**
 * Per-player inventory snapshot for a {@link GuiWindow}.
 *
 * <p>Each player gets their own button map so runtime menu state can diverge safely between viewers.
 */
public record GuiInventory(HashMap<Integer, Button> buttons, Inventory inventory) {
    /** Creates a per-player inventory snapshot from a window template. */
    public GuiInventory(GuiWindow buttons, Inventory inventory) {
        this(new HashMap<>(buttons.getButtons()), inventory);
    }

    /** Sets or replaces a button in this player-scoped inventory snapshot. */
    public void setButton(int slot, Button button) {
        this.buttons.put(slot, button);
        this.inventory.setItem(slot, button.getItem());
    }

    /** Removes a button from this player-scoped inventory snapshot. */
    public void removeButton(int slot) {
        this.buttons.remove(slot);
        this.inventory.setItem(slot, null);
    }
}
