package nl.hauntedmc.dungeons.model.instance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.function.SetStatusFunction;
import nl.hauntedmc.dungeons.content.instance.edit.BranchingEditableInstance;
import nl.hauntedmc.dungeons.generation.layout.BranchingLayout;
import nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.registry.LayoutRegistry;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.metadata.TypeMetadataSupport;
import nl.hauntedmc.dungeons.util.world.GameRuleUtils;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

class BranchingEditorPersistenceContractTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void branchingEditorSavesWorldRulesRoomMechanicsAndSchematicsAsOneCompletedOperation(
            boolean writableSchematic) throws Exception {
        try (var aliases = new StaticMapSnapshot(ConfigurationSerialization.class, "aliases");
                var layouts = new StaticMapSnapshot(LayoutRegistry.class, "layouts");
                var f = new RunFixture(directory, new YamlConfiguration());
                var menus = mockStatic(RoomMenus.class);
                var hotbars = new HeadlessMenus();
                var gamerules = mockStatic(GameRuleUtils.class)) {
            LayoutRegistry.register(BranchingLayout.class, "branching");
            TypeMetadataSupport.registerConfigurationSerializable(SetStatusFunction.class);
            TypeMetadataSupport.registerConfigurationSerializable(FunctionTargetType.class);
            var folder = Files.createDirectories(directory.resolve("branch"));
            var settings = new YamlConfiguration();
            settings.set("generator.seal_open_connectors_with", "AIR");
            var dungeon = new BranchingDungeon(f.paper.runtime, folder.toFile(), settings);
            var room =
                    new BranchingRoomDefinition(dungeon, "hall", new BoundingBox(0, 0, 0, 1, 1, 1));
            if (!writableSchematic) {
                var target = Files.createDirectories(folder.resolve("rooms/hall.struct"));
                Files.writeString(target.resolve("keep"), "previous content");
            }
            var status = new SetStatusFunction(Map.of("status", "saved"));
            status.initializeFields();
            status.setLocation(new Location(null, 0, 0, 0));
            room.addFunction(status.getLocation(), status);
            dungeon.getUniqueRooms().put("hall", room);
            var source = Files.createDirectories(directory.resolve("live-branch"));
            Files.writeString(source.resolve("level.dat"), "edited branching world");
            when(f.world.getWorldFolder()).thenReturn(source.toFile());
            gamerules.when(() -> GameRuleUtils.capture(f.world, dungeon.getRuleConfig()))
                    .thenAnswer(call -> {
                        dungeon.getRuleConfig().set("Gamerule.spawn_mobs", false);
                        return null;
                    });
            when(f.world.getDifficulty()).thenReturn(Difficulty.HARD);
            var air = mock(Block.class);
            when(air.getType()).thenReturn(Material.AIR);
            when(f.world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
            var editor = new Editor(dungeon, f.world);
            var latch = new CountDownLatch(1);
            assertEquals(writableSchematic, editor.saveWorldAsync(latch).get(1, TimeUnit.SECONDS));
            assertEquals(0, latch.getCount());
            assertFalse(dungeon.isSaving());
            verify(f.world).save(true);
            if (!writableSchematic) {
                assertTrue(room.isChangedSinceLastSave());
                assertEquals(
                        "previous content",
                        Files.readString(folder.resolve("rooms/hall.struct/keep")));
                return;
            }
            assertEquals("edited branching world", Files.readString(folder.resolve("level.dat")));
            var rules =
                    YamlConfiguration.loadConfiguration(folder.resolve("gamerules.yml").toFile());
            assertEquals("HARD", rules.getString("Difficulty"));
            assertEquals("false", rules.getString("Gamerule.spawn_mobs"));
            var loaded =
                    new BranchingRoomDefinition(dungeon, folder.resolve("rooms/hall.yml").toFile());
            assertEquals(1, loaded.getFunctionsMapRelative().size());
            assertNotNull(loaded.getSchematic());
            assertEquals(0, loaded.getSchematic().size());
        }
    }

    static class Editor extends BranchingEditableInstance {
        Editor(BranchingDungeon dungeon, World world) {
            super(dungeon, null);
            instanceWorld = world;
            startLocation = new Location(world, 0, 70, 0);
        }
    }
}
