package nl.hauntedmc.dungeons.gui.framework.window;

import java.util.HashMap;
import java.util.Map;
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
        this(cloneButtons(buttons.getButtons()), inventory);
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

    private static HashMap<Integer, Button> cloneButtons(Map<Integer, Button> source) {
        HashMap<Integer, Button> clones = new HashMap<>();
        for (Map.Entry<Integer, Button> entry : source.entrySet()) {
            Button button = entry.getValue();
            clones.put(entry.getKey(), button == null ? null : button.clone());
        }
        return clones;
    }
}
