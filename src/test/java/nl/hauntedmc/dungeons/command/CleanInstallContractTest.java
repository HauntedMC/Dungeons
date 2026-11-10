package nl.hauntedmc.dungeons.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.StaticDungeon;
import nl.hauntedmc.dungeons.support.*;
import nl.hauntedmc.dungeons.util.item.ItemUtils;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class CleanInstallContractTest {
    @TempDir Path directory;

    @Test
    void retiredCommandsCannotMutateStateOrAppearInCompletion() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            var c = new CommandContractTest.CommandFixture(f);
            c.permissions.add("dungeons.role.operator");
            f.paper.runtime.setDungeonCatalog(c.catalog);
            for (String root :
                    List.of(
                            "delete",
                            "reload",
                            "join",
                            "kick",
                            "setcooldown",
                            "player",
                            "instance",
                            "addkey",
                            "setexit")) {
                c.execute(root, "crypt");
                assertFalse(
                        c.root.onTabComplete(
                                        c.caller.player,
                                        mock(Command.class),
                                        "dungeon",
                                        new String[] {root})
                                .contains(root));
            }
            verifyNoInteractions(c.lifecycle, c.coordinator);
            verify(c.catalog, never()).remove(any());
            assertTrue(f.dungeon.getFolder().exists());
        }
    }

    @Test
    void separateGeneratorFilesHaveNoEffectAndAreNotRewritten() throws Exception {
        try (var f = new RunFixture(directory, new YamlConfiguration())) {
            Path folder = Files.createDirectories(directory.resolve("fresh"));
            Files.writeString(
                    folder.resolve("config.yml"),
                    "generator:\n  branching:\n    trunk:\n      straightness: 0.25\n");
            String retired = "branching:\n  trunk:\n    straightness: 0.95\n";
            Files.writeString(folder.resolve("generation.yml"), retired);
            var dungeon = new StaticDungeon(f.paper.runtime, folder.toFile(), null);
            assertEquals(
                    0.25, dungeon.getConfig().getDouble("generator.branching.trunk.straightness"));
            assertEquals(retired, Files.readString(folder.resolve("generation.yml")));
            assertFalse(Files.exists(folder.resolve("generation.yml.migrated")));
        }
    }

    @Test
    void toolRecognitionRequiresPersistentIdentityAndTheConfiguredMaterial() {
        try (var paper = new PaperBoundary()) {
            when(paper.plugin.namespace()).thenReturn("dungeons");
            paper.runtime.setFunctionBuilderMaterial(Material.STICK);
            paper.runtime.setRoomEditorMaterial(Material.BLAZE_ROD);
            var key = new NamespacedKey(paper.plugin, "functiontool");
            assertFalse(ItemUtils.isFunctionTool(TaggedItems.stack(Material.STICK, 1, Map.of())));
            assertFalse(
                    ItemUtils.isFunctionTool(
                            TaggedItems.stack(Material.BLAZE_ROD, 1, Map.of(key, 1))));
            assertTrue(
                    ItemUtils.isFunctionTool(TaggedItems.stack(Material.STICK, 1, Map.of(key, 1))));
            assertFalse(ItemUtils.isRoomTool(TaggedItems.stack(Material.BLAZE_ROD, 1, Map.of())));
            assertTrue(
                    ItemUtils.isRoomTool(
                            TaggedItems.stack(
                                    Material.BLAZE_ROD,
                                    1,
                                    Map.of(new NamespacedKey(paper.plugin, "roomtool"), 1))));
        }
    }
}
