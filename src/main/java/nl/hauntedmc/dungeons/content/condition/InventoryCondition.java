package nl.hauntedmc.dungeons.content.condition;

import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ValueMenuItem;
import nl.hauntedmc.dungeons.model.element.TriggerCondition;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.inventory.ItemStack;

/** Player Inventory. */
@AutoRegister(id = "dungeons.condition.inventory")
@SerializableAs("dungeons.condition.inventory")
public class InventoryCondition extends TriggerCondition {
    public InventoryCondition() {
        super("Player Inventory");
    }

    public InventoryCondition(Map<String, Object> config) {
        super("Player Inventory", config);
    }

    @PersistedField private Material material = Material.TRIPWIRE_HOOK;
    @PersistedField private int count = 1;

    @Override
    public boolean check(TriggerFireEvent event) {
        if (event == null
                || event.getPlayerSession() == null
                || event.getInstance() != instance
                || event.getPlayerSession().getInstance() != instance
                || event.getPlayerSession().isObserving()
                || event.getPlayer() == null
                || material == null
                || (material == Material.AIR
                        || material == Material.CAVE_AIR
                        || material == Material.VOID_AIR)
                || count < 0) return false;
        long found = 0;
        for (ItemStack item : event.getPlayer().getInventory().getStorageContents()) {
            if (item != null && item.getType() == material) found += Math.max(0, item.getAmount());
        }
        return found >= count;
    }

    @Override
    public void buildHotbarMenu() {
        menu.addMenuItem(
                new ValueMenuItem(
                        "Material",
                        () -> material,
                        v -> {
                            Material parsed = Material.matchMaterial(v);
                            if (parsed == null || parsed.isAir() || !parsed.isItem())
                                throw new IllegalArgumentException();
                            material = parsed;
                        }));
        menu.addMenuItem(
                new ValueMenuItem(
                        "Required items",
                        () -> count,
                        v -> count = ValueMenuItem.nonNegativeInt(v)));
    }

    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.CHEST);
        button.setDisplayName("&dPlayer Inventory");
        return button;
    }
}
