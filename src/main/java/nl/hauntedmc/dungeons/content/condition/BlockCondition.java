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

/** Tests a block at an offset from the owning function without loading a chunk. */
@AutoRegister(id = "dungeons.condition.block")
@SerializableAs("dungeons.condition.block")
public class BlockCondition extends TriggerCondition {
    @PersistedField private Material material = Material.REDSTONE_BLOCK;
    @PersistedField private int offsetX = 0;
    @PersistedField private int offsetY = -1;
    @PersistedField private int offsetZ = 0;

    public BlockCondition() {
        super("Block Material");
    }

    public BlockCondition(Map<String, Object> config) {
        super("Block Material", config);
    }

    @Override
    public boolean check(TriggerFireEvent event) {
        if (location == null || location.getWorld() == null || material == null) return false;
        var target = location.clone().add(offsetX, offsetY, offsetZ);
        var world = target.getWorld();
        int x = target.getBlockX(), y = target.getBlockY(), z = target.getBlockZ();
        return y >= world.getMinHeight()
                && y < world.getMaxHeight()
                && world.isChunkLoaded(x >> 4, z >> 4)
                && world.getBlockAt(x, y, z).getType() == material;
    }

    @Override
    public void buildHotbarMenu() {
        menu.addMenuItem(
                new ValueMenuItem(
                        "Block material",
                        () -> material,
                        value -> {
                            Material parsed = Material.matchMaterial(value);
                            if (parsed == null || !parsed.isBlock())
                                throw new IllegalArgumentException();
                            material = parsed;
                        }));
        menu.addMenuItem(
                new ValueMenuItem("Offset X", () -> offsetX, v -> offsetX = Integer.parseInt(v)));
        menu.addMenuItem(
                new ValueMenuItem("Offset Y", () -> offsetY, v -> offsetY = Integer.parseInt(v)));
        menu.addMenuItem(
                new ValueMenuItem("Offset Z", () -> offsetZ, v -> offsetZ = Integer.parseInt(v)));
    }

    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.REDSTONE_BLOCK);
        button.setDisplayName("&dBlock Material");
        return button;
    }
}
