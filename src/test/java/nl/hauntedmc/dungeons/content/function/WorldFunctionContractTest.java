package nl.hauntedmc.dungeons.content.function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import nl.hauntedmc.dungeons.support.*;

import org.bukkit.*;
import org.bukkit.Axis;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;

class WorldFunctionContractTest {
    @TempDir Path directory;

    @Test
    void chunkLoaderTogglesTheConfiguredNeighborhoodAndReleasesItOnDisable() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Map<String, Chunk> chunks = new HashMap<>();
            Map<String, Boolean> forced = new HashMap<>();
            BiFunction<Integer, Integer, Chunk> lookup =
                    (x, z) ->
                            chunks.computeIfAbsent(
                                    x + "," + z,
                                    key -> {
                                        var chunk = mock(Chunk.class);
                                        when(chunk.getWorld()).thenReturn(f.world);
                                        when(chunk.getX()).thenReturn(x);
                                        when(chunk.getZ()).thenReturn(z);
                                        when(chunk.isForceLoaded())
                                                .thenAnswer(c -> forced.getOrDefault(key, false));
                                        doAnswer(
                                                        c -> {
                                                            forced.put(key, c.getArgument(0));
                                                            return null;
                                                        })
                                                .when(chunk)
                                                .setForceLoaded(anyBoolean());
                                        return chunk;
                                    });
            when(f.world.getChunkAt(anyInt(), anyInt()))
                    .thenAnswer(c -> lookup.apply(c.getArgument(0), c.getArgument(1)));
            when(f.world.getChunkAt(any(Location.class)))
                    .thenAnswer(
                            c -> {
                                Location loc = c.getArgument(0);
                                return lookup.apply(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                            });
            lookup.apply(100, 100).setForceLoaded(true);
            var loader =
                    GameplayFunctionContractTest.bind(
                            new ChunkLoadFunction(Map.of("radius", 1)), f);
            loader.runFunction(null, List.of());
            assertEquals(10, chunks.size());
            assertTrue(forced.entrySet().stream().allMatch(e -> e.getValue()));
            loader.runFunction(null, List.of());
            assertEquals(1, forced.values().stream().filter(Boolean::booleanValue).count());
            loader.runFunction(null, List.of());
            loader.onDisable();
            assertEquals(1, forced.values().stream().filter(Boolean::booleanValue).count());
            assertTrue(forced.get("100,100"));
        }
    }

    @Test
    void hologramsToggleVisibilityAndCleanupOnlyDisplaysOwnedByTheRun() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var display = mock(TextDisplay.class);
            when(display.isValid()).thenReturn(true);
            when(display.getPersistentDataContainer())
                    .thenReturn(mock(PersistentDataContainer.class));
            when(f.world.spawn(any(Location.class), eq(TextDisplay.class))).thenReturn(display);
            var outsider = mock(TextDisplay.class);
            when(outsider.getPersistentDataContainer())
                    .thenReturn(mock(PersistentDataContainer.class));
            when(f.world.getEntitiesByClass(TextDisplay.class))
                    .thenReturn(List.of(display, outsider));
            var hologram =
                    GameplayFunctionContractTest.bind(
                            new HologramFunction(
                                    Map.of(
                                            "message",
                                            "hello",
                                            "radius",
                                            32,
                                            "visibleByDefault",
                                            true)),
                            f);
            hologram.onEnable();
            assertTrue(hologram.isVisible());
            hologram.runFunction(null, List.of());
            assertFalse(hologram.isVisible());
            verify(display).setViewRange(0f);
            hologram.runFunction(null, List.of());
            assertTrue(hologram.isVisible());
            verify(display, atLeastOnce()).setViewRange(.5f);
            hologram.onDisable();
            f.run.clearHolograms();
            verify(display).remove();
            verify(outsider, never()).remove();
        }
    }

    @Test
    void lockedDoorsDenyInteractionAndUnlockingChangesTheRealBlockState() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var state = new PlayerState();
            state.location = new Location(f.world, 0, 70, 0);
            var block = mock(Block.class);
            when(block.getLocation()).thenReturn(state.location.clone());
            var data = mock(Openable.class);
            boolean[] opened = {false};
            when(data.isOpen()).thenAnswer(c -> opened[0]);
            doAnswer(
                            c -> {
                                opened[0] = c.getArgument(0);
                                return null;
                            })
                    .when(data)
                    .setOpen(anyBoolean());
            when(block.getBlockData()).thenReturn(data);
            when(f.world.getBlockAt(any(Location.class))).thenReturn(block);
            var door =
                    GameplayFunctionContractTest.bind(
                            new DoorControlFunction(
                                    Map.of("lockedByDefault", true, "openOnUnlock", true)),
                            f);
            door.onEnable();
            var event = mock(PlayerInteractEvent.class);
            when(event.getPlayer()).thenReturn(state.player);
            when(event.getHand()).thenReturn(EquipmentSlot.HAND);
            when(event.getClickedBlock()).thenReturn(block);
            door.onInteractDoor(event);
            verify(event).setUseInteractedBlock(Event.Result.DENY);
            verify(event).setUseItemInHand(Event.Result.DENY);
            door.runFunction(null, List.of());
            assertFalse(door.isLocked());
            assertTrue(opened[0]);
            door.runFunction(null, List.of());
            assertTrue(door.isLocked());
            assertFalse(opened[0]);
            verify(block, times(2)).setBlockData(data);
        }
    }

    @Test
    void disablingRedstonePulsesCancelsBothTheRepeaterAndItsPendingBlockReset() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var block = mock(Block.class);
            when(f.world.getBlockAt(any(Location.class))).thenReturn(block);
            Material[] current = {Material.AIR};
            doAnswer(
                            c -> {
                                current[0] = c.getArgument(0);
                                return null;
                            })
                    .when(block)
                    .setType(any(Material.class));
            var pulse =
                    GameplayFunctionContractTest.bind(
                            new RedstoneBlockFunction(Map.of("count", 2, "rate", 3)), f);
            pulse.runFunction(null, List.of());
            var timer = f.scheduler.jobs.getFirst();
            timer.run();
            assertEquals(Material.REDSTONE_BLOCK, current[0]);
            assertEquals(2, f.scheduler.jobs.size());
            pulse.onDisable();
            assertTrue(f.scheduler.jobs.stream().allMatch(job -> job.cancelled));
            f.scheduler.jobs.getLast().run();
            assertEquals(Material.REDSTONE_BLOCK, current[0]);
        }
    }

    @Test
    void clonedRedstoneMechanicsCannotCancelTheirSourcesScheduledPulseOrReset() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus()) {
            var block = mock(Block.class);
            when(f.world.getBlockAt(any(Location.class))).thenReturn(block);
            var pulse =
                    GameplayFunctionContractTest.bind(
                            new RedstoneBlockFunction(Map.of("count", 2)), f);
            pulse.runFunction(null, List.of());
            f.scheduler.jobs.getFirst().run();
            var copy = pulse.clone();
            assertNotNull(copy);
            copy.onDisable();
            assertTrue(f.scheduler.jobs.stream().noneMatch(job -> job.cancelled));
            pulse.onDisable();
            assertTrue(f.scheduler.jobs.stream().allMatch(job -> job.cancelled));
        }
    }

    @Test
    void clonedMobSpawnersKeepActivationCountsAndPendingTasksIndependent() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var menus = new HeadlessMenus()) {
            var block = mock(Block.class);
            when(f.world.getBlockAt(any(Location.class))).thenReturn(block);
            // Count and placement contracts stop at Paper's spawn boundary; entity attributes need
            // its registry.
            when(f.world.spawnEntity(any(Location.class), any(EntityType.class)))
                    .thenReturn(mock(Entity.class));
            var source =
                    new SpawnMobFunction(
                            Map.of("mob", "not-a-mob", "maxCount", 2, "delay", 3, "interval", 4));
            source.initializeFields();
            source.setInstance(f.run);
            source.setLocation(new Location(f.world, 0, 70, 0));
            source.runFunction(null, List.of());
            source.runFunction(null, List.of());
            var copy = (SpawnMobFunction) source.clone();
            copy.runFunction(null, List.of());
            assertEquals(3, f.scheduler.jobs.size());
            assertEquals(3, f.scheduler.jobs.getFirst().delay);
            assertEquals(4, f.scheduler.jobs.getFirst().period);
            copy.onDisable();
            assertFalse(f.scheduler.jobs.getFirst().cancelled);
            assertFalse(f.scheduler.jobs.get(1).cancelled);
            assertTrue(f.scheduler.jobs.get(2).cancelled);
            for (int tick = 0; tick < 3; tick++) {
                f.scheduler.jobs.getFirst().run();
                f.scheduler.jobs.get(1).run();
            }
            verify(f.world, times(4))
                    .spawnEntity(eq(new Location(f.world, 0.5, 70.1, 0.5)), eq(EntityType.ZOMBIE));
            assertTrue(f.scheduler.jobs.getFirst().cancelled);
            assertTrue(f.scheduler.jobs.get(1).cancelled);
            assertEquals(new Location(f.world, 0, 70, 0), source.getLocation());
        }
    }

    @Test
    void blockEditingAppliesDirectionalStateAndRemovesOnlyTheConfiguredMaterial() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration());
                var materials = mockStatic(Material.class)) {
            var material = mock(Material.class);
            when(material.isBlock()).thenReturn(true);
            materials.when(() -> Material.valueOf("TEST_BLOCK")).thenReturn(material);
            materials
                    .when(() -> Material.valueOf("MISSING"))
                    .thenThrow(new IllegalArgumentException("unknown material"));
            var block = mock(Block.class);
            var state = mock(BlockState.class);
            when(f.world.getBlockAt(any(Location.class))).thenReturn(block);
            when(block.getState()).thenReturn(state);
            var directional = mock(Directional.class);
            var rotatable = mock(Rotatable.class);
            var orientable = mock(Orientable.class);
            var data = List.<BlockData>of(directional, rotatable, orientable);
            for (int index = 0; index < data.size(); index++) {
                clearInvocations(block, state);
                when(block.getBlockData()).thenReturn(data.get(index));
                var function =
                        new BlockEditorFunction(
                                Map.of(
                                        "blockType",
                                        "TEST_BLOCK",
                                        "direction",
                                        List.of("SOUTH", "WEST", "X").get(index)));
                function.initializeFields();
                function.setInstance(f.run);
                function.setLocation(new Location(f.world, 0, 70, 0));
                function.runFunction(null, List.of());
                verify(block).setType(material);
                verify(state).setBlockData(data.get(index));
                verify(state).update(true);
            }
            verify(directional).setFacing(BlockFace.SOUTH);
            verify(rotatable).setRotation(BlockFace.WEST);
            verify(orientable).setAxis(Axis.X);
            clearInvocations(block, state);
            var invalid = new BlockEditorFunction(Map.of("blockType", "missing"));
            invalid.initializeFields();
            invalid.setInstance(f.run);
            invalid.setLocation(new Location(f.world, 0, 70, 0));
            invalid.runFunction(null, List.of());
            verify(block, never()).setType(any(Material.class));
            var remove = new BlockEditorFunction(Map.of("blockType", "TEST_BLOCK", "remove", true));
            remove.initializeFields();
            remove.setInstance(f.run);
            remove.setLocation(new Location(f.world, 0, 70, 0));
            when(block.getType()).thenReturn(Material.AIR);
            remove.runFunction(null, List.of());
            verify(block, never()).setType(any(Material.class));
            when(block.getType()).thenReturn(material);
            remove.runFunction(null, List.of());
            verify(block).setType(Material.AIR);
        }
    }
}
