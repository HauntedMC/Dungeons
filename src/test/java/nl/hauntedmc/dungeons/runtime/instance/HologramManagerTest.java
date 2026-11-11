package nl.hauntedmc.dungeons.runtime.instance;

import static org.mockito.Mockito.*;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class HologramManagerTest {
    @Test
    void displaysUseTheirOwnersTagWithoutTheGlobalRuntimeBridge() {
        var plugin = mock(Plugin.class);
        when(plugin.namespace()).thenReturn("dungeons");
        var world = mock(World.class);
        var location = new Location(world, 1, 2, 3);
        var display = mock(TextDisplay.class);
        var data = mock(PersistentDataContainer.class);
        when(display.getPersistentDataContainer()).thenReturn(data);
        when(world.getEntitiesByClass(TextDisplay.class)).thenReturn(List.of());
        when(world.spawn(location, TextDisplay.class)).thenReturn(display);
        var manager = new HologramManager(plugin);
        manager.createHologram(location, 32, "Label", true);
        verify(data)
                .set(
                        new NamespacedKey(plugin, "dungeonhologram"),
                        PersistentDataType.BOOLEAN,
                        true);
        manager.clearAllHolograms();
        verify(display).remove();
    }
}
