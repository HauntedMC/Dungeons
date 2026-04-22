package nl.hauntedmc.dungeons.runtime.player;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Captured copy of the first nine hotbar slots and held-slot index.
 */
public class PlayerHotbarSnapshot {
    private final List<ItemStack> items;
    private int heldSlot;

    /**
     * Captures the current hotbar state from a player inventory.
     */
    public PlayerHotbarSnapshot(Player player) {
        this.items = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        this.heldSlot = inventory.getHeldItemSlot();

        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                this.items.add(inventory.getItem(i));
            } else {
                this.items.add(new ItemStack(Material.AIR));
            }
        }
    }

    /**
     * Creates an empty snapshot with air in all nine slots.
     */
    public PlayerHotbarSnapshot() {
        this.items = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            this.items.add(new ItemStack(Material.AIR));
        }
    }

    /** Replaces the item at one hotbar index in this snapshot. */
    public void setItem(int index, ItemStack item) {
        this.items.set(index, item);
    }

    /** Applies the snapshot to an inventory without changing held slot. */
    public void applyTo(PlayerInventory inventory) {
        this.applyTo(inventory, false);
    }

    /** Applies the snapshot to an inventory and optionally restores held slot. */
    public void applyTo(PlayerInventory inventory, boolean restoreHeldSlot) {
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, this.items.get(i));
        }

        if (restoreHeldSlot) {
            inventory.setHeldItemSlot(this.heldSlot);
        }
    }

    /** Returns the captured item at one hotbar index. */
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }
}
