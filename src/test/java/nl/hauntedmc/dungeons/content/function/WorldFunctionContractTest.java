package nl.hauntedmc.dungeons.content.function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import nl.hauntedmc.dungeons.support.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldFunctionContractTest {
    @TempDir Path directory;
    @Test
    void chunkLoaderTogglesTheConfiguredNeighborhoodAndReleasesItOnDisable() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            Map<String,Chunk> chunks=new HashMap<>(); Map<String,Boolean> forced=new HashMap<>();
            java.util.function.BiFunction<Integer,Integer,Chunk> lookup=(x,z) -> chunks.computeIfAbsent(x+","+z,key -> {
                var chunk=mock(Chunk.class); when(chunk.getWorld()).thenReturn(f.world); when(chunk.getX()).thenReturn(x); when(chunk.getZ()).thenReturn(z);
                when(chunk.isForceLoaded()).thenAnswer(c -> forced.getOrDefault(key,false));
                doAnswer(c -> { forced.put(key,c.getArgument(0)); return null; }).when(chunk).setForceLoaded(anyBoolean()); return chunk;
            });
            when(f.world.getChunkAt(anyInt(),anyInt())).thenAnswer(c -> lookup.apply(c.getArgument(0),c.getArgument(1)));
            when(f.world.getChunkAt(any(Location.class))).thenAnswer(c -> { Location loc=c.getArgument(0); return lookup.apply(loc.getBlockX()>>4,loc.getBlockZ()>>4); });
            lookup.apply(100,100).setForceLoaded(true);
            var loader=GameplayFunctionContractTest.bind(new ChunkLoadFunction(Map.of("radius",1)),f);
            loader.runFunction(null,List.of()); assertEquals(10,chunks.size());
            assertTrue(forced.entrySet().stream().allMatch(e -> e.getValue()));
            loader.runFunction(null,List.of()); assertEquals(1,forced.values().stream().filter(Boolean::booleanValue).count());
            loader.runFunction(null,List.of()); loader.onDisable();
            assertEquals(1,forced.values().stream().filter(Boolean::booleanValue).count()); assertTrue(forced.get("100,100"));
        }
    }
    @Test
    void hologramsToggleVisibilityAndCleanupOnlyDisplaysOwnedByTheRun() throws Exception {
        try(var f=new RunFixture(directory,new YamlConfiguration())) {
            var display=mock(org.bukkit.entity.TextDisplay.class); when(display.isValid()).thenReturn(true);
            when(display.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
            when(f.world.spawn(any(Location.class),eq(org.bukkit.entity.TextDisplay.class))).thenReturn(display);
            var outsider=mock(org.bukkit.entity.TextDisplay.class);
            when(outsider.getPersistentDataContainer()).thenReturn(mock(org.bukkit.persistence.PersistentDataContainer.class));
            when(f.world.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)).thenReturn(List.of(display,outsider));
            var hologram=GameplayFunctionContractTest.bind(new HologramFunction(Map.of("message","hello","radius",32,"visibleByDefault",true)),f);
            hologram.onEnable(); assertTrue(hologram.isVisible());
            hologram.runFunction(null,List.of()); assertFalse(hologram.isVisible()); verify(display).setViewRange(0f);
            hologram.runFunction(null,List.of()); assertTrue(hologram.isVisible()); verify(display,atLeastOnce()).setViewRange(.5f);
            hologram.onDisable(); f.run.clearHolograms();
            verify(display).remove(); verify(outsider,never()).remove();
        }
    }

}
